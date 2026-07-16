package com.coobird.staticlogistics.content.item;

import PortLib.extensions.net.minecraft.world.item.ItemStack.PortItemStackExtension;
import com.coobird.staticlogistics.api.group.GroupKey;
import com.coobird.staticlogistics.api.group.GroupRef;
import com.coobird.staticlogistics.logistics.SLDataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * 服务端修改玩家手持链接配置器分组选择的统一入口。
 */
public final class LinkConfiguratorSelection {
    private LinkConfiguratorSelection() {
    }

    public static boolean select(Player player, GroupRef group) {
        ItemStack stack = findHeldTool(player);
        if (stack == null || group == null) return false;
        PortItemStackExtension.setData(
            stack, SLDataComponents.SELECTED_GROUP.get(), group.displayName());
        PortItemStackExtension.setData(
            stack, SLDataComponents.SELECTED_GROUP_KEY.get(), group.key());
        return true;
    }

    /**
     * 重命名或合并后切换到服务端最终保留的稳定分组身份。
     */
    public static void replaceIfSelected(Player player, GroupKey sourceKey,
                                         String oldName, GroupRef result) {
        ItemStack stack = findHeldTool(player);
        if (stack == null || sourceKey == null || result == null) return;
        GroupKey selectedKey = PortItemStackExtension.getData(
            stack, SLDataComponents.SELECTED_GROUP_KEY.get());
        String selectedName = PortItemStackExtension.getDataOrDefault(
            stack, SLDataComponents.SELECTED_GROUP.get(), "");
        if (sourceKey.equals(selectedKey) || selectedKey == null && oldName.equals(selectedName)) {
            PortItemStackExtension.setData(
                stack, SLDataComponents.SELECTED_GROUP.get(), result.displayName());
            PortItemStackExtension.setData(
                stack, SLDataComponents.SELECTED_GROUP_KEY.get(), result.key());
        }
    }

    public static void clearIfSelected(Player player, GroupKey key, String displayName) {
        ItemStack stack = findHeldTool(player);
        if (stack == null) return;
        GroupKey selectedKey = PortItemStackExtension.getData(
            stack, SLDataComponents.SELECTED_GROUP_KEY.get());
        String selectedName = PortItemStackExtension.getDataOrDefault(
            stack, SLDataComponents.SELECTED_GROUP.get(), "");
        if (key.equals(selectedKey) || selectedKey == null && displayName.equals(selectedName)) {
            PortItemStackExtension.setData(stack, SLDataComponents.SELECTED_GROUP.get(), "");
            PortItemStackExtension.removeData(stack, SLDataComponents.SELECTED_GROUP_KEY.get());
        }
    }

    private static ItemStack findHeldTool(Player player) {
        if (player == null) return null;
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = player.getItemInHand(hand);
            if (stack.getItem() instanceof LinkConfiguratorItem) return stack;
        }
        return null;
    }
}

