package com.coobird.staticlogistics.logistics.node;

import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.api.group.GroupConstraints;
import com.coobird.staticlogistics.api.group.GroupKey;

import java.util.Objects;

/**
 * 分组作用域内的一条有向拓扑边，并携带该无向连接的可选显示名。
 */
public record ScopedTopologyLink(
    GroupKey groupKey,
    LogisticsNode source,
    LogisticsNode target,
    String displayName
) {
    public ScopedTopologyLink {
        Objects.requireNonNull(groupKey, "Topology link group must not be null");
        Objects.requireNonNull(source, "Topology link source must not be null");
        Objects.requireNonNull(target, "Topology link target must not be null");
        displayName = GroupConstraints.normalizeConnectionName(displayName);
    }
}
