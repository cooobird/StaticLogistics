package com.coobird.staticlogistics.transfer.context;

import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.api.type.TransferType;
import com.coobird.staticlogistics.storage.config.FaceConfigComposite;
import net.minecraft.server.level.ServerLevel;

/**
 * 传输执行上下文，封装一次传输中所有不变的参数。
 * 用于传递给 ITransferHandler 和 TargetSelector。
 */
public record TransferContext(
    ServerLevel level,
    LogisticsNode sourceNode,
    FaceConfigComposite sourceConfig,
    TransferType type,
    int limit,
    boolean isPullMode,
    long currentTick
) {
}