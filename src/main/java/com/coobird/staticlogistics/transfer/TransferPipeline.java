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
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.Capability;
import org.slf4j.Logger;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/// 编排资源传输、事件通知和结果记录。
public final class TransferPipeline {
    private static final Logger LOGGER = LogUtils.getLogger();

    private TransferPipeline() {
    }

    /// 使用 BlockCapability 执行传输（自动缓存 capability）。
    public static <C, T> boolean execute(
        ServerLevel localLevel, BlockPos localPos, Direction localFace,
        List<LogisticsNode> destinations, Capability<C> cap,
        long limit, TransferProtocol<C, T> protocol, boolean isPullMode,
        TransferContext context
    ) {
        return execute(localLevel, localPos, localFace, destinations,
            (level, pos, face) -> LinkManager.get(level).getCapability(pos, face, cap),
            limit, protocol, isPullMode, context);
    }

    /// 执行传输管线。
    public static <C, T> boolean execute(
        ServerLevel localLevel, BlockPos localPos, Direction localFace,
        List<LogisticsNode> destinations, CapGetter<C> capGetter,
        long limit, TransferProtocol<C, T> protocol, boolean isPullMode,
        TransferContext context
    ) {
        TransferContext.requireServerThread(localLevel);
        if (destinations.isEmpty() || limit <= 0) return false;

        long remaining = limit;

        LinkManager localMgr = LinkManager.get(localLevel);
        ContainerConfig localContainer = localMgr.getContainerConfig(localPos);
        if (localContainer == null && context != null && context.sourceConfig() != null) {
            localContainer = context.sourceConfig().getContainerConfig();
        }
        if (localContainer == null) return false;

        C localCap;
        try {
            localCap = capGetter.get(localLevel, localPos, localFace);
        } catch (RuntimeException exception) {
            LOGGER.error("Source capability query failed at {} face {}", localPos, localFace, exception);
            return false;
        }
        if (localCap == null) return false;

        TransferContext.Progress progress = context == null ? null : context.progress();
        boolean movedAny = false;
        Set<Object> rejectedCandidateContexts = null;
        GlobalPos localGlobalPos = GlobalPos.of(localLevel.dimension(), localPos);

        while (remaining > 0L && (context == null || context.hasTimeRemaining())) {
            boolean movedThisPass = false;
            boolean restartPass = false;
            boolean yielded = false;
            boolean terminalFailure = false;
            ExtractionResult<T> rejectedCandidate = null;

            int start = context == null ? 0 : Mth.clamp(context.targetIndex(), 0, destinations.size());
            for (int targetIndex = start; targetIndex < destinations.size(); targetIndex++) {
                /// 已尝试的目标不因预算耗尽而从头重试；资源模拟和提交仍在同一次调用内完成。
                if (context != null && !context.hasTimeRemaining()) break;
                LogisticsNode remoteNode = destinations.get(targetIndex);
                boolean targetFinished = true;
                try {
                    boolean isSameDim = remoteNode.isInSameDimension(localLevel.dimension());

                    boolean dimensionEffective = LogisticsCalculator.isDimensionEffective(localContainer);
                    if (!isSameDim && !dimensionEffective) {
                        if (context != null) {
                            logFailure(context, remoteNode, TransferFailureReason.NO_DIMENSION_UPGRADE);
                        }
                        continue;
                    }

                    /// 传输只需要可达性，不需要界面使用的实际距离；无限范围跳过空间变换。
                    if (isSameDim && !dimensionEffective
                        && LogisticsCalculator.getMaxTransferBlocks(localContainer) != Integer.MAX_VALUE
                        && !LogisticsCalculator.assessTransferRange(
                        localLevel, localGlobalPos, remoteNode.gPos(), localContainer).allowed()) {
                        if (context != null) {
                            logFailure(context, remoteNode, TransferFailureReason.OUT_OF_RANGE);
                        }
                        continue;
                    }

                    /// 区块加载检查
                    ServerLevel remoteLevel = isSameDim ? localLevel
                        : localLevel.getServer().getLevel(remoteNode.gPos().dimension());
                    if (remoteLevel == null || !remoteLevel.getChunkSource().hasChunk(
                        remoteNode.gPos().pos().getX() >> 4, remoteNode.gPos().pos().getZ() >> 4)) {
                        if (context != null) {
                            logFailure(context, remoteNode, TransferFailureReason.CHUNK_UNLOADED);
                        }
                        continue;
                    }

                    C remoteCap;
                    try {
                        remoteCap = capGetter.get(remoteLevel, remoteNode.gPos().pos(), remoteNode.face());
                    } catch (RuntimeException exception) {
                        LOGGER.error("Target capability query failed at {} face {}",
                            remoteNode.gPos().pos(), remoteNode.face(), exception);
                        if (context != null) {
                            logFailure(context, remoteNode, TransferFailureReason.CAPABILITY_NULL);
                        }
                        continue;
                    }
                    if (remoteCap == null) {
                        /// 脏链接清理
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

                    /// 前置事件
                    if (context != null) {
                        LogisticsNode srcNode = transferSource(context, remoteNode);
                        LogisticsNode dstNode = transferTarget(context, remoteNode);
                        PreTransferEvent preEvent = new PreTransferEvent(
                            srcNode, dstNode, context.typeId(), remaining);
                        MinecraftForge.EVENT_BUS.post(preEvent);
                        if (preEvent.isCanceled()) {
                            logFailure(context, remoteNode, TransferFailureReason.EVENT_CANCELLED);
                            continue;
                        }
                    }

                    /// 前置事件允许修改配置；再次确认端点、角色、资源类型、红石和连接归属。
                    if (context != null && !TransferUtils.isCurrentTransferAllowed(context, remoteNode)) continue;

                    /// 双方先模拟，再以源端真实提取结果为唯一可提交资源。
                    long targetAccepted = 0;
                    boolean transferComplete = true;
                    TransferFailureReason commitFailureReason = null;
                    ExtractionResult<T> simulated;
                    boolean simulatedEmpty;
                    boolean targetCanInsert;
                    long simulatedAccepted;
                    try {
                        simulated = protocol.simulateExtract(from, remaining);
                        if (simulated.isYielded()) {
                            targetFinished = false;
                            yielded = true;
                            if (context != null) context.yieldExecution();
                            break;
                        }
                        simulatedEmpty = protocol.isEmpty(simulated);
                        if (simulatedEmpty && context != null)
                            TransferLogManager.get(localLevel.getServer()).observeNoCandidate(
                                transferSource(context, remoteNode), transferTarget(context, remoteNode));
                        /// 候选资源变化时先重启目标顺序，不对即将放弃的目标调用第三方模拟。
                        if (!simulatedEmpty && !isPullMode && progress != null) {
                            Object candidateKey = protocol.candidateKey(simulated);
                            if (candidateKey != null) {
                                if (progress.candidateKey != null && !progress.candidateKey.equals(candidateKey)) {
                                    progress.reset();
                                    progress.candidateKey = candidateKey;
                                    restartPass = true;
                                    targetFinished = false;
                                    break;
                                }
                                progress.candidateKey = candidateKey;
                            }
                        }
                        targetCanInsert = !simulatedEmpty
                            && protocol.canInsert(to, simulated.value(), remoteNode);
                        simulatedAccepted = targetCanInsert
                            ? Math.min(remaining, protocol.simulateInsert(to, simulated.value())) : 0L;
                    } catch (RuntimeException exception) {
                        LOGGER.error("Transfer simulation failed from {} to {}", localPos, remoteNode, exception);
                        if (context != null) {
                            logFailure(context, remoteNode, TransferFailureReason.SIMULATION_FAILED);
                        }
                        continue;
                    }
                    if (!simulatedEmpty) {
                        rejectedCandidate = simulated;
                    }
                    if (!simulatedEmpty && targetCanInsert) {
                        if (simulatedAccepted > 0) {
                            TransferTransaction.Result result = TransferTransaction.commit(
                                protocol, from, to, simulated, simulatedAccepted, remoteNode);
                            targetAccepted = result.accepted();
                            transferComplete = result.failure() == TransferTransaction.Failure.NONE;
                            if (result.failure() == TransferTransaction.Failure.SOURCE_COMMIT_FAILED) {
                                commitFailureReason = TransferFailureReason.SOURCE_COMMIT_FAILED;
                                terminalFailure = true;
                            } else if (result.failure() == TransferTransaction.Failure.COMMIT_STATE_UNKNOWN) {
                                commitFailureReason = TransferFailureReason.COMMIT_STATE_UNKNOWN;
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
                    } else if (!simulatedEmpty && context != null) {
                        logFailure(context, remoteNode, TransferFailureReason.TARGET_POLICY_REJECTED);
                    }

                    if (targetAccepted > 0) {
                        remaining -= targetAccepted;
                        movedAny = true;
                        movedThisPass = true;
                    }

                    if (targetAccepted > 0 && context != null) {
                        LogisticsNode srcNode = transferSource(context, remoteNode);
                        LogisticsNode dstNode = transferTarget(context, remoteNode);
                        TransferLogManager.get(localLevel.getServer()).logTransfer(
                            srcNode, dstNode, context.type(), targetAccepted, transferComplete,
                            commitFailureReason);
                        PostTransferEvent postEvent = new PostTransferEvent(
                            srcNode, dstNode, context.typeId(), targetAccepted, transferComplete);
                        MinecraftForge.EVENT_BUS.post(postEvent);
                    }
                    if (remaining <= 0L || terminalFailure
                        || context != null && !context.hasTimeRemaining()) break;
                } finally {
                    /// 只有完整处理过当前目标才前进；预算中断保留当前目标，而非事后倒退游标。
                    if (targetFinished && context != null) context.targetIndex(targetIndex + 1);
                }
            }

            if (yielded) break;
            if (terminalFailure || remaining <= 0L) break;
            if (restartPass) continue;
            if (context != null && context.targetIndex() < destinations.size() && !context.hasTimeRemaining()) break;
            /// 上一 tick 已检查完目标但尚未结束拒绝轮次：重新模拟，不复用旧资源或旧能力。
            if (!movedThisPass && rejectedCandidate == null && progress != null
                && progress.candidateKey != null && !isPullMode) {
                if (!context.hasTimeRemaining()) break;
                ExtractionResult<T> current = protocol.simulateExtract(localCap, remaining);
                if (current.isYielded()) {
                    context.yieldExecution();
                    break;
                }
                if (!Objects.equals(progress.candidateKey, protocol.candidateKey(current))) {
                    progress.reset();
                    continue;
                }
                rejectedCandidate = current;
            }
            if (progress != null) progress.reset();
            if (context != null) context.targetIndex(0);
            if (movedThisPass) continue;
            if (rejectedCandidate == null || rejectedCandidate.context() == null) break;
            if (rejectedCandidateContexts == null) rejectedCandidateContexts = new HashSet<>();
            if (!rejectedCandidateContexts.add(rejectedCandidate.context())
                || !protocol.advanceRejectedCandidate(rejectedCandidate)) {
                break;
            }
        }
        if (context == null || !context.isYielded() && context.hasTimeRemaining()) {
            protocol.onActivationCompleted();
        }
        return movedAny;
    }

    private static void logFailure(TransferContext context, LogisticsNode remoteNode, TransferFailureReason reason) {
        LogisticsNode srcNode = transferSource(context, remoteNode);
        LogisticsNode dstNode = transferTarget(context, remoteNode);
        TransferLogManager.get(context.level().getServer()).logTransfer(
            srcNode, dstNode, context.type(), 0, false, reason);
    }

    private static LogisticsNode transferSource(TransferContext context, LogisticsNode remoteNode) {
        return context.isPullMode() ? remoteNode : context.sourceNode();
    }

    private static LogisticsNode transferTarget(TransferContext context, LogisticsNode remoteNode) {
        return context.isPullMode() ? context.sourceNode() : remoteNode;
    }

    private static void cleanStaleTarget(LogisticsNode remoteNode, TransferContext ctx) {
        ctx.linkManager().removeLink(ctx.sourceNode(), remoteNode);
    }

}
