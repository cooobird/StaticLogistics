package com.coobird.staticlogistics.filter.core;

import com.coobird.staticlogistics.api.filter.ILogisticsFilter;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

public abstract class AbstractLogisticsFilter implements ILogisticsFilter {
    protected final boolean hasUpgrade;

    protected AbstractLogisticsFilter(boolean hasUpgrade) {
        this.hasUpgrade = hasUpgrade;
    }

    @Override
    public boolean test(ItemStack stack, boolean isBlacklist) {
        if (!isActive() || stack.isEmpty()) return true;
        boolean matches = testItem(stack);
        return isBlacklist != matches;
    }

    @Override
    public boolean test(FluidStack stack, boolean isBlacklist) {
        if (!isActive() || stack.isEmpty()) return true;
        boolean matches = testFluid(stack);
        return isBlacklist != matches;
    }

    protected abstract boolean testItem(ItemStack stack);

    protected boolean testFluid(FluidStack stack) {
        return false;
    }

    @Override
    public abstract boolean isActive();
}