package com.coobird.staticlogistics.api;

import com.coobird.staticlogistics.api.type.TransferType;
import net.minecraft.server.level.ServerLevel;

/**
 * 传输上下文只读接口 —— 供 ITransferHandler 实现读取传输参数。
 * <p>
 * 不暴露对象池、LinkManager 等内部实现。
 * 需要更多内部访问的实现可向下转型为具体 TransferContext。
 */
public interface ITransferContext {
    ServerLevel level();

    LogisticsNode sourceNode();

    TransferType type();

    int limit();

    boolean isPullMode();

    long currentTick();

    int depth();

    boolean isDepthExceeded();
}
