package com.coobird.staticlogistics.transfer.handler;

import com.coobird.staticlogistics.api.ITransferHandler;
import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.api.type.DistributionStrategy;
import com.coobird.staticlogistics.config.manager.ConfigFilterManager;
import com.coobird.staticlogistics.storage.LinkManager;
import com.coobird.staticlogistics.storage.config.FaceConfigComposite;
import com.coobird.staticlogistics.transfer.context.TransferContext;
import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import org.slf4j.Logger;

public class StandardTransferHandlers {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final ThreadLocal<Boolean> isInItemTransfer = ThreadLocal.withInitial(() -> false);
    private static final ThreadLocal<Boolean> isInFluidTransfer = ThreadLocal.withInitial(() -> false);
    private static final ThreadLocal<Boolean> isInEnergyTransfer = ThreadLocal.withInitial(() -> false);

    public static final ITransferHandler ITEM = (context, targets) -> {
        if (isInItemTransfer.get()) {
            LOGGER.debug("Skipped reentrant item transfer for {}", context.sourceNode());
            return false;
        }

        TransferContext newContext = null;
        try {
            isInItemTransfer.set(true);
            newContext = context.withIncrementedDepth();
            final TransferContext ctx = newContext;
            DistributionStrategy strategy = context.sourceConfig().linkConfig.getStrategy();

            TransferUtils.TransferProtocol<IItemHandler, ItemStack> protocol;
            if (strategy == DistributionStrategy.SLOT_ROUND_ROBIN) {
                protocol = new SlotRoundRobinProtocol(ctx);
            } else {
                protocol = new TransferUtils.SimpleProtocol<>(
                    (handler, max) -> {
                        for (int i = 0; i < handler.getSlots(); i++) {
                            ItemStack stack = handler.extractItem(i, max, true);
                            if (!stack.isEmpty() && isItemAllowed(ctx.sourceNode(), stack, ctx.isPullMode(), ctx.level())) {
                                return stack;
                            }
                        }
                        return ItemStack.EMPTY;
                    },
                    (handler, stack) -> {
                        ItemStack remain = stack;
                        for (int i = 0; i < handler.getSlots(); i++) {
                            remain = handler.insertItem(i, remain, false);
                            if (remain.isEmpty()) break;
                        }
                        return stack.getCount() - remain.getCount();
                    },
                    (handler, stack, act) -> {
                        int toExtract = act;
                        for (int i = 0; i < handler.getSlots() && toExtract > 0; i++) {
                            ItemStack simulated = handler.extractItem(i, toExtract, true);
                            if (!simulated.isEmpty() && isItemAllowed(ctx.sourceNode(), simulated, ctx.isPullMode(), ctx.level())) {
                                handler.extractItem(i, toExtract, false);
                                toExtract -= simulated.getCount();
                            }
                        }
                    },
                    ItemStack::isEmpty
                );
            }

            return TransferUtils.doTransferNodes(
                ctx.level(),
                ctx.sourceNode().gPos().pos(),
                ctx.sourceNode().face(),
                targets,
                Capabilities.ItemHandler.BLOCK,
                ctx.limit(),
                protocol,
                ctx.isPullMode(),
                ctx,
                ctx.linkManager().getCapabilityCache()
            );
        } finally {
            if (newContext != null) newContext.recycle();
            isInItemTransfer.set(false);
        }
    };

    private static class SlotRoundRobinProtocol implements TransferUtils.TransferProtocol<IItemHandler, ItemStack> {
        private final TransferContext context;
        private final int[] cursor;

        SlotRoundRobinProtocol(TransferContext context) {
            this.context = context;
            this.cursor = context.getSlotCursor();
        }

        @Override
        public ExtractionResult<ItemStack> simulateExtract(IItemHandler handler, int max) {
            int slots = handler.getSlots();
            if (slots == 0) return ExtractionResult.of(ItemStack.EMPTY);
            int start = cursor[0];
            for (int i = 0; i < slots; i++) {
                int slot = (start + i) % slots;
                ItemStack stack = handler.extractItem(slot, max, true);
                if (!stack.isEmpty() && isItemAllowed(context.sourceNode(), stack, context.isPullMode(), context.level())) {
                    return ExtractionResult.of(stack, slot);
                }
            }
            return ExtractionResult.of(ItemStack.EMPTY);
        }

        @Override
        public int executeInsert(IItemHandler dest, ItemStack stack) {
            ItemStack remain = stack;
            for (int i = 0; i < dest.getSlots(); i++) {
                remain = dest.insertItem(i, remain, false);
                if (remain.isEmpty()) break;
            }
            return stack.getCount() - remain.getCount();
        }

        @Override
        public void commitExtract(IItemHandler source, ExtractionResult<ItemStack> result, int actual) {
            Integer slot = result.getContext();
            if (slot == null) {
                LOGGER.warn("SlotRoundRobinProtocol.commitExtract called without slot context");
                return;
            }
            if (actual > 0) {
                source.extractItem(slot, actual, false);
            }
            int slots = source.getSlots();
            if (slots > 0) {
                cursor[0] = (slot + 1) % slots;
            }
        }

        @Override
        public boolean isEmpty(ExtractionResult<ItemStack> result) {
            return result.value().isEmpty();
        }
    }

    private static boolean isItemAllowed(LogisticsNode sourceNode, ItemStack stack, boolean isPullMode, ServerLevel level) {
        LinkManager manager = LinkManager.get(level);
        FaceConfigComposite config = manager.getFaceConfig(sourceNode.toKey());
        if (config == null) return true;
        return isPullMode
            ? ConfigFilterManager.isItemInputAllowed(stack, config)
            : ConfigFilterManager.isItemOutputAllowed(stack, config);
    }

    public static final ITransferHandler FLUID = (context, targets) -> {
        if (isInFluidTransfer.get()) {
            LOGGER.debug("Skipped reentrant fluid transfer for {}", context.sourceNode());
            return false;
        }

        TransferContext newContext = null;
        try {
            isInFluidTransfer.set(true);
            newContext = context.withIncrementedDepth();
            final TransferContext ctx = newContext;

            return TransferUtils.doTransferNodes(
                ctx.level(),
                ctx.sourceNode().gPos().pos(),
                ctx.sourceNode().face(),
                targets,
                Capabilities.FluidHandler.BLOCK,
                ctx.limit(),
                new TransferUtils.SimpleProtocol<>(
                    (handler, max) -> handler.drain(max, IFluidHandler.FluidAction.SIMULATE),
                    (handler, stack) -> {
                        try {
                            return handler.fill(stack, IFluidHandler.FluidAction.EXECUTE);
                        } catch (Exception e) {
                            LOGGER.error("Failed to insert fluid: {}", e.getMessage());
                            return 0;
                        }
                    },
                    (handler, stack, act) -> handler.drain(act, IFluidHandler.FluidAction.EXECUTE),
                    FluidStack::isEmpty
                ),
                ctx.isPullMode(),
                ctx,
                ctx.linkManager().getCapabilityCache()
            );
        } finally {
            if (newContext != null) newContext.recycle();
            isInFluidTransfer.set(false);
        }
    };

    public static final ITransferHandler ENERGY = (context, targets) -> {
        if (isInEnergyTransfer.get()) {
            LOGGER.debug("Skipped reentrant energy transfer for {}", context.sourceNode());
            return false;
        }

        TransferContext newContext = null;
        try {
            isInEnergyTransfer.set(true);
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
                ctx,
                ctx.linkManager().getCapabilityCache()
            );
        } finally {
            if (newContext != null) newContext.recycle();
            isInEnergyTransfer.set(false);
        }
    };
}