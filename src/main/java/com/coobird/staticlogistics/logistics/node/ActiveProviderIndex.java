package com.coobird.staticlogistics.logistics.node;

import com.coobird.staticlogistics.logistics.util.LogisticsConstants;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/// 主线程维护的活跃提供者索引；批量变更只使快照失效，调度器读取时统一重建。
final class ActiveProviderIndex {
    private final Set<EndpointAddress> providers = new LinkedHashSet<>(LogisticsConstants.Cache.getExpectedProviderCount());
    private List<EndpointAddress> snapshot = List.of();
    private boolean dirty;

    void update(EndpointAddress key, EndpointConfig config) {
        if (config.faceConfig.hasGroup() && config.determineRole().canSend()
            && config.linkConfig.isEnabled()) {
            dirty |= providers.add(key);
        } else {
            remove(key);
        }
    }

    void remove(EndpointAddress key) {
        dirty |= providers.remove(key);
    }

    /// 索引变化后发布新的不可变快照，不影响正在进行的扫描。
    List<EndpointAddress> snapshot() {
        if (dirty) {
            snapshot = List.copyOf(providers);
            dirty = false;
        }
        return snapshot;
    }
}
