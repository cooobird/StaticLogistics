package com.coobird.staticlogistics.gui.menu;

import com.coobird.staticlogistics.api.type.UpgradeType;
import com.coobird.staticlogistics.gui.screen.texture.SLGuiTextures;
import com.coobird.staticlogistics.item.UpgradeItem;
import com.coobird.staticlogistics.registry.SLMenuTypes;
import com.coobird.staticlogistics.storage.LinkManager;
import com.coobird.staticlogistics.storage.config.ContainerConfig;
import com.coobird.staticlogistics.util.LogisticsCalculator;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

public class ContainerConfiguratorMenu extends AbstractContainerMenu {
    private static final int TOTAL_CONFIG_SLOTS = 3;
    private static final int INV_SLOT_START = TOTAL_CONFIG_SLOTS;
    private static final int INV_SLOT_END = INV_SLOT_START + 27;
    private static final int HOTBAR_SLOT_START = INV_SLOT_END;
    private static final int HOTBAR_SLOT_END = HOTBAR_SLOT_START + 9;

    private final BlockPos pos;
    private ContainerConfig serverConfig;

    private final DataSlot speedMultSlot = DataSlot.standalone();
    private final DataSlot rangeMultSlot = DataSlot.standalone();
    private final DataSlot stackMultSlot = DataSlot.standalone();
    private final DataSlot dimensionSlot = DataSlot.standalone();

    private final ItemStack[] lastUpgradeStacks = new ItemStack[TOTAL_CONFIG_SLOTS];

    public ContainerConfiguratorMenu(int containerId, Inventory playerInventory, FriendlyByteBuf buf) {
        this(containerId, playerInventory, buf.readBlockPos());
    }

    public ContainerConfiguratorMenu(int containerId, Inventory playerInventory, @Nullable BlockPos pos) {
        super(SLMenuTypes.CONTAINER_CONFIGURATOR_MENU.get(), containerId);
        this.pos = pos;

        addDataSlot(speedMultSlot);
        addDataSlot(rangeMultSlot);
        addDataSlot(stackMultSlot);
        addDataSlot(dimensionSlot);

        Arrays.fill(lastUpgradeStacks, ItemStack.EMPTY);

        IItemHandler upgradeHandler = null;
        if (playerInventory.player.level() instanceof ServerLevel serverLevel && pos != null) {
            LinkManager mgr = LinkManager.get(serverLevel);
            if (mgr != null) {
                this.serverConfig = mgr.getOrCreateContainerConfig(pos);
                upgradeHandler = serverConfig.getUpgrades();
                updateDataSlots();
                cacheUpgradeStacks();
            }
        }

        if (upgradeHandler == null) {
            upgradeHandler = new ItemStackHandler(TOTAL_CONFIG_SLOTS);
        }

        final IItemHandler finalHandler = upgradeHandler;

        this.addSlot(new ContainerUpgradeSlot(finalHandler, 0, 18, 21, UpgradeType.SPEED));
        this.addSlot(new ContainerUpgradeSlot(finalHandler, 1, 18, 51, UpgradeType.RANGE, UpgradeType.DIMENSION));
        this.addSlot(new ContainerUpgradeSlot(finalHandler, 2, 18, 81, UpgradeType.STACK));

        addPlayerInventorySlots(playerInventory);
    }

    private void cacheUpgradeStacks() {
        if (serverConfig == null) return;
        for (int i = 0; i < TOTAL_CONFIG_SLOTS; i++) {
            lastUpgradeStacks[i] = serverConfig.getUpgrades().getStackInSlot(i).copy();
        }
    }

    private boolean hasUpgradeStacksChanged() {
        if (serverConfig == null) return false;
        for (int i = 0; i < TOTAL_CONFIG_SLOTS; i++) {
            ItemStack current = serverConfig.getUpgrades().getStackInSlot(i);
            if (!ItemStack.matches(current, lastUpgradeStacks[i])) {
                return true;
            }
        }
        return false;
    }

