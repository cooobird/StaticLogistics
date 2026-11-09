package com.coobird.staticlogistics.transfer.strategy;

import com.coobird.staticlogistics.transfer.TransferContext;

/// 槽位轮询提取：从上次结束位置的下一个槽位开始，确保所有槽位公平轮转。
/// 游标值为下一次应读取的真实槽位下标。
public enum RoundRobinExtractionStrategy implements ItemExtractionStrategy {
    INSTANCE;

    @Override
    public int beginActivation(int slotCount, TransferContext ctx) {
        return Math.floorMod(ctx.slotCursor(), slotCount);
    }

    @Override
    public void advanceAfterAttempt(int processedSlot, int slotCount, TransferContext ctx) {
        ctx.slotCursor((processedSlot + 1) % slotCount);
    }
}
