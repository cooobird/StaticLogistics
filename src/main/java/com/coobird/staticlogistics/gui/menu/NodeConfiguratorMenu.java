package com.coobird.staticlogistics.gui.menu;

import com.coobird.staticlogistics.api.type.DistributionStrategy;
import com.coobird.staticlogistics.api.type.ExtractionMode;
import com.coobird.staticlogistics.api.type.TransferType;
import com.coobird.staticlogistics.api.type.UpgradeType;
import com.coobird.staticlogistics.gui.screen.texture.SLGuiTextures;
import com.coobird.staticlogistics.item.UpgradeItem;
import com.coobird.staticlogistics.registry.SLMenuTypes;
import com.coobird.staticlogistics.storage.LinkManager;
import com.coobird.staticlogistics.storage.config.ContainerConfig;
import com.coobird.staticlogistics.storage.config.FaceConfigComposite;
import com.coobird.staticlogistics.storage.config.LinkConfig;
import com.coobird.staticlogistics.util.LogisticsCalculator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
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

public class NodeConfiguratorMenu extends AbstractContainerMenu {
    private static final int FILTER_SLOTS = 2;
    private static final int UPGRADE_SLOTS = 3;
    private static final int TOTAL_CONFIG_SLOTS = FILTER_SLOTS + UPGRADE_SLOTS;
    private static final int INV_SLOT_START = TOTAL_CONFIG_SLOTS;
    private static final int INV_SLOT_END = INV_SLOT_START + 27;
    private static final int HOTBAR_SLOT_START = INV_SLOT_END;
    private static final int HOTBAR_SLOT_END = HOTBAR_SLOT_START + 9;

    public static final int INPUT_FILTER_X = 10, INPUT_FILTER_Y = 12;
    public static final int OUTPUT_FILTER_X = 96, OUTPUT_FILTER_Y = 12;
    public static final int STACK_UPGRADE_X = 142, STACK_UPGRADE_Y = 12;
    public static final int SPEED_UPGRADE_X = 162, SPEED_UPGRADE_Y = 12;
    public static final int RANGE_UPGRADE_X = 182, RANGE_UPGRADE_Y = 12;

    private final BlockPos pos;
    private final Direction face;
    private final Player player;
    private FaceConfigComposite faceConfig;
    private ContainerConfig containerConfig;

    private final DataSlot globalInputSlot = DataSlot.standalone();
    private final DataSlot globalOutputSlot = DataSlot.standalone();
    private final DataSlot inputChannelSlot = DataSlot.standalone();
    private final DataSlot outputChannelSlot = DataSlot.standalone();
    private final DataSlot strategySlot = DataSlot.standalone();
    private final DataSlot extractionModeSlot = DataSlot.standalone();
    private final DataSlot prioritySlot = DataSlot.standalone();
    private final DataSlot keepStockSlot = DataSlot.standalone();
    public final DataSlot selectedTypesMaskSlot = DataSlot.standalone();

    private final DataSlot speedMultSlot = DataSlot.standalone();
    private final DataSlot rangeMultSlot = DataSlot.standalone();
    private final DataSlot stackMultSlot = DataSlot.standalone();
    private final DataSlot dimensionSlot = DataSlot.standalone();

    private final ItemStack[] lastUpgradeStacks = new ItemStack[UPGRADE_SLOTS];

    public NodeConfiguratorMenu(int containerId, Inventory playerInventory, FriendlyByteBuf buf) {
        this(containerId, playerInventory, buf.readBlockPos(), buf.readEnum(Direction.class));
    }

