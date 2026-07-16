package com.coobird.staticlogistics.transfer;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;

import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Forge 方块能力缓存。
 *
 * <p>缓存持有原始 {@link LazyOptional} 并监听失效事件，绝不在能力重建后返回旧句柄。
 */
public final class CapabilityCache {
    private static final int MAX_ENTRIES_PER_DIMENSION = 16_384;
    private static final Map<Level, DimensionCache> CACHE = new ConcurrentHashMap<>();

    private CapabilityCache() {
    }

    private record CacheKey(long pos, Direction face, Capability<?> capability) {
    }

    private static final class CacheEntry {
        private final LazyOptional<?> optional;

        private CacheEntry(LazyOptional<?> optional) {
            this.optional = optional;
        }
    }

    private static final class DimensionCache {
        private final LinkedHashMap<CacheKey, CacheEntry> entries =
            new LinkedHashMap<>(256, 0.75F, true);
    }

    public record Stats(int dimensions, int entries, int liveEntries, int staleEntries) {
    }

    @SuppressWarnings("unchecked")
    public static <C> C get(ServerLevel level, BlockPos pos, Direction face,
                            Capability<C> capability) {
        DimensionCache dimension = CACHE.computeIfAbsent(level, ignored -> new DimensionCache());
        CacheKey key = new CacheKey(pos.asLong(), face, capability);
        synchronized (dimension) {
            CacheEntry cached = dimension.entries.get(key);
            if (cached != null) {
                LazyOptional<C> optional = (LazyOptional<C>) cached.optional;
                if (optional.isPresent()) return optional.orElse(null);
                dimension.entries.remove(key);
            }

            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity == null) return null;
            LazyOptional<C> optional = blockEntity.getCapability(capability, face);
            if (!optional.isPresent()) return null;
            evictIfFull(dimension);
            CacheEntry entry = new CacheEntry(optional);
            dimension.entries.put(key, entry);
            optional.addListener(ignored -> removeIfSame(level, dimension, key, entry));
            return optional.orElse(null);
        }
    }

    private static void removeIfSame(Level level, DimensionCache dimension,
                                     CacheKey key, CacheEntry expected) {
        synchronized (dimension) {
            dimension.entries.remove(key, expected);
            if (dimension.entries.isEmpty()) CACHE.remove(level, dimension);
        }
        if (level instanceof ServerLevel serverLevel) {
            NodeQueryService.invalidateFace(serverLevel, BlockPos.of(key.pos()), key.face());
        }
    }

    private static void evictIfFull(DimensionCache dimension) {
        if (dimension.entries.size() < MAX_ENTRIES_PER_DIMENSION) return;
        Iterator<CacheKey> iterator = dimension.entries.keySet().iterator();
        if (iterator.hasNext()) {
            iterator.next();
            iterator.remove();
        }
    }

    public static void clearDimension(Level level) {
        CACHE.remove(level);
    }

    public static void clearPosition(Level level, BlockPos pos) {
        removeMatching(level, key -> key.pos() == pos.asLong());
        if (level instanceof ServerLevel serverLevel) NodeQueryService.invalidateBlock(serverLevel, pos);
    }

    public static void clearPositions(Level level, Collection<BlockPos> positions) {
        if (positions.isEmpty()) return;
        java.util.Set<Long> keys = positions.stream().map(BlockPos::asLong)
            .collect(java.util.stream.Collectors.toSet());
        removeMatching(level, key -> keys.contains(key.pos()));
        if (level instanceof ServerLevel serverLevel) {
            positions.forEach(pos -> NodeQueryService.invalidateBlock(serverLevel, pos));
        }
    }

    public static void clearPositionAndNeighbors(Level level, BlockPos pos) {
        clearPosition(level, pos);
        for (Direction direction : Direction.values()) clearPosition(level, pos.relative(direction));
    }

    public static void clearChunk(Level level, ChunkPos chunkPos) {
        removeMatching(level, key -> new ChunkPos(BlockPos.of(key.pos())).equals(chunkPos));
    }

    private static void removeMatching(Level level,
                                       java.util.function.Predicate<CacheKey> predicate) {
        DimensionCache dimension = CACHE.get(level);
        if (dimension == null) return;
        synchronized (dimension) {
            dimension.entries.keySet().removeIf(predicate);
            if (dimension.entries.isEmpty()) CACHE.remove(level, dimension);
        }
    }

    public static Stats snapshotStats() {
        int entries = 0;
        int live = 0;
        int stale = 0;
        for (DimensionCache dimension : CACHE.values()) {
            synchronized (dimension) {
                entries += dimension.entries.size();
                for (CacheEntry entry : dimension.entries.values()) {
                    if (entry.optional.isPresent()) live++;
                    else stale++;
                }
            }
        }
        return new Stats(CACHE.size(), entries, live, stale);
    }

    public static void cleanStaleEntries() {
        for (var entry : CACHE.entrySet()) {
            DimensionCache dimension = entry.getValue();
            synchronized (dimension) {
                dimension.entries.values().removeIf(value -> !value.optional.isPresent());
                if (dimension.entries.isEmpty()) CACHE.remove(entry.getKey(), dimension);
            }
        }
    }
}
