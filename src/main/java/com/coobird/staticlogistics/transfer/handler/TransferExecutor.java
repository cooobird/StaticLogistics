package com.coobird.staticlogistics.transfer.handler;

import com.coobird.staticlogistics.api.ITransferHandler;
import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.api.type.TransferType;
import com.coobird.staticlogistics.core.registration.TransferRegistries;
import com.coobird.staticlogistics.storage.config.FaceConfigComposite;
import com.coobird.staticlogistics.storage.config.LinkConfig;
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
        if (context.isDepthExceeded()) {
            return false;
        }

        FaceConfigComposite config = context.sourceConfig();
        LogisticsNode sourceNode = context.sourceNode();
        TransferType type = context.type();
        int limit = context.limit();

        // 获取该传输类型的侧配置
        LinkConfig.SideData settings = config.linkConfig.getSettings(type);
        if (!settings.outputEnabled) return false;

        // 使用 TargetSelector 获取排序后的目标节点列表
        List<LogisticsNode> targets = targetSelector.selectTargets(context, settings);
        if (targets.isEmpty()) return false;

        // 获取对应的传输处理器
        ITransferHandler handler = TransferRegistries.getHandler(type);
        if (handler == null) return false;

        // 调用处理器，传入完整的上下文和目标列表
        return handler.performTransfer(context, targets);
    }
}