package com.coobird.staticlogistics.client.data;

import PortLib.extensions.net.minecraft.world.item.ItemStack.PortItemStackExtension;
import com.coobird.staticlogistics.api.group.GroupKey;
import com.coobird.staticlogistics.logistics.SLDataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;

/**
 * 客户端选择上下文 —— 追踪当前手持连接配置器选中的稳定分组身份和工具模式。
 *
 * <p>由 {@link com.coobird.staticlogistics.client.render.LinkWorldRenderer}
 * 在渲染前通过 {@link #syncFromItem} 从 ItemStack 同步到静态字段，
 * 供 GUI 和世界渲染器快速读取，避免重复解析 ItemStack。
 */
@OnlyIn(Dist.CLIENT)
public class SelectionContext {
    private static String selectedGroupId = "";
    private static GroupKey selectedGroupKey;
    private static int selectedMode = 0;

    public static void setSelection(String groupId, @Nullable GroupKey groupKey, int mode) {
        selectedGroupId = groupId;
        selectedGroupKey = groupKey;
        selectedMode = mode;
    }

    public static String getSelectedGroupId() {
        return selectedGroupId;
    }

    @Nullable
    public static GroupKey getSelectedGroupKey() {
        return selectedGroupKey;
    }

    public static int getSelectedMode() {
        return selectedMode;
    }

    public static void syncFromItem(ItemStack stack) {
        selectedGroupId = PortItemStackExtension.getDataOrDefault(
            stack, SLDataComponents.SELECTED_GROUP.get(), "");
        selectedGroupKey = PortItemStackExtension.getData(
            stack, SLDataComponents.SELECTED_GROUP_KEY.get());
        selectedMode = PortItemStackExtension.getDataOrDefault(
            stack, SLDataComponents.TOOL_MODE.get(), 0);
    }

    public static void clear() {
        selectedGroupId = "";
        selectedGroupKey = null;
        selectedMode = 0;
    }
}
