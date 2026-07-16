package com.coobird.staticlogistics.logistics.filter;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

/** 统一三类过滤器的空资源放行、激活判断和黑白名单反转语义。 */
public abstract class AbstractLogisticsFilter {
    protected final boolean hasUpgrade;

    protected AbstractLogisticsFilter(boolean hasUpgrade) {
        this.hasUpgrade = hasUpgrade;
    }

    public boolean test(ItemStack stack, boolean isBlacklist) {
        if (!isActive() || stack.isEmpty()) return true;
        boolean matches = testItem(stack);
        return isBlacklist != matches;
    }

    public boolean test(FluidStack stack, boolean isBlacklist) {
        if (!isActive() || stack.isEmpty()) return true;
        boolean matches = testFluid(stack);
        return isBlacklist != matches;
    }

    protected abstract boolean testItem(ItemStack stack);

    protected boolean testFluid(FluidStack stack) {
        return true;
    }

    public abstract boolean isActive();
}
