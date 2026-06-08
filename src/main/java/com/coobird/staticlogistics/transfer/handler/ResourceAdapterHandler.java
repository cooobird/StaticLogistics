package com.coobird.staticlogistics.transfer.handler;

import com.coobird.staticlogistics.api.ITransferContext;
import com.coobird.staticlogistics.api.ITransferHandler;
import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.api.LogisticsResource;
import com.coobird.staticlogistics.transfer.TransferContext;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.util.List;

/**
 * 将 {@link LogisticsResource} 适配为 {@link ITransferHandler} 的通用处理器。
 *
 * <p>通过 {@code TransferRegistries.registerAdapter()} 注册时自动创建。
 * 内部委托给 {@link TransferUtils#doTransferNodes}，统一管线：
 * 能力缓存、维度/距离/区块检查、脏链接清理、传输日志。
 *
 * @param <C> 委托给 LogisticsResource 的句柄类型
 */
public class ResourceAdapterHandler<C> implements ITransferHandler {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final LogisticsResource<C> adapter;
    // 缓存 protocol 实例，context 通过方法参数传入
    private final ResourceAdapterProtocol<C> protocol;

    /**
     * ThreadLocal 重入锁。
     *
     * <p>如果一次传输触发了一连串的 {@code performTransfer}（A 传输到 B，
     * B 在 tick 中回调 A），此标志位会阻止递归调用，避免死循环。
     */
    private final ThreadLocal<Boolean> isInTransfer = ThreadLocal.withInitial(() -> false);

    public ResourceAdapterHandler(LogisticsResource<C> adapter) {
        this.adapter = adapter;
        this.protocol = new ResourceAdapterProtocol<>(adapter);
    }

    @Override
    public boolean performTransfer(ITransferContext context, List<LogisticsNode> targets) {
        if (isInTransfer.get()) {
            LOGGER.debug("Skipped reentrant transfer for {}", context.sourceNode());
            return false;
        }

        TransferContext newCtx = null;
        try {
            isInTransfer.set(true);
            newCtx = ((TransferContext) context).withIncrementedDepth();
            if (newCtx.isDepthExceeded()) return false;

            // 更新 protocol 上下文（不重建实例）
            protocol.updateContext(newCtx.sourceConfig(), newCtx.isPullMode(), newCtx);

            return TransferUtils.doTransferNodes(
                newCtx.level(),
                newCtx.sourceNode().gPos().pos(),
                newCtx.sourceNode().face(),
                targets,
                adapter::resolve,
                newCtx.limit(),
                protocol,
                newCtx.isPullMode(),
                newCtx
            );
        } finally {
            if (newCtx != null) newCtx.recycle();
            isInTransfer.set(false);
        }
    }
}
