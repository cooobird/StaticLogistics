package com.coobird.staticlogistics.transfer.handler;

import com.coobird.staticlogistics.api.CapGetter;
import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.api.event.PostTransferEvent;
import com.coobird.staticlogistics.api.event.PreTransferEvent;
import com.coobird.staticlogistics.storage.link.LinkManager;
import com.coobird.staticlogistics.storage.model.ContainerConfig;
import com.coobird.staticlogistics.storage.model.FaceConfigComposite;
import com.coobird.staticlogistics.transfer.TransferContext;
import com.coobird.staticlogistics.transfer.TransferFailureReason;
import com.coobird.staticlogistics.transfer.log.TransferLogManager;
import com.coobird.staticlogistics.util.LogisticsCalculator;
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
            localContainer = context.sourceConfig().sharedContainerConfig;
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
                    cleanStaleTarget(context.sourceConfig(), remoteNode, context);
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
                PreTransferEvent preEvent = new PreTransferEvent(srcNode, dstNode, context.type(), remaining);
                MinecraftForge.EVENT_BUS.post(preEvent);
                if (preEvent.isCanceled()) {
                    logFailure(context, remoteNode, TransferFailureReason.EVENT_CANCELLED);
                    continue;
                }
            }

            // 一对一传输
            long targetAccepted = 0;
            if (context != null && context.type().isSimpleResource()) {
                targetAccepted = transferSimpleResource(protocol, from, to, remaining);
            } else {
                ExtractionResult<T> extracted = protocol.simulateExtract(from, remaining);
                if (!protocol.isEmpty(extracted)) {
                    if (protocol.canInsert(to, extracted.value(), remoteNode)) {
                        long accepted = protocol.executeInsert(to, extracted.value());
                        if (accepted > 0) {
                            protocol.commitExtract(from, extracted, accepted);
                            targetAccepted = accepted;
                        }
                    }
                }
            }

            if (targetAccepted > 0) {
                remaining -= targetAccepted;
                movedAny = true;
            }

            // 日志 + Post 事件
            if (targetAccepted > 0 && context != null) {
                LogisticsNode srcNode = context.isPullMode() ? remoteNode : context.sourceNode();
                LogisticsNode dstNode = context.isPullMode() ? context.sourceNode() : remoteNode;
                TransferLogManager.get().logTransfer(srcNode, dstNode, context.type(), targetAccepted, true);
                PostTransferEvent postEvent = new PostTransferEvent(srcNode, dstNode, context.type(), targetAccepted, true);
                MinecraftForge.EVENT_BUS.post(postEvent);
            }
            if (remaining <= 0) break;
        }
        return movedAny;
    }

    private static void logFailure(TransferContext context, LogisticsNode remoteNode, TransferFailureReason reason) {
        LogisticsNode srcNode = context.isPullMode() ? remoteNode : context.sourceNode();
        LogisticsNode dstNode = context.isPullMode() ? context.sourceNode() : remoteNode;
        TransferLogManager.get().logTransfer(srcNode, dstNode, context.type(), 0, false, reason);
    }

    private static void cleanStaleTarget(FaceConfigComposite sourceCfg, LogisticsNode remoteNode, TransferContext ctx) {
        sourceCfg.getLinkedNodes().remove(remoteNode);
        LinkManager mgr = ctx.linkManager();
        FaceConfigComposite targetCfg = mgr.getFaceConfig(remoteNode.toKey());
        if (targetCfg != null) {
            targetCfg.getLinkedNodes().remove(ctx.sourceNode());
            targetCfg.markDirty();
        }
        if (sourceCfg.getLinkedNodes().isEmpty()) {
            sourceCfg.setGlobalOutputEnabled(false);
            sourceCfg.setGlobalInputEnabled(false);
        }
        sourceCfg.markDirty();
    }

    @SuppressWarnings("unchecked")
    private static <C, T> long transferSimpleResource(
        TransferUtils.TransferProtocol<C, T> protocol, C from, C to, long limit) {
        long extractedAmount = ((ResourceAdapterProtocol<C>) protocol).getAdapter().extract(from, limit, false);
        if (extractedAmount <= 0) return 0;

        long accepted = ((ResourceAdapterProtocol<C>) protocol).getAdapter().insert(to, extractedAmount, false);
        if (accepted <= 0) {
            ((ResourceAdapterProtocol<C>) protocol).getAdapter().insert(from, extractedAmount, false);
            return 0;
        }

        if (accepted < extractedAmount) {
            ((ResourceAdapterProtocol<C>) protocol).getAdapter().insert(from, extractedAmount - accepted, false);
        }

        return accepted;
    }
}
