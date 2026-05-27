package com.coobird.staticlogistics.transfer.strategy.extract;

import com.coobird.staticlogistics.api.type.ExtractionMode;
import com.coobird.staticlogistics.transfer.context.TransferContext;

/**
 * 物品槽位提取策略 —— 控制每 tick 从源容器槽位的遍历起点。
 */
public interface ItemExtractionStrategy {

    /**
     * 根据提取模式获取对应策略实例。
     */
    static ItemExtractionStrategy forMode(ExtractionMode mode) {
        return switch (mode) {
            case SLOT_ROUND_ROBIN -> RoundRobinExtractionStrategy.INSTANCE;
            default -> SequentialExtractionStrategy.INSTANCE;
        };
    }

    /**
     * 每个 tick 开始传输前调用，返回 slotOrder 中的遍历起点。
     *
     * @param passCount 本次通过源过滤的槽位数量（即遍历范围）
     * @param ctx       传输上下文，可从中取游标等信息
     * @return 遍历起点索引 [0, passCount)
     */
    int beginTick(int passCount, TransferContext ctx);

    /**
     * 每个 tick 传输结束后调用，更新游标等持久状态。
     *
     * @param lastProcessedIdx 本轮最后一个成功提取的 slotOrder 中的索引
     * @param passCount        槽位数量
     * @param ctx              传输上下文
     * @param movedAny         本轮是否实际传输了物品
     */
    void endTick(int lastProcessedIdx, int passCount, TransferContext ctx, boolean movedAny);
}
