package com.coobird.staticlogistics.api.filter;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

public interface ILogisticsFilter {
    /**
     * 执行过滤测试
     *
     * @param stack       要测试的物品堆
     * @param isBlacklist 是否为黑名单模式
     * @return 如果通过过滤则返回 true
     */
    boolean test(ItemStack stack, boolean isBlacklist);

    /**
     * 执行过滤测试
     *
     * @param stack       要测试的流体堆
     * @param isBlacklist 是否为黑名单模式
     * @return 如果通过过滤则返回 true
     */
    boolean test(FluidStack stack, boolean isBlacklist);

    /**
     * 该过滤器是否处于激活状态（例如是否安装了对应的升级，或者名单是否为空）
     */
    boolean isActive();
}