    public void updateDataSlots() {
        if (serverConfig != null) {
            speedMultSlot.set(LogisticsCalculator.getSpeedMultiplier(serverConfig));
            rangeMultSlot.set(LogisticsCalculator.getRangeMultiplier(serverConfig));
            stackMultSlot.set(LogisticsCalculator.getStackMultiplier(serverConfig));
            dimensionSlot.set(LogisticsCalculator.isDimensionEffective(serverConfig) ? 1 : 0);
        }
    }

    public int getSpeedMultiplier() {
        return speedMultSlot.get();
    }

    public int getRangeMultiplier() {
        return rangeMultSlot.get();
    }

    public int getStackMultiplier() {
        return stackMultSlot.get();
    }

    public boolean isDimensionEffective() {
        return dimensionSlot.get() == 1;
    }

    @Override
    public void broadcastChanges() {
        if (hasUpgradeStacksChanged()) {
            updateDataSlots();
            cacheUpgradeStacks();
        }
        super.broadcastChanges();
    }

    private static class ContainerUpgradeSlot extends SlotItemHandler {
        private final UpgradeType[] allowedTypes;

        public ContainerUpgradeSlot(IItemHandler itemHandler, int index, int x, int y, UpgradeType... allowedTypes) {
            super(itemHandler, index, x, y);
            this.allowedTypes = allowedTypes;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            if (!(stack.getItem() instanceof UpgradeItem upgrade)) return false;

            for (UpgradeType type : allowedTypes) {
                if (upgrade.getType() == type) return true;
            }
            return false;
        }

        @Override
        public int getMaxStackSize(ItemStack stack) {
            return 1;
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack itemstack = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot != null && slot.hasItem()) {
            ItemStack itemstack1 = slot.getItem();
            itemstack = itemstack1.copy();

            if (index < TOTAL_CONFIG_SLOTS) {
                if (!this.moveItemStackTo(itemstack1, INV_SLOT_START, HOTBAR_SLOT_END, true)) return ItemStack.EMPTY;
            } else {
                if (itemstack1.getItem() instanceof UpgradeItem upgrade) {
                    int targetSlot = getTargetSlot(upgrade);
                    if (targetSlot != -1) {
                        if (!this.moveItemStackTo(itemstack1, targetSlot, targetSlot + 1, false))
                            return ItemStack.EMPTY;
                    } else {
                        if (index < INV_SLOT_END) {
                            if (!this.moveItemStackTo(itemstack1, HOTBAR_SLOT_START, HOTBAR_SLOT_END, false))
                                return ItemStack.EMPTY;
                        } else {
                            if (!this.moveItemStackTo(itemstack1, INV_SLOT_START, INV_SLOT_END, false))
                                return ItemStack.EMPTY;
                        }
                    }
                } else {
                    if (index < INV_SLOT_END) {
                        if (!this.moveItemStackTo(itemstack1, HOTBAR_SLOT_START, HOTBAR_SLOT_END, false))
                            return ItemStack.EMPTY;
                    } else {
                        if (!this.moveItemStackTo(itemstack1, INV_SLOT_START, INV_SLOT_END, false))
                            return ItemStack.EMPTY;
                    }
                }
            }
            if (itemstack1.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
            else slot.setChanged();

            if (itemstack1.getCount() == itemstack.getCount()) return ItemStack.EMPTY;
            slot.onTake(player, itemstack1);
        }
        return itemstack;
    }

    private int getTargetSlot(UpgradeItem upgrade) {
        return switch (upgrade.getType()) {
            case SPEED -> 0;
            case RANGE, DIMENSION -> 1;
            case STACK -> 2;
            default -> -1;
        };
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

    @Override
    public boolean stillValid(Player player) {
        return pos != null && player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64.0;
    }

    public BlockPos getPos() {
        return pos;
    }
}