package com.coobird.staticlogistics.transfer.handler;

import com.coobird.staticlogistics.api.ITransferHandler;
import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.api.type.TransferType;
import com.coobird.staticlogistics.core.registration.TransferRegistries;
import com.coobird.staticlogistics.storage.config.FaceConfigComposite;
import com.coobird.staticlogistics.transfer.context.TransferContext;
import com.coobird.staticlogistics.transfer.strategy.TargetSelector;

import java.util.List;

public class TransferExecutor {
    private final TargetSelector targetSelector;

    public TransferExecutor(TargetSelector targetSelector) {
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
        TransferType type = context.type();
        if (!config.isGlobalOutputEnabled()) return false;
        List<LogisticsNode> targets = targetSelector.selectTargets(context);
        if (targets.isEmpty()) return false;
        ITransferHandler handler = TransferRegistries.getHandler(type);
        if (handler == null) return false;
        return handler.performTransfer(context, targets);
    }
}