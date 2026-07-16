package com.coobird.staticlogistics.logistics.filter;

import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.registries.ForgeRegistries;

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
     * @param excludedItemTags  排除的物品标签集合，当物品包含其中任一标签时被排除
     * @param fluidTags         允许的流体标签集合
     * @param excludedFluidTags 排除的流体标签集合
     * @param hasUpgrade        是否拥有升级
     */
    public TagLogisticsFilter(Set<TagKey<Item>> itemTags, Set<TagKey<Item>> excludedItemTags,
                              Set<TagKey<Fluid>> fluidTags, Set<TagKey<Fluid>> excludedFluidTags,
                              boolean hasUpgrade) {
        super(hasUpgrade);
        this.itemTags = itemTags;
        this.excludedItemTags = excludedItemTags;
        this.fluidTags = fluidTags;
        this.excludedFluidTags = excludedFluidTags;
    }

    @Override
    protected boolean testItem(net.minecraft.world.item.ItemStack stack) {
        for (TagKey<Item> tag : excludedItemTags) {
            if (stack.is(tag)) return false;
        }
        if (itemTags.isEmpty()) return true;
        for (TagKey<Item> tag : itemTags) {
            if (stack.is(tag)) return true;
        }
        return false;
    }

    protected boolean testFluid(FluidStack stack) {
        for (TagKey<Fluid> tag : excludedFluidTags) {
            if (isFluidTag(stack.getFluid(), tag)) return false;
        }
        if (fluidTags.isEmpty()) return true;
        for (TagKey<Fluid> tag : fluidTags) {
            if (isFluidTag(stack.getFluid(), tag)) return true;
        }
        return false;
    }

    private static boolean isFluidTag(Fluid fluid, TagKey<Fluid> tag) {
        return ForgeRegistries.FLUIDS.getHolder(fluid)
            .map(holder -> holder.is(tag))
            .orElse(false);
    }

    @Override
    public boolean isActive() {
        return hasUpgrade && (!itemTags.isEmpty() || !fluidTags.isEmpty());
    }
}
