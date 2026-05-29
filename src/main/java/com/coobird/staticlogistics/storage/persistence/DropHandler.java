package com.coobird.staticlogistics.storage.persistence;

import com.coobird.staticlogistics.storage.LinkManager;
import com.coobird.staticlogistics.storage.config.ContainerConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.List;

public class DropHandler {
    private final ServerLevel level;

    public DropHandler(ServerLevel level) {
        this.level = level;
    }

    public void handleBulkDrops(List<BlockPos> positions) {
        LinkManager mgr = LinkManager.get(level);
        for (BlockPos pos : positions) {
            ContainerConfig containerConfig = mgr.getContainerConfig(pos);
            if (containerConfig != null) {
                dropInventory(pos, containerConfig.getUpgrades());
            }
        }
    }

    public void dropFilterUpgrades(BlockPos pos, IItemHandler upgrades) {
        dropInventory(pos, upgrades);
    }

    private void dropInventory(BlockPos pos, IItemHandler inventory) {
        for (int i = 0; i < inventory.getSlots(); i++) {
            ItemStack stack = inventory.getStackInSlot(i);
            if (!stack.isEmpty()) {
                Block.popResource(level, pos, stack.copy());
            }
        }
    }
}