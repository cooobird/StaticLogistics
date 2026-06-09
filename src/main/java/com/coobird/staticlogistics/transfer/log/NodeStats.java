package com.coobird.staticlogistics.transfer.log;

/**
 * 按节点统计。
 */
public class NodeStats {
    public long sentCount;
    public long sentAmount;
    public long receivedCount;
    public long receivedAmount;
    public int posX, posY, posZ;
    public String dim;
    public String face;
    public long firstTransferTime;
    public long lastTransferTime;

    public double getTransfersPerMinute() {
        long totalTransfers = sentCount + receivedCount;
        if (totalTransfers <= 1 || firstTransferTime == 0 || lastTransferTime == 0) return 0;
        long durationMs = lastTransferTime - firstTransferTime;
        if (durationMs <= 0) return 0;
        return (double) totalTransfers / durationMs * 60000.0;
    }
}
