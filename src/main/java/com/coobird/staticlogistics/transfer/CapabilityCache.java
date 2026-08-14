package com.coobird.staticlogistics.transfer;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 单维度原生方块能力缓存，由所属 LinkManager 控制生命周期。
 */
public final class CapabilityCache {
    private static final int MAX_ENTRIES = 16_384;
    private final ServerLevel level;
    private final Map<CacheKey, CacheEntry> caches = new LinkedHashMap<>(256, 0.75F, true);
    private boolean active = true;

    public CapabilityCache(ServerLevel level) {
        this.level = level;
    }

    private record CacheKey(long pos, Direction face, BlockCapability<?, Direction> capability) {
    }

    private static final class CacheEntry {
        private BlockCapabilityCache<?, Direction> cache;
        private boolean valid = true;
    }

    @SuppressWarnings("unchecked")
    public <C> C get(BlockPos pos, Direction face, BlockCapability<C, Direction> capability) {
        if (!active) throw new IllegalStateException("Capability cache is not active");
        CacheKey key = new CacheKey(pos.asLong(), face, capability);
        CacheEntry entry = caches.get(key);
        if (entry == null) {
            evictIfFull();
            entry = new CacheEntry();
            CacheEntry created = entry;
            created.cache = BlockCapabilityCache.create(capability, level, pos, face,
                () -> active && created.valid,
                () -> {
                    created.valid = false;
                    caches.remove(key, created);
                    NodeQueryService.invalidateFace(level, pos, face);
                });
            caches.put(key, created);
        }
        BlockCapabilityCache<C, Direction> cache =
            (BlockCapabilityCache<C, Direction>) entry.cache;
        return cache.getCapability();
    }

    /**
     * 节点或方块生命周期结束时主动释放该面的全部能力缓存。
     */
    public void invalidateFace(BlockPos pos, Direction face) {
        invalidate(pos.asLong(), face);
        NodeQueryService.invalidateFace(level, pos, face);
    }

    /**
     * 方块替换、爆炸或破坏时主动释放六个面的全部能力缓存。
     */
    public void invalidateBlock(BlockPos pos) {
        invalidate(pos.asLong(), null);
        NodeQueryService.invalidateBlock(level, pos);
    }

    private void invalidate(long posLong, Direction face) {
        Iterator<Map.Entry<CacheKey, CacheEntry>> iterator = caches.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<CacheKey, CacheEntry> entry = iterator.next();
            if (entry.getKey().pos() != posLong
                || (face != null && entry.getKey().face() != face)) continue;
            entry.getValue().valid = false;
            iterator.remove();
        }
    }

    private void evictIfFull() {
        if (caches.size() < MAX_ENTRIES) return;
        Iterator<Map.Entry<CacheKey, CacheEntry>> iterator = caches.entrySet().iterator();
        if (!iterator.hasNext()) return;
        Map.Entry<CacheKey, CacheEntry> eldest = iterator.next();
        eldest.getValue().valid = false;
        iterator.remove();
    }

    public void shutdown() {
        active = false;
        caches.clear();
    }
}
