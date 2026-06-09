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
 */
public class ResourceAdapterHandler<C> implements ITransferHandler {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final LogisticsResource<C> adapter;
    private final ResourceAdapterProtocol<C> protocol;
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
