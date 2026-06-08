package com.coobird.staticlogistics.api;

import net.minecraft.server.level.ServerLevel;

/**
 * 传输上下文只读接口，供 {@link ITransferHandler} 读取传输参数。
 */
public interface ITransferContext {

    ServerLevel level();

    LogisticsNode sourceNode();

    /**
     * 本次传输的资源类型。
     */
    LogisticsResource<?> type();

    long limit();

    boolean isPullMode();

    long currentTick();

    int depth();

    boolean isDepthExceeded();
}
