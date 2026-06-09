package com.coobird.staticlogistics.transfer.handler;

import com.coobird.staticlogistics.api.ITransferHandler;
import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.api.LogisticsResource;
import com.coobird.staticlogistics.logic.TransferRegistries;
import com.coobird.staticlogistics.storage.model.FaceConfigComposite;
import com.coobird.staticlogistics.transfer.TransferContext;
import com.coobird.staticlogistics.transfer.strategy.TargetSelector;

import java.util.List;

/**
 * 传输执行器 —— 协调目标选择和传输处理器，完成一次完整的传输尝试。
 */
public class TransferExecutor {
    private final TargetSelector targetSelector;

    public TransferExecutor(TargetSelector targetSelector) {
        this.targetSelector = targetSelector;
    }

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
