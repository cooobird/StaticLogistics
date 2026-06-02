package com.coobird.staticlogistics.transfer.handler;

import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.core.registration.TransferRegistries;
import com.coobird.staticlogistics.storage.LinkManager;
import com.coobird.staticlogistics.storage.config.ContainerConfig;
import com.coobird.staticlogistics.storage.config.FaceConfigComposite;
import com.coobird.staticlogistics.transfer.TransferLogManager;
import com.coobird.staticlogistics.transfer.context.TransferContext;
import com.coobird.staticlogistics.util.LogisticsCalculator;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.BlockCapability;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.lang.ref.WeakReference;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Predicate;

/**
 * 传输工具类 —— 核心传输管线 + 按维度缓存的 capability 查询
 * <p>
 * 缓存机制：
 * <ul>
 *   <li>每个 IItemHandler / IFluidHandler / IEnergyStorage 实例的生命周期
 *       等于其 BlockEntity 的生命周期</li>
 *   <li>用 WeakReference 持有缓存引用 —— BlockEntity 被 GC 时缓存自动清除</li>
 *   <li>按维度分桶 —— 维度卸载时整桶清空</li>
 * </ul>
 */
public class TransferUtils {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final Map<Level, Map<Long, WeakReference<Object>>> CAP_CACHE = new ConcurrentHashMap<>();

    /**
     * 获取缓存的 capability，缓存未命中时查询一次并缓存。
     */
    @SuppressWarnings("unchecked")
    private static <C> C getCachedCapability(ServerLevel level, BlockPos pos, Direction face,
                                             BlockCapability<C, Direction> cap) {
        Map<Long, WeakReference<Object>> dimCache = CAP_CACHE.computeIfAbsent(level, k -> new ConcurrentHashMap<>());
        long key = (pos.asLong() << 4) | face.ordinal();

        WeakReference<Object> ref = dimCache.get(key);
        if (ref != null) {
            Object cached = ref.get();
            if (cached != null) return (C) cached;
            // BlockEntity 已被 GC → 清理过期条目
            dimCache.remove(key);
        }

        // Cache miss → 真实查询
        C fresh = level.getCapability(cap, pos, face);
        if (fresh != null) {
            dimCache.put(key, new WeakReference<>((Object) fresh));
        }
        return fresh;
    }

    /**
     * 维度卸载时调用 —— 清空该维度的全部缓存
     */
    public static void clearDimCache(Level level) {
        CAP_CACHE.remove(level);
    }

    @FunctionalInterface
    public interface CapGetter<C> {
        @Nullable C get(ServerLevel level, BlockPos pos, Direction face);
    }

    public static <C, T> boolean doTransferNodes(
        ServerLevel localLevel, BlockPos localPos, Direction localFace,
        List<LogisticsNode> destinations, BlockCapability<C, Direction> cap,
        int limit, TransferProtocol<C, T> protocol, boolean isPullMode,
        TransferContext context
    ) {
        return doTransferNodes(localLevel, localPos, localFace, destinations,
            (level, pos, face) -> getCachedCapability(level, pos, face, cap),
            limit, protocol, isPullMode, context);
    }

    public static <C, T> boolean doTransferNodes(
        ServerLevel localLevel, BlockPos localPos, Direction localFace,
        List<LogisticsNode> destinations, CapGetter<C> capGetter,
        int limit, TransferProtocol<C, T> protocol, boolean isPullMode,
        TransferContext context
    ) {
        if (context != null && context.isDepthExceeded()) {
            LOGGER.debug("Depth exceeded for transfer at {} (depth={})", localPos, context.depth());
            return false;
        }
        if (destinations.isEmpty() || limit <= 0) return false;

        int remaining = limit;

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

            if (!isSameDim && !canCrossDim) continue;

            if (isSameDim && !LogisticsCalculator.isWithinRange(localPos, remoteNode.gPos().pos(), localContainer)) {
                continue;
            }

            ServerLevel remoteLevel = isSameDim ? localLevel :
                localLevel.getServer().getLevel(remoteNode.gPos().dimension());

            if (remoteLevel == null || !remoteLevel.getChunkSource().hasChunk(
                remoteNode.gPos().pos().getX() >> 4, remoteNode.gPos().pos().getZ() >> 4))
                continue;

            C remoteCap = capGetter.get(remoteLevel, remoteNode.gPos().pos(), remoteNode.face());
            if (remoteCap == null) {
                if (context != null && context.sourceConfig() != null
                    && remoteLevel.getBlockEntity(remoteNode.gPos().pos()) == null) {
                    cleanStaleTarget(context.sourceConfig(), remoteNode, context);
                }
                continue;
            }

            C from = isPullMode ? remoteCap : localCap;
            C to = isPullMode ? localCap : remoteCap;

            while (remaining > 0) {
                ExtractionResult<T> result = protocol.simulateExtract(from, remaining);
                if (protocol.isEmpty(result)) break;

                if (!protocol.canInsert(to, result.value(), remoteNode)) break;

                int accepted = protocol.executeInsert(to, result.value());
                if (accepted <= 0) break;

                protocol.commitExtract(from, result, accepted);
                remaining -= accepted;
                movedAny = true;

                if (context != null) {
                    LogisticsNode srcNode = context.isPullMode() ? remoteNode : context.sourceNode();
                    LogisticsNode dstNode = context.isPullMode() ? context.sourceNode() : remoteNode;
                    TransferLogManager.get().logTransfer(srcNode, dstNode, context.type(), accepted, true);
                }
            }
            if (remaining <= 0) break;
        }
        return movedAny;
    }

    public static boolean hasLogisticsCapability(Level level, BlockPos pos, Direction face) {
        return TransferRegistries.getAllActive().stream().anyMatch(type -> {
            if (type.customCapCheck() != null) return type.customCapCheck().test(level, pos);
            var cap = type.capability();
            return cap != null && (level.getCapability(cap, pos, face) != null || level.getCapability(cap, pos, null) != null);
        });
    }

    public interface TransferProtocol<C, T> {
        ExtractionResult<T> simulateExtract(C source, int max);

        int executeInsert(C dest, T stack);

        void commitExtract(C source, ExtractionResult<T> result, int actual);

        boolean isEmpty(ExtractionResult<T> result);

        default boolean canInsert(C dest, T stack, LogisticsNode targetNode) {
            return true;
        }
    }

    public record SimpleProtocol<C, T>(
        BiFunction<C, Integer, T> extractor,
        BiFunction<C, T, Integer> inserter,
        TriConsumer<C, T, Integer> committer,
        Predicate<T> emptyChecker,
        @javax.annotation.Nullable java.util.function.BiPredicate<T, LogisticsNode> targetFilter
    ) implements TransferProtocol<C, T> {
        public SimpleProtocol(BiFunction<C, Integer, T> extractor, BiFunction<C, T, Integer> inserter,
                              TriConsumer<C, T, Integer> committer, Predicate<T> emptyChecker) {
            this(extractor, inserter, committer, emptyChecker, null);
        }

        @Override
        public ExtractionResult<T> simulateExtract(C source, int max) {
            T value = extractor.apply(source, max);
            return ExtractionResult.of(value);
        }

        @Override
        public int executeInsert(C dest, T stack) {
            return inserter.apply(dest, stack);
        }

        @Override
        public void commitExtract(C source, ExtractionResult<T> result, int actual) {
            committer.accept(source, result.value(), actual);
        }

        @Override
        public boolean isEmpty(ExtractionResult<T> result) {
            return emptyChecker.test(result.value());
        }

        @Override
        public boolean canInsert(C dest, T stack, LogisticsNode targetNode) {
            return targetFilter == null || targetFilter.test(stack, targetNode);
        }
    }

    @FunctionalInterface
    public interface TriConsumer<A, B, C> {
        void accept(A a, B b, C c);
    }

    private static void cleanStaleTarget(FaceConfigComposite sourceCfg, LogisticsNode remoteNode,
                                         TransferContext ctx) {
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
}
