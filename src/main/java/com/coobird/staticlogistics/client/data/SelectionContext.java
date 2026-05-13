package com.coobird.staticlogistics.client.data;

import com.coobird.staticlogistics.registry.SLDataComponents;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class SelectionContext {
    private static String selectedGroupId = "";
    private static int selectedMode = 0;

    public static void setSelection(String groupId, int mode) {
        selectedGroupId = groupId;
        selectedMode = mode;
    }

    public static String getSelectedGroupId() {
        return selectedGroupId;
    }

    public static int getSelectedMode() {
        return selectedMode;
    }

    public static void syncFromItem(ItemStack stack) {
        if (selectedGroupId.isEmpty()) {
            selectedGroupId = stack.getOrDefault(SLDataComponents.SELECTED_GROUP.get(), "");
            selectedMode = stack.getOrDefault(SLDataComponents.TOOL_MODE.get(), 0);
        }
    }

    public static void clear() {
        selectedGroupId = "";
        selectedMode = 0;
    }
}