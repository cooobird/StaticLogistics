package com.coobird.staticlogistics.logistics.node;

import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.api.group.GroupConstraints;
import com.coobird.staticlogistics.api.group.GroupKey;

/// 分组作用域内的一条有向拓扑边，并携带该无向连接的可选显示名。
public record ScopedTopologyLink(
    GroupKey groupKey,
    LogisticsNode source,
    LogisticsNode target,
    String displayName
) {
    public ScopedTopologyLink {
        displayName = GroupConstraints.normalizeConnectionName(displayName);
    }
}
