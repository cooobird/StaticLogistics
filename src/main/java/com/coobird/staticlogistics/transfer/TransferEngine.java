package com.coobird.staticlogistics.transfer;

import com.coobird.staticlogistics.SLConfig;
import com.coobird.staticlogistics.common.util.TransferUtils;
import com.coobird.staticlogistics.core.DistributionStrategy;
import com.coobird.staticlogistics.core.FaceConfig;
import com.coobird.staticlogistics.core.StaticLink;
import mekanism.api.Action;
import mekanism.api.chemical.ChemicalStack;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;

import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

public class TransferEngine {

    public static boolean execute(ServerLevel level, List<StaticLink> filteredLinks, TransferType type, FaceConfig faceConfig, int[] rrCursor, UUID sourceOwner) {
        List<StaticLink> validLinks = filteredLinks.stream()
            .filter(link -> {
                return link.owner().equals(sourceOwner) && link.canTransfer(level, faceConfig);
            })
            .toList();

        if (validLinks.isEmpty()) return false;

        FaceConfig.SideData data = faceConfig.getSettings(type);
        int limit = calculateLimit(type, faceConfig, data);
        int[] activeRR = (data.strategy == DistributionStrategy.ROUND_ROBIN) ? rrCursor : null;

        return switch (type) {
            case ITEM ->
                TransferUtils.doTransfer(level, validLinks, Capabilities.ItemHandler.BLOCK, limit, activeRR, createItemProtocol(data));
            case FLUID -> TransferUtils.doTransfer(level, validLinks, Capabilities.FluidHandler.BLOCK, limit, activeRR,
                new TransferUtils.SimpleProtocol<>(
                    (src, max) -> src.drain(max, IFluidHandler.FluidAction.SIMULATE),
                    (dst, stack) -> dst.fill(stack, IFluidHandler.FluidAction.EXECUTE),
                    (src, stack, act) -> src.drain(stack.copyWithAmount(act), IFluidHandler.FluidAction.EXECUTE),
                    stack -> stack == null || stack.isEmpty()
                ));
            case ENERGY ->
                TransferUtils.doTransfer(level, validLinks, Capabilities.EnergyStorage.BLOCK, limit, activeRR,
                    new TransferUtils.SimpleProtocol<>(
                        (src, max) -> src.extractEnergy(max, true),
                        (dst, val) -> dst.receiveEnergy(val, false),
                        (src, val, act) -> src.extractEnergy(act, false),
                        val -> val <= 0
                    ));
            case MEK_CHEMICALS ->
                TransferUtils.doTransfer(level, validLinks, mekanism.common.capabilities.Capabilities.CHEMICAL.block(), limit, activeRR,
                    new TransferUtils.SimpleProtocol<>(
                        (src, max) -> src.extractChemical((long) max, Action.SIMULATE),
                        (dst, stack) -> (int) (stack.getAmount() - dst.insertChemical(stack, Action.EXECUTE).getAmount()),
                        (src, stack, act) -> src.extractChemical(stack.copyWithAmount((long) act), Action.EXECUTE),
                        ChemicalStack::isEmpty
                    ));
            case ARS_SOURCE ->
                TransferUtils.doTransfer(level, validLinks, com.hollingsworth.arsnouveau.setup.registry.CapabilityRegistry.SOURCE_CAPABILITY, limit, activeRR,
                    new TransferUtils.SimpleProtocol<>(
                        (src, max) -> src.extractSource(max, true),
                        (dst, val) -> dst.receiveSource(val, false),
                        (src, val, act) -> src.extractSource(act, false),
                        val -> val <= 0
                    ));
        };
    }

    private static TransferUtils.TransferProtocol<IItemHandler, ItemStack> createItemProtocol(FaceConfig.SideData data) {
        return new TransferUtils.TransferProtocol<>() {
            private int lastSlot = -1;
            private final Predicate<ItemStack> filter = s -> {
                if (data.filterItems.isEmpty()) return false;
                return data.isBlacklist == data.filterItems.contains(s.getItem());
            };

            @Override
            public ItemStack simulateExtract(IItemHandler src, int max) {
                for (int i = 0; i < src.getSlots(); i++) {
                    ItemStack s = src.getStackInSlot(i);
                    if (s.isEmpty() || filter.test(s)) continue;
                    lastSlot = i;
                    return src.extractItem(i, max, true);
                }
                lastSlot = -1;
                return ItemStack.EMPTY;
            }

            @Override
            public int executeInsert(IItemHandler dst, ItemStack s) {
                ItemStack rem = ItemHandlerHelper.insertItemStacked(dst, s.copy(), false);
                return s.getCount() - rem.getCount();
            }

            @Override
            public void commitExtract(IItemHandler src, ItemStack s, int a) {
                if (lastSlot != -1) src.extractItem(lastSlot, a, false);
            }

            @Override
            public boolean isEmpty(ItemStack s) {
                return s.isEmpty();
            }
        };
    }

    private static int calculateLimit(TransferType type, FaceConfig fc, FaceConfig.SideData sd) {
        if (sd.customBulkSize > 0) return sd.customBulkSize;

        long base = switch (type) {
            case ITEM -> SLConfig.getItemStack();
            case ENERGY -> SLConfig.getEnergyStack();
            case MEK_CHEMICALS -> SLConfig.getMekChemicalStack();
            case ARS_SOURCE -> SLConfig.getArsSourceStack();
            default -> SLConfig.getFluidStack();
        };

        return (int) Math.min(base * fc.getStackMultiplier(), Integer.MAX_VALUE);
    }
}