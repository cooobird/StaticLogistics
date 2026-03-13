package com.coobird.staticlogistics.transfer;

import com.coobird.staticlogistics.core.DistributionStrategy;
import com.coobird.staticlogistics.core.FaceConfig;
import com.coobird.staticlogistics.core.StaticLink;
import com.coobird.staticlogistics.core.TransferType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class TransferEngine {

    public static void execute(ServerLevel level, List<StaticLink> allLinks, TransferType type, FaceConfig faceConfig, int[] rrCursor) {
        FaceConfig.SideData sourceData = faceConfig.getSettings(type);

        List<StaticLink> filtered = new ArrayList<>();
        for (StaticLink l : allLinks) if (l.hasType(type)) filtered.add(l);
        if (filtered.isEmpty()) return;

        filtered.sort(Comparator.comparingInt(StaticLink::priority).reversed());
        DistributionStrategy strategy = sourceData.strategy != null ? sourceData.strategy : DistributionStrategy.SEQUENTIAL;
        List<StaticLink> finalLinks = strategy.sort(filtered);

        int finalLimit = calculateTransferLimit(type, faceConfig, sourceData);

        int[] activeRR = (strategy == DistributionStrategy.ROUND_ROBIN) ? rrCursor : null;

        switch (type) {
            case ITEM -> transferItems(level, finalLinks, sourceData, finalLimit, activeRR);
            case FLUID -> transferFluids(level, finalLinks, finalLimit, activeRR);
            case ENERGY -> transferEnergy(level, finalLinks, finalLimit, activeRR);
        }
    }

    private static int calculateTransferLimit(TransferType type, FaceConfig faceConfig, FaceConfig.SideData sourceData) {
        if (sourceData.customBulkSize > 0) {
            return sourceData.customBulkSize;
        }

        int baseBulk = switch (type) {
            case ITEM -> 64;
            case FLUID -> 1000;
            case ENERGY -> 1000;
        };

        int tierMultiplier = faceConfig.getStackMultiplier();

        if (tierMultiplier == Integer.MAX_VALUE || tierMultiplier < 0) {
            return Integer.MAX_VALUE;
        }

        long result = (long) baseBulk * tierMultiplier;
        return (int) Math.min(Integer.MAX_VALUE, result);
    }

    private static void transferItems(ServerLevel level, List<StaticLink> links, FaceConfig.SideData data, int limit, int[] rrCursor) {
        final int[] slotIdx = {0};
        TransferUtils.doTransfer(level, links, Capabilities.ItemHandler.BLOCK, limit, rrCursor, new TransferUtils.TransferProtocol<IItemHandler, ItemStack>() {
            @Override
            public ItemStack simulateExtract(IItemHandler src, int max) {
                for (int i = slotIdx[0]; i < src.getSlots(); i++) {
                    ItemStack s = src.getStackInSlot(i);
                    if (s.isEmpty()) continue;
                    if (!data.filterItems.isEmpty()) {
                        boolean match = data.filterItems.contains(s.getItem());
                        if (data.isBlacklist == match) continue;
                    }
                    slotIdx[0] = i;
                    return src.extractItem(i, max, true);
                }
                return ItemStack.EMPTY;
            }

            @Override
            public int executeInsert(IItemHandler dst, ItemStack s) {
                ItemStack rem = ItemHandlerHelper.insertItemStacked(dst, s.copy(), false);
                return s.getCount() - rem.getCount();
            }

            @Override
            public void commitExtract(IItemHandler src, ItemStack s, int a) {
                src.extractItem(slotIdx[0], a, false);
            }

            @Override
            public boolean isEmpty(ItemStack s) {
                return s.isEmpty();
            }
        });
    }

    private static void transferFluids(ServerLevel level, List<StaticLink> links, int limit, int[] rrCursor) {
        TransferUtils.doTransfer(level, links, Capabilities.FluidHandler.BLOCK, limit, rrCursor, new TransferUtils.TransferProtocol<IFluidHandler, FluidStack>() {
            @Override
            public FluidStack simulateExtract(IFluidHandler src, int max) {
                return src.drain(max, IFluidHandler.FluidAction.SIMULATE);
            }

            @Override
            public int executeInsert(IFluidHandler dst, FluidStack s) {
                return dst.fill(s, IFluidHandler.FluidAction.EXECUTE);
            }

            @Override
            public void commitExtract(IFluidHandler src, FluidStack s, int a) {
                src.drain(s.copyWithAmount(a), IFluidHandler.FluidAction.EXECUTE);
            }

            @Override
            public boolean isEmpty(FluidStack s) {
                return s.isEmpty();
            }
        });
    }

    private static void transferEnergy(ServerLevel level, List<StaticLink> links, int limit, int[] rrCursor) {
        TransferUtils.doTransfer(level, links, Capabilities.EnergyStorage.BLOCK, limit, rrCursor, new TransferUtils.TransferProtocol<IEnergyStorage, Integer>() {
            @Override
            public Integer simulateExtract(IEnergyStorage src, int max) {
                return src.extractEnergy(max, true);
            }

            @Override
            public int executeInsert(IEnergyStorage dst, Integer a) {
                return dst.receiveEnergy(a, false);
            }

            @Override
            public void commitExtract(IEnergyStorage src, Integer a, int act) {
                src.extractEnergy(act, false);
            }

            @Override
            public boolean isEmpty(Integer a) {
                return a <= 0;
            }
        });
    }
}