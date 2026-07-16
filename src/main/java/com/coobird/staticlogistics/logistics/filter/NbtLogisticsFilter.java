package com.coobird.staticlogistics.logistics.filter;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

public class NbtLogisticsFilter extends AbstractLogisticsFilter {
    private final ItemStack template;
    private final NbtMatchMode mode;
    private final boolean ignoreDamage;

    public NbtLogisticsFilter(ItemStack template, NbtMatchMode mode, boolean hasUpgrade, boolean ignoreDamage) {
        super(hasUpgrade);
        this.template = template;
        this.mode = mode;
        this.ignoreDamage = ignoreDamage;
    }

    @Override
    protected boolean testItem(ItemStack stack) {
        if (template.isEmpty()) return false;
        if (!ItemStack.isSameItem(stack, template)) return false;
        return switch (mode) {
            case PARTIAL -> matchesPartial(stack);
            case FULL -> matchesFull(stack);
        };
    }

    private boolean matchesPartial(ItemStack target) {
        CompoundTag s = template.getTag(), t = target.getTag();
        if (s == null || s.isEmpty()) return true;
        if (t == null) return false;
        for (String k : s.getAllKeys()) {
            if (ignoreDamage && k.equals("Damage")) continue;
            if (!t.contains(k) || !s.get(k).equals(t.get(k))) return false;
        }
        return true;
    }

    private boolean matchesFull(ItemStack target) {
        CompoundTag s = template.getTag(), t = target.getTag();
        if (s == null || s.isEmpty()) return t == null || t.isEmpty();
        if (t == null) return false;
        for (String k : s.getAllKeys()) {
            if (ignoreDamage && k.equals("Damage")) continue;
            if (!t.contains(k) || !s.get(k).equals(t.get(k))) return false;
        }
        for (String k : t.getAllKeys()) {
            if (ignoreDamage && k.equals("Damage")) continue;
            if (!s.contains(k)) return false;
        }
        return true;
    }

    @Override
    public boolean isActive() {
        return hasUpgrade && !template.isEmpty();
    }
}
