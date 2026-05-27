package com.coobird.staticlogistics.transfer.handler.impl;

import com.coobird.staticlogistics.api.ITransferHandler;
import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.transfer.context.TransferContext;
import com.coobird.staticlogistics.transfer.handler.TransferUtils;
import com.mojang.logging.LogUtils;
import net.neoforged.neoforge.capabilities.Capabilities;
import org.slf4j.Logger;

import java.util.List;

public class EnergyTransferHandler implements ITransferHandler {
    public static final EnergyTransferHandler INSTANCE = new EnergyTransferHandler();

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ThreadLocal<Boolean> isInTransfer = ThreadLocal.withInitial(() -> false);

    @Override
    public boolean performTransfer(TransferContext context, List<LogisticsNode> targets) {
        if (isInTransfer.get()) {
            LOGGER.debug("Skipped reentrant energy transfer for {}", context.sourceNode());
            return false;
        }

        TransferContext newContext = null;
        try {
            isInTransfer.set(true);
            newContext = context.withIncrementedDepth();
            final TransferContext ctx = newContext;

            return TransferUtils.doTransferNodes(
                ctx.level(),
                ctx.sourceNode().gPos().pos(),
                ctx.sourceNode().face(),
                targets,
                Capabilities.EnergyStorage.BLOCK,
                ctx.limit(),
                new TransferUtils.SimpleProtocol<>(
                    (handler, max) -> handler.extractEnergy(max, true),
                    (handler, val) -> {
                        try {
                            return handler.receiveEnergy(val, false);
                        } catch (Exception e) {
                            LOGGER.error("Failed to receive energy: {}", e.getMessage());
                            return 0;
                        }
                    },
                    (handler, val, act) -> handler.extractEnergy(act, false),
                    val -> val <= 0
                ),
                ctx.isPullMode(),
                ctx
            );
        } finally {
            if (newContext != null) newContext.recycle();
            isInTransfer.set(false);
        }
    }
}
