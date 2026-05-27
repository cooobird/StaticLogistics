package com.coobird.staticlogistics.transfer.handler.impl;

import com.coobird.staticlogistics.api.ITransferHandler;
import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.config.manager.ConfigFilterManager;
import com.coobird.staticlogistics.storage.LinkManager;
import com.coobird.staticlogistics.storage.config.FaceConfigComposite;
import com.coobird.staticlogistics.transfer.context.TransferContext;
import com.coobird.staticlogistics.transfer.handler.TransferUtils;
import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.slf4j.Logger;

import java.util.List;

/**
 * 流体传输处理器。
 */
public class FluidTransferHandler implements ITransferHandler {
    public static final FluidTransferHandler INSTANCE = new FluidTransferHandler();

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ThreadLocal<Boolean> isInTransfer = ThreadLocal.withInitial(() -> false);

    @Override
    public boolean performTransfer(TransferContext context, List<LogisticsNode> targets) {
        if (isInTransfer.get()) {
            LOGGER.debug("Skipped reentrant fluid transfer for {}", context.sourceNode());
            return false;
        }

        TransferContext newContext = null;
        try {
            isInTransfer.set(true);
            newContext = context.withIncrementedDepth();
            final TransferContext ctx = newContext;
            FaceConfigComposite sourceCfg = ctx.linkManager().getFaceConfig(ctx.sourceNode().toKey());

            return TransferUtils.doTransferNodes(
                ctx.level(),
                ctx.sourceNode().gPos().pos(),
                ctx.sourceNode().face(),
                targets,
                Capabilities.FluidHandler.BLOCK,
                ctx.limit(),
                new TransferUtils.SimpleProtocol<>(
                    (handler, max) -> {
                        FluidStack drained = handler.drain(max, IFluidHandler.FluidAction.SIMULATE);
                        if (!drained.isEmpty() && !isFluidAllowed(sourceCfg, drained, ctx.isPullMode())) {
                            return FluidStack.EMPTY;
                        }
                        return drained;
                    },
                    (handler, stack) -> {
                        if (!isFluidAllowed(sourceCfg, stack, ctx.isPullMode())) return 0;
                        try {
                            return handler.fill(stack, IFluidHandler.FluidAction.EXECUTE);
                        } catch (Exception e) {
                            LOGGER.error("Failed to insert fluid: {}", e.getMessage());
                            return 0;
                        }
                    },
                    (handler, stack, act) -> handler.drain(act, IFluidHandler.FluidAction.EXECUTE),
                    FluidStack::isEmpty,
                    (stack, targetNode) -> {
                        if (ctx.isPullMode()) return true;
                        ServerLevel targetLevel = ctx.level().getServer().getLevel(targetNode.gPos().dimension());
                        if (targetLevel == null) return true;
                        FaceConfigComposite targetCfg = LinkManager.get(targetLevel).getFaceConfig(targetNode.toKey());
                        return targetCfg == null || ConfigFilterManager.isFluidInputAllowed(stack, targetCfg);
                    }
                ),
                ctx.isPullMode(),
                ctx
            );
        } finally {
            if (newContext != null) newContext.recycle();
            isInTransfer.set(false);
        }
    }

    private static boolean isFluidAllowed(FaceConfigComposite config, FluidStack stack, boolean isPullMode) {
        if (config == null) return true;
        return isPullMode
            ? ConfigFilterManager.isFluidInputAllowed(stack, config)
            : ConfigFilterManager.isFluidOutputAllowed(stack, config);
    }
}
