package com.coobird.staticlogistics.transfer.strategy.extract;

import com.coobird.staticlogistics.transfer.context.TransferContext;

/**
 * 顺序提取：每 tick 始终从 slotOrder[0] 开始遍历。
 */
public enum SequentialExtractionStrategy implements ItemExtractionStrategy {
    INSTANCE;

    @Override
    public int beginTick(int passCount, TransferContext ctx) {
        return 0;
    }

    @Override
    public void endTick(int lastProcessedIdx, int passCount, TransferContext ctx, boolean movedAny) {
    }
}
