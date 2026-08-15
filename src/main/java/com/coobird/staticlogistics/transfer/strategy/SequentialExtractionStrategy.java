package com.coobird.staticlogistics.transfer.strategy;

import com.coobird.staticlogistics.transfer.TransferContext;

/**
 * 顺序提取：通常从首槽开始；仅在性能预算截断时从上次中断处继续。
 */
public enum SequentialExtractionStrategy implements ItemExtractionStrategy {
    INSTANCE;

    @Override
    public int beginActivation(int slotCount, TransferContext ctx) {
        return Math.floorMod(ctx.getSlotCursor()[0], slotCount);
    }

    @Override
    public void advanceAfterAttempt(int processedSlot, int slotCount, TransferContext ctx) {
        ctx.getSlotCursor()[0] = (processedSlot + 1) % slotCount;
    }

    @Override
    public void finishActivation(TransferContext ctx) {
        ctx.getSlotCursor()[0] = 0;
    }
}
