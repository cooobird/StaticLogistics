package com.coobird.staticlogistics.network.c2s;

import com.coobird.staticlogistics.content.item.LinkConfiguratorItem;
import com.coobird.staticlogistics.content.menu.LinkConfiguratorMenu;
import com.coobird.staticlogistics.network.ServerPacketRateLimiter;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/// 统一定位正在被客户端配置的工具物品。
final class ToolSettingsTarget {
    private ToolSettingsTarget() {
    }

    static ItemStack findConfigurator(Player player) {
        if (!isAllowed(player)) return ItemStack.EMPTY;
        ItemStack menuStack = findMenuConfigurator(player);
        return menuStack.isEmpty() ? findInHands(player) : menuStack;
    }

    static ItemStack findSelectionTool(Player player) {
        if (!isAllowed(player)) return ItemStack.EMPTY;
        ItemStack menuStack = findMenuConfigurator(player);
        return menuStack.isEmpty() ? findInHands(player) : menuStack;
    }

    private static ItemStack findMenuConfigurator(Player player) {
        if (player.containerMenu instanceof LinkConfiguratorMenu menu) {
            ItemStack stack = menu.getToolStack();
            if (stack.getItem() instanceof LinkConfiguratorItem) return stack;
        }
        return ItemStack.EMPTY;
    }

    private static ItemStack findInHands(Player player) {
        ItemStack mainHand = player.getItemInHand(InteractionHand.MAIN_HAND);
        if (isSupported(mainHand)) return mainHand;
        ItemStack offHand = player.getItemInHand(InteractionHand.OFF_HAND);
        return isSupported(offHand) ? offHand : ItemStack.EMPTY;
    }

    private static boolean isSupported(ItemStack stack) {
        return stack.getItem() instanceof LinkConfiguratorItem;
    }

    private static boolean isAllowed(Player player) {
        return player instanceof ServerPlayer serverPlayer
            && ServerPacketRateLimiter.allow(serverPlayer, ServerPacketRateLimiter.Action.TOOL_SETTINGS);
    }
}
