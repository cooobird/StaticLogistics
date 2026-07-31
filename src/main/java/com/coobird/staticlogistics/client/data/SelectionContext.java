package com.coobird.staticlogistics.client.data;

import com.coobird.staticlogistics.api.group.GroupKey;
import com.coobird.staticlogistics.logistics.SLDataComponents;
import com.coobird.staticlogistics.logistics.node.ConnectionKey;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
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

    /**
     * 返回界面和世界渲染共同使用的有效预览范围。
     */
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

    /**
     * 从工具物品解析世界渲染使用的只读选择。
     */
    @Nullable
    public static LinkSelectionScope getSelectionScope(ItemStack stack) {
        Objects.requireNonNull(stack, "Selection stack must not be null");
        if (previewGroupKey != null) return getSelectionScope();

        GroupKey groupKey = stack.get(SLDataComponents.SELECTED_GROUP_KEY.get());
        if (groupKey == null) return null;
        ConnectionKey connectionKey =
            stack.get(SLDataComponents.SELECTED_CONNECTION_KEY.get());
        if (connectionKey != null
            && !groupKey.equals(connectionKey.groupKey())) {
            connectionKey = null;
        }
        return new LinkSelectionScope(groupKey, connectionKey);
    }

    /**
     * 返回世界渲染使用的只读分组显示名。
     */
    public static String getSelectedGroupId(ItemStack stack) {
        Objects.requireNonNull(stack, "Selection stack must not be null");
        return previewGroupId == null
            ? stack.getOrDefault(SLDataComponents.SELECTED_GROUP.get(), "")
            : previewGroupId;
    }

    @Nullable
    public static ConnectionKey getFocusedConnectionKey() {
        LinkSelectionScope scope = getSelectionScope();
        return scope == null ? null : scope.connectionKey();
    }

    /**
     * 将当前预览缩小到一条连接，不改变已选分组。
     */
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

    /**
     * 清除单条连接焦点，恢复整个分组预览。
     */
    public static void clearConnectionFocus() {
        if (previewGroupKey == null) {
            selectedConnectionKey = null;
        } else {
            previewConnectionKey = null;
        }
    }

    /**
     * 从工具物品同步已确认的分组和单条连接。
     */
    public static void syncFromItem(ItemStack stack) {
        selectedGroupId =
            stack.getOrDefault(SLDataComponents.SELECTED_GROUP.get(), "");
        selectedGroupKey = stack.get(SLDataComponents.SELECTED_GROUP_KEY.get());
        ConnectionKey storedConnection =
            stack.get(SLDataComponents.SELECTED_CONNECTION_KEY.get());
        if (storedConnection != null
            && !Objects.equals(selectedGroupKey, storedConnection.groupKey())) {
            storedConnection = null;
        }
        selectedConnectionKey = storedConnection;
    }

    /**
     * 临时切换世界预览分组，不修改工具数据。
     */
    public static void preview(String groupId, GroupKey groupKey) {
        if (!Objects.equals(previewGroupKey, groupKey)) {
            previewConnectionKey = null;
        }
        previewGroupId = groupId;
        previewGroupKey = groupKey;
    }

    /**
     * 结束临时预览，恢复工具中已确认的选择。
     */
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
