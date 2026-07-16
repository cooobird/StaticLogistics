package com.coobird.staticlogistics.transfer;

import java.util.Arrays;

/** 使用服务器游戏刻维护最近五分钟的传输速率。 */
class TransferRateCalculator {
    private static final int SLOTS = 5;
    private static final long TICKS_PER_MINUTE = 1_200L;

    private final long[] slotIds = new long[SLOTS];
    private final long[] slotCounts = new long[SLOTS];
    private final long[] slotAmounts = new long[SLOTS];
    private long firstRecordTick = -1;

    TransferRateCalculator() {
        Arrays.fill(slotIds, Long.MIN_VALUE);
    }

    void record(long amount, long gameTick) {
        if (firstRecordTick < 0) firstRecordTick = gameTick;
        long slotId = Math.floorDiv(gameTick, TICKS_PER_MINUTE);
        int index = Math.floorMod(slotId, SLOTS);
        if (slotIds[index] != slotId) {
            slotIds[index] = slotId;
            slotCounts[index] = 0;
            slotAmounts[index] = 0;
        }
        slotCounts[index] = saturatedAdd(slotCounts[index], 1);
        slotAmounts[index] = saturatedAdd(slotAmounts[index], amount);
    }

    double getTransfersPerMinute(long gameTick) {
        return rate(sumRecent(slotCounts, gameTick), gameTick);
    }

    double getAmountPerMinute(long gameTick) {
        return rate(sumRecent(slotAmounts, gameTick), gameTick);
    }

    private long sumRecent(long[] values, long gameTick) {
        long currentSlot = Math.floorDiv(gameTick, TICKS_PER_MINUTE);
        long total = 0;
        for (int index = 0; index < SLOTS; index++) {
            long age = currentSlot - slotIds[index];
            if (age >= 0 && age < SLOTS) total = saturatedAdd(total, values[index]);
        }
        return total;
    }

    private double rate(long total, long gameTick) {
        if (firstRecordTick < 0 || total == 0) return 0;
        long observedMinutes = Math.max(1,
            Math.min(SLOTS, Math.floorDiv(Math.max(0, gameTick - firstRecordTick), TICKS_PER_MINUTE) + 1));
        return (double) total / observedMinutes;
    }

    void clear() {
        Arrays.fill(slotIds, Long.MIN_VALUE);
        Arrays.fill(slotCounts, 0);
        Arrays.fill(slotAmounts, 0);
        firstRecordTick = -1;
    }

    private static long saturatedAdd(long value, long increment) {
        if (increment > 0 && value > Long.MAX_VALUE - increment) return Long.MAX_VALUE;
        if (increment < 0 && value < Long.MIN_VALUE - increment) return Long.MIN_VALUE;
        return value + increment;
    }
}
