package com.coobird.staticlogistics.transfer.strategy;

import com.coobird.staticlogistics.api.type.ExtractionMode;
import com.coobird.staticlogistics.transfer.TransferContext;

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
     * 当前候选已成功传输或确定无法被任何目标接收后，推进持久游标。
     *
     * @param processedIdx 当前候选在 slotOrder 中的索引
     * @param passCount    本次通过过滤的槽位数量
     * @param ctx          传输上下文
     */
    void advanceAfterAttempt(int processedIdx, int passCount, TransferContext ctx);

    /**
     * 是否允许在候选被全部目标拒绝后跳到下一候选。
     */
    default boolean supportsRejectedCandidateAdvance() {
        return false;
    }
}
