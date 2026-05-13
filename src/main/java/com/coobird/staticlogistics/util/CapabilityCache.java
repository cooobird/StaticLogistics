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

public class CapabilityCache {
    private static final Map<CacheKey, BlockCapabilityCache<?, Direction>> CACHE_POOL = new ConcurrentHashMap<>();

    public record CacheKey(ResourceKey<Level> dimension, BlockPos pos, Direction side,
                           BlockCapability<?, Direction> cap) {
    }

    @SuppressWarnings("unchecked")
    public static <C> BlockCapabilityCache<C, Direction> getOrCreateCache(ServerLevel level, BlockPos pos, Direction side, BlockCapability<C, Direction> cap) {
        CacheKey key = new CacheKey(level.dimension(), pos.immutable(), side, cap);
        return (BlockCapabilityCache<C, Direction>) CACHE_POOL.computeIfAbsent(key, k -> BlockCapabilityCache.create(cap, level, pos, side));
    }

    public static void clearCache() {
        CACHE_POOL.clear();
    }

    public static void clearCacheForLevel(ResourceKey<Level> dimension) {
        CACHE_POOL.entrySet().removeIf(entry -> entry.getKey().dimension().equals(dimension));
    }
}