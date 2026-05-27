package com.coobird.staticlogistics.transfer.strategy.extract;

import com.coobird.staticlogistics.transfer.context.TransferContext;

/**
 * 槽位轮询提取：从上次结束位置的下一个槽位开始，确保所有槽位公平轮转。
 * <p>
 * 游标存于 {@link TransferContext#getSlotCursor()} 返回的 int[1] 中，
 * 值为 slotOrder 中的绝对索引。
 */
public enum RoundRobinExtractionStrategy implements ItemExtractionStrategy {
    INSTANCE;

    @Override
    public int beginTick(int passCount, TransferContext ctx) {
        int[] cursor = ctx.getSlotCursor();
        return cursor[0] % passCount;
    }

    @Override
    public void endTick(int lastProcessedIdx, int passCount, TransferContext ctx, boolean movedAny) {
        if (!movedAny) return;
        int[] cursor = ctx.getSlotCursor();
        cursor[0] = (lastProcessedIdx + 1) % passCount;
    }
}
