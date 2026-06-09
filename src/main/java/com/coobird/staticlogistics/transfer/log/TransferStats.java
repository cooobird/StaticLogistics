package com.coobird.staticlogistics.transfer.log;

import java.util.*;
import java.util.concurrent.atomic.LongAdder;

/**
 * 传输累计统计 —— 总量计数器(无锁) + 按类型/按节点统计(锁保护)。
 */
class TransferStats {
    private final LongAdder totalTransfers = new LongAdder();
    private final LongAdder totalAmount = new LongAdder();
    private final LongAdder failedTransfers = new LongAdder();

    private final Map<String, TypeStats> perType = new HashMap<>();
    private static final int MAX_NODE_STATS = 500;
    private final Map<Long, NodeStats> perNode = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, NodeStats> eldest) {
            return size() > MAX_NODE_STATS;
        }
    };

    void incrementTotal(long amount) {
        totalTransfers.increment();
        totalAmount.add(amount);
    }

    void incrementFailed() {
        failedTransfers.increment();
    }

    synchronized void recordType(String typeId, long amount) {
        perType.computeIfAbsent(typeId, k -> new TypeStats()).count++;
        perType.get(typeId).totalAmount += amount;
    }

    synchronized void recordSource(long srcKey, int sx, int sy, int sz, String dim, String face, long amount) {
        NodeStats s = perNode.computeIfAbsent(srcKey, k -> {
            NodeStats ns = new NodeStats();
            ns.posX = sx;
            ns.posY = sy;
            ns.posZ = sz;
            ns.dim = dim;
            ns.face = face;
            ns.firstTransferTime = System.currentTimeMillis();
            return ns;
        });
        s.sentCount++;
        s.sentAmount += amount;
        s.lastTransferTime = System.currentTimeMillis();
    }

    synchronized void recordTarget(long tgtKey, int tx, int ty, int tz, String dim, String face, long amount) {
        NodeStats s = perNode.computeIfAbsent(tgtKey, k -> {
            NodeStats ns = new NodeStats();
            ns.posX = tx;
            ns.posY = ty;
            ns.posZ = tz;
            ns.dim = dim;
            ns.face = face;
            ns.firstTransferTime = System.currentTimeMillis();
            return ns;
        });
        s.receivedCount++;
        s.receivedAmount += amount;
        s.lastTransferTime = System.currentTimeMillis();
    }

    long getTotalTransfers() {
        return totalTransfers.sum();
    }

    long getTotalAmount() {
        return totalAmount.sum();
    }

    long getFailedTransfers() {
        return failedTransfers.sum();
    }

    synchronized Map<String, TypeStats> getPerTypeStats() {
        return Collections.unmodifiableMap(new HashMap<>(perType));
    }

    synchronized NodeStats getPerNodeStats(long nodeKey) {
        return perNode.get(nodeKey);
    }

    synchronized List<Map.Entry<Long, NodeStats>> getTopNodes(int n, boolean bySent) {
        return perNode.entrySet().stream()
            .sorted((a, b) -> {
                long va = bySent ? a.getValue().sentCount : a.getValue().receivedCount;
                long vb = bySent ? b.getValue().sentCount : b.getValue().receivedCount;
                return Long.compare(vb, va);
            })
            .limit(n)
            .toList();
    }

    synchronized void clear() {
        totalTransfers.reset();
        totalAmount.reset();
        failedTransfers.reset();
        perType.clear();
        perNode.clear();
    }
}
