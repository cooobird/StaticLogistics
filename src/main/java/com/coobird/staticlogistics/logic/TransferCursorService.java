package com.coobird.staticlogistics.logic;

import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.api.LogisticsResource;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

/**
 * 管理传输游标：
 * - 顺序/一般轮询游标（每个节点每种类型一个 int[1]）
 * - 特定轮询（Round-Robin）游标（独立存储）
 *
 * <p>外层用 fastutil Long2ObjectOpenHashMap 避免 Long 装箱。
 * 内层用 HashMap（主线程单线程访问）。
 */
public class TransferCursorService {
    private final Long2ObjectOpenHashMap<Map<LogisticsResource<?>, int[]>> nodeCursors = new Long2ObjectOpenHashMap<>();
    private final Long2ObjectOpenHashMap<Map<ResourceLocation, Integer>> rrCursors = new Long2ObjectOpenHashMap<>();

    /**
     * 获取指定节点和传输类型的游标数组（长度为1，可修改）。
     */
    public int[] getOrCreateCursor(long nodeKey, LogisticsResource<?> type) {
        return nodeCursors
            .computeIfAbsent(nodeKey, k -> new HashMap<>())
            .computeIfAbsent(type, t -> new int[]{0});
    }

    /**
     * 移除节点所有游标（节点注销时调用）
     */
    public void removeCursor(long nodeKey) {
        nodeCursors.remove(nodeKey);
        rrCursors.remove(nodeKey);
    }

    /**
     * 获取轮询索引并自动更新（Round-Robin），返回当前索引。
     */
    public int getNextRoundRobinIndex(long nodeKey, int poolSize) {
        if (poolSize <= 0) return 0;
        Map<ResourceLocation, Integer> nodeMap = rrCursors.computeIfAbsent(nodeKey, k -> new HashMap<>());
        ResourceLocation defaultKey = StaticLogistics.asResource("default_rr");
        int current = nodeMap.getOrDefault(defaultKey, 0);
        int next = (current + 1) % poolSize;
        nodeMap.put(defaultKey, next);
        return current;
    }
}
