package com.coobird.staticlogistics.transfer.context;

import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.api.type.TransferType;
import com.coobird.staticlogistics.core.manager.GlobalLogisticsManager;
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
    long currentTick,
    int depth
) {
    public static final int MAX_DEPTH = 3;

    public TransferContext {
        if (depth < 0) depth = 0;
    }

    public TransferContext(ServerLevel level, LogisticsNode sourceNode, FaceConfigComposite sourceConfig,
                           TransferType type, int limit, boolean isPullMode, long currentTick) {
        this(level, sourceNode, sourceConfig, type, limit, isPullMode, currentTick, 0);
    }

    public TransferContext withIncrementedDepth() {
        return new TransferContext(level, sourceNode, sourceConfig, type, limit, isPullMode, currentTick, depth + 1);
    }

    public boolean isDepthExceeded() {
        return depth >= MAX_DEPTH;
    }

    public int[] getSlotCursor() {
        return GlobalLogisticsManager.get(level.getServer()).getCursor(sourceNode.toKey(), type);
    }
}