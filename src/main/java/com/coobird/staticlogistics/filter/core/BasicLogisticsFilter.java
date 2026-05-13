package com.coobird.staticlogistics.filter.core;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.Set;

public class BasicLogisticsFilter extends AbstractLogisticsFilter {
    private final Set<Item> items;
    private final Set<Fluid> fluids;

    public BasicLogisticsFilter(Set<Item> items, Set<Fluid> fluids, boolean hasUpgrade) {
        super(hasUpgrade);
        this.items = items;
        this.fluids = fluids;
    }

    @Override
    protected boolean testItem(ItemStack stack) {
        return items.contains(stack.getItem());
    }

    @Override
    protected boolean testFluid(FluidStack stack) {
        return fluids.contains(stack.getFluid());
    }

    @Override
    public boolean isActive() {
        return hasUpgrade && (!items.isEmpty() || !fluids.isEmpty());
    }
}