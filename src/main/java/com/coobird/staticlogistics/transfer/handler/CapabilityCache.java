package com.coobird.staticlogistics.transfer.handler;

import com.coobird.staticlogistics.api.CapGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import java.lang.ref.WeakReference;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Capability 缓存 —— 按维度分桶，WeakReference 持有引用。
 */
public final class CapabilityCache {
    private static final Map<Level, Map<CacheKey, WeakReference<Object>>> CACHE = new ConcurrentHashMap<>();

    private CapabilityCache() {
    }

    private record CacheKey(long pos, Direction face, CapGetter<?> capGetter) {
    }

    public record Stats(int dimensions, int entries, int liveEntries, int staleEntries) {
    }

    /**
     * 获取缓存的 capability，缓存未命中时查询一次并缓存。
     */
    @SuppressWarnings("unchecked")
    public static <C> C get(ServerLevel level, BlockPos pos, Direction face, CapGetter<C> capGetter) {
        Map<CacheKey, WeakReference<Object>> dimCache = CACHE.computeIfAbsent(level, k -> new ConcurrentHashMap<>());
        CacheKey key = new CacheKey(pos.asLong(), face, capGetter);

        WeakReference<Object> ref = dimCache.get(key);
        if (ref != null) {
            Object cached = ref.get();
            if (cached != null) return (C) cached;
            dimCache.remove(key);
        }

        C fresh = capGetter.get(level, pos, face);
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

        dimCache.keySet().removeIf(key -> positions.stream().anyMatch(pos -> pos.asLong() == key.pos()));
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
        int dimensions = CACHE.size();
        int entries = 0;
        int liveEntries = 0;
        int staleEntries = 0;

        for (Map<CacheKey, WeakReference<Object>> dimCache : CACHE.values()) {
            entries += dimCache.size();
            for (WeakReference<Object> ref : dimCache.values()) {
                if (ref.get() == null) {
                    staleEntries++;
                } else {
                    liveEntries++;
                }
            }
        }
        return new Stats(dimensions, entries, liveEntries, staleEntries);
    }

    public static void cleanStaleEntries() {
        CACHE.values().forEach(dimCache -> dimCache.entrySet().removeIf(entry -> entry.getValue().get() == null));
        CACHE.entrySet().removeIf(entry -> entry.getValue().isEmpty());
    }
}
