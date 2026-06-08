package com.coobird.staticlogistics.transfer.log;

import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.api.LogisticsResource;
import com.coobird.staticlogistics.transfer.TransferFailureReason;

import java.util.List;
import java.util.Map;

/**
 * 传输日志 facade —— 协调最近日志、累计统计、速率计算三个组件。
 * <p>
 * 线程安全策略：
 * <ul>
 *   <li>{@link TransferRecentLog} — ConcurrentLinkedDeque，自身线程安全</li>
 *   <li>{@link TransferStats} — LongAdder 计数器无锁，perType/perNode 用 synchronized</li>
 *   <li>{@link TransferRateCalculator} — synchronized 保护所有状态</li>
 * </ul>
 * logTransfer 由传输管线多线程调用，get* 方法由命令线程调用。
 */
public class TransferLogManager {
    private static final TransferLogManager INSTANCE = new TransferLogManager();

    public static TransferLogManager get() {
        return INSTANCE;
    }

    private final TransferRecentLog recentLog = new TransferRecentLog();
    private final TransferStats stats = new TransferStats();
    private final TransferRateCalculator rateCalc = new TransferRateCalculator();

    // 最后一次传输时间（volatile：写入多线程，读取命令线程）
    private volatile long lastTransferTime = 0;

    // 记录
    public void logTransfer(LogisticsNode source, LogisticsNode target,
                            LogisticsResource<?> type, int amount, boolean success) {
        logTransfer(source, target, type, amount, success, null);
    }

    public void logTransfer(LogisticsNode source, LogisticsNode target,
                            LogisticsResource<?> type, int amount, boolean success,
                            TransferFailureReason reason) {
        long now = System.currentTimeMillis();

        // 最近日志
        recentLog.add(new TransferEntry(
            now,
            source.gPos().dimension().location().toString(),
            source.gPos().pos().getX(), source.gPos().pos().getY(), source.gPos().pos().getZ(),
            source.face().getName(),
            target.gPos().dimension().location().toString(),
            target.gPos().pos().getX(), target.gPos().pos().getY(), target.gPos().pos().getZ(),
            target.face().getName(),
            type.typeId().getPath(), type.color(),
            amount, success, reason != null ? reason.id().toString() : null
        ));

        // 累计统计
        stats.incrementTotal(amount);
        if (!success) stats.incrementFailed();
        stats.recordType(type.typeId().getPath(), amount);
        stats.recordSource(source.toKey(),
            source.gPos().pos().getX(), source.gPos().pos().getY(), source.gPos().pos().getZ(),
            source.gPos().dimension().location().toString(), source.face().getName(), amount);
        stats.recordTarget(target.toKey(),
            target.gPos().pos().getX(), target.gPos().pos().getY(), target.gPos().pos().getZ(),
            target.gPos().dimension().location().toString(), target.face().getName(), amount);

        // 速率统计
        rateCalc.record(amount);

        lastTransferTime = now;
    }

    // 最近日志查询
    public List<TransferEntry> getRecent(int count) {
        return recentLog.getRecent(count);
    }

    public int getLogSize() {
        return recentLog.size();
    }

    // 累计统计查询
    public long getTotalTransfers() {
        return stats.getTotalTransfers();
    }

    public long getTotalAmount() {
        return stats.getTotalAmount();
    }

    public long getFailedTransfers() {
        return stats.getFailedTransfers();
    }

    public Map<String, TypeStats> getPerTypeStats() {
        return stats.getPerTypeStats();
    }

    public NodeStats getPerNodeStats(long nodeKey) {
        return stats.getPerNodeStats(nodeKey);
    }

    public List<Map.Entry<Long, NodeStats>> getTopNodes(int n, boolean bySent) {
        return stats.getTopNodes(n, bySent);
    }

    // 速率查询
    public double getTransfersPerMinute() {
        return rateCalc.getTransfersPerMinute();
    }

    public double getAmountPerMinute() {
        return rateCalc.getAmountPerMinute();
    }

    // 其他
    public long getTimeSinceLastTransfer() {
        if (lastTransferTime == 0) return -1;
        return System.currentTimeMillis() - lastTransferTime;
    }

    public void reset() {
        recentLog.clear();
        stats.clear();
        rateCalc.clear();
        lastTransferTime = 0;
    }
}
