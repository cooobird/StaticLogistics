package com.coobird.staticlogistics.content.menu;

import com.coobird.staticlogistics.content.item.UpgradeItem;
import com.coobird.staticlogistics.content.registry.SLMenuTypes;
import com.coobird.staticlogistics.transfer.UpgradeType;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public class HandFilterMenu extends AbstractFilterMenu {
    private final ItemStack filterStack;
    private final Player player;
    private final int inventorySlot;
    private final Item filterItem;

    public HandFilterMenu(int containerId, Inventory inv, ItemStack stack) {
        this(containerId, inv, stack, inv.selected);
    }

    public HandFilterMenu(int containerId, Inventory inv, ItemStack stack, int inventorySlot) {
        super(SLMenuTypes.HAND_FILTER.get(), containerId, stack);
        this.filterStack = stack;
        this.player = inv.player;
        this.inventorySlot = inventorySlot;
        this.filterItem = stack.getItem();
        addPlayerInventorySlots(inv);
    }

    @Override
    public ItemStack getFilterStack() {
        // 客户端背包同步不再携带过滤规则，界面编辑使用菜单打开时显式下发的快照。
        if (player.level().isClientSide) return filterStack;
        ItemStack current = getBoundStack();
        return current.isEmpty() ? filterStack : current;
    }

    /**
     * 返回菜单打开时绑定的真实背包槽，切换当前快捷栏槽位不会改变绑定。
     */
    public ItemStack getBoundStack() {
        if (inventorySlot < 0 || inventorySlot >= player.getInventory().getContainerSize()) {
            return ItemStack.EMPTY;
        }
        ItemStack current = player.getInventory().getItem(inventorySlot);
        return current.getItem() == filterItem
            && current.getItem() instanceof UpgradeItem upgrade
            && upgrade.isFilterUpgrade() ? current : ItemStack.EMPTY;
    }

    public Player getPlayer() {
        return player;
    }

    public boolean isBoundSlotSelected() {
        return inventorySlot == Inventory.SLOT_OFFHAND || player.getInventory().selected == inventorySlot;
    }

    @Override
    public UpgradeType getActiveUpgradeType() {
        if (filterStack.getItem() instanceof UpgradeItem upgrade) {
            return upgrade.getType();
        }
        return null;
    }

    @Override
    public boolean stillValid(Player player) {
        return player == this.player && !getBoundStack().isEmpty();
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    private void addPlayerInventorySlots(Inventory playerInventory) {
        int playerInvX = (MenuLayout.BACKGROUND_WIDTH - MenuLayout.INVENTORY_WIDTH) / 2
            + MenuLayout.INVENTORY_SLOT_X;
        int playerInvY = MenuLayout.BACKGROUND_HEIGHT + MenuLayout.INVENTORY_SLOT_Y;
        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 9; ++col) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9, playerInvX + col * 18, playerInvY + row * 18));
            }
        }
        int hotbarY = MenuLayout.BACKGROUND_HEIGHT + MenuLayout.HOTBAR_SLOT_Y;
        for (int col = 0; col < 9; ++col) {
            this.addSlot(new Slot(playerInventory, col, playerInvX + col * 18, hotbarY));
        }
    }
}