    public NodeConfiguratorMenu(int containerId, Inventory playerInventory,
                                @Nullable BlockPos pos, @Nullable Direction face) {
        super(SLMenuTypes.NODE_CONFIGURATOR_MENU.get(), containerId);
        this.pos = pos;
        this.face = face;
        this.player = playerInventory.player;

        this.addDataSlot(globalInputSlot);
        this.addDataSlot(globalOutputSlot);
        this.addDataSlot(inputChannelSlot);
        this.addDataSlot(outputChannelSlot);
        this.addDataSlot(strategySlot);
        this.addDataSlot(extractionModeSlot);
        this.addDataSlot(prioritySlot);
        this.addDataSlot(keepStockSlot);
        this.addDataSlot(selectedTypesMaskSlot);

        addDataSlot(speedMultSlot);
        addDataSlot(rangeMultSlot);
        addDataSlot(stackMultSlot);
        addDataSlot(dimensionSlot);

        Arrays.fill(lastUpgradeStacks, ItemStack.EMPTY);

        IItemHandler filterHandler;
        IItemHandler containerHandler;

        if (player.level() instanceof ServerLevel serverLevel && pos != null && face != null) {
            LinkManager mgr = LinkManager.get(serverLevel);
            if (mgr != null) {
                this.faceConfig = mgr.getOrCreateFaceConfig(pos, face);
                this.containerConfig = mgr.getOrCreateContainerConfig(pos);
                filterHandler = faceConfig.filterConfig.getUpgrades();
                containerHandler = containerConfig.getUpgrades();
                syncFaceSlots();
                syncContainerSlots();
                cacheUpgradeStacks();
            } else {
                filterHandler = new ItemStackHandler(FILTER_SLOTS);
                containerHandler = new ItemStackHandler(UPGRADE_SLOTS);
            }
        } else {
            filterHandler = new ItemStackHandler(FILTER_SLOTS);
            containerHandler = new ItemStackHandler(UPGRADE_SLOTS);
        }

        final IItemHandler fh = filterHandler;
        final IItemHandler ch = containerHandler;

        this.addSlot(new FilterSlot(fh, 0, INPUT_FILTER_X, INPUT_FILTER_Y, true,
            UpgradeType.BASIC_FILTER, UpgradeType.TAG_FILTER, UpgradeType.NBT_FILTER));
        this.addSlot(new FilterSlot(fh, 1, OUTPUT_FILTER_X, OUTPUT_FILTER_Y, false,
            UpgradeType.BASIC_FILTER, UpgradeType.TAG_FILTER, UpgradeType.NBT_FILTER));
        this.addSlot(new UpgradeSlot(ch, 0, SPEED_UPGRADE_X, SPEED_UPGRADE_Y, UpgradeType.SPEED));
        this.addSlot(new UpgradeSlot(ch, 1, RANGE_UPGRADE_X, RANGE_UPGRADE_Y, UpgradeType.RANGE, UpgradeType.DIMENSION));
        this.addSlot(new UpgradeSlot(ch, 2, STACK_UPGRADE_X, STACK_UPGRADE_Y, UpgradeType.STACK));

        addPlayerInventory(playerInventory);
    }


    public boolean isGlobalInputEnabled() {
        return globalInputSlot.get() == 1;
    }

    public boolean isGlobalOutputEnabled() {
        return globalOutputSlot.get() == 1;
    }

    public int getInputChannel() {
        return inputChannelSlot.get();
    }

    public int getOutputChannel() {
        return outputChannelSlot.get();
    }

    public DistributionStrategy getStrategy() {
        int idx = strategySlot.get();
        var vals = DistributionStrategy.values();
        return idx >= 0 && idx < vals.length ? vals[idx] : DistributionStrategy.SEQUENTIAL;
    }

    public ExtractionMode getExtractionMode() {
        int idx = extractionModeSlot.get();
        var vals = ExtractionMode.values();
        return idx >= 0 && idx < vals.length ? vals[idx] : ExtractionMode.SEQUENTIAL;
    }

    public int getPriority() {
        return prioritySlot.get();
    }

    public int getKeepStock() {
        return keepStockSlot.get();
    }

    public int getSelectedTypesMask() {
        return selectedTypesMaskSlot.get();
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


    public void setGlobalInputEnabled(boolean enabled) {
        if (faceConfig != null && faceConfig.isGlobalInputEnabled() != enabled) {
            faceConfig.setGlobalInputEnabled(enabled);
            if (enabled && faceConfig.linkConfig.getInputChannel() == LinkConfig.DISABLED_CHANNEL)
                faceConfig.linkConfig.setInputChannel(LinkConfig.MIN_CHANNEL);
            syncFaceSlots();
        }
    }

    public void setGlobalOutputEnabled(boolean enabled) {
        if (faceConfig != null && faceConfig.isGlobalOutputEnabled() != enabled) {
            faceConfig.setGlobalOutputEnabled(enabled);
            if (enabled && faceConfig.linkConfig.getOutputChannel() == LinkConfig.DISABLED_CHANNEL)
                faceConfig.linkConfig.setOutputChannel(LinkConfig.MIN_CHANNEL);
            syncFaceSlots();
        }
    }

    public void setInputChannel(int channel) {
        if (faceConfig != null && faceConfig.linkConfig.getInputChannel() != channel) {
            faceConfig.linkConfig.setInputChannel(channel);
            faceConfig.markDirty();
            syncFaceSlots();
        }
    }

    public void setOutputChannel(int channel) {
        if (faceConfig != null && faceConfig.linkConfig.getOutputChannel() != channel) {
            faceConfig.linkConfig.setOutputChannel(channel);
            faceConfig.markDirty();
            syncFaceSlots();
        }
    }

    public void setStrategy(DistributionStrategy s) {
        if (faceConfig != null && faceConfig.linkConfig.getStrategy() != s) {
            faceConfig.linkConfig.setStrategy(s);
            faceConfig.markDirty();
            syncFaceSlots();
        }
    }

    public void setExtractionMode(ExtractionMode m) {
        if (faceConfig != null && faceConfig.linkConfig.getExtractionMode() != m) {
            faceConfig.linkConfig.setExtractionMode(m);
            faceConfig.markDirty();
            syncFaceSlots();
        }
    }

    public void setPriority(int v) {
        if (faceConfig != null && faceConfig.linkConfig.getPriority() != v) {
            faceConfig.linkConfig.setPriority(v);
            faceConfig.markDirty();
            syncFaceSlots();
        }
    }

    public void setKeepStock(int v) {
        if (faceConfig != null && faceConfig.linkConfig.getKeepStock() != v) {
            faceConfig.linkConfig.setKeepStock(v);
            faceConfig.markDirty();
            syncFaceSlots();
        }
    }

    public void setSelectedTypesMask(int mask) {
        selectedTypesMaskSlot.set(mask);
        if (faceConfig != null) {
            faceConfig.setSelectedTypesMask(mask);
            faceConfig.markDirty();
            broadcastChanges();
        }
    }

    public void toggleTypeSelection(TransferType type) {
        int current = getSelectedTypesMask();
        setSelectedTypesMask(current ^ type.getFlag());
    }

    // --- Sync ---

    public void syncFaceSlots() {
        if (faceConfig == null) return;
        globalInputSlot.set(faceConfig.isGlobalInputEnabled() ? 1 : 0);
        globalOutputSlot.set(faceConfig.isGlobalOutputEnabled() ? 1 : 0);
        inputChannelSlot.set(faceConfig.linkConfig.getInputChannel());
        outputChannelSlot.set(faceConfig.linkConfig.getOutputChannel());
        strategySlot.set(faceConfig.linkConfig.getStrategy().ordinal());
        extractionModeSlot.set(faceConfig.linkConfig.getExtractionMode().ordinal());
        prioritySlot.set(faceConfig.linkConfig.getPriority());
        keepStockSlot.set(faceConfig.linkConfig.getKeepStock());
        selectedTypesMaskSlot.set(faceConfig.getSelectedTypesMask());
    }

    public void syncContainerSlots() {
        if (containerConfig == null) return;
        speedMultSlot.set(LogisticsCalculator.getSpeedMultiplier(containerConfig));
        rangeMultSlot.set(LogisticsCalculator.getRangeMultiplier(containerConfig));
        stackMultSlot.set(LogisticsCalculator.getStackMultiplier(containerConfig));
        dimensionSlot.set(LogisticsCalculator.isDimensionEffective(containerConfig) ? 1 : 0);
    }

    private void cacheUpgradeStacks() {
        if (containerConfig == null) return;
        for (int i = 0; i < UPGRADE_SLOTS; i++)
            lastUpgradeStacks[i] = containerConfig.getUpgrades().getStackInSlot(i).copy();
    }

    private boolean hasUpgradeStacksChanged() {
        if (containerConfig == null) return false;
        for (int i = 0; i < UPGRADE_SLOTS; i++) {
            if (!ItemStack.matches(containerConfig.getUpgrades().getStackInSlot(i), lastUpgradeStacks[i]))
                return true;
        }
        return false;
    }

    @Override
    public void broadcastChanges() {
        if (hasUpgradeStacksChanged()) {
            syncContainerSlots();
            cacheUpgradeStacks();
        }
        super.broadcastChanges();
    }

    public boolean applyFromTag(CompoundTag tag) {
        boolean changed = false;
        for (String key : tag.getAllKeys()) {
            changed |= switch (key) {
                case "globalInput" -> {
                    boolean v = tag.getBoolean(key);
                    if (v != isGlobalInputEnabled()) {
                        setGlobalInputEnabled(v);
                        yield true;
                    }
                    yield false;
                }
                case "globalOutput" -> {
                    boolean v = tag.getBoolean(key);
                    if (v != isGlobalOutputEnabled()) {
                        setGlobalOutputEnabled(v);
                        yield true;
                    }
                    yield false;
                }
                case "inputChannel" -> {
                    int v = Math.clamp(tag.getInt(key), 1, 16);
                    if (v != getInputChannel()) {
                        setInputChannel(v);
                        yield true;
                    }
                    yield false;
                }
                case "outputChannel" -> {
                    int v = Math.clamp(tag.getInt(key), 1, 16);
                    if (v != getOutputChannel()) {
                        setOutputChannel(v);
                        yield true;
                    }
                    yield false;
                }
                case "priority" -> {
                    int v = tag.getInt(key);
                    if (v != getPriority()) {
                        setPriority(v);
                        yield true;
                    }
                    yield false;
                }
                case "keep_stock" -> {
                    int v = tag.getInt(key);
                    if (v != getKeepStock()) {
                        setKeepStock(v);
                        yield true;
                    }
                    yield false;
                }
                case "strategy" -> {
                    DistributionStrategy v = DistributionStrategy.byName(tag.getString(key), DistributionStrategy.SEQUENTIAL);
                    if (v != getStrategy()) {
                        setStrategy(v);
                        yield true;
                    }
                    yield false;
                }
                case "extractionMode" -> {
                    ExtractionMode v = ExtractionMode.byName(tag.getString(key), ExtractionMode.SEQUENTIAL);
                    if (v != getExtractionMode()) {
                        setExtractionMode(v);
                        yield true;
                    }
                    yield false;
                }
                case "selected_types_mask" -> {
                    int v = tag.getInt(key);
                    if (getSelectedTypesMask() != v) {
                        setSelectedTypesMask(v);
                        yield true;
                    }
                    yield false;
                }
                default -> false;
            };
        }
        if (changed) {
            syncFaceSlots();
            broadcastChanges();
        }
        return changed;
    }

    // --- Slots ---

    private void addPlayerInventory(Inventory inv) {
        int x = (SLGuiTextures.Background.WIDTH - SLGuiTextures.Inventory.WIDTH) / 2 + 8;
        int ty = SLGuiTextures.Background.HEIGHT + 8;
        for (int row = 0; row < 3; row++)
            for (int col = 0; col < 9; col++)
                this.addSlot(new Slot(inv, col + row * 9 + 9, x + col * 18, ty + row * 18));
        int hy = ty + 60;
        for (int col = 0; col < 9; col++)
            this.addSlot(new Slot(inv, col, x + col * 18, hy));
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        if (slot == null || !slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem().copy();
        ItemStack result = stack.copy();

        if (index < TOTAL_CONFIG_SLOTS) {
            if (!moveItemStackTo(stack, INV_SLOT_START, HOTBAR_SLOT_END, true))
                return ItemStack.EMPTY;
        } else {
            if (stack.getItem() instanceof UpgradeItem upg) {
                for (int i = 0; i < TOTAL_CONFIG_SLOTS; i++) {
                    Slot cfgSlot = this.slots.get(i);
                    if (cfgSlot.mayPlace(stack)) {
                        ItemStack existing = cfgSlot.getItem();
                        if (existing.isEmpty() || (ItemStack.isSameItemSameComponents(existing, stack) && existing.getCount() < cfgSlot.getMaxStackSize(stack))) {
                            if (moveItemStackTo(stack, i, i + 1, false)) break;
                        }
                    }
                }
            }
            if (!stack.isEmpty()) {
                if (index < INV_SLOT_END)
                    moveItemStackTo(stack, HOTBAR_SLOT_START, HOTBAR_SLOT_END, false);
                else
                    moveItemStackTo(stack, INV_SLOT_START, INV_SLOT_END, false);
            }
        }

        slot.set(stack.isEmpty() ? ItemStack.EMPTY : stack);
        if (stack.getCount() == result.getCount()) return ItemStack.EMPTY;
        slot.onTake(player, stack);
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
        return faceConfig;
    }

    public ContainerConfig getContainerConfig() {
        return containerConfig;
    }

    private class FilterSlot extends SlotItemHandler {
        private final UpgradeType[] allowedTypes;
        private final boolean isInput;

        FilterSlot(IItemHandler handler, int index, int x, int y, boolean isInput, UpgradeType... allowed) {
            super(handler, index, x, y);
            this.allowedTypes = allowed;
            this.isInput = isInput;
        }

        @Override
        public boolean isActive() {
            return isInput ? isGlobalInputEnabled() : isGlobalOutputEnabled();
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            if (!isActive()) return false;
            if (!(stack.getItem() instanceof UpgradeItem upg)) return false;
            for (UpgradeType t : allowedTypes) if (upg.getType() == t) return true;
            return false;
        }

        @Override
        public int getMaxStackSize(ItemStack stack) {
            return 1;
        }
    }

    private class UpgradeSlot extends SlotItemHandler {
        private final UpgradeType[] allowedTypes;

        UpgradeSlot(IItemHandler handler, int index, int x, int y, UpgradeType... allowed) {
            super(handler, index, x, y);
            this.allowedTypes = allowed;
        }

        @Override
        public boolean isActive() {
            return isGlobalOutputEnabled();
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            if (!(stack.getItem() instanceof UpgradeItem upg)) return false;
            for (UpgradeType t : allowedTypes) if (upg.getType() == t) return true;
            return false;
        }

        @Override
        public int getMaxStackSize(ItemStack stack) {
            return 1;
        }
    }
}
