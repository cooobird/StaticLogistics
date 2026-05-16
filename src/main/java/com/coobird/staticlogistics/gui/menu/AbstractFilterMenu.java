package com.coobird.staticlogistics.gui.menu;

import com.coobird.staticlogistics.api.type.NbtMatchMode;
import com.coobird.staticlogistics.api.type.UpgradeType;
import com.coobird.staticlogistics.filter.data.FilterData;
import com.coobird.staticlogistics.registry.SLDataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.UnaryOperator;

public abstract class AbstractFilterMenu extends AbstractContainerMenu {
    protected final DataSlot blacklistSlot = DataSlot.standalone();
    protected final DataSlot ignoreDamageSlot = DataSlot.standalone();

    protected AbstractFilterMenu(MenuType<?> type, int containerId, ItemStack upgradeStack) {
        super(type, containerId);
        syncFromStack(upgradeStack);
        this.addDataSlot(blacklistSlot);
        this.addDataSlot(ignoreDamageSlot);
    }

    public void bulkUpdate(UnaryOperator<FilterData> operator) {
        updateFilterData(operator);
    }

    protected abstract ItemStack getFilterStack();

    public abstract UpgradeType getActiveUpgradeType();

    private void syncFromStack(ItemStack stack) {
        FilterData data = stack.getOrDefault(SLDataComponents.FILTER_DATA.get(), FilterData.EMPTY);
        blacklistSlot.set(data.isBlacklist() ? 1 : 0);
        ignoreDamageSlot.set(data.ignoreDamage() ? 1 : 0);
    }

    private void updateFilterData(UnaryOperator<FilterData> operator) {
        ItemStack currentStack = getFilterStack();
        FilterData current = getFilterData();
        FilterData updated = operator.apply(current);
        currentStack.set(SLDataComponents.FILTER_DATA.get(), updated);
        this.broadcastChanges();
    }

    public void setFilterItem(int index, ItemStack stack) {
        updateFilterData(current -> {
            Map<String, ItemStack> newItems = new HashMap<>(current.items());
            Map<String, Fluid> newFluids = new HashMap<>(current.fluids());
            newItems.put(String.valueOf(index), stack.copy());
            newFluids.remove(String.valueOf(index));
            return new FilterData(
                newItems, newFluids,
                current.isBlacklist(), current.nbtMatchMode(),
                current.tagSlots(), current.excludedTagSlots(), current.fluidFilterTags(), current.excludedFluidTags(),
                current.ignoreDamage()
            );
        });
    }

    public void setFluidSlot(int index, Fluid fluid) {
        updateFilterData(current -> {
            Map<String, ItemStack> newItems = new HashMap<>(current.items());
            Map<String, Fluid> newFluids = new HashMap<>(current.fluids());
            newFluids.put(String.valueOf(index), fluid);
            newItems.remove(String.valueOf(index));
            return new FilterData(
                newItems, newFluids,
                current.isBlacklist(), current.nbtMatchMode(),
                current.tagSlots(), current.excludedTagSlots(), current.fluidFilterTags(), current.excludedFluidTags(),
                current.ignoreDamage()
            );
        });
    }

    public void removeFilterItem(int index) {
        updateFilterData(current -> {
            Map<String, ItemStack> newItems = new HashMap<>(current.items());
            if (newItems.remove(String.valueOf(index)) == null) return current;
            return new FilterData(
                newItems, current.fluids(),
                current.isBlacklist(), current.nbtMatchMode(),
                current.tagSlots(), current.excludedTagSlots(), current.fluidFilterTags(), current.excludedFluidTags(),
                current.ignoreDamage()
            );
        });
    }

    public void removeFluidSlot(int index) {
        updateFilterData(current -> {
            Map<String, Fluid> newFluids = new HashMap<>(current.fluids());
            if (newFluids.remove(String.valueOf(index)) == null) return current;
            return new FilterData(
                current.items(), newFluids,
                current.isBlacklist(), current.nbtMatchMode(),
                current.tagSlots(), current.excludedTagSlots(), current.fluidFilterTags(), current.excludedFluidTags(),
                current.ignoreDamage()
            );
        });
    }

    public ItemStack getFilterItem(int index) {
        return getFilterData().items().getOrDefault(String.valueOf(index), ItemStack.EMPTY);
    }

    public Fluid getFluidSlot(int index) {
        return getFilterData().fluids().get(String.valueOf(index));
    }

    public boolean isBlacklistMode() {
        return this.blacklistSlot.get() == 1;
    }

    public void setBlacklistMode(boolean blacklist) {
        updateFilterData(current -> new FilterData(
            current.items(), current.fluids(),
            blacklist, current.nbtMatchMode(),
            current.tagSlots(), current.excludedTagSlots(), current.fluidFilterTags(), current.excludedFluidTags(),
            current.ignoreDamage()
        ));
        this.blacklistSlot.set(blacklist ? 1 : 0);
    }

    public NbtMatchMode getNbtMatchMode() {
        return getFilterData().nbtMatchMode();
    }

    public void setNbtMatchMode(NbtMatchMode mode) {
        updateFilterData(current -> new FilterData(
            current.items(), current.fluids(),
            current.isBlacklist(), mode,
            current.tagSlots(), current.excludedTagSlots(), current.fluidFilterTags(), current.excludedFluidTags(),
            current.ignoreDamage()
        ));
    }

    public boolean isIgnoreDamage() {
        return this.ignoreDamageSlot.get() == 1;
    }

    public void setIgnoreDamage(boolean ignore) {
        updateFilterData(current -> new FilterData(
            current.items(), current.fluids(),
            current.isBlacklist(), current.nbtMatchMode(),
            current.tagSlots(), current.excludedTagSlots(), current.fluidFilterTags(), current.excludedFluidTags(),
            ignore
        ));
        this.ignoreDamageSlot.set(ignore ? 1 : 0);
    }

    // ==================== 物品标签相关 ====================
    public Set<TagKey<Item>> getSlotTags(int slot) {
        return getFilterData().tagSlots().getOrDefault(String.valueOf(slot), Set.of());
    }

    public Set<TagKey<Item>> getExcludedTags(int slot) {
        return getFilterData().excludedTagSlots().getOrDefault(String.valueOf(slot), Set.of());
    }

    public void addSlotTag(int slot, TagKey<Item> tag) {
        updateFilterData(current -> {
            Map<String, Set<TagKey<Item>>> newSlots = new HashMap<>(current.tagSlots());
            String key = String.valueOf(slot);
            Set<TagKey<Item>> tags = new HashSet<>(newSlots.getOrDefault(key, Set.of()));
            if (!tags.add(tag)) return current;
            newSlots.put(key, tags);
            return new FilterData(
                current.items(), current.fluids(),
                current.isBlacklist(), current.nbtMatchMode(),
                newSlots, current.excludedTagSlots(), current.fluidFilterTags(), current.excludedFluidTags(),
                current.ignoreDamage()
            );
        });
    }

    public void addExcludedTag(int slot, TagKey<Item> tag) {
        updateFilterData(current -> {
            Map<String, Set<TagKey<Item>>> newExcluded = new HashMap<>(current.excludedTagSlots());
            String key = String.valueOf(slot);
            Set<TagKey<Item>> tags = new HashSet<>(newExcluded.getOrDefault(key, Set.of()));
            if (!tags.add(tag)) return current;
            newExcluded.put(key, tags);
            return new FilterData(
                current.items(), current.fluids(),
                current.isBlacklist(), current.nbtMatchMode(),
                current.tagSlots(), newExcluded, current.fluidFilterTags(), current.excludedFluidTags(),
                current.ignoreDamage()
            );
        });
    }

    public boolean removeSlotTag(int slot, String tagString) {
        ResourceLocation rl = ResourceLocation.tryParse(tagString);
        if (rl == null) return false;
        return removeSlotTag(slot, TagKey.create(Registries.ITEM, rl));
    }

    public boolean removeSlotTag(int slot, TagKey<Item> tag) {
        boolean[] changed = {false};
        updateFilterData(current -> {
            Map<String, Set<TagKey<Item>>> newSlots = new HashMap<>(current.tagSlots());
            String key = String.valueOf(slot);
            Set<TagKey<Item>> tags = new HashSet<>(newSlots.getOrDefault(key, Set.of()));
            if (!tags.remove(tag)) return current;
            if (tags.isEmpty()) newSlots.remove(key);
            else newSlots.put(key, tags);
            changed[0] = true;
            return new FilterData(
                current.items(), current.fluids(),
                current.isBlacklist(), current.nbtMatchMode(),
                newSlots, current.excludedTagSlots(), current.fluidFilterTags(), current.excludedFluidTags(),
                current.ignoreDamage()
            );
        });
        return changed[0];
    }

    public void removeExcludedTag(int slot, TagKey<Item> tag) {
        updateFilterData(current -> {
            Map<String, Set<TagKey<Item>>> newExcluded = new HashMap<>(current.excludedTagSlots());
            String key = String.valueOf(slot);
            Set<TagKey<Item>> tags = new HashSet<>(newExcluded.getOrDefault(key, Set.of()));
            if (!tags.remove(tag)) return current;
            if (tags.isEmpty()) newExcluded.remove(key);
            else newExcluded.put(key, tags);
            return new FilterData(
                current.items(), current.fluids(),
                current.isBlacklist(), current.nbtMatchMode(),
                current.tagSlots(), newExcluded, current.fluidFilterTags(), current.excludedFluidTags(),
                current.ignoreDamage()
            );
        });
    }

    public void clearSlotTags(int slot) {
        updateFilterData(current -> {
            Map<String, Set<TagKey<Item>>> newSlots = new HashMap<>(current.tagSlots());
            Map<String, Set<TagKey<Item>>> newExcluded = new HashMap<>(current.excludedTagSlots());
            newSlots.remove(String.valueOf(slot));
            newExcluded.remove(String.valueOf(slot));
            return new FilterData(
                current.items(), current.fluids(),
                current.isBlacklist(), current.nbtMatchMode(),
                newSlots, newExcluded, current.fluidFilterTags(), current.excludedFluidTags(),
                current.ignoreDamage()
            );
        });
    }

    // ==================== 流体标签相关 ====================
    public Set<TagKey<Fluid>> getSlotFluidTags(int slot) {
        return getFilterData().fluidFilterTags().getOrDefault(String.valueOf(slot), Set.of());
    }

    public Set<TagKey<Fluid>> getExcludedFluidTags(int slot) {
        return getFilterData().excludedFluidTags().getOrDefault(String.valueOf(slot), Set.of());
    }

    public void addSlotFluidTag(int slot, TagKey<Fluid> tag) {
        updateFilterData(current -> {
            Map<String, Set<TagKey<Fluid>>> newSlots = new HashMap<>(current.fluidFilterTags());
            String key = String.valueOf(slot);
            Set<TagKey<Fluid>> tags = new HashSet<>(newSlots.getOrDefault(key, Set.of()));
            if (!tags.add(tag)) return current;
            newSlots.put(key, tags);
            return new FilterData(
                current.items(), current.fluids(),
                current.isBlacklist(), current.nbtMatchMode(),
                current.tagSlots(), current.excludedTagSlots(),
                newSlots, current.excludedFluidTags(),
                current.ignoreDamage()
            );
        });
    }

    public void removeSlotFluidTag(int slot, TagKey<Fluid> tag) {
        updateFilterData(current -> {
            Map<String, Set<TagKey<Fluid>>> newSlots = new HashMap<>(current.fluidFilterTags());
            String key = String.valueOf(slot);
            Set<TagKey<Fluid>> tags = new HashSet<>(newSlots.getOrDefault(key, Set.of()));
            if (!tags.remove(tag)) return current;
            if (tags.isEmpty()) newSlots.remove(key);
            else newSlots.put(key, tags);
            return new FilterData(
                current.items(), current.fluids(),
                current.isBlacklist(), current.nbtMatchMode(),
                current.tagSlots(), current.excludedTagSlots(),
                newSlots, current.excludedFluidTags(),
                current.ignoreDamage()
            );
        });
    }

    public void addExcludedFluidTag(int slot, TagKey<Fluid> tag) {
        updateFilterData(current -> {
            Map<String, Set<TagKey<Fluid>>> newExcluded = new HashMap<>(current.excludedFluidTags());
            String key = String.valueOf(slot);
            Set<TagKey<Fluid>> tags = new HashSet<>(newExcluded.getOrDefault(key, Set.of()));
            if (!tags.add(tag)) return current;
            newExcluded.put(key, tags);
            return new FilterData(
                current.items(), current.fluids(),
                current.isBlacklist(), current.nbtMatchMode(),
                current.tagSlots(), current.excludedTagSlots(),
                current.fluidFilterTags(), newExcluded,
                current.ignoreDamage()
            );
        });
    }

    public void removeExcludedFluidTag(int slot, TagKey<Fluid> tag) {
        updateFilterData(current -> {
            Map<String, Set<TagKey<Fluid>>> newExcluded = new HashMap<>(current.excludedFluidTags());
            String key = String.valueOf(slot);
            Set<TagKey<Fluid>> tags = new HashSet<>(newExcluded.getOrDefault(key, Set.of()));
            if (!tags.remove(tag)) return current;
            if (tags.isEmpty()) newExcluded.remove(key);
            else newExcluded.put(key, tags);
            return new FilterData(
                current.items(), current.fluids(),
                current.isBlacklist(), current.nbtMatchMode(),
                current.tagSlots(), current.excludedTagSlots(),
                current.fluidFilterTags(), newExcluded,
                current.ignoreDamage()
            );
        });
    }

    public void clearSlotFluidTags(int slot) {
        updateFilterData(current -> {
            Map<String, Set<TagKey<Fluid>>> newSlots = new HashMap<>(current.fluidFilterTags());
            Map<String, Set<TagKey<Fluid>>> newExcluded = new HashMap<>(current.excludedFluidTags());
            newSlots.remove(String.valueOf(slot));
            newExcluded.remove(String.valueOf(slot));
            return new FilterData(
                current.items(), current.fluids(),
                current.isBlacklist(), current.nbtMatchMode(),
                current.tagSlots(), current.excludedTagSlots(),
                newSlots, newExcluded,
                current.ignoreDamage()
            );
        });
    }

    public FilterData getFilterData() {
        return getFilterStack().getOrDefault(SLDataComponents.FILTER_DATA.get(), FilterData.EMPTY);
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }
}