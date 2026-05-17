package com.coobird.staticlogistics.api;

import com.coobird.staticlogistics.transfer.context.TransferContext;

import java.util.List;

/**
 * 传输处理器——具体执行一次物流传输（抽物品/流体/能量等）
 */
@FunctionalInterface
public interface ITransferHandler {
    /**
     * 执行物流传输
     *
     * @param context 传输上下文（源、目标类型、限制等）
     * @param targets 目标节点列表（已经是经过排序和筛选后的）
     * @return 是否产生了实际的传输动作
     */
    boolean performTransfer(TransferContext context, List<LogisticsNode> targets);
}