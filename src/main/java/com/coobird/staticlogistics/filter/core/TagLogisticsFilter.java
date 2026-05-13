package com.coobird.staticlogistics.filter.core;

import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.Set;

public class TagLogisticsFilter extends AbstractLogisticsFilter {
    private final Set<TagKey<Item>> itemTags;
    private final Set<TagKey<Item>> excludedItemTags;
    private final Set<TagKey<Fluid>> fluidTags;
    private final Set<TagKey<Fluid>> excludedFluidTags;

    /**
     * 构造一个基于标签的物流过滤器
     *
     * @param itemTags          允许的物品标签集合，当物品包含其中任一标签时通过测试
     * @param excludedItemTags  排除的物品标签集合，当物品包含其中任一标签时直接拒绝
     * @param fluidTags         允许的流体标签集合，当流体包含其中任一标签时通过测试
     * @param excludedFluidTags 排除的流体标签集合，当流体包含其中任一标签时直接拒绝
     * @param hasUpgrade        指示过滤器是否已升级（影响 {@link #isActive()} 的返回值）
     */
    public TagLogisticsFilter(Set<TagKey<Item>> itemTags, Set<TagKey<Item>> excludedItemTags,
                              Set<TagKey<Fluid>> fluidTags, Set<TagKey<Fluid>> excludedFluidTags,
                              boolean hasUpgrade) {
        super(hasUpgrade);
        // 初始化物品相关的允许与排除标签集合
        this.itemTags = itemTags;
        this.excludedItemTags = excludedItemTags;
        // 初始化流体相关的允许与排除标签集合
        this.fluidTags = fluidTags;
        this.excludedFluidTags = excludedFluidTags;
    }

    @Override
    protected boolean testItem(ItemStack stack) {
        for (TagKey<Item> tag : excludedItemTags) {
            if (stack.is(tag)) {
                return false;
            }
        }
        for (TagKey<Item> tag : itemTags) {
            if (stack.is(tag)) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected boolean testFluid(FluidStack stack) {
        for (TagKey<Fluid> tag : excludedFluidTags) {
            if (stack.is(tag)) {
                return false;
            }
        }
        for (TagKey<Fluid> tag : fluidTags) {
            if (stack.is(tag)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean isActive() {
        return hasUpgrade && (!itemTags.isEmpty() || !fluidTags.isEmpty());
    }
}