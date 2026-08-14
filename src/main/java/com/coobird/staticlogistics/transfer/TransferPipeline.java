package com.coobird.staticlogistics.transfer;

import com.coobird.staticlogistics.api.CapGetter;
import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.api.event.PostTransferEvent;
import com.coobird.staticlogistics.api.event.PreTransferEvent;
import com.coobird.staticlogistics.logistics.node.ContainerConfig;
import com.coobird.staticlogistics.logistics.node.LinkManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.common.NeoForge;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 传输管线 —— 编排一次完整的传输流程。
 *
 * <p>职责：
 * <ul>
 *   <li>范围/维度/区块检查</li>
 *   <li>脏链接清理</li>
 *   <li>Pre/Post 事件触发</li>
 *   <li>批量传输循环</li>
 *   <li>传输日志记录</li>
 * </ul>
 *
 * <p>不负责 capability 缓存（由 {@link CapabilityCache} 处理）。
 */
public final class TransferPipeline {
    private TransferPipeline() {
    }

    /**
     * 使用 BlockCapability 执行传输（自动缓存 capability）。
     */
    public static <C, T> boolean execute(
        ServerLevel localLevel, BlockPos localPos, Direction localFace,
        List<LogisticsNode> destinations, BlockCapability<C, Direction> cap,
        long limit, TransferUtils.TransferProtocol<C, T> protocol, boolean isPullMode,
        TransferContext context
    ) {
        return execute(localLevel, localPos, localFace, destinations,
            (level, pos, face) -> LinkManager.get(level).getCapability(pos, face, cap),
            limit, protocol, isPullMode, context);
    }

    /**
     * 执行传输管线。
     */
    public static <C, T> boolean execute(
        ServerLevel localLevel, BlockPos localPos, Direction localFace,
        List<LogisticsNode> destinations, CapGetter<C> capGetter,
        long limit, TransferUtils.TransferProtocol<C, T> protocol, boolean isPullMode,
        TransferContext context
    ) {
        if (context != null && context.isDepthExceeded()) return false;
        if (destinations.isEmpty() || limit <= 0) return false;

        long remaining = limit;

        LinkManager localMgr = LinkManager.get(localLevel);
        ContainerConfig localContainer = localMgr.getContainerConfig(localPos);
        if (localContainer == null && context != null && context.sourceConfig() != null) {
            localContainer = context.sourceConfig().getContainerConfig();
        }
        if (localContainer == null) return false;

        C localCap = capGetter.get(localLevel, localPos, localFace);
        if (localCap == null) return false;

        boolean movedAny = false;
        int attemptBudget = Math.max(1, protocol.maxTransactionsPerActivation());
        int attempts = 0;
        Set<Object> rejectedCandidateContexts = null;

        while (remaining > 0L && attempts < attemptBudget
            && (context == null || context.hasTimeRemaining())) {
            boolean movedThisPass = false;
            boolean candidateSeen = false;
            boolean terminalFailure = false;
            ExtractionResult<T> rejectedCandidate = null;

            for (LogisticsNode remoteNode : destinations) {
                boolean isSameDim = remoteNode.isInSameDimension(localLevel.dimension());

                LogisticsCalculator.TransferRangeAssessment range = LogisticsCalculator.assessTransferRange(
                    GlobalPos.of(localLevel.dimension(), localPos), remoteNode.gPos(), localContainer);
                if (range.crossDimension() && !range.allowed()) {
                    if (context != null) {
                        logFailure(context, remoteNode, TransferFailureReason.NO_DIMENSION_UPGRADE);
                    }
                    continue;
                }

                if (!range.crossDimension() && !range.allowed()) {
                    if (context != null) {
                        logFailure(context, remoteNode, TransferFailureReason.OUT_OF_RANGE);
                    }
                    continue;
                }

                // 区块加载检查
                ServerLevel remoteLevel = isSameDim ? localLevel
                    : localLevel.getServer().getLevel(remoteNode.gPos().dimension());
                if (remoteLevel == null || !remoteLevel.getChunkSource().hasChunk(
                    remoteNode.gPos().pos().getX() >> 4, remoteNode.gPos().pos().getZ() >> 4)) {
                    if (context != null) {
                        logFailure(context, remoteNode, TransferFailureReason.CHUNK_UNLOADED);
                    }
                    continue;
                }

                C remoteCap = capGetter.get(remoteLevel, remoteNode.gPos().pos(), remoteNode.face());
                if (remoteCap == null) {
                    // 脏链接清理
                    if (context != null && context.sourceConfig() != null
                        && remoteLevel.getBlockEntity(remoteNode.gPos().pos()) == null) {
                        cleanStaleTarget(remoteNode, context);
                    }
                    if (context != null) {
                        logFailure(context, remoteNode, TransferFailureReason.CAPABILITY_NULL);
                    }
                    continue;
                }

                C from = isPullMode ? remoteCap : localCap;
                C to = isPullMode ? localCap : remoteCap;

                // 前置事件
                if (context != null) {
                    LogisticsNode srcNode = context.isPullMode() ? remoteNode : context.sourceNode();
                    LogisticsNode dstNode = context.isPullMode() ? context.sourceNode() : remoteNode;
                    PreTransferEvent preEvent = new PreTransferEvent(
                        srcNode, dstNode, context.typeId(), remaining);
                    NeoForge.EVENT_BUS.post(preEvent);
                    if (preEvent.isCanceled()) {
                        logFailure(context, remoteNode, TransferFailureReason.EVENT_CANCELLED);
                        continue;
                    }
                }

                // 双方先模拟，再以源端真实提取结果为唯一可提交资源。
                long targetAccepted = 0;
                boolean transferComplete = true;
                TransferFailureReason commitFailureReason = null;
                ExtractionResult<T> simulated = protocol.simulateExtract(from, remaining);
                if (!protocol.isEmpty(simulated)) {
                    candidateSeen = true;
                    rejectedCandidate = simulated;
                }
                if (!protocol.isEmpty(simulated) && protocol.canInsert(to, simulated.value(), remoteNode)) {
                    long simulatedAccepted = Math.min(remaining,
                        protocol.simulateInsert(to, simulated.value()));
                    if (simulatedAccepted > 0) {
                        TransferTransaction.Result result = TransferTransaction.commit(
                            protocol, from, to, simulated, simulatedAccepted, remoteNode);
                        targetAccepted = result.accepted();
                        transferComplete = result.failure() == TransferTransaction.Failure.NONE;
                        if (result.failure() == TransferTransaction.Failure.SOURCE_COMMIT_FAILED) {
                            commitFailureReason = TransferFailureReason.SOURCE_COMMIT_FAILED;
                            terminalFailure = true;
                        } else if (result.failure() == TransferTransaction.Failure.ROLLBACK_FAILED) {
                            commitFailureReason = TransferFailureReason.ROLLBACK_FAILED;
                            terminalFailure = true;
                        }
                        if (context != null && targetAccepted <= 0 && commitFailureReason != null) {
                            logFailure(context, remoteNode, commitFailureReason);
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
                    movedThisPass = true;
                    attempts++;
                }

                if (targetAccepted > 0 && context != null) {
                    LogisticsNode srcNode = context.isPullMode() ? remoteNode : context.sourceNode();
                    LogisticsNode dstNode = context.isPullMode() ? context.sourceNode() : remoteNode;
                    TransferLogManager.get(localLevel.getServer()).logTransfer(
                        srcNode, dstNode, context.type(), targetAccepted, transferComplete,
                        commitFailureReason);
                    PostTransferEvent postEvent = new PostTransferEvent(
                        srcNode, dstNode, context.typeId(), targetAccepted, transferComplete);
                    NeoForge.EVENT_BUS.post(postEvent);
                }
                if (remaining <= 0L || attempts >= attemptBudget || terminalFailure
                    || context != null && !context.hasTimeRemaining()) break;
            }

            if (terminalFailure || remaining <= 0L || attempts >= attemptBudget) break;
            if (movedThisPass) continue;
            if (!candidateSeen || rejectedCandidate == null || rejectedCandidate.context() == null) break;
            if (rejectedCandidateContexts == null) rejectedCandidateContexts = new HashSet<>();
            if (!rejectedCandidateContexts.add(rejectedCandidate.context())
                || !protocol.advanceRejectedCandidate(rejectedCandidate)) {
                break;
            }
            attempts++;
        }
        return movedAny;
    }

    private static void logFailure(TransferContext context, LogisticsNode remoteNode, TransferFailureReason reason) {
        LogisticsNode srcNode = context.isPullMode() ? remoteNode : context.sourceNode();
        LogisticsNode dstNode = context.isPullMode() ? context.sourceNode() : remoteNode;
        TransferLogManager.get(context.level().getServer()).logTransfer(
            srcNode, dstNode, context.type(), 0, false, reason);
    }

    private static void cleanStaleTarget(LogisticsNode remoteNode, TransferContext ctx) {
        ctx.linkManager().removeLink(ctx.sourceNode(), remoteNode);
    }

}
