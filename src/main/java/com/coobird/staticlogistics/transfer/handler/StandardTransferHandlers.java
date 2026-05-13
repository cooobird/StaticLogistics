package com.coobird.staticlogistics.transfer.handler;

import com.coobird.staticlogistics.api.ITransferHandler;
import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.config.manager.ConfigFilterManager;
import com.coobird.staticlogistics.storage.LinkManager;
import com.coobird.staticlogistics.storage.config.FaceConfigComposite;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

public class StandardTransferHandlers {

    public static final ITransferHandler ITEM = (context, targets) ->
        TransferUtils.doTransferNodes(
            context.level(),
            context.sourceNode().gPos().pos(),
            context.sourceNode().face(),
            targets,
            Capabilities.ItemHandler.BLOCK,
            context.limit(),
            new TransferUtils.SimpleProtocol<>(
                (handler, max) -> {
                    for (int i = 0; i < handler.getSlots(); i++) {
                        ItemStack stack = handler.extractItem(i, max, true);
                        if (!stack.isEmpty() && isItemAllowed(context.sourceNode(), stack, context.isPullMode(), context.level())) {
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
                        if (!simulated.isEmpty() && isItemAllowed(context.sourceNode(), simulated, context.isPullMode(), context.level())) {
                            ItemStack taken = handler.extractItem(i, toExtract, false);
                            toExtract -= taken.getCount();
                        }
                    }
                },
                ItemStack::isEmpty
            ),
            context.isPullMode()
        );

    private static boolean isItemAllowed(LogisticsNode sourceNode, ItemStack stack, boolean isPullMode, ServerLevel level) {
        LinkManager manager = LinkManager.get(level);
        FaceConfigComposite config = manager.getFaceConfig(sourceNode.toKey());
        if (config == null) return true;

        return isPullMode
            ? ConfigFilterManager.isItemInputAllowed(stack, config)
            : ConfigFilterManager.isItemOutputAllowed(stack, config);
    }

    public static final ITransferHandler FLUID = (context, targets) ->
        TransferUtils.doTransferNodes(
            context.level(),
            context.sourceNode().gPos().pos(),
            context.sourceNode().face(),
            targets,
            Capabilities.FluidHandler.BLOCK,
            context.limit(),
            new TransferUtils.SimpleProtocol<>(
                (handler, max) -> {
                    FluidStack stack = handler.drain(max, IFluidHandler.FluidAction.SIMULATE);
                    if (!stack.isEmpty() && !isFluidAllowed(context.sourceNode(), stack, context.isPullMode(), context.level())) {
                        return FluidStack.EMPTY;
                    }
                    return stack;
                },
                (handler, stack) -> handler.fill(stack, IFluidHandler.FluidAction.EXECUTE),
                (handler, stack, act) -> handler.drain(act, IFluidHandler.FluidAction.EXECUTE),
                FluidStack::isEmpty
            ),
            context.isPullMode()
        );

    private static boolean isFluidAllowed(LogisticsNode sourceNode, FluidStack stack, boolean isPullMode, ServerLevel level) {
        LinkManager manager = LinkManager.get(level);
        FaceConfigComposite config = manager.getFaceConfig(sourceNode.toKey());
        if (config == null) return true;

        return isPullMode
            ? ConfigFilterManager.isFluidInputAllowed(stack, config)
            : ConfigFilterManager.isFluidOutputAllowed(stack, config);
    }

    public static final ITransferHandler ENERGY = (context, targets) ->
        TransferUtils.doTransferNodes(
            context.level(),
            context.sourceNode().gPos().pos(),
            context.sourceNode().face(),
            targets,
            Capabilities.EnergyStorage.BLOCK,
            context.limit(),
            new TransferUtils.SimpleProtocol<>(
                (handler, max) -> handler.extractEnergy(max, true),
                (handler, val) -> handler.receiveEnergy(val, false),
                (handler, val, act) -> handler.extractEnergy(act, false),
                val -> val <= 0
            ),
            context.isPullMode()
        );
}