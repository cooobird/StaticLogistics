package com.coobird.staticlogistics.transfer.strategy;

import com.coobird.staticlogistics.transfer.TransferContext;

/**
 * 槽位轮询提取：从上次结束位置的下一个槽位开始，确保所有槽位公平轮转。
 * <p>
 * 游标存于 {@link TransferContext#getSlotCursor()} 返回的 int[1] 中，
 * 值为下一次应从 slotOrder 中读取的位置索引。
 */
public enum RoundRobinExtractionStrategy implements ItemExtractionStrategy {
    INSTANCE;

    @Override
    public int beginTick(int passCount, TransferContext ctx) {
        int[] cursor = ctx.getSlotCursor();
        return Math.floorMod(cursor[0], passCount);
    }

    @Override
    public void advanceAfterAttempt(int processedIdx, int passCount, TransferContext ctx) {
        int[] cursor = ctx.getSlotCursor();
        cursor[0] = (processedIdx + 1) % passCount;
    }

    @Override
    public boolean supportsRejectedCandidateAdvance() {
        return true;
    }
}
