package com.coobird.staticlogistics.util;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 能力缓存，绑定维度生命周期。
 * NeoForge 的 BlockCapabilityCache 会自动响应方块变化并作废，
 * 但作废条目会残留在 Map 里，需要定期清理。
 */
public class CapabilityCache {
    private final Map<CacheKey, BlockCapabilityCache<?, Direction>> cache = new ConcurrentHashMap<>();
    private int accessCounter = 0;

    public record CacheKey(ResourceKey<Level> dimension, BlockPos pos, Direction side,
                           BlockCapability<?, Direction> cap) {
    }

    @SuppressWarnings("unchecked")
    public <C> BlockCapabilityCache<C, Direction> getOrCreateCache(ServerLevel level, BlockPos pos, Direction side, BlockCapability<C, Direction> cap) {
        CacheKey key = new CacheKey(level.dimension(), pos.immutable(), side, cap);
        // 每 500 次访问扫一次，清理已被 NeoForge 作废的缓存条目
        if (++accessCounter > 500) {
            accessCounter = 0;
            cache.entrySet().removeIf(e -> e.getValue().getCapability() == null);
        }
        return (BlockCapabilityCache<C, Direction>) cache.computeIfAbsent(key, k -> BlockCapabilityCache.create(cap, level, pos, side));
    }

    /**
     * 清理指定维度的所有缓存
     */
    public void clearForLevel(ResourceKey<Level> dimension) {
        cache.entrySet().removeIf(entry -> entry.getKey().dimension().equals(dimension));
    }

    /**
     * 清空所有缓存
     */
    public void clear() {
        cache.clear();
    }
}