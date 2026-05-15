package com.coobird.staticlogistics.gui.menu;

import com.coobird.staticlogistics.api.type.UpgradeType;
import com.coobird.staticlogistics.gui.screen.texture.SLGuiTextures;
import com.coobird.staticlogistics.item.UpgradeItem;
import com.coobird.staticlogistics.registry.SLMenuTypes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class HandFilterMenu extends AbstractFilterMenu {
    private final ItemStack filterStack;
    private final Player player;

    public HandFilterMenu(int containerId, Inventory inv, ItemStack stack) {
        super(SLMenuTypes.HAND_FILTER.get(), containerId, stack);
        this.filterStack = stack;
        this.player = inv.player;
        addPlayerInventorySlots(inv);
    }

    @Override
    public ItemStack getFilterStack() {
        return filterStack;
    }

    public Player getPlayer() {
        return player;
    }

    @Override
    public UpgradeType getActiveUpgradeType() {
        if (filterStack.getItem() instanceof UpgradeItem upgrade) {
            return upgrade.getType();
        }
        return null;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    private void addPlayerInventorySlots(Inventory playerInventory) {
        int playerInvX = (SLGuiTextures.Background.WIDTH - SLGuiTextures.Inventory.WIDTH) / 2 + 8;
        int playerInvY = SLGuiTextures.Background.HEIGHT + 8;
        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 9; ++col) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9, playerInvX + col * 18, playerInvY + row * 18));
            }
        }
        int hotbarY = playerInvY + 60;
        for (int col = 0; col < 9; ++col) {
            this.addSlot(new Slot(playerInventory, col, playerInvX + col * 18, hotbarY));
        }
    }
}