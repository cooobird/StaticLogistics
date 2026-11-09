package com.coobird.staticlogistics.transfer;

import com.coobird.staticlogistics.api.ITransferContext;
import com.coobird.staticlogistics.api.ITransferHandler;
import com.coobird.staticlogistics.api.LogisticsNode;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.util.List;

/// 将资源适配器接入统一传输管线。
public class ResourceAdapterHandler<C> implements ITransferHandler {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final LogisticsResource<C> adapter;

    /// 阻止同一资源处理器在同步能力调用中重入。
    private final ThreadLocal<Boolean> isInTransfer = ThreadLocal.withInitial(() -> false);

    public ResourceAdapterHandler(LogisticsResource<C> adapter) {
        this.adapter = adapter;
    }

    @Override
    public boolean performTransfer(ITransferContext context, List<LogisticsNode> targets) {
        if (context != null && context.level() != null) TransferContext.requireServerThread(context.level());
        if (isInTransfer.get()) {
            return false;
        }

        try {
            isInTransfer.set(true);
            TransferContext newCtx = TransferContext.copyOf(context, adapter);
            if (newCtx == null) {
                LOGGER.error("Invalid transfer context for resource adapter {}", adapter.typeId());
                return false;
            }
            /// 协议对象仅属于本次调用，避免服务器实例或未来异步调度互相覆盖上下文。
            ResourceAdapterProtocol<C> protocol = new ResourceAdapterProtocol<>(
                adapter, newCtx.sourceConfig(), newCtx.isPullMode(), newCtx);

            var capability = adapter.blockCapability();
            if (capability != null) {
                return TransferPipeline.execute(
                    newCtx.level(), newCtx.sourceNode().gPos().pos(), newCtx.sourceNode().face(),
                    targets, capability, newCtx.limit(), protocol, newCtx.isPullMode(), newCtx);
            }
            return TransferPipeline.execute(
                newCtx.level(), newCtx.sourceNode().gPos().pos(), newCtx.sourceNode().face(),
                targets, adapter::resolve, newCtx.limit(), protocol, newCtx.isPullMode(), newCtx);
        } finally {
            isInTransfer.set(false);
        }
    }
}
