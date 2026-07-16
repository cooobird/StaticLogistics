package com.coobird.staticlogistics.transfer;

/**
 * 按完整节点地址累计的服务器会话统计快照。
 */
public class NodeStats {
    private static final int RATE_SLOTS = 5;
    private static final long TICKS_PER_MINUTE = 1_200L;

    public long sentCount;
    public long sentAmount;
    public long receivedCount;
    public long receivedAmount;
    public int posX, posY, posZ;
    public String dim;
    public String face;
    long firstTransferTick = -1;
    long lastTransferTick = -1;
    private final long[] rateSlotIds = new long[RATE_SLOTS];
    private final long[] rateSlotCounts = new long[RATE_SLOTS];
    private long snapshotTick = -1;

    public NodeStats() {
        java.util.Arrays.fill(rateSlotIds, Long.MIN_VALUE);
    }

    public long sentAmount() {
        return sentAmount;
    }

    public long receivedAmount() {
        return receivedAmount;
    }

    /** 返回最近五分钟滑动窗口内的平均每分钟传输次数。 */
    public double getTransfersPerMinute() {
        if (snapshotTick < 0) return 0;
        long currentSlot = Math.floorDiv(snapshotTick, TICKS_PER_MINUTE);
        long total = 0;
        int observedSlots = 0;
        for (int index = 0; index < RATE_SLOTS; index++) {
            long age = currentSlot - rateSlotIds[index];
            if (age < 0 || age >= RATE_SLOTS) continue;
            total = saturatedAdd(total, rateSlotCounts[index]);
            observedSlots = Math.max(observedSlots, (int) age + 1);
        }
        return observedSlots == 0 ? 0 : (double) total / observedSlots;
    }

    public long ticksSinceLastTransfer(long currentTick) {
        return lastTransferTick < 0 ? -1 : Math.max(0, currentTick - lastTransferTick);
    }

    void recordTransfer(long gameTick) {
        long slotId = Math.floorDiv(gameTick, TICKS_PER_MINUTE);
        int index = Math.floorMod(slotId, RATE_SLOTS);
        if (rateSlotIds[index] != slotId) {
            rateSlotIds[index] = slotId;
            rateSlotCounts[index] = 0;
        }
        rateSlotCounts[index] = saturatedAdd(rateSlotCounts[index], 1);
    }

    NodeStats copy() {
        NodeStats copy = new NodeStats();
        copy.sentCount = sentCount;
        copy.sentAmount = sentAmount;
        copy.receivedCount = receivedCount;
        copy.receivedAmount = receivedAmount;
        copy.posX = posX;
        copy.posY = posY;
        copy.posZ = posZ;
        copy.dim = dim;
        copy.face = face;
        copy.firstTransferTick = firstTransferTick;
        copy.lastTransferTick = lastTransferTick;
        System.arraycopy(rateSlotIds, 0, copy.rateSlotIds, 0, RATE_SLOTS);
        System.arraycopy(rateSlotCounts, 0, copy.rateSlotCounts, 0, RATE_SLOTS);
        copy.snapshotTick = snapshotTick;
        return copy;
    }

    NodeStats snapshotAt(long currentTick) {
        NodeStats copy = copy();
        copy.snapshotTick = currentTick;
        return copy;
    }

    private static long saturatedAdd(long value, long increment) {
        return value > Long.MAX_VALUE - increment ? Long.MAX_VALUE : value + increment;
    }
}
