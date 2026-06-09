package com.coobird.staticlogistics.transfer.log;

/**
 * 传输速率计算器 —— 5 个时间槽，每槽 1 分钟，循环覆盖。
 */
class TransferRateCalculator {
    private static final int SLOTS = 5;
    private static final long SLOT_DURATION_MS = 60_000L;

    private final long[] slotCounts = new long[SLOTS];
    private final long[] slotAmounts = new long[SLOTS];
    private long slotStartTime = System.currentTimeMillis();
    private int currentSlot = 0;

    synchronized void record(long amount) {
        advanceSlot(System.currentTimeMillis());
        slotCounts[currentSlot]++;
        slotAmounts[currentSlot] += amount;
    }

    synchronized double getTransfersPerMinute() {
        advanceSlot(System.currentTimeMillis());
        long total = 0;
        for (long c : slotCounts) total += c;
        return (double) total / SLOTS;
    }

    synchronized double getAmountPerMinute() {
        advanceSlot(System.currentTimeMillis());
        long total = 0;
        for (long a : slotAmounts) total += a;
        return (double) total / SLOTS;
    }

    synchronized void clear() {
        java.util.Arrays.fill(slotCounts, 0);
        java.util.Arrays.fill(slotAmounts, 0);
        slotStartTime = System.currentTimeMillis();
        currentSlot = 0;
    }

    private void advanceSlot(long now) {
        long elapsed = now - slotStartTime;
        if (elapsed < SLOT_DURATION_MS) return;
        int steps = (int) Math.min(elapsed / SLOT_DURATION_MS, SLOTS);
        for (int i = 0; i < steps; i++) {
            currentSlot = (currentSlot + 1) % SLOTS;
            slotCounts[currentSlot] = 0;
            slotAmounts[currentSlot] = 0;
        }
        slotStartTime += (long) steps * SLOT_DURATION_MS;
    }
}
