package com.coobird.staticlogistics.transfer;

import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.api.type.TransferType;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;

/*
  传输日志管理器 — 环形缓冲记录最近传输，提供累计统计
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

    // 环形缓冲区（最近传输记录）
    private final Deque<TransferEntry> recentLog = new ConcurrentLinkedDeque<>();
    private static final int MAX_LOG_ENTRIES = 200;

    // 所有可变状态共用一把锁（TransferLogManager 是全局单例，多线程访问）
    private final Object lock = new Object();

    // 累计统计
    private long totalTransfers;
    private long totalAmount;
    private long failedTransfers;
    private final Map<String, TypeStats> perType = new HashMap<>();
    private final Map<Long, NodeStats> perNode = new LinkedHashMap<>(); // 保持插入顺序用于 TopN

    // 速率统计：5 个时间槽，每槽 1 分钟，循环覆盖
    private static final int RATE_SLOTS = 5;
    private static final long SLOT_DURATION_MS = 60_000L;
    private final long[] slotCounts = new long[RATE_SLOTS];
    private final long[] slotAmounts = new long[RATE_SLOTS];
    private long slotStartTime = System.currentTimeMillis();
    private int currentSlot = 0;

    // 最后一次传输时间
    private volatile long lastTransferTime = 0;

    public record TransferEntry(
        long timestamp,
        String sourceDim, int sx, int sy, int sz, String sourceFace,
        String targetDim, int tx, int ty, int tz, String targetFace,
        String typeName, int typeColor,
        int amount, boolean success, String failureReason
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
     * 记录一条成功的传输日志
     */
    public void logTransfer(LogisticsNode source, LogisticsNode target,
                            TransferType type, int amount, boolean success) {
        logTransfer(source, target, type, amount, success, null);
    }

    /**
     * 记录一条传输日志（含失败原因）
     */
    public void logTransfer(LogisticsNode source, LogisticsNode target,
                            TransferType type, int amount, boolean success,
                            TransferFailureReason reason) {
        long now = System.currentTimeMillis();
        TransferEntry entry = new TransferEntry(
            now,
            source.gPos().dimension().location().toString(),
            source.gPos().pos().getX(), source.gPos().pos().getY(), source.gPos().pos().getZ(),
            source.face().getName(),
            target.gPos().dimension().location().toString(),
            target.gPos().pos().getX(), target.gPos().pos().getY(), target.gPos().pos().getZ(),
            target.face().getName(),
            type.id().getPath(), type.color(),
            amount, success, reason != null ? reason.getId() : null
        );

        // 环形缓冲区（ConcurrentLinkedDeque 自身线程安全）
        while (recentLog.size() >= MAX_LOG_ENTRIES) {
            recentLog.pollFirst();
        }
        recentLog.offerLast(entry);

        synchronized (lock) {
            // 累计统计
            totalTransfers++;
            totalAmount += amount;
            if (!success) failedTransfers++;

            // 速率统计
            advanceSlot(now);
            slotCounts[currentSlot]++;
            slotAmounts[currentSlot] += amount;

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

        lastTransferTime = now;
    }

    // 查询接口
    public List<TransferEntry> getRecent(int count) {
        List<TransferEntry> list = new ArrayList<>(recentLog);
        if (list.size() <= count) return list;
        return list.subList(list.size() - count, list.size());
    }

    public long getTotalTransfers() {
        synchronized (lock) {
            return totalTransfers;
        }
    }

    public long getTotalAmount() {
        synchronized (lock) {
            return totalAmount;
        }
    }

    public long getFailedTransfers() {
        synchronized (lock) {
            return failedTransfers;
        }
    }

    public Map<String, TypeStats> getPerTypeStats() {
        synchronized (lock) {
            return Collections.unmodifiableMap(new HashMap<>(perType));
        }
    }

    /**
     * 获取指定节点的统计信息，无记录返回 null
     */
    public NodeStats getPerNodeStats(long nodeKey) {
        synchronized (lock) {
            return perNode.get(nodeKey);
        }
    }

    public List<Map.Entry<Long, NodeStats>> getTopNodes(int n, boolean bySent) {
        synchronized (lock) {
            return perNode.entrySet().stream()
                .sorted((a, b) -> {
                    long va = bySent ? a.getValue().sentCount : a.getValue().receivedCount;
                    long vb = bySent ? b.getValue().sentCount : b.getValue().receivedCount;
                    return Long.compare(vb, va);
                })
                .limit(n)
                .toList();
        }
    }

    public int getLogSize() {
        return recentLog.size();
    }

    /**
     * 推进到当前时间对应的槽。超过整个窗口则清零所有槽。
     */
    private void advanceSlot(long now) {
        long elapsed = now - slotStartTime;
        if (elapsed < SLOT_DURATION_MS) return;
        int steps = (int) Math.min(elapsed / SLOT_DURATION_MS, RATE_SLOTS);
        for (int i = 0; i < steps; i++) {
            currentSlot = (currentSlot + 1) % RATE_SLOTS;
            slotCounts[currentSlot] = 0;
            slotAmounts[currentSlot] = 0;
        }
        slotStartTime += (long) steps * SLOT_DURATION_MS;
    }

    /**
     * 获取最近 5 分钟的平均传输速率 (次/分钟)
     */
    public double getTransfersPerMinute() {
        synchronized (lock) {
            advanceSlot(System.currentTimeMillis());
            long total = 0;
            for (long c : slotCounts) total += c;
            return (double) total / RATE_SLOTS;
        }
    }

    /**
     * 获取最近 5 分钟的平均传输量 (物品数/分钟)
     */
    public double getAmountPerMinute() {
        synchronized (lock) {
            advanceSlot(System.currentTimeMillis());
            long total = 0;
            for (long a : slotAmounts) total += a;
            return (double) total / RATE_SLOTS;
        }
    }

    /**
     * 获取距离最后一次传输过了多少毫秒，无传输记录返回 -1
     */
    public long getTimeSinceLastTransfer() {
        if (lastTransferTime == 0) return -1;
        return System.currentTimeMillis() - lastTransferTime;
    }

    public void reset() {
        synchronized (lock) {
            recentLog.clear();
            totalTransfers = 0;
            totalAmount = 0;
            failedTransfers = 0;
            perType.clear();
            perNode.clear();
            Arrays.fill(slotCounts, 0);
            Arrays.fill(slotAmounts, 0);
            lastTransferTime = 0;
        }
    }
}
