package com.coobird.staticlogistics.transfer;

import com.coobird.staticlogistics.api.CapGetter;
import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.api.event.PostTransferEvent;
import com.coobird.staticlogistics.api.event.PreTransferEvent;
import com.coobird.staticlogistics.logistics.node.ContainerConfig;
import com.coobird.staticlogistics.logistics.node.LinkManager;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.common.MinecraftForge;
import org.slf4j.Logger;

import java.util.List;

/**
 * 传输管线 —— 编排一次完整的传输流程。
 */
public final class TransferPipeline {
    private static final Logger LOGGER = LogUtils.getLogger();

    private TransferPipeline() {
    }

    public static <C, T> boolean execute(
        ServerLevel localLevel, BlockPos localPos, Direction localFace,
        List<LogisticsNode> destinations, CapGetter<C> capGetter,
        long limit, TransferUtils.TransferProtocol<C, T> protocol, boolean isPullMode,
        TransferContext context
    ) {
        if (context != null && context.isDepthExceeded()) {
            LOGGER.debug("Depth exceeded for transfer at {} (depth={})", localPos, context.depth());
            return false;
        }
        if (destinations.isEmpty() || limit <= 0) return false;

        long remaining = limit;

        LinkManager localMgr = LinkManager.get(localLevel);
        ContainerConfig localContainer = localMgr.getContainerConfig(localPos);
        if (localContainer == null && context != null && context.sourceConfig() != null) {
            localContainer = context.sourceConfig().getContainerConfig();
        }
        if (localContainer == null) return false;

        boolean canCrossDim = LogisticsCalculator.isDimensionEffective(localContainer);
        C localCap = capGetter.get(localLevel, localPos, localFace);
        if (localCap == null) return false;

        boolean movedAny = false;

        for (LogisticsNode remoteNode : destinations) {
            boolean isSameDim = remoteNode.isInSameDimension(localLevel.dimension());

            if (!isSameDim && !canCrossDim) {
                if (context != null) logFailure(context, remoteNode, TransferFailureReason.NO_DIMENSION_UPGRADE);
                continue;
            }

            if (isSameDim && LogisticsCalculator.isOutOfRange(localPos, remoteNode.gPos().pos(), localContainer)) {
                if (context != null) logFailure(context, remoteNode, TransferFailureReason.OUT_OF_RANGE);
                continue;
            }

            ServerLevel remoteLevel = isSameDim ? localLevel :
                localLevel.getServer().getLevel(remoteNode.gPos().dimension());
            if (remoteLevel == null || !remoteLevel.getChunkSource().hasChunk(
                remoteNode.gPos().pos().getX() >> 4, remoteNode.gPos().pos().getZ() >> 4)) {
                if (context != null) logFailure(context, remoteNode, TransferFailureReason.CHUNK_UNLOADED);
                continue;
            }

            C remoteCap = capGetter.get(remoteLevel, remoteNode.gPos().pos(), remoteNode.face());
            if (remoteCap == null) {
                if (context != null && context.sourceConfig() != null
                    && remoteLevel.getBlockEntity(remoteNode.gPos().pos()) == null) {
                    cleanStaleTarget(remoteNode, context);
                }
                if (context != null) logFailure(context, remoteNode, TransferFailureReason.CAPABILITY_NULL);
                continue;
            }

            C from = isPullMode ? remoteCap : localCap;
            C to = isPullMode ? localCap : remoteCap;

            // Pre 事件
            if (context != null) {
                LogisticsNode srcNode = context.isPullMode() ? remoteNode : context.sourceNode();
                LogisticsNode dstNode = context.isPullMode() ? context.sourceNode() : remoteNode;
                PreTransferEvent preEvent = new PreTransferEvent(
                    srcNode, dstNode, context.typeId(), remaining);
                MinecraftForge.EVENT_BUS.post(preEvent);
                if (preEvent.isCanceled()) {
                    logFailure(context, remoteNode, TransferFailureReason.EVENT_CANCELLED);
                    continue;
                }
            }

            long targetAccepted = 0;
            boolean transferComplete = true;
            TransferFailureReason commitFailure = null;
            ExtractionResult<T> simulated = protocol.simulateExtract(from, remaining);
            if (!protocol.isEmpty(simulated) && protocol.canInsert(to, simulated.value(), remoteNode)) {
                long simulatedAccepted = Math.min(remaining,
                    Math.max(0L, protocol.simulateInsert(to, simulated.value())));
                if (simulatedAccepted > 0L) {
                    TransferTransaction.Result result = TransferTransaction.commit(
                        protocol, from, to, simulated, simulatedAccepted, remoteNode);
                    targetAccepted = result.accepted();
                    transferComplete = result.failure() == TransferTransaction.Failure.NONE;
                    if (result.failure() == TransferTransaction.Failure.SOURCE_COMMIT_FAILED) {
                        commitFailure = TransferFailureReason.SOURCE_COMMIT_FAILED;
                    } else if (result.failure() == TransferTransaction.Failure.ROLLBACK_FAILED) {
                        commitFailure = TransferFailureReason.ROLLBACK_FAILED;
                    }
                    if (context != null && targetAccepted <= 0L && commitFailure != null) {
                        logFailure(context, remoteNode, commitFailure);
                    }
                } else if (context != null) {
                    logFailure(context, remoteNode, TransferFailureReason.TARGET_REJECTED);
                }
            } else if (!protocol.isEmpty(simulated) && context != null) {
                logFailure(context, remoteNode, TransferFailureReason.TARGET_REJECTED);
            }

            if (targetAccepted > 0) {
                remaining -= targetAccepted;
                movedAny = true;
            }

            // 日志 + Post 事件
            if (targetAccepted > 0 && context != null) {
                LogisticsNode srcNode = context.isPullMode() ? remoteNode : context.sourceNode();
                LogisticsNode dstNode = context.isPullMode() ? context.sourceNode() : remoteNode;
                TransferLogManager.get(localLevel.getServer()).logTransfer(
                    srcNode, dstNode, context.type(), targetAccepted, transferComplete, commitFailure);
                PostTransferEvent postEvent = new PostTransferEvent(
                    srcNode, dstNode, context.typeId(), targetAccepted, transferComplete);
                MinecraftForge.EVENT_BUS.post(postEvent);
            }
            if (remaining <= 0) break;
        }
        return movedAny;
    }

    private static void logFailure(TransferContext context, LogisticsNode remoteNode, TransferFailureReason reason) {
        LogisticsNode srcNode = context.isPullMode() ? remoteNode : context.sourceNode();
        LogisticsNode dstNode = context.isPullMode() ? context.sourceNode() : remoteNode;
        TransferLogManager.get(context.level().getServer())
            .logTransfer(srcNode, dstNode, context.type(), 0, false, reason);
    }

    private static void cleanStaleTarget(LogisticsNode remoteNode, TransferContext ctx) {
        ctx.linkManager().removeLink(ctx.sourceNode(), remoteNode);
    }

}
