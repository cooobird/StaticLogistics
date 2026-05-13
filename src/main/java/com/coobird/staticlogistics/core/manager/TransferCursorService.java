package com.coobird.staticlogistics.core.manager;

import com.coobird.staticlogistics.api.type.TransferType;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 管理传输游标：
 * - 顺序/一般轮询游标（每个节点每种类型一个 int[1]）
 * - 特定轮询（Round-Robin）游标（独立存储）
 */
public class TransferCursorService {
    private final Map<Long, Map<TransferType, int[]>> nodeCursors = new ConcurrentHashMap<>();
    private final Map<Long, Map<ResourceLocation, Integer>> rrCursors = new ConcurrentHashMap<>();

    /**
     * 获取指定节点和传输类型的游标数组（长度为1，可修改）。
     */
    public int[] getOrCreateCursor(long nodeKey, TransferType type) {
        return nodeCursors
            .computeIfAbsent(nodeKey, k -> new ConcurrentHashMap<>())
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
        Map<ResourceLocation, Integer> nodeMap = rrCursors.computeIfAbsent(nodeKey, k -> new ConcurrentHashMap<>());
        ResourceLocation defaultKey = com.coobird.staticlogistics.Staticlogistics.asResource("default_rr");
        int current = nodeMap.getOrDefault(defaultKey, 0);
        int next = (current + 1) % poolSize;
        nodeMap.put(defaultKey, next);
        return current;
    }
}