package com.coobird.staticlogistics.storage;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * 掉落处理器 —— 方块实体被破坏时，将升级卡和过滤器物品掉落为实体。
 */
public class DropHandler {
    private final ServerLevel level;

    public DropHandler(ServerLevel level) {
        this.level = level;
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