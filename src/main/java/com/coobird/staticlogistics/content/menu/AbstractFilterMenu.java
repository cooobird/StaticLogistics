package com.coobird.staticlogistics.content.menu;

import com.coobird.staticlogistics.logistics.SLDataComponents;
import com.coobird.staticlogistics.logistics.filter.FilterData;
import com.coobird.staticlogistics.logistics.filter.FilterRule;
import com.coobird.staticlogistics.transfer.UpgradeType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;

import java.util.*;
import java.util.function.UnaryOperator;

public abstract class AbstractFilterMenu extends AbstractContainerMenu {

    protected AbstractFilterMenu(MenuType<?> type, int containerId, ItemStack upgradeStack) {
        super(type, containerId);
    }

    protected abstract ItemStack getFilterStack();

    public abstract UpgradeType getActiveUpgradeType();

    private void update(UnaryOperator<FilterData> change) {
        ItemStack stack = getFilterStack();
        stack.set(SLDataComponents.FILTER_DATA.get(), change.apply(getFilterData()).normalizedFor(getActiveUpgradeType()));
        broadcastChanges();
    }

    public void setFilterItem(int index, ItemStack stack) {
        update(current -> {
            String key = Integer.toString(index);
            Map<String, ItemStack> items = new HashMap<>(current.items());
            Map<String, Fluid> fluids = new HashMap<>(current.fluids());
            Map<String, List<FilterRule>> rules = new HashMap<>(current.rules());
            items.put(key, stack.copyWithCount(1));
            fluids.remove(key);
            rules.remove(key);
            Map<String, Boolean> blacklist = new HashMap<>(current.blacklist());
            blacklist.remove(key);
            return new FilterData(items, fluids, blacklist, rules);
        });
    }

    public void setFluidSlot(int index, Fluid fluid) {
        update(current -> {
            String key = Integer.toString(index);
            Map<String, ItemStack> items = new HashMap<>(current.items());
            Map<String, Fluid> fluids = new HashMap<>(current.fluids());
            Map<String, List<FilterRule>> rules = new HashMap<>(current.rules());
            items.remove(key);
            fluids.put(key, fluid);
            rules.remove(key);
            Map<String, Boolean> blacklist = new HashMap<>(current.blacklist());
            blacklist.remove(key);
            return new FilterData(items, fluids, blacklist, rules);
        });
    }

    public void clearSlot(int index) {
        update(current -> {
            String key = Integer.toString(index);
            Map<String, ItemStack> items = new HashMap<>(current.items());
            Map<String, Fluid> fluids = new HashMap<>(current.fluids());
            Map<String, List<FilterRule>> rules = new HashMap<>(current.rules());
            items.remove(key);
            fluids.remove(key);
            rules.remove(key);
            Map<String, Boolean> blacklist = new HashMap<>(current.blacklist());
            blacklist.remove(key);
            return new FilterData(items, fluids, blacklist, rules);
        });
    }

    public boolean applySelection(Set<Integer> slots, Boolean blacklistMode,
                                  List<FilterRule> original, List<FilterRule> edited) {
        FilterData current = getFilterData();
        Map<String, List<FilterRule>> allRules = new HashMap<>(current.rules());
        Map<String, Boolean> modes = new HashMap<>(current.blacklist());
        for (int slot : slots) {
            String key = Integer.toString(slot);
            if (blacklistMode != null) {
                if (blacklistMode) modes.put(key, true);
                else modes.remove(key);
            }
            List<FilterRule> target = new ArrayList<>(allRules.getOrDefault(key, List.of()));
            if (!original.equals(edited)) {
                if (slots.size() == 1) {
                    target = new ArrayList<>(edited);
                } else {
                    for (FilterRule oldRule : original) {
                        if (!edited.contains(oldRule)) target.remove(oldRule);
                    }
                    for (FilterRule newRule : edited) {
                        if (!original.contains(newRule) && !target.contains(newRule)) {
                            if (newRule.kind() == FilterRule.Kind.TAG || newRule.kind() == FilterRule.Kind.DURABILITY)
                                target.removeIf(rule -> rule.kind() == newRule.kind());
                            if (newRule.kind() == FilterRule.Kind.NUMERIC) {
                                String component = newRule.key().substring(0, newRule.key().indexOf('|') + 1);
                                target.removeIf(rule -> rule.kind() == FilterRule.Kind.NUMERIC && rule.key().startsWith(component));
                            }
                            if (newRule.kind() == FilterRule.Kind.COMPONENT) {
                                if (newRule.mode() == FilterRule.Mode.MATCH)
                                    target.removeIf(rule -> rule.kind() == FilterRule.Kind.COMPONENT);
                                else target.removeIf(rule -> rule.kind() == FilterRule.Kind.COMPONENT
                                    && rule.mode() == FilterRule.Mode.MATCH);
                            }
                            target.add(newRule);
                        }
                    }
                }
                if (target.size() > FilterData.MAX_RULES_PER_SLOT) return false;
                if (target.isEmpty()) allRules.remove(key);
                else allRules.put(key, List.copyOf(target));
            }
        }
        update(ignored -> new FilterData(current.items(), current.fluids(), modes, allRules));
        return true;
    }

    public ItemStack getFilterItem(int index) {
        return getFilterData().item(index);
    }

    public Fluid getFluidSlot(int index) {
        return getFilterData().fluids().get(Integer.toString(index));
    }

    public boolean isBlacklistMode(int slot) {
        return getFilterData().blacklist().getOrDefault(Integer.toString(slot), false);
    }

    public FilterData getFilterData() {
        ItemStack stack = getFilterStack();
        FilterData current = stack.getOrDefault(SLDataComponents.FILTER_DATA.get(), FilterData.EMPTY);
        FilterData normalized = current.normalizedFor(getActiveUpgradeType());
        if (normalized != current) stack.set(SLDataComponents.FILTER_DATA.get(), normalized);
        return normalized;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }
}
