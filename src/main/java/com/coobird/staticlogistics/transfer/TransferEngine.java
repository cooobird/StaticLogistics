package com.coobird.staticlogistics.transfer;

import com.coobird.staticlogistics.core.DistributionStrategy;
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

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class TransferEngine {

    /**
     * 执行分发作业
     * @param type 由 Ticker 传入的当前 Tick 处理类型
     */
    public static boolean execute(ServerLevel level, List<StaticLink> allLinks, TransferType type, TransferSettings settings) {
        if (allLinks == null || allLinks.isEmpty()) return false;

        List<StaticLink> filteredLinks = allLinks.stream()
            .filter(link -> link.hasType(type))
            .collect(Collectors.toList());

        if (filteredLinks.isEmpty()) return false;

        List<StaticLink> sortedLinks = settings.strategy().sort(filteredLinks).stream()
            .sorted(Comparator.comparingInt(StaticLink::priority).reversed())
            .toList();

        return switch (type) {
            case ITEM -> transferItems(level, sortedLinks, settings);
            case FLUID -> transferFluids(level, sortedLinks, settings);
        };
    }

    private static boolean transferItems(ServerLevel level, List<StaticLink> links, TransferSettings settings) {
        StaticLink first = links.getFirst();
        IItemHandler source = level.getCapability(Capabilities.ItemHandler.BLOCK, first.sourcePos(), first.sourceFace());
        if (source == null) return false;

        boolean movedAny = false;
        int remainingToMove = settings.bulkSize();

        for (int i = 0; i < source.getSlots() && remainingToMove > 0; i++) {
            ItemStack stackInSlot = source.getStackInSlot(i);
            if (stackInSlot.isEmpty()) continue;

            if (!settings.filter().test(stackInSlot)) continue;

            int wantToExtract = Math.min(stackInSlot.getCount(), remainingToMove);
            ItemStack extractedSim = source.extractItem(i, wantToExtract, true);
            if (extractedSim.isEmpty()) continue;

            for (StaticLink link : links) {
                IItemHandler destination = level.getCapability(Capabilities.ItemHandler.BLOCK, link.destPos(), link.destFace());
                if (destination == null) continue;

                ItemStack toInsert = extractedSim.copy();
                if (settings.stock().enabled()) {
                    int existingCount = countItemsInHandler(destination, stackInSlot);
                    if (!settings.stock().canInsert(existingCount)) continue;

                    int roomLeft = settings.stock().maxAmount() - existingCount;
                    if (toInsert.getCount() > roomLeft) {
                        toInsert.setCount(roomLeft);
                    }
                }

                ItemStack remainder = ItemHandlerHelper.insertItemStacked(destination, toInsert, false);
                int actualMoved = toInsert.getCount() - remainder.getCount();

                if (actualMoved > 0) {
                    source.extractItem(i, actualMoved, false);
                    remainingToMove -= actualMoved;
                    movedAny = true;

                    if (settings.strategy() == DistributionStrategy.ROUND_ROBIN) return true;
                    if (remainingToMove <= 0) break;

                    wantToExtract = Math.min(source.getStackInSlot(i).getCount(), remainingToMove);
                    extractedSim = source.extractItem(i, wantToExtract, true);
                    if (extractedSim.isEmpty()) break;
                }
            }
        }
        return movedAny;
    }

    private static boolean transferEnergy(ServerLevel level, List<StaticLink> links, TransferSettings settings) {
        StaticLink first = links.getFirst();
        IEnergyStorage source = level.getCapability(Capabilities.EnergyStorage.BLOCK, first.sourcePos(), first.sourceFace());
        if (source == null || !source.canExtract()) return false;

        boolean movedAny = false;
        int energyToMove = settings.bulkSize();

        for (StaticLink link : links) {
            IEnergyStorage dest = level.getCapability(Capabilities.EnergyStorage.BLOCK, link.destPos(), link.destFace());
            if (dest == null || !dest.canReceive()) continue;

            // 能量传输通常不需要过滤器，但可以受 StockControl 限制（视为最大储能百分比或固定值）
            int energyInDest = dest.getEnergyStored();
            int maxToInsert = energyToMove;

            if (settings.stock().enabled()) {
                if (energyInDest >= settings.stock().maxAmount()) continue;
                maxToInsert = Math.min(energyToMove, settings.stock().maxAmount() - energyInDest);
            }

            int extracted = source.extractEnergy(maxToInsert, true);
            int accepted = dest.receiveEnergy(extracted, false);

            if (accepted > 0) {
                source.extractEnergy(accepted, false);
                energyToMove -= accepted;
                movedAny = true;
                if (energyToMove <= 0) break;
            }
        }
        return movedAny;
    }

    private static boolean transferFluids(ServerLevel level, List<StaticLink> links, TransferSettings settings) {
        StaticLink first = links.getFirst();
        IFluidHandler source = level.getCapability(Capabilities.FluidHandler.BLOCK, first.sourcePos(), first.sourceFace());
        if (source == null) return false;

        boolean movedAny = false;
        int remainingToMove = settings.bulkSize();

        FluidStack stackInSource = source.drain(remainingToMove, IFluidHandler.FluidAction.SIMULATE);
        if (stackInSource.isEmpty()) return false;

        for (StaticLink link : links) {
            if (remainingToMove <= 0) break;

            IFluidHandler destination = level.getCapability(Capabilities.FluidHandler.BLOCK, link.destPos(), link.destFace());
            if (destination == null) continue;

            FluidStack toInsert = stackInSource.copy();
            toInsert.setAmount(remainingToMove);

            if (settings.stock().enabled()) {
                int existingAmount = countFluidInHandler(destination, toInsert);
                if (!settings.stock().canInsert(existingAmount)) continue;

                int roomLeft = settings.stock().maxAmount() - existingAmount;
                if (toInsert.getAmount() > roomLeft) {
                    toInsert.setAmount(roomLeft);
                }
            }

            if (toInsert.getAmount() <= 0) continue;

            int accepted = destination.fill(toInsert, IFluidHandler.FluidAction.EXECUTE);

            if (accepted > 0) {
                source.drain(accepted, IFluidHandler.FluidAction.EXECUTE);
                remainingToMove -= accepted;
                movedAny = true;

                if (settings.strategy() == DistributionStrategy.ROUND_ROBIN) return true;

                stackInSource = source.drain(remainingToMove, IFluidHandler.FluidAction.SIMULATE);
                if (stackInSource.isEmpty()) break;
            }
        }

        return movedAny;
    }

    private static int countFluidInHandler(IFluidHandler handler, FluidStack target) {
        int total = 0;
        for (int i = 0; i < handler.getTanks(); i++) {
            FluidStack inTank = handler.getFluidInTank(i);
            if (FluidStack.isSameFluidSameComponents(inTank, target)) {
                total += inTank.getAmount();
            }
        }
        return total;
    }

    /**
     * 辅助方法：统计容器中特定物品的总数
     */
    private static int countItemsInHandler(IItemHandler handler, ItemStack target) {
        int count = 0;
        for (int i = 0; i < handler.getSlots(); i++) {
            ItemStack stack = handler.getStackInSlot(i);
            if (ItemStack.isSameItemSameComponents(stack, target)) {
                count += stack.getCount();
            }
        }
        return count;
    }
}