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
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

import java.util.List;

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
    private static final Logger LOGGER = LogUtils.getLogger();

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
            (level, pos, face) -> CapabilityCache.get(level, pos, face, cap),
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

            // 跨维度检查
            if (!isSameDim && !canCrossDim) {
                if (context != null) logFailure(context, remoteNode, TransferFailureReason.NO_DIMENSION_UPGRADE);
                continue;
            }

            // 距离检查
            if (isSameDim && LogisticsCalculator.isOutOfRange(localPos, remoteNode.gPos().pos(), localContainer)) {
                if (context != null) logFailure(context, remoteNode, TransferFailureReason.OUT_OF_RANGE);
                continue;
            }

            // 区块加载检查
            ServerLevel remoteLevel = isSameDim ? localLevel :
                localLevel.getServer().getLevel(remoteNode.gPos().dimension());
            if (remoteLevel == null || !remoteLevel.getChunkSource().hasChunk(
                remoteNode.gPos().pos().getX() >> 4, remoteNode.gPos().pos().getZ() >> 4)) {
                if (context != null) logFailure(context, remoteNode, TransferFailureReason.CHUNK_UNLOADED);
                continue;
            }

            // 获取远程 capability
            C remoteCap = capGetter.get(remoteLevel, remoteNode.gPos().pos(), remoteNode.face());
            if (remoteCap == null) {
                // 脏链接清理
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
                PreTransferEvent preEvent = PreTransferEvent.obtain(srcNode, dstNode, context.type(), remaining);
                try {
                    NeoForge.EVENT_BUS.post(preEvent);
                    if (preEvent.isCanceled()) {
                        logFailure(context, remoteNode, TransferFailureReason.EVENT_CANCELLED);
                        continue;
                    }
                } finally {
                    preEvent.recycle();
                }
            }

            // 一对一传输
            long targetAccepted = 0;
            if (context != null && context.type().isSimpleResource()) {
                // 简单资源快速路径：跳过模拟，直接 extract + insert
                targetAccepted = transferSimpleResource(protocol, from, to, remaining);
            } else {
                // 复杂资源路径：simulateExtract → canInsert → executeInsert → commitExtract
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
                PostTransferEvent postEvent = PostTransferEvent.obtain(srcNode, dstNode, context.type(), targetAccepted, true);
                try {
                    NeoForge.EVENT_BUS.post(postEvent);
                } finally {
                    postEvent.recycle();
                }
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

    /**
     * 简单资源快速传输路径。
     * <p>跳过模拟提取，直接执行 extract + insert。
     * 如果插入量不足，回退多余部分。
     *
     * @return 实际传输量
     */
    @SuppressWarnings("unchecked")
    private static <C, T> long transferSimpleResource(
        TransferUtils.TransferProtocol<C, T> protocol, C from, C to, long limit) {
        // 直接提取（不模拟）- 简单资源返回 long
        long extractedAmount = ((ResourceAdapterProtocol<C>) protocol).getAdapter().extract(from, limit, false);
        if (extractedAmount <= 0) return 0;

        // 直接插入
        long accepted = ((ResourceAdapterProtocol<C>) protocol).getAdapter().insert(to, extractedAmount, false);
        if (accepted <= 0) {
            // 插入失败，回退
            ((ResourceAdapterProtocol<C>) protocol).getAdapter().insert(from, extractedAmount, false);
            return 0;
        }

        // 如果插入量 < 提取量，回退多余部分
        if (accepted < extractedAmount) {
            ((ResourceAdapterProtocol<C>) protocol).getAdapter().insert(from, extractedAmount - accepted, false);
        }

        return accepted;
    }
}
