package com.coobird.staticlogistics.logic;

import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.api.LogisticsResource;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 管理传输游标：
 * - 顺序/一般轮询游标（每个节点每种类型一个 int[1]）
 * - 特定轮询（Round-Robin）游标（独立存储）
 */
public class TransferCursorService {
    private static final ResourceLocation DEFAULT_KEY = StaticLogistics.asResource("default");

    private final Map<Long, Map<ResourceLocation, int[]>> nodeCursors = new ConcurrentHashMap<>();
    private final Map<Long, Map<ResourceLocation, Integer>> rrCursors = new ConcurrentHashMap<>();

    /**
     * 获取指定节点和传输类型的游标数组（长度为1，可修改）。
     */
    public int[] getOrCreateCursor(long nodeKey, LogisticsResource<?> type) {
        ResourceLocation key = type != null ? type.typeId() : DEFAULT_KEY;
        return nodeCursors
            .computeIfAbsent(nodeKey, k -> new ConcurrentHashMap<>())
            .computeIfAbsent(key, t -> new int[]{0});
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
        Map<ResourceLocation, Integer> nodeMap = rrCursors.computeIfAbsent(nodeKey, k -> new ConcurrentHashMap<>());
        ResourceLocation defaultKey = StaticLogistics.asResource("default_rr");
        int current = nodeMap.getOrDefault(defaultKey, 0);
        int next = (current + 1) % poolSize;
        nodeMap.put(defaultKey, next);
        return current;
    }
}