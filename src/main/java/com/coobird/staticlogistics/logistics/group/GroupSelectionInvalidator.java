package com.coobird.staticlogistics.logistics.group;

import com.coobird.staticlogistics.api.group.GroupKey;
import com.coobird.staticlogistics.logistics.SLDataComponents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * 在分组生命周期结束时清理在线玩家物品中指向该分组的持久化选择。
 */
public final class GroupSelectionInvalidator {
    private GroupSelectionInvalidator() {
    }

    static void clearOnlineSelections(MinecraftServer server, GroupKey removedGroup) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
                clearSelection(player.getInventory().getItem(slot), removedGroup);
            }
            clearSelection(player.containerMenu.getCarried(), removedGroup);
        }
    }

    public static void clearInvalidSelection(MinecraftServer server, ItemStack stack) {
        GroupKey selectedGroup = stack.get(SLDataComponents.SELECTED_GROUP_KEY.get());
        if (selectedGroup != null
            && PlayerGroupStore.get(server).findGroup(selectedGroup) == null) {
            clearSelection(stack, selectedGroup);
        }
    }

    private static void clearSelection(ItemStack stack, GroupKey removedGroup) {
        if (stack.isEmpty()
            || !removedGroup.equals(stack.get(SLDataComponents.SELECTED_GROUP_KEY.get()))) {
            return;
        }
        stack.set(SLDataComponents.SELECTED_GROUP.get(), "");
        stack.remove(SLDataComponents.SELECTED_GROUP_KEY.get());
        stack.remove(SLDataComponents.SELECTED_CONNECTION_KEY.get());
    }
}
