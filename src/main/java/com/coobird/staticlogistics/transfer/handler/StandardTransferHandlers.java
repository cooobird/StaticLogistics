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

        try {
            isInItemTransfer.set(true);
            TransferContext newContext = context.withIncrementedDepth();
            DistributionStrategy strategy = context.sourceConfig().linkConfig.getSettings(context.type()).strategy;

            return TransferUtils.doTransferNodes(
                newContext.level(),
                newContext.sourceNode().gPos().pos(),
                newContext.sourceNode().face(),
                targets,
                Capabilities.ItemHandler.BLOCK,
                newContext.limit(),
                new TransferUtils.SimpleProtocol<>(
                    (handler, max) -> {
                        if (strategy == DistributionStrategy.SLOT_ROUND_ROBIN) {
                            int[] cursor = newContext.getSlotCursor();
                            int start = cursor[0];
                            int slots = handler.getSlots();
                            for (int i = 0; i < slots; i++) {
                                int slot = (start + i) % slots;
                                ItemStack stack = handler.extractItem(slot, max, true);
                                if (!stack.isEmpty() && isItemAllowed(newContext.sourceNode(), stack, newContext.isPullMode(), newContext.level())) {
                                    cursor[0] = (slot + 1) % slots;
                                    return stack;
                                }
                            }
                            return ItemStack.EMPTY;
                        } else {
                            for (int i = 0; i < handler.getSlots(); i++) {
                                ItemStack stack = handler.extractItem(i, max, true);
                                if (!stack.isEmpty() && isItemAllowed(newContext.sourceNode(), stack, newContext.isPullMode(), newContext.level())) {
                                    return stack;
                                }
                            }
                            return ItemStack.EMPTY;
                        }
                    },
                    (handler, stack) -> {
                        ItemStack remain = stack;
                        try {
                            for (int i = 0; i < handler.getSlots(); i++) {
                                remain = handler.insertItem(i, remain, false);
                                if (remain.isEmpty()) break;
                            }
                        } catch (Exception e) {
                            LOGGER.error("Failed to insert item into {}: {}", handler.getClass().getSimpleName(), e.getMessage());
                            return 0;
                        }
                        return stack.getCount() - remain.getCount();
                    },
                    (handler, stack, act) -> {
                        int toExtract = act;
                        for (int i = 0; i < handler.getSlots() && toExtract > 0; i++) {
                            ItemStack simulated = handler.extractItem(i, toExtract, true);
                            if (!simulated.isEmpty() && isItemAllowed(newContext.sourceNode(), simulated, newContext.isPullMode(), newContext.level())) {
                                ItemStack taken = handler.extractItem(i, toExtract, false);
                                toExtract -= taken.getCount();
                            }
                        }
                    },
                    ItemStack::isEmpty
                ),
                newContext.isPullMode(),
                newContext
            );
        } finally {
            isInItemTransfer.set(false);
        }
    };

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

        try {
            isInFluidTransfer.set(true);
            TransferContext newContext = context.withIncrementedDepth();
            return TransferUtils.doTransferNodes(
                newContext.level(),
                newContext.sourceNode().gPos().pos(),
                newContext.sourceNode().face(),
                targets,
                Capabilities.FluidHandler.BLOCK,
                newContext.limit(),
                new TransferUtils.SimpleProtocol<>(
                    (handler, max) -> {
                        FluidStack stack = handler.drain(max, IFluidHandler.FluidAction.SIMULATE);
                        if (!stack.isEmpty() && !isFluidAllowed(newContext.sourceNode(), stack, newContext.isPullMode(), newContext.level())) {
                            return FluidStack.EMPTY;
                        }
                        return stack;
                    },
                    (handler, stack) -> {
                        try {
                            return handler.fill(stack, IFluidHandler.FluidAction.EXECUTE);
                        } catch (Exception e) {
                            LOGGER.error("Failed to insert fluid into {}: {}", handler.getClass().getSimpleName(), e.getMessage());
                            return 0;
                        }
                    },
                    (handler, stack, act) -> handler.drain(act, IFluidHandler.FluidAction.EXECUTE),
                    FluidStack::isEmpty
                ),
                newContext.isPullMode(),
                newContext
            );
        } finally {
            isInFluidTransfer.set(false);
        }
    };

    private static boolean isFluidAllowed(LogisticsNode sourceNode, FluidStack stack, boolean isPullMode, ServerLevel level) {
        LinkManager manager = LinkManager.get(level);
        FaceConfigComposite config = manager.getFaceConfig(sourceNode.toKey());
        if (config == null) return true;

        return isPullMode
            ? ConfigFilterManager.isFluidInputAllowed(stack, config)
            : ConfigFilterManager.isFluidOutputAllowed(stack, config);
    }

    public static final ITransferHandler ENERGY = (context, targets) -> {
        if (isInEnergyTransfer.get()) {
            LOGGER.debug("Skipped reentrant energy transfer for {}", context.sourceNode());
            return false;
        }

        try {
            isInEnergyTransfer.set(true);
            TransferContext newContext = context.withIncrementedDepth();
            return TransferUtils.doTransferNodes(
                newContext.level(),
                newContext.sourceNode().gPos().pos(),
                newContext.sourceNode().face(),
                targets,
                Capabilities.EnergyStorage.BLOCK,
                newContext.limit(),
                new TransferUtils.SimpleProtocol<>(
                    (handler, max) -> handler.extractEnergy(max, true),
                    (handler, val) -> {
                        try {
                            return handler.receiveEnergy(val, false);
                        } catch (Exception e) {
                            LOGGER.error("Failed to receive energy into {}: {}", handler.getClass().getSimpleName(), e.getMessage());
                            return 0;
                        }
                    },
                    (handler, val, act) -> handler.extractEnergy(act, false),
                    val -> val <= 0
                ),
                newContext.isPullMode(),
                newContext
            );
        } finally {
            isInEnergyTransfer.set(false);
        }
    };
}