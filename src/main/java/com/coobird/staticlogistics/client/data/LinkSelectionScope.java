package com.coobird.staticlogistics.client.data;

import com.coobird.staticlogistics.api.group.GroupKey;
import com.coobird.staticlogistics.logistics.node.ConnectionKey;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * 客户端当前预览的链接范围。
 *
 * <p>分组是稳定的操作目标；连接是可选的单链接焦点。配置器会把已确认的焦点写入
 * 物品组件，临时蓝图预览则只保留在客户端会话中。
 */
public record LinkSelectionScope(
    GroupKey groupKey,
    @Nullable ConnectionKey connectionKey
) {
    public LinkSelectionScope {
        Objects.requireNonNull(groupKey, "Selection group must not be null");
        if (connectionKey != null && !groupKey.equals(connectionKey.groupKey())) {
            throw new IllegalArgumentException("Connection selection must belong to the selected group");
        }
    }

    public static LinkSelectionScope group(GroupKey groupKey) {
        return new LinkSelectionScope(groupKey, null);
    }
}
