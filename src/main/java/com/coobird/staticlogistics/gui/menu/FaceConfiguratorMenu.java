package com.coobird.staticlogistics.gui.menu;

import com.coobird.staticlogistics.api.type.DistributionStrategy;
import com.coobird.staticlogistics.api.type.TransferType;
import com.coobird.staticlogistics.api.type.UpgradeTier;
import com.coobird.staticlogistics.api.type.UpgradeType;
import com.coobird.staticlogistics.config.SLConfig;
import com.coobird.staticlogistics.core.registration.TransferRegistries;
import com.coobird.staticlogistics.gui.screen.texture.SLGuiTextures;
import com.coobird.staticlogistics.item.UpgradeItem;
import com.coobird.staticlogistics.registry.SLMenuTypes;
import com.coobird.staticlogistics.storage.LinkManager;
import com.coobird.staticlogistics.storage.config.FaceConfigComposite;
import com.coobird.staticlogistics.storage.config.LinkConfig;
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

import java.util.function.Consumer;

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
    private TransferType type;
    private FaceConfigComposite serverConfig;

    private final DataSlot inputEnabledSlot = DataSlot.standalone();
    private final DataSlot outputEnabledSlot = DataSlot.standalone();
    private final DataSlot inputChannelSlot = DataSlot.standalone();
    private final DataSlot outputChannelSlot = DataSlot.standalone();
    private final DataSlot strategySlot = DataSlot.standalone();
    private final DataSlot prioritySlot = DataSlot.standalone();
    public final DataSlot selectedTypesMaskSlot = DataSlot.standalone();

    public FaceConfiguratorMenu(int containerId, Inventory playerInventory, FriendlyByteBuf buf) {
        this(containerId, playerInventory,
            buf.readBlockPos(),
            buf.readEnum(Direction.class),
            TransferRegistries.get(buf.readResourceLocation())
        );
    }

    public FaceConfiguratorMenu(int containerId, Inventory playerInventory, @Nullable BlockPos pos, @Nullable Direction face, @Nullable TransferType type) {
        super(SLMenuTypes.FACE_CONFIGURATOR_MENU.get(), containerId);
        this.pos = pos;
        this.face = face;
        this.type = type;

        this.addDataSlot(inputEnabledSlot);
        this.addDataSlot(outputEnabledSlot);
        this.addDataSlot(inputChannelSlot);
        this.addDataSlot(outputChannelSlot);
        this.addDataSlot(strategySlot);
        this.addDataSlot(prioritySlot);
        this.addDataSlot(selectedTypesMaskSlot);

        IItemHandler upgradeHandler = null;
        if (playerInventory.player.level() instanceof ServerLevel serverLevel && pos != null && face != null) {
            LinkManager mgr = LinkManager.get(serverLevel);
            if (mgr != null) {
                this.serverConfig = mgr.getOrCreateFaceConfig(pos, face);
                upgradeHandler = serverConfig.filterConfig.getUpgrades();
                syncToSlots();
            }
        }

        if (upgradeHandler == null) {
            upgradeHandler = new ItemStackHandler(TOTAL_CONFIG_SLOTS);
        }

        final IItemHandler finalHandler = upgradeHandler;

        this.addSlot(new SLUpgradeSlot(finalHandler, 0, INPUT_FILTER_SLOT_X, INPUT_FILTER_SLOT_Y, true,
            UpgradeType.BASIC_FILTER, UpgradeType.TAG_FILTER, UpgradeType.NBT_FILTER));
        this.addSlot(new SLUpgradeSlot(finalHandler, 1, OUTPUT_FILTER_SLOT_X, OUTPUT_FILTER_SLOT_Y, false,
            UpgradeType.BASIC_FILTER, UpgradeType.TAG_FILTER, UpgradeType.NBT_FILTER));

        addPlayerInventorySlots(playerInventory);
    }

    public void updateSettings(Consumer<LinkConfig.SideData> updater) {
        if (serverConfig != null && type != null) {
            LinkConfig.SideData data = serverConfig.linkConfig.getSettings(type);
            updater.accept(data);
            serverConfig.markDirty();
            syncToSlots();
        }
    }

    public void switchTransferType(TransferType newType) {
        if (!newType.equals(type)) {
            boolean currentInputEnabled = isInputEnabled();
            boolean currentOutputEnabled = isOutputEnabled();
            int currentInputChannel = getInputChannel();
            int currentOutputChannel = getOutputChannel();
            DistributionStrategy currentStrategy = getStrategy();
            int currentPriority = getPriority();

            this.type = newType;

            if (serverConfig != null) {
                LinkConfig.SideData data = serverConfig.linkConfig.getSettings(newType);
                data.inputEnabled = currentInputEnabled;
                data.outputEnabled = currentOutputEnabled;
                data.inputChannel = currentInputChannel;
                data.outputChannel = currentOutputChannel;
                data.strategy = currentStrategy;
                data.priority = currentPriority;
                serverConfig.markDirty();
            }
            syncToSlots();
        }
    }

    public int getSelectedTypesMask() {
        return this.selectedTypesMaskSlot.get();
    }

    public void toggleTypeSelection(TransferType type) {
        int currentMask = getSelectedTypesMask();
        int newMask = currentMask ^ type.getFlag();
        if (newMask == 0) newMask = type.getFlag();
        selectedTypesMaskSlot.set(newMask);
    }

    public void cycleStrategy() {
        updateSettings(data -> {
            int nextOrdinal = (data.strategy.ordinal() + 1) % DistributionStrategy.values().length;
            data.strategy = DistributionStrategy.values()[nextOrdinal];
        });
    }

    public void changePriority(int delta) {
        updateSettings(data -> data.priority += delta);
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
            return isInputSlot ? isInputEnabled() : isOutputEnabled();
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
            if (!(stack.getItem() instanceof UpgradeItem upgrade)) return 64;
            if (upgrade.getTier() == UpgradeTier.NETHER_STAR) return 1;
            return switch (upgrade.getType()) {
                case DIMENSION, BASIC_FILTER, TAG_FILTER, NBT_FILTER -> 1;
                default -> SLConfig.getUpgradeStackLimit();
            };
        }
    }

    public void syncToSlots() {
        if (serverConfig != null) {
            LinkConfig.SideData data = serverConfig.linkConfig.getSettings(type);
            inputEnabledSlot.set(data.inputEnabled ? 1 : 0);
            outputEnabledSlot.set(data.outputEnabled ? 1 : 0);
            inputChannelSlot.set(data.inputChannel);
            outputChannelSlot.set(data.outputChannel);
            strategySlot.set(data.strategy.ordinal());
            prioritySlot.set(data.priority);
            selectedTypesMaskSlot.set(serverConfig.getSelectedTypesMask());
        }
    }

    public boolean isInputEnabled() {
        return this.inputEnabledSlot.get() == 1;
    }

    public boolean isOutputEnabled() {
        return this.outputEnabledSlot.get() == 1;
    }

    public int getInputChannel() {
        return this.inputChannelSlot.get();
    }

    public int getOutputChannel() {
        return this.outputChannelSlot.get();
    }

    public DistributionStrategy getStrategy() {
        int index = this.strategySlot.get();
        if (index < 0 || index >= DistributionStrategy.values().length) return DistributionStrategy.SEQUENTIAL;
        return DistributionStrategy.values()[index];
    }

    public int getPriority() {
        return this.prioritySlot.get();
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
                            if (moveItemStackTo(slotStack, i, i + 1, false))
                                break;
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

        if (slotStack.isEmpty())
            slot.setByPlayer(ItemStack.EMPTY);
        else
            slot.setChanged();

        if (slotStack.getCount() == result.getCount())
            return ItemStack.EMPTY;

        slot.onTake(player, slotStack);
        return result;
    }

    private void addPlayerInventorySlots(Inventory playerInventory) {
        int playerInvX = (SLGuiTextures.Background.WIDTH - SLGuiTextures.Inventory.WIDTH) / 2 + 8;
        int playerInvY = SLGuiTextures.GUI_HEIGHT + 8;
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
        return true;
    }

    public BlockPos getPos() {
        return pos;
    }

    public Direction getFace() {
        return face;
    }

    public TransferType getTransferType() {
        return type;
    }

    public FaceConfigComposite getFaceConfig() {
        return serverConfig;
    }
}