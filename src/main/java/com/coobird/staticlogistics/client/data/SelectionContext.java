package com.coobird.staticlogistics.client.data;

import com.coobird.staticlogistics.api.group.GroupKey;
import com.coobird.staticlogistics.content.item.ToolMode;
import com.coobird.staticlogistics.logistics.SLDataComponents;
import com.coobird.staticlogistics.logistics.node.ConnectionKey;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * 客户端选择上下文，追踪当前手持连接配置器选中的稳定分组、连接和工具模式。
 *
 * <p>界面交互负责同步并修改该上下文；世界渲染只通过接收 {@link ItemStack}
 * 的只读方法解析有效选择，不得反向修改交互状态。
 */
@OnlyIn(Dist.CLIENT)
public final class SelectionContext {
    private static String selectedGroupId = "";
    private static GroupKey selectedGroupKey;
    private static int selectedMode = 0;
    private static String previewGroupId;
    private static GroupKey previewGroupKey;
    private static ConnectionKey selectedConnectionKey;
    private static ConnectionKey previewConnectionKey;

    private SelectionContext() {
    }

    public static void setSelection(String groupId, @Nullable GroupKey groupKey, int mode) {
        setSelection(groupId, groupKey, mode, null);
    }

    /**
     * 更新物品已确认的选择，并结束临时预览。
     */
    public static void setSelection(
        String groupId, @Nullable GroupKey groupKey, int mode, @Nullable ConnectionKey connectionKey) {
        if (connectionKey != null
            && !Objects.equals(groupKey, connectionKey.groupKey())) {
            throw new IllegalArgumentException(
                "Selected connection must belong to the selected group");
        }
        selectedGroupId = groupId;
        selectedGroupKey = groupKey;
        selectedMode = mode;
        selectedConnectionKey = connectionKey;
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
     * 返回世界和界面共同消费的有效预览范围。
     */
    @Nullable
    public static LinkSelectionScope getSelectionScope() {
        GroupKey groupKey = getSelectedGroupKey();
        if (groupKey == null) return null;
        ConnectionKey connectionKey = previewGroupKey == null
            ? selectedConnectionKey
            : previewConnectionKey;
        if (connectionKey != null
            && !groupKey.equals(connectionKey.groupKey())) {
            connectionKey = null;
        }
        return new LinkSelectionScope(groupKey, connectionKey);
    }

    /**
     * 为世界渲染解析只读选择。
     *
     * <p>临时蓝图预览优先于物品中的持久选择；除此之外直接读取物品组件，
     * 不得借由渲染循环改写客户端交互状态。
     */
    @Nullable
    public static LinkSelectionScope getSelectionScope(ItemStack stack) {
        Objects.requireNonNull(stack, "Selection stack must not be null");
        if (previewGroupKey != null) {
            return getSelectionScope();
        }
        GroupKey groupKey = stack.get(SLDataComponents.SELECTED_GROUP_KEY.get());
        if (groupKey == null) return null;
        ConnectionKey connectionKey = stack.get(SLDataComponents.SELECTED_CONNECTION_KEY.get());
        if (connectionKey != null
            && !groupKey.equals(connectionKey.groupKey())) {
            connectionKey = null;
        }
        return new LinkSelectionScope(groupKey, connectionKey);
    }

    /**
     * 返回世界渲染应使用的只读分组显示名。
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
     * 把当前预览缩小到一条连接，不改变持久分组选择。
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
     * 清除单连接焦点并恢复整组预览。
     */
    public static void clearConnectionFocus() {
        if (previewGroupKey == null) {
            selectedConnectionKey = null;
        } else {
            previewConnectionKey = null;
        }
    }

    public static int getSelectedMode() {
        return selectedMode;
    }

    public static void syncFromItem(ItemStack stack) {
        selectedGroupId = stack.getOrDefault(SLDataComponents.SELECTED_GROUP.get(), "");
        selectedGroupKey = stack.get(SLDataComponents.SELECTED_GROUP_KEY.get());
        selectedMode = ToolMode.fromId(
            stack.getOrDefault(SLDataComponents.TOOL_MODE.get(), 0)).getId();
        ConnectionKey storedConnection = stack.get(SLDataComponents.SELECTED_CONNECTION_KEY.get());
        if (storedConnection != null
            && !Objects.equals(selectedGroupKey, storedConnection.groupKey())) {
            storedConnection = null;
        }
        selectedConnectionKey = storedConnection;
    }

    /**
     * 临时覆盖世界渲染使用的分组，但不修改物品数据，也不会向服务端提交。
     */
    public static void preview(String groupId, GroupKey groupKey) {
        if (!Objects.equals(previewGroupKey, groupKey)) {
            previewConnectionKey = null;
        }
        previewGroupId = groupId;
        previewGroupKey = groupKey;
    }

    /**
     * 结束临时预览，恢复使用物品中已经确认的分组。
     */
    public static void clearPreview() {
        previewGroupId = null;
        previewGroupKey = null;
        previewConnectionKey = null;
    }

    public static void clear() {
        selectedGroupId = "";
        selectedGroupKey = null;
        selectedMode = 0;
        selectedConnectionKey = null;
        clearPreview();
    }
}
