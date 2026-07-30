package com.coobird.staticlogistics.transfer;

import com.coobird.staticlogistics.api.ITransferHandler;
import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.logistics.node.FaceConfigComposite;
import com.coobird.staticlogistics.transfer.strategy.StrategyBasedTargetSelector;

import java.util.List;

/**
 * 传输执行器 —— 协调目标选择和传输处理器，完成一次完整的传输尝试。
 *
 * <p>调用方（{@link com.coobird.staticlogistics.transfer.LogisticsTicker}）
 * 已完成冷却检查后才调用此方法。
 *
 * <p>执行流程：
 * <ol>
 *   <li>深度检查 → 源配置输出开关检查</li>
 *   <li>目标选择（{@link StrategyBasedTargetSelector}）</li>
 *   <li>获取对应的 {@link ITransferHandler}（通过 {@link TransferRegistries}）</li>
 *   <li>执行传输</li>
 * </ol>
 */
public class TransferExecutor {
    private final StrategyBasedTargetSelector targetSelector;

    public TransferExecutor(StrategyBasedTargetSelector targetSelector) {
        this.targetSelector = targetSelector;
    }

    /**
     * 执行一次传输（不包含冷却检查，由调用方负责判断）
     *
     * @param context 传输上下文（包含源节点、配置、类型、限制等）
     * @return 是否发生了实际传输
     */
    public boolean executeTransfer(TransferContext context) {
        if (context.isDepthExceeded()) return false;
        FaceConfigComposite config = context.sourceConfig();
        LogisticsResource<?> type = context.type();
        if (!config.isGlobalOutputEnabled()) return false;
        List<LogisticsNode> targets = targetSelector.selectTargets(context);
        if (targets.isEmpty()) return false;
        ITransferHandler handler = TransferRegistries.getHandler(type);
        if (handler == null) return false;
        return handler.performTransfer(context, targets);
    }
}
