package com.coobird.staticlogistics.transfer.handler;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.BlockCapability;

import java.lang.ref.WeakReference;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Capability 缓存 —— 按维度分桶，WeakReference 持有引用。
 *
 * <p>缓存机制：
 * <ul>
 *   <li>每个 IItemHandler / IFluidHandler / IEnergyStorage 实例的生命周期
 *       等于其 BlockEntity 的生命周期</li>
 *   <li>用 WeakReference 持有缓存引用 —— BlockEntity 被 GC 时缓存自动清除</li>
 *   <li>按维度分桶 —— 维度卸载时整桶清空</li>
 * </ul>
 *
 * <p>线程安全：使用 ConcurrentHashMap，支持多线程读写。
 */
public final class CapabilityCache {
    private static final Map<Level, Map<CacheKey, WeakReference<Object>>> CACHE = new ConcurrentHashMap<>();

    private CapabilityCache() {
    }

    private record CacheKey(long pos, Direction face, BlockCapability<?, Direction> capability) {
    }

    public record Stats(int dimensions, int entries, int liveEntries, int staleEntries) {
    }

    /**
     * 获取缓存的 capability，缓存未命中时查询一次并缓存。
     */
    @SuppressWarnings("unchecked")
    public static <C> C get(ServerLevel level, BlockPos pos, Direction face, BlockCapability<C, Direction> cap) {
        Map<CacheKey, WeakReference<Object>> dimCache = CACHE.computeIfAbsent(level, k -> new ConcurrentHashMap<>());
        CacheKey key = new CacheKey(pos.asLong(), face, cap);

        WeakReference<Object> ref = dimCache.get(key);
        if (ref != null) {
            Object cached = ref.get();
            if (cached != null) return (C) cached;
            dimCache.remove(key);
        }

        C fresh = level.getCapability(cap, pos, face);
        if (fresh != null) {
            dimCache.put(key, new WeakReference<>(fresh));
        }
        return fresh;
    }

    /**
     * 维度卸载时调用 —— 清空该维度的全部缓存。
     */
    public static void clearDimension(Level level) {
        CACHE.remove(level);
    }

    public static void clearPosition(Level level, BlockPos pos) {
        Map<CacheKey, WeakReference<Object>> dimCache = CACHE.get(level);
        if (dimCache == null) return;

        long posKey = pos.asLong();
        dimCache.keySet().removeIf(key -> key.pos() == posKey);
    }

    public static void clearPositions(Level level, Collection<BlockPos> positions) {
        if (positions.isEmpty()) return;
        Map<CacheKey, WeakReference<Object>> dimCache = CACHE.get(level);
        if (dimCache == null) return;

        java.util.Set<Long> posKeys = positions.stream()
            .map(BlockPos::asLong)
            .collect(java.util.stream.Collectors.toSet());
        dimCache.keySet().removeIf(key -> posKeys.contains(key.pos()));
    }

    public static void clearPositionAndNeighbors(Level level, BlockPos pos) {
        clearPosition(level, pos);
        for (Direction direction : Direction.values()) {
            clearPosition(level, pos.relative(direction));
        }
    }

    public static void clearChunk(Level level, ChunkPos chunkPos) {
        Map<CacheKey, WeakReference<Object>> dimCache = CACHE.get(level);
        if (dimCache == null) return;

        dimCache.keySet().removeIf(key -> new ChunkPos(BlockPos.of(key.pos())).equals(chunkPos));
    }

    public static Stats snapshotStats() {
        int entries = 0;
        int live = 0;
        int stale = 0;

        for (Map<CacheKey, WeakReference<Object>> dimCache : CACHE.values()) {
            entries += dimCache.size();
            for (WeakReference<Object> ref : dimCache.values()) {
                if (ref.get() == null) stale++;
                else live++;
            }
        }
        return new Stats(CACHE.size(), entries, live, stale);
    }

    public static void cleanStaleEntries() {
        CACHE.values().forEach(dimCache -> dimCache.entrySet().removeIf(entry -> entry.getValue().get() == null));
    }
}
