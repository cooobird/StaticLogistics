package com.coobird.staticlogistics.gui.menu;

import com.coobird.staticlogistics.api.type.DistributionStrategy;
import com.coobird.staticlogistics.api.type.TransferType;
import com.coobird.staticlogistics.api.type.UpgradeType;
import com.coobird.staticlogistics.gui.screen.texture.SLGuiTextures;
import com.coobird.staticlogistics.item.UpgradeItem;
import com.coobird.staticlogistics.registry.SLMenuTypes;
import com.coobird.staticlogistics.storage.LinkManager;
import com.coobird.staticlogistics.storage.config.FaceConfigComposite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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

public class FaceConfiguratorMenu extends AbstractContainerMenu {
    private static final int TOTAL_CONFIG_SLOTS = 2;
    private static final int INV_SLOT_START = TOTAL_CONFIG_SLOTS;
    private static final int INV_SLOT_END = INV_SLOT_START + 27;
    private static final int HOTBAR_SLOT_START = INV_SLOT_END;
    private static final int HOTBAR_SLOT_END = HOTBAR_SLOT_START + 9;

    public static final int INPUT_FILTER_SLOT_X = 10;
    public static final int INPUT_FILTER_SLOT_Y = 35;
    public static final int OUTPUT_FILTER_SLOT_X = 90;
    public static final int OUTPUT_FILTER_SLOT_Y = 35;

    private final BlockPos pos;
    private final Direction face;
    private FaceConfigComposite serverConfig;
    private final Player player;

    private final DataSlot globalInputSlot = DataSlot.standalone();
    private final DataSlot globalOutputSlot = DataSlot.standalone();
    private final DataSlot inputChannelSlot = DataSlot.standalone();
    private final DataSlot outputChannelSlot = DataSlot.standalone();
    private final DataSlot strategySlot = DataSlot.standalone();
    private final DataSlot prioritySlot = DataSlot.standalone();
    public final DataSlot selectedTypesMaskSlot = DataSlot.standalone();

    public FaceConfiguratorMenu(int containerId, Inventory playerInventory, FriendlyByteBuf buf) {
        this(containerId, playerInventory, buf.readBlockPos(), buf.readEnum(Direction.class));
    }

    public FaceConfiguratorMenu(int containerId, Inventory playerInventory, @Nullable BlockPos pos, @Nullable Direction face) {
        super(SLMenuTypes.FACE_CONFIGURATOR_MENU.get(), containerId);
        this.pos = pos;
        this.face = face;
        this.player = playerInventory.player;

        this.addDataSlot(globalInputSlot);
        this.addDataSlot(globalOutputSlot);
        this.addDataSlot(inputChannelSlot);
        this.addDataSlot(outputChannelSlot);
        this.addDataSlot(strategySlot);
        this.addDataSlot(prioritySlot);
        this.addDataSlot(selectedTypesMaskSlot);

        IItemHandler upgradeHandler = null;
        if (player.level() instanceof ServerLevel serverLevel && pos != null && face != null) {
            LinkManager mgr = LinkManager.get(serverLevel);
            if (mgr != null) {
                this.serverConfig = mgr.getOrCreateFaceConfig(pos, face);
                upgradeHandler = serverConfig.filterConfig.getUpgrades();
                syncToSlots();
            }
        }

        if (upgradeHandler == null) upgradeHandler = new ItemStackHandler(TOTAL_CONFIG_SLOTS);
        final IItemHandler finalHandler = upgradeHandler;

        this.addSlot(new SLUpgradeSlot(finalHandler, 0, INPUT_FILTER_SLOT_X, INPUT_FILTER_SLOT_Y, true,
            UpgradeType.BASIC_FILTER, UpgradeType.TAG_FILTER, UpgradeType.NBT_FILTER));
        this.addSlot(new SLUpgradeSlot(finalHandler, 1, OUTPUT_FILTER_SLOT_X, OUTPUT_FILTER_SLOT_Y, false,
            UpgradeType.BASIC_FILTER, UpgradeType.TAG_FILTER, UpgradeType.NBT_FILTER));

        addPlayerInventorySlots(playerInventory);
    }

    public boolean isGlobalInputEnabled() {
        return globalInputSlot.get() == 1;
    }

    public boolean isGlobalOutputEnabled() {
        return globalOutputSlot.get() == 1;
    }

    public void setGlobalInputEnabled(boolean enabled) {
        if (serverConfig != null && serverConfig.isGlobalInputEnabled() != enabled) {
            serverConfig.setGlobalInputEnabled(enabled);
            if (enabled && serverConfig.linkConfig.getInputChannel() == 0) {
                serverConfig.linkConfig.setInputChannel(1);
            }
            syncToSlots();
        }
    }

    public void setGlobalOutputEnabled(boolean enabled) {
        if (serverConfig != null && serverConfig.isGlobalOutputEnabled() != enabled) {
            serverConfig.setGlobalOutputEnabled(enabled);
            if (enabled && serverConfig.linkConfig.getOutputChannel() == 0) {
                serverConfig.linkConfig.setOutputChannel(1);
            }
            syncToSlots();
        }
    }

    public int getInputChannel() {
        return inputChannelSlot.get();
    }

    public int getOutputChannel() {
        return outputChannelSlot.get();
    }

    public DistributionStrategy getStrategy() {
        int idx = strategySlot.get();
        if (idx < 0 || idx >= DistributionStrategy.values().length) return DistributionStrategy.SEQUENTIAL;
        return DistributionStrategy.values()[idx];
    }

    public int getPriority() {
        return prioritySlot.get();
    }

    public void setInputChannel(int channel) {
        if (serverConfig != null && serverConfig.linkConfig.getInputChannel() != channel) {
            serverConfig.linkConfig.setInputChannel(channel);
            serverConfig.markDirty();
            syncToSlots();
        }
    }

    public void setOutputChannel(int channel) {
        if (serverConfig != null && serverConfig.linkConfig.getOutputChannel() != channel) {
            serverConfig.linkConfig.setOutputChannel(channel);
            serverConfig.markDirty();
            syncToSlots();
        }
    }

    public void setStrategy(DistributionStrategy strategy) {
        if (serverConfig != null && serverConfig.linkConfig.getStrategy() != strategy) {
            serverConfig.linkConfig.setStrategy(strategy);
            serverConfig.markDirty();
            syncToSlots();
        }
    }

    public void setPriority(int priority) {
        if (serverConfig != null && serverConfig.linkConfig.getPriority() != priority) {
            serverConfig.linkConfig.setPriority(priority);
            serverConfig.markDirty();
            syncToSlots();
        }
    }

    public int getSelectedTypesMask() {
        return selectedTypesMaskSlot.get();
    }

    public void setSelectedTypesMask(int mask) {
        selectedTypesMaskSlot.set(mask);
        if (serverConfig != null) {
            serverConfig.setSelectedTypesMask(mask);
            serverConfig.markDirty();
            broadcastChanges();
        }
    }

    public void toggleTypeSelection(TransferType type) {
        int current = getSelectedTypesMask();
        int newMask = current ^ type.getFlag();
        if (newMask == 0) newMask = type.getFlag();
        setSelectedTypesMask(newMask);
    }

    public void syncToSlots() {
        if (serverConfig != null) {
            globalInputSlot.set(serverConfig.isGlobalInputEnabled() ? 1 : 0);
            globalOutputSlot.set(serverConfig.isGlobalOutputEnabled() ? 1 : 0);
            inputChannelSlot.set(serverConfig.linkConfig.getInputChannel());
            outputChannelSlot.set(serverConfig.linkConfig.getOutputChannel());
            strategySlot.set(serverConfig.linkConfig.getStrategy().ordinal());
            prioritySlot.set(serverConfig.linkConfig.getPriority());
            selectedTypesMaskSlot.set(serverConfig.getSelectedTypesMask());
        }
    }

    private class SLUpgradeSlot extends SlotItemHandler {
        private final UpgradeType[] allowedTypes;
        private final boolean isInputSlot;

        public SLUpgradeSlot(IItemHandler itemHandler, int index, int x, int y, boolean isInputSlot, UpgradeType... allowedTypes) {
            super(itemHandler, index, x, y);
            this.allowedTypes = allowedTypes;
            this.isInputSlot = isInputSlot;
        }

        @Override
        public boolean isActive() {
            return isInputSlot ? isGlobalInputEnabled() : isGlobalOutputEnabled();
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            if (!isActive()) return false;
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
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        if (slot == null || !slot.hasItem()) return ItemStack.EMPTY;
        ItemStack slotStack = slot.getItem();
        ItemStack result = slotStack.copy();

        if (index < TOTAL_CONFIG_SLOTS) {
            if (!moveItemStackTo(slotStack, INV_SLOT_START, HOTBAR_SLOT_END, true))
                return ItemStack.EMPTY;
        } else {
            if (slotStack.getItem() instanceof UpgradeItem) {
                for (int i = 0; i < TOTAL_CONFIG_SLOTS; i++) {
                    Slot cfgSlot = this.slots.get(i);
                    if (cfgSlot.isActive() && cfgSlot.mayPlace(slotStack)) {
                        ItemStack existing = cfgSlot.getItem();
                        if (existing.isEmpty() || (ItemStack.isSameItemSameComponents(existing, slotStack) && existing.getCount() < cfgSlot.getMaxStackSize(slotStack))) {
                            if (moveItemStackTo(slotStack, i, i + 1, false)) break;
                        }
                    }
                }
            }
            if (!slotStack.isEmpty()) {
                if (index < INV_SLOT_END) {
                    if (!moveItemStackTo(slotStack, HOTBAR_SLOT_START, HOTBAR_SLOT_END, false))
                        return ItemStack.EMPTY;
                } else if (!moveItemStackTo(slotStack, INV_SLOT_START, INV_SLOT_END, false))
                    return ItemStack.EMPTY;
            }
        }

        if (slotStack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();

        if (slotStack.getCount() == result.getCount()) return ItemStack.EMPTY;
        slot.onTake(player, slotStack);
        return result;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    public BlockPos getPos() {
        return pos;
    }

    public Direction getFace() {
        return face;
    }

    public FaceConfigComposite getFaceConfig() {
        return serverConfig;
    }
}