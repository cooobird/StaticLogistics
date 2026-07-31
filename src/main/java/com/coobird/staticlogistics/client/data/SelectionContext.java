package com.coobird.staticlogistics.client.data;

import PortLib.extensions.net.minecraft.world.item.ItemStack.PortItemStackExtension;
import com.coobird.staticlogistics.api.group.GroupKey;
import com.coobird.staticlogistics.logistics.SLDataComponents;
import com.coobird.staticlogistics.logistics.node.ConnectionKey;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * 客户端当前选中的分组与单条连接。
 *
 * <p>临时预览只影响界面和世界渲染，不会改写工具数据。工具模式和资源类型拥有各自独立的
 * 状态链，不属于此上下文。
 */
@OnlyIn(Dist.CLIENT)
public final class SelectionContext {
    private static String selectedGroupId = "";
    private static GroupKey selectedGroupKey;
    private static String previewGroupId;
    private static GroupKey previewGroupKey;
    private static ConnectionKey selectedConnectionKey;
    private static ConnectionKey previewConnectionKey;

    private SelectionContext() {
    }

    /**
     * 更新已确认的分组，不修改工具模式或资源类型。
     */
    public static void setGroupSelection(
        String groupId,
        @Nullable GroupKey groupKey
    ) {
        selectedGroupId = groupId;
        selectedGroupKey = groupKey;
        if (selectedConnectionKey != null
            && !Objects.equals(groupKey, selectedConnectionKey.groupKey())) {
            selectedConnectionKey = null;
        }
        clearPreview();
    }

    public static String getSelectedGroupId() {
        return previewGroupId == null ? selectedGroupId : previewGroupId;
    }

    @Nullable
    public static GroupKey getSelectedGroupKey() {
        return previewGroupKey == null ? selectedGroupKey : previewGroupKey;
    }

    @Nullable
    public static LinkSelectionScope getSelectionScope() {
        GroupKey groupKey = getSelectedGroupKey();
        if (groupKey == null) return null;
        ConnectionKey connectionKey =
            previewGroupKey == null ? selectedConnectionKey : previewConnectionKey;
        if (connectionKey != null
            && !groupKey.equals(connectionKey.groupKey())) {
            connectionKey = null;
        }
        return new LinkSelectionScope(groupKey, connectionKey);
    }

    @Nullable
    public static LinkSelectionScope getSelectionScope(ItemStack stack) {
        Objects.requireNonNull(stack, "Selection stack must not be null");
        if (previewGroupKey != null) return getSelectionScope();

        GroupKey groupKey = PortItemStackExtension.getData(
            stack, SLDataComponents.SELECTED_GROUP_KEY.get());
        if (groupKey == null) return null;
        ConnectionKey connectionKey = PortItemStackExtension.getData(
            stack, SLDataComponents.SELECTED_CONNECTION_KEY.get());
        if (connectionKey != null
            && !groupKey.equals(connectionKey.groupKey())) {
            connectionKey = null;
        }
        return new LinkSelectionScope(groupKey, connectionKey);
    }

    public static String getSelectedGroupId(ItemStack stack) {
        Objects.requireNonNull(stack, "Selection stack must not be null");
        return previewGroupId == null
            ? PortItemStackExtension.getDataOrDefault(
            stack, SLDataComponents.SELECTED_GROUP.get(), "")
            : previewGroupId;
    }

    @Nullable
    public static ConnectionKey getFocusedConnectionKey() {
        LinkSelectionScope scope = getSelectionScope();
        return scope == null ? null : scope.connectionKey();
    }

    public static void focusConnection(ConnectionKey connectionKey) {
        Objects.requireNonNull(connectionKey, "Focused connection must not be null");
        GroupKey groupKey = getSelectedGroupKey();
        if (groupKey == null
            || !groupKey.equals(connectionKey.groupKey())) {
            throw new IllegalArgumentException(
                "Focused connection must belong to the active group");
        }
        if (previewGroupKey == null) {
            selectedConnectionKey = connectionKey;
        } else {
            previewConnectionKey = connectionKey;
        }
    }

    public static void clearConnectionFocus() {
        if (previewGroupKey == null) {
            selectedConnectionKey = null;
        } else {
            previewConnectionKey = null;
        }
    }

    public static void syncFromItem(ItemStack stack) {
        selectedGroupId = PortItemStackExtension.getDataOrDefault(
            stack, SLDataComponents.SELECTED_GROUP.get(), "");
        selectedGroupKey = PortItemStackExtension.getData(
            stack, SLDataComponents.SELECTED_GROUP_KEY.get());
        ConnectionKey storedConnection = PortItemStackExtension.getData(
            stack, SLDataComponents.SELECTED_CONNECTION_KEY.get());
        if (storedConnection != null
            && !Objects.equals(selectedGroupKey, storedConnection.groupKey())) {
            storedConnection = null;
        }
        selectedConnectionKey = storedConnection;
    }

    public static void preview(String groupId, GroupKey groupKey) {
        if (!Objects.equals(previewGroupKey, groupKey)) {
            previewConnectionKey = null;
        }
        previewGroupId = groupId;
        previewGroupKey = groupKey;
    }

    public static void clearPreview() {
        previewGroupId = null;
        previewGroupKey = null;
        previewConnectionKey = null;
    }

    public static void clear() {
        selectedGroupId = "";
        selectedGroupKey = null;
        selectedConnectionKey = null;
        clearPreview();
    }
}
