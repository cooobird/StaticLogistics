package com.coobird.staticlogistics.transfer;

import com.coobird.staticlogistics.util.LogisticsConstants;
import it.unimi.dsi.fastutil.longs.Long2LongMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class CooldownManager {
    // 主线程单线程访问（setCooldown/hasCooldown 由 LogisticsTicker.tick 调用）
    // clearForDimension 在 LevelEvent.Unload 中调用，也由主线程触发
    private final Map<ResourceKey<Level>, Long2LongMap> dimensionCooldowns = new HashMap<>();
    private final Map<ResourceKey<Level>, Integer> dimCleanCounters = new HashMap<>();

    private static long extractSourceKey(long cooldownKey) {
        return cooldownKey >> 8;
    }

    public void setCooldown(ResourceKey<Level> dimension, long key, int durationTicks, long currentTick) {
        long nextAllowedTick = currentTick + durationTicks;
        Long2LongMap map = dimensionCooldowns.computeIfAbsent(dimension, k -> {
            Long2LongMap m = new Long2LongOpenHashMap();
            m.defaultReturnValue(Long.MIN_VALUE);
            return m;
        });
        map.put(key, nextAllowedTick);
    }

    public boolean hasCooldown(ResourceKey<Level> dimension, long key, long currentTick) {
        Long2LongMap map = dimensionCooldowns.get(dimension);
        if (map == null) return false;
        long nextAllowed = map.get(key);
        return nextAllowed > currentTick;
    }

    public void tick(ResourceKey<Level> dimension, long currentTick) {
        int counter = dimCleanCounters.getOrDefault(dimension, 0) + 1;
        dimCleanCounters.put(dimension, counter);
        int fullCleanInterval = LogisticsConstants.Performance.getFullCleanIntervalTicks();
        if (counter >= fullCleanInterval) {
            dimCleanCounters.put(dimension, 0);
            cleanExpiredAll(dimension, currentTick);
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
        map.long2LongEntrySet().removeIf(entry -> entry.getLongValue() <= currentTick);
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
            if (entry.getLongValue() <= currentTick) it.remove();
            processed++;
        }
        if (map.isEmpty()) {
            dimensionCooldowns.remove(dimension);
        }
    }

    /**
     * 线性扫描移除指定 sourceKey 的所有冷却。
     * cooldown 表通常只有几百条，线性扫描代价可接受。
     */
    public void removeAllForSourceKey(ResourceKey<Level> dimension, long sourceKey) {
        Long2LongMap map = dimensionCooldowns.get(dimension);
        if (map == null) return;

        var it = map.long2LongEntrySet().iterator();
        while (it.hasNext()) {
            if (extractSourceKey(it.next().getLongKey()) == sourceKey) it.remove();
        }

        if (map.isEmpty()) {
            dimensionCooldowns.remove(dimension);
        }
    }

    public void clearForDimension(ResourceKey<Level> dimension) {
        dimensionCooldowns.remove(dimension);
        dimCleanCounters.remove(dimension);
    }
}
