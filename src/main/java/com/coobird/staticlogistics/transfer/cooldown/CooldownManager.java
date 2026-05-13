package com.coobird.staticlogistics.transfer.cooldown;

import it.unimi.dsi.fastutil.longs.Long2LongMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class CooldownManager {
    private final Map<ResourceKey<Level>, Long2LongMap> dimensionCooldowns = new ConcurrentHashMap<>();
    private final Map<ResourceKey<Level>, Integer> cleanCursors = new ConcurrentHashMap<>();

    private static final int CLEAN_THRESHOLD = 1000;  // 超过此数量才进行清理
    private static final int BATCH_SIZE = 200;       // 每次最多清理的条目数

    /**
     * 设置冷却
     *
     * @param dimension     维度
     * @param key           节点 key
     * @param durationTicks 冷却持续时间（tick）
     * @param currentTick   当前维度的游戏时间（绝对 tick）
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
     * 分片清理过期条目，每次调用最多清理 BATCH_SIZE 个条目。
     * 仅在条目数超过 CLEAN_THRESHOLD 时执行。
     */
    public void cleanExpiredBatched(ResourceKey<Level> dimension, long currentTick) {
        Long2LongMap map = dimensionCooldowns.get(dimension);
        if (map == null || map.size() < CLEAN_THRESHOLD) return;

        int cursor = cleanCursors.getOrDefault(dimension, 0);
        int size = map.size();
        if (cursor >= size) cursor = 0;

        LongIterator it = map.keySet().iterator();
        // 跳过 cursor 个元素
        for (int i = 0; i < cursor && it.hasNext(); i++) it.next();

        int processed = 0;
        int removed = 0;
        while (it.hasNext() && processed < BATCH_SIZE) {
            long key = it.next();
            if (map.get(key) <= currentTick) {
                it.remove();
                removed++;
            }
            processed++;
        }

        if (it.hasNext()) {
            cleanCursors.put(dimension, cursor + processed);
        } else {
            cleanCursors.remove(dimension);
        }

        if (removed > 0 && map.isEmpty()) {
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
     * 清理整个维度的冷却数据（维度卸载时调用）
     */
    public void clearForDimension(ResourceKey<Level> dimension) {
        dimensionCooldowns.remove(dimension);
        cleanCursors.remove(dimension);
    }
}