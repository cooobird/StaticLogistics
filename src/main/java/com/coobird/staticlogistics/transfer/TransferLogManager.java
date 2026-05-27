package com.coobird.staticlogistics.transfer;

import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.api.type.TransferType;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * 传输日志管理器 — 环形缓冲记录最近传输，提供累计统计
 */

/**
 * 传输日志 — 记录最近 200 条传输，同时累计总量/按类型/按节点统计
 * /sl stats 命令从这读数据
 */
public class TransferLogManager {
    // 全局唯一实例，想在哪记日志直接 TransferLogManager.get().logTransfer(...)
    private static final TransferLogManager INSTANCE = new TransferLogManager();

    public static TransferLogManager get() {
        return INSTANCE;
    }

    // ── 环形缓冲区 ──
    private final Deque<TransferEntry> recentLog = new ConcurrentLinkedDeque<>();
    private static final int MAX_LOG_ENTRIES = 200;

    // ── 累计统计 ──
    private long totalTransfers;
    private long totalAmount;
    private long failedTransfers;
    private final Map<String, TypeStats> perType = new HashMap<>();
    private final Map<Long, NodeStats> perNode = new LinkedHashMap<>(); // 保持插入顺序用于 TopN

    public record TransferEntry(
        long timestamp,
        String sourceDim, int sx, int sy, int sz, String sourceFace,
        String targetDim, int tx, int ty, int tz, String targetFace,
        String typeName, int typeColor,
        int amount, boolean success
    ) {
    }

    public static class TypeStats {
        public long count;
        public long totalAmount;
    }

    public static class NodeStats {
        public long sentCount;
        public long sentAmount;
        public long receivedCount;
        public long receivedAmount;
        public int posX, posY, posZ;
        public String dim;
        public String face;
    }

    /**
     * 记录一条传输日志
     */
    public void logTransfer(LogisticsNode source, LogisticsNode target,
                            TransferType type, int amount, boolean success) {
        TransferEntry entry = new TransferEntry(
            System.currentTimeMillis(),
            source.gPos().dimension().location().toString(),
            source.gPos().pos().getX(), source.gPos().pos().getY(), source.gPos().pos().getZ(),
            source.face().getName(),
            target.gPos().dimension().location().toString(),
            target.gPos().pos().getX(), target.gPos().pos().getY(), target.gPos().pos().getZ(),
            target.face().getName(),
            type.id().getPath(), type.color(),
            amount, success
        );

        // 环形缓冲区
        while (recentLog.size() >= MAX_LOG_ENTRIES) {
            recentLog.pollFirst();
        }
        recentLog.offerLast(entry);

        // 累计统计
        totalTransfers++;
        totalAmount += amount;
        if (!success) failedTransfers++;

        // 按类型统计
        perType.computeIfAbsent(type.id().getPath(), k -> new TypeStats()).count++;
        perType.get(type.id().getPath()).totalAmount += amount;

        // 按源节点统计
        long srcKey = source.toKey();
        NodeStats srcStats = perNode.computeIfAbsent(srcKey, k -> {
            NodeStats s = new NodeStats();
            s.posX = source.gPos().pos().getX();
            s.posY = source.gPos().pos().getY();
            s.posZ = source.gPos().pos().getZ();
            s.dim = source.gPos().dimension().location().toString();
            s.face = source.face().getName();
            return s;
        });
        srcStats.sentCount++;
        srcStats.sentAmount += amount;

        // 按目标节点统计
        long tgtKey = target.toKey();
        NodeStats tgtStats = perNode.computeIfAbsent(tgtKey, k -> {
            NodeStats s = new NodeStats();
            s.posX = target.gPos().pos().getX();
            s.posY = target.gPos().pos().getY();
            s.posZ = target.gPos().pos().getZ();
            s.dim = target.gPos().dimension().location().toString();
            s.face = target.face().getName();
            return s;
        });
        tgtStats.receivedCount++;
        tgtStats.receivedAmount += amount;
    }

    // ── 查询接口 ──

    public List<TransferEntry> getRecent(int count) {
        List<TransferEntry> list = new ArrayList<>(recentLog);
        if (list.size() <= count) return list;
        return list.subList(list.size() - count, list.size());
    }

    public long getTotalTransfers() {
        return totalTransfers;
    }

    public long getTotalAmount() {
        return totalAmount;
    }

    public long getFailedTransfers() {
        return failedTransfers;
    }

    public Map<String, TypeStats> getPerTypeStats() {
        return Collections.unmodifiableMap(perType);
    }

    public List<Map.Entry<Long, NodeStats>> getTopNodes(int n, boolean bySent) {
        return perNode.entrySet().stream()
            .sorted((a, b) -> {
                long va = bySent ? a.getValue().sentCount : a.getValue().receivedCount;
                long vb = bySent ? b.getValue().sentCount : b.getValue().receivedCount;
                return Long.compare(vb, va);
            })
            .limit(n)
            .toList();
    }

    public int getLogSize() {
        return recentLog.size();
    }

    public void reset() {
        recentLog.clear();
        totalTransfers = 0;
        totalAmount = 0;
        failedTransfers = 0;
        perType.clear();
        perNode.clear();
    }
}
