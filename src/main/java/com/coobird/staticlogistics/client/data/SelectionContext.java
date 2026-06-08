package com.coobird.staticlogistics.client.data;

import com.coobird.staticlogistics.registry.SLDataComponents;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * 客户端选择上下文 —— 追踪当前手持连接配置器选中的组 ID 和工具模式。
 *
 * <p>由 {@link com.coobird.staticlogistics.client.render.LinkWorldRenderer}
 * 在渲染前通过 {@link #syncFromItem} 从 ItemStack 同步到静态字段，
 * 供 GUI 和世界渲染器快速读取，避免重复解析 ItemStack。
 */
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
        selectedGroupId = stack.getOrDefault(SLDataComponents.SELECTED_GROUP.get(), "");
        selectedMode = stack.getOrDefault(SLDataComponents.TOOL_MODE.get(), 0);
    }

    public static void clear() {
        selectedGroupId = "";
        selectedMode = 0;
    }
}