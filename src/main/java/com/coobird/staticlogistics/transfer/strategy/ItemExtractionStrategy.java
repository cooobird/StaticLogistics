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
     * 每次激活开始传输前调用，返回真实槽位的遍历起点。
     *
     * @param slotCount 源句柄公开的真实槽位数量
     * @param ctx       传输上下文，可从中取游标等信息
     * @return 遍历起点索引 [0, slotCount)
     */
    int beginActivation(int slotCount, TransferContext ctx);

    /**
     * 当前候选已成功传输或确定无法被任何目标接收后，推进持久游标。
     *
     * @param processedSlot 当前候选的真实槽位下标
     * @param slotCount     源句柄公开的真实槽位数量
     * @param ctx           传输上下文
     */
    void advanceAfterAttempt(int processedSlot, int slotCount, TransferContext ctx);

    /**
     * 本轮激活未被性能预算截断时，清理临时续扫状态。
     */
    default void finishActivation(TransferContext ctx) {
    }

}
