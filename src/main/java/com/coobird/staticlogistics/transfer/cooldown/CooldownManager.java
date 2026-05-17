package com.coobird.staticlogistics.transfer.cooldown;

import com.coobird.staticlogistics.util.LogisticsConstants;
import it.unimi.dsi.fastutil.longs.Long2LongMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class CooldownManager {
    private final Map<ResourceKey<Level>, Long2LongMap> dimensionCooldowns = new ConcurrentHashMap<>();

    private int tickCounter = 0;

    /**
     * 设置冷却
     */
    public void setCooldown(ResourceKey<Level> dimension, long key, int durationTicks, long currentTick) {
        long nextAllowedTick = currentTick + durationTicks;
        Long2LongMap map = dimensionCooldowns.computeIfAbsent(dimension, k -> {
            Long2LongMap m = new Long2LongOpenHashMap();
            m.defaultReturnValue(Long.MIN_VALUE);
            return m;
        });
        map.put(key, nextAllowedTick);
    }

    /**
     * 检查是否处于冷却中
     */
    public boolean hasCooldown(ResourceKey<Level> dimension, long key, long currentTick) {
        Long2LongMap map = dimensionCooldowns.get(dimension);
        if (map == null) return false;
        long nextAllowed = map.get(key);
        return nextAllowed > currentTick;
    }

    /**
     * 每 tick 调用，执行清理策略
     */
    public void tick(ResourceKey<Level> dimension, long currentTick) {
        tickCounter++;
        if (tickCounter >= LogisticsConstants.Performance.getFullCleanIntervalTicks()) {
            tickCounter = 0;
            for (ResourceKey<Level> dim : dimensionCooldowns.keySet()) {
                cleanExpiredAll(dim, currentTick);
            }
        } else {
            Long2LongMap map = dimensionCooldowns.get(dimension);
            if (map != null && map.size() > LogisticsConstants.Performance.getBatchCleanThreshold()) {
                cleanExpiredBatched(dimension, currentTick);
            }
        }
    }

    private void cleanExpiredAll(ResourceKey<Level> dimension, long currentTick) {
        Long2LongMap map = dimensionCooldowns.get(dimension);
        if (map == null) return;
        map.values().removeIf(nextTick -> nextTick <= currentTick);
        if (map.isEmpty()) {
            dimensionCooldowns.remove(dimension);
        }
    }

    private void cleanExpiredBatched(ResourceKey<Level> dimension, long currentTick) {
        Long2LongMap map = dimensionCooldowns.get(dimension);
        if (map == null) return;
        Iterator<Long2LongMap.Entry> it = map.long2LongEntrySet().iterator();
        int processed = 0;
        while (it.hasNext() && processed < LogisticsConstants.Performance.getBatchCleanSize()) {
            Long2LongMap.Entry entry = it.next();
            if (entry.getLongValue() <= currentTick) {
                it.remove();
            }
            processed++;
        }
        if (map.isEmpty()) {
            dimensionCooldowns.remove(dimension);
        }
    }

    /**
     * 立即移除指定节点的冷却（唤醒）
     */
    public void removeCooldown(ResourceKey<Level> dimension, long key) {
        Long2LongMap map = dimensionCooldowns.get(dimension);
        if (map != null) {
            map.remove(key);
            if (map.isEmpty()) dimensionCooldowns.remove(dimension);
        }
    }

    /**
     * 批量移除多个节点的冷却（方块拆除/批量移除时调用，防止冷却残留）
     */
    public void removeCooldowns(ResourceKey<Level> dimension, long[] keys) {
        Long2LongMap map = dimensionCooldowns.get(dimension);
        if (map == null) return;
        for (long key : keys) map.remove(key);
        if (map.isEmpty()) dimensionCooldowns.remove(dimension);
    }

    /**
     * 清理整个维度的冷却数据（维度卸载时调用）
     */
    public void clearForDimension(ResourceKey<Level> dimension) {
        dimensionCooldowns.remove(dimension);
    }
}