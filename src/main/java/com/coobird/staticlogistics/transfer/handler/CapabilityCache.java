package com.coobird.staticlogistics.transfer.handler;

import com.coobird.staticlogistics.api.CapGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Capability 缓存 —— 按维度分桶，WeakReference 持有引用。
 */
public final class CapabilityCache {
    private static final Map<Level, Map<Long, WeakReference<Object>>> CACHE = new ConcurrentHashMap<>();

    private CapabilityCache() {
    }

    /**
     * 获取缓存的 capability，缓存未命中时查询一次并缓存。
     */
    @SuppressWarnings("unchecked")
    public static <C> C get(ServerLevel level, BlockPos pos, Direction face, CapGetter<C> capGetter) {
        Map<Long, WeakReference<Object>> dimCache = CACHE.computeIfAbsent(level, k -> new ConcurrentHashMap<>());
        long key = (pos.asLong() << 4) | face.ordinal();

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
}
