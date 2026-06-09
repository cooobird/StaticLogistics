package com.coobird.staticlogistics.item.handler;

import com.coobird.staticlogistics.item.LinkConfiguratorItem;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;

public interface ModeHandler {
    InteractionResult handle(LinkConfiguratorItem item, UseOnContext context, ItemStack stack, LinkConfiguratorItem.ToolSettings settings);
}
