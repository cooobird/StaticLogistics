package com.coobird.staticlogistics.transfer;

import com.coobird.staticlogistics.api.LogisticsNode;

import java.util.*;

/**
 * 服务器会话内的传输累计统计。
 *
 * <p>传输、命令和 Jade 查询均在服务器主线程执行，因此这里使用普通计数器和集合。
 * 统计不会写入世界存档，服务器重启或执行重置命令后从零开始。
 */
class TransferStats {
    private long totalTransfers;
    private long totalAmount;
    private long failedTransfers;

    private final Map<String, TypeStats> perType = new HashMap<>();
    private static final int MAX_NODE_STATS = 500;
    private final Map<LogisticsNode, NodeStats> perNode = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<LogisticsNode, NodeStats> eldest) {
            return size() > MAX_NODE_STATS;
        }
    };

    void incrementTotal(long amount) {
        totalTransfers = saturatedAdd(totalTransfers, 1);
        totalAmount = saturatedAdd(totalAmount, amount);
    }

    void incrementFailed() {
        failedTransfers = saturatedAdd(failedTransfers, 1);
    }

    void recordType(String typeId, long amount) {
        TypeStats stats = perType.computeIfAbsent(typeId, ignored -> new TypeStats());
        stats.count = saturatedAdd(stats.count, 1);
        stats.totalAmount = saturatedAdd(stats.totalAmount, amount);
    }

    void recordSource(LogisticsNode source, long amount, long gameTick) {
        NodeStats stats = perNode.computeIfAbsent(source, ignored -> createNodeStats(source, gameTick));
        stats.sentCount = saturatedAdd(stats.sentCount, 1);
        stats.sentAmount = saturatedAdd(stats.sentAmount, amount);
        stats.lastTransferTick = gameTick;
        stats.recordTransfer(gameTick);
    }

    void recordTarget(LogisticsNode target, long amount, long gameTick) {
        NodeStats stats = perNode.computeIfAbsent(target, ignored -> createNodeStats(target, gameTick));
        stats.receivedCount = saturatedAdd(stats.receivedCount, 1);
        stats.receivedAmount = saturatedAdd(stats.receivedAmount, amount);
        stats.lastTransferTick = gameTick;
        stats.recordTransfer(gameTick);
    }

    private static NodeStats createNodeStats(LogisticsNode node, long gameTick) {
        NodeStats stats = new NodeStats();
        stats.posX = node.gPos().pos().getX();
        stats.posY = node.gPos().pos().getY();
        stats.posZ = node.gPos().pos().getZ();
        stats.dim = node.gPos().dimension().location().toString();
        stats.face = node.face().getName();
        stats.firstTransferTick = gameTick;
        stats.lastTransferTick = gameTick;
        return stats;
    }

    long getTotalTransfers() {
        return totalTransfers;
    }

    long getTotalAmount() {
        return totalAmount;
    }

    long getFailedTransfers() {
        return failedTransfers;
    }

    Map<String, TypeStats> getPerTypeStats() {
        Map<String, TypeStats> snapshot = new HashMap<>();
        perType.forEach((key, value) -> {
            TypeStats copy = new TypeStats();
            copy.count = value.count;
            copy.totalAmount = value.totalAmount;
            snapshot.put(key, copy);
        });
        return Collections.unmodifiableMap(snapshot);
    }

    NodeStats getPerNodeStats(LogisticsNode node, long currentTick) {
        NodeStats stats = perNode.get(node);
        return stats == null ? null : stats.snapshotAt(currentTick);
    }

    List<Map.Entry<LogisticsNode, NodeStats>> getTopNodes(int n, boolean bySent) {
        if (n <= 0) return List.of();
        return perNode.entrySet().stream()
            .sorted((a, b) -> {
                long left = bySent ? a.getValue().sentCount : a.getValue().receivedCount;
                long right = bySent ? b.getValue().sentCount : b.getValue().receivedCount;
                return Long.compare(right, left);
            })
            .limit(n)
            .<Map.Entry<LogisticsNode, NodeStats>>map(entry ->
                new AbstractMap.SimpleImmutableEntry<>(entry.getKey(), entry.getValue().copy()))
            .toList();
    }

    private static long saturatedAdd(long value, long increment) {
        if (increment > 0 && value > Long.MAX_VALUE - increment) return Long.MAX_VALUE;
        if (increment < 0 && value < Long.MIN_VALUE - increment) return Long.MIN_VALUE;
        return value + increment;
    }

    void clear() {
        totalTransfers = 0;
        totalAmount = 0;
        failedTransfers = 0;
        perType.clear();
        perNode.clear();
    }
}
