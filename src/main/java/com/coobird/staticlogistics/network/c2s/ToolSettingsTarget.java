package com.coobird.staticlogistics.network.c2s;

import com.coobird.staticlogistics.content.item.BlueprintItem;
import com.coobird.staticlogistics.content.item.LinkConfiguratorItem;
import com.coobird.staticlogistics.content.menu.LinkConfiguratorMenu;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * 统一定位正在被客户端配置的工具物品。
 */
final class ToolSettingsTarget {
    private ToolSettingsTarget() {
    }

    static ItemStack findConfigurator(Player player) {
        ItemStack menuStack = findMenuConfigurator(player);
        return menuStack.isEmpty() ? findInHands(player, false) : menuStack;
    }

    static ItemStack findSelectionTool(Player player) {
        ItemStack menuStack = findMenuConfigurator(player);
        return menuStack.isEmpty() ? findInHands(player, true) : menuStack;
    }

    private static ItemStack findMenuConfigurator(Player player) {
        if (player.containerMenu instanceof LinkConfiguratorMenu menu) {
            ItemStack stack = menu.getToolStack();
            if (stack.getItem() instanceof LinkConfiguratorItem) return stack;
        }
        return ItemStack.EMPTY;
    }

    private static ItemStack findInHands(Player player, boolean allowBlueprint) {
        ItemStack mainHand = player.getItemInHand(InteractionHand.MAIN_HAND);
        if (isSupported(mainHand, allowBlueprint)) return mainHand;
        ItemStack offHand = player.getItemInHand(InteractionHand.OFF_HAND);
        return isSupported(offHand, allowBlueprint) ? offHand : ItemStack.EMPTY;
    }

    private static boolean isSupported(ItemStack stack, boolean allowBlueprint) {
        return stack.getItem() instanceof LinkConfiguratorItem
            || allowBlueprint && stack.getItem() instanceof BlueprintItem;
    }
}
