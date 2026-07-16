package com.coobird.staticlogistics.content.item;

import com.coobird.staticlogistics.logistics.SLDataComponents;
import com.coobird.staticlogistics.api.group.GroupKey;
import com.coobird.staticlogistics.api.group.GroupRef;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** 服务端修改玩家手持链接配置器分组选择的统一入口。 */
public final class LinkConfiguratorSelection {
    private LinkConfiguratorSelection() {
    }

    public static boolean select(Player player, GroupRef group) {
        ItemStack stack = findHeldTool(player);
        if (stack == null || group == null) return false;
        stack.set(SLDataComponents.SELECTED_GROUP.get(), group.displayName());
        stack.set(SLDataComponents.SELECTED_GROUP_KEY.get(), group.key());
        return true;
    }

    /** 重命名或合并成功后，把来源选择切换到服务端最终保留的分组身份。 */
    public static void replaceIfSelected(Player player, GroupKey sourceKey,
                                         String oldName, GroupRef result) {
        ItemStack stack = findHeldTool(player);
        if (stack == null || sourceKey == null || result == null) return;
        GroupKey selectedKey = stack.get(SLDataComponents.SELECTED_GROUP_KEY.get());
        String selectedName = stack.getOrDefault(SLDataComponents.SELECTED_GROUP.get(), "");
        if (sourceKey.equals(selectedKey) || selectedKey == null && oldName.equals(selectedName)) {
            stack.set(SLDataComponents.SELECTED_GROUP.get(), result.displayName());
            stack.set(SLDataComponents.SELECTED_GROUP_KEY.get(), result.key());
        }
    }

    public static void clearIfSelected(Player player, GroupKey key, String displayName) {
        ItemStack stack = findHeldTool(player);
        if (stack == null) return;
        GroupKey selectedKey = stack.get(SLDataComponents.SELECTED_GROUP_KEY.get());
        String selectedName = stack.getOrDefault(SLDataComponents.SELECTED_GROUP.get(), "");
        if (key.equals(selectedKey) || selectedKey == null && displayName.equals(selectedName)) {
            stack.set(SLDataComponents.SELECTED_GROUP.get(), "");
            stack.remove(SLDataComponents.SELECTED_GROUP_KEY.get());
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
