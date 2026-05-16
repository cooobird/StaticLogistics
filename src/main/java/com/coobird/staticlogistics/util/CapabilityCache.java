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
 * 能力缓存，每个 LinkManager 实例持有自己的缓存，绑定维度生命周期。
 */
public class CapabilityCache {
    private final Map<CacheKey, BlockCapabilityCache<?, Direction>> cache = new ConcurrentHashMap<>();

    public record CacheKey(ResourceKey<Level> dimension, BlockPos pos, Direction side,
                           BlockCapability<?, Direction> cap) {
    }

    @SuppressWarnings("unchecked")
    public <C> BlockCapabilityCache<C, Direction> getOrCreateCache(ServerLevel level, BlockPos pos, Direction side, BlockCapability<C, Direction> cap) {
        CacheKey key = new CacheKey(level.dimension(), pos.immutable(), side, cap);
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