package com.coobird.staticlogistics.transfer.log;

import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.api.LogisticsResource;
import com.coobird.staticlogistics.transfer.TransferFailureReason;

import java.util.List;
import java.util.Map;

/**
 * 传输日志 facade —— 协调最近日志、累计统计、速率计算三个组件。
 */
public class TransferLogManager {
    private static final TransferLogManager INSTANCE = new TransferLogManager();

    public static TransferLogManager get() {
        return INSTANCE;
    }

    private final TransferRecentLog recentLog = new TransferRecentLog();
    private final TransferStats stats = new TransferStats();
    private final TransferRateCalculator rateCalc = new TransferRateCalculator();

    private volatile long lastTransferTime = 0;

    public void logTransfer(LogisticsNode source, LogisticsNode target,
                            LogisticsResource<?> type, long amount, boolean success) {
        logTransfer(source, target, type, amount, success, null);
    }

    public void logTransfer(LogisticsNode source, LogisticsNode target,
                            LogisticsResource<?> type, long amount, boolean success,
                            TransferFailureReason reason) {
        long now = System.currentTimeMillis();

        TransferEntry entry = TransferEntry.obtain(
            now,
            source.gPos().dimension().location().toString(),
            source.gPos().pos().getX(), source.gPos().pos().getY(), source.gPos().pos().getZ(),
            source.face().getName(),
            target.gPos().dimension().location().toString(),
            target.gPos().pos().getX(), target.gPos().pos().getY(), target.gPos().pos().getZ(),
            target.face().getName(),
            type.typeId().getPath(), type.color(),
            amount, success, reason != null ? reason.id().toString() : null
        );
        recentLog.add(entry);

        stats.incrementTotal(amount);
        if (!success) stats.incrementFailed();
        stats.recordType(type.typeId().getPath(), amount);
        stats.recordSource(source.toKey(),
            source.gPos().pos().getX(), source.gPos().pos().getY(), source.gPos().pos().getZ(),
            source.gPos().dimension().location().toString(), source.face().getName(), amount);
        stats.recordTarget(target.toKey(),
            target.gPos().pos().getX(), target.gPos().pos().getY(), target.gPos().pos().getZ(),
            target.gPos().dimension().location().toString(), target.face().getName(), amount);

        rateCalc.record(amount);
        lastTransferTime = now;
    }

    public List<TransferEntry> getRecent(int count) {
        return recentLog.getRecent(count);
    }

    public int getLogSize() {
        return recentLog.size();
    }

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

    public double getTransfersPerMinute() {
        return rateCalc.getTransfersPerMinute();
    }

    public double getAmountPerMinute() {
        return rateCalc.getAmountPerMinute();
    }

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
