package com.coobird.staticlogistics.transfer;

import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.api.LogisticsNode;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;
import java.util.function.IntUnaryOperator;

/// 按完整节点身份和资源类型管理传输游标。
public final class TransferCursorService {
    private static final ResourceLocation DEFAULT_KEY = StaticLogistics.asResource("default");

    private final Map<LogisticsNode, Map<ResourceLocation, Integer>> nodeCursors = new HashMap<>();

    private record DistributionKey(ResourceLocation type, long priority) {
    }

    private final Map<LogisticsNode, Map<DistributionKey, Integer>> distributionCursors = new HashMap<>();

    /// 轮询分发按资源类型与优先级隔离，避免多个组互相推进游标。
    public int getAndUpdateDistributionCursor(LogisticsNode node, ResourceLocation type, long priority,
                                              IntUnaryOperator update) {
        if (node == null || update == null)
            throw new IllegalArgumentException("Cursor update requires a node and operation");
        Map<DistributionKey, Integer> cursors =
            distributionCursors.computeIfAbsent(node, ignored -> new HashMap<>());
        DistributionKey key = new DistributionKey(type, priority);
        int previous = cursors.getOrDefault(key, 0);
        cursors.put(key, update.applyAsInt(previous));
        return previous;
    }

    public int getAndUpdateCursor(LogisticsNode node, ResourceLocation cursorType, IntUnaryOperator update) {
        if (node == null || update == null)
            throw new IllegalArgumentException("Cursor update requires a node and operation");
        ResourceLocation key = cursorType != null ? cursorType : DEFAULT_KEY;
        Map<ResourceLocation, Integer> cursors = nodeCursors.computeIfAbsent(node, ignored -> new HashMap<>());
        int previous = cursors.getOrDefault(key, 0);
        cursors.put(key, update.applyAsInt(previous));
        return previous;
    }

    /// 移除节点所有游标（节点注销时调用）
    public void removeCursor(LogisticsNode node) {
        if (node == null) return;
        nodeCursors.remove(node);
        distributionCursors.remove(node);
    }
}
