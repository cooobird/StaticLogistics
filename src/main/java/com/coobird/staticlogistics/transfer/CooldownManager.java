package com.coobird.staticlogistics.transfer;

import com.coobird.staticlogistics.logistics.node.FaceAddress;
import com.coobird.staticlogistics.logistics.util.LogisticsConstants;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * 使用“维度 + 面 + 资源 ID”的无碰撞身份管理冷却。
 */
public class CooldownManager {
    private record CooldownKey(FaceAddress source, ResourceLocation typeId) {
    }

    private final Map<ResourceKey<Level>, Map<CooldownKey, Long>> dimensionCooldowns = new HashMap<>();
    private final Map<ResourceKey<Level>, Long> nextFullCleanupTicks = new HashMap<>();

    public void setCooldown(ResourceKey<Level> dimension, FaceAddress source, ResourceLocation typeId,
                            int durationTicks, long currentTick) {
        dimensionCooldowns.computeIfAbsent(dimension, ignored -> new HashMap<>())
            .put(new CooldownKey(source, typeId), currentTick + durationTicks);
    }

    public boolean hasCooldown(ResourceKey<Level> dimension, FaceAddress source, ResourceLocation typeId,
                               long currentTick) {
        Map<CooldownKey, Long> map = dimensionCooldowns.get(dimension);
        if (map == null) return false;
        return map.getOrDefault(new CooldownKey(source, typeId), Long.MIN_VALUE) > currentTick;
    }

    public void tick(ResourceKey<Level> dimension, long currentTick) {
        int interval = Math.max(1, LogisticsConstants.Performance.getFullCleanIntervalTicks());
        Long nextCleanupTick = nextFullCleanupTicks.get(dimension);
        if (nextCleanupTick == null) {
            nextFullCleanupTicks.put(dimension, scheduleAfter(currentTick, interval));
        } else if (currentTick >= nextCleanupTick) {
            cleanExpired(dimension, currentTick, Integer.MAX_VALUE);
            nextFullCleanupTicks.put(dimension, scheduleAfter(currentTick, interval));
            return;
        }
        Map<CooldownKey, Long> map = dimensionCooldowns.get(dimension);
        if (map != null && map.size() > LogisticsConstants.Performance.getBatchCleanThreshold()) {
            cleanExpired(dimension, currentTick, LogisticsConstants.Performance.getBatchCleanSize());
        }
    }

    private static long scheduleAfter(long currentTick, int interval) {
        long scheduledTick = currentTick + interval;
        return scheduledTick < currentTick ? Long.MAX_VALUE : scheduledTick;
    }

    private void cleanExpired(ResourceKey<Level> dimension, long currentTick, int maximum) {
        Map<CooldownKey, Long> map = dimensionCooldowns.get(dimension);
        if (map == null) return;
        Iterator<Map.Entry<CooldownKey, Long>> iterator = map.entrySet().iterator();
        int processed = 0;
        while (iterator.hasNext() && processed++ < maximum) {
            if (iterator.next().getValue() <= currentTick) iterator.remove();
        }
        if (map.isEmpty()) dimensionCooldowns.remove(dimension);
    }

    public void removeAllForSource(ResourceKey<Level> dimension, FaceAddress source) {
        Map<CooldownKey, Long> map = dimensionCooldowns.get(dimension);
        if (map == null) return;
        map.keySet().removeIf(key -> key.source().equals(source));
        if (map.isEmpty()) dimensionCooldowns.remove(dimension);
    }

    public void clearForDimension(ResourceKey<Level> dimension) {
        dimensionCooldowns.remove(dimension);
        nextFullCleanupTicks.remove(dimension);
    }

    public void clearAll() {
        dimensionCooldowns.clear();
        nextFullCleanupTicks.clear();
    }
}
