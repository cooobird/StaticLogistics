package com.coobird.staticlogistics.transfer.handler;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.BlockCapability;

import java.lang.ref.WeakReference;
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
    private static final Map<Level, Map<Long, WeakReference<Object>>> CACHE = new ConcurrentHashMap<>();

    private CapabilityCache() {
    }

    /**
     * 获取缓存的 capability，缓存未命中时查询一次并缓存。
     */
    @SuppressWarnings("unchecked")
    public static <C> C get(ServerLevel level, BlockPos pos, Direction face, BlockCapability<C, Direction> cap) {
        Map<Long, WeakReference<Object>> dimCache = CACHE.computeIfAbsent(level, k -> new ConcurrentHashMap<>());
        long key = (pos.asLong() << 4) | face.ordinal();

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
}
