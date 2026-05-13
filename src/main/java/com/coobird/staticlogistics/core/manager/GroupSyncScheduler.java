package com.coobird.staticlogistics.core.manager;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 管理需要延迟同步的组 ID 集合，用于 tick 中统一处理。
 */
public class GroupSyncScheduler {
    private final Set<String> pendingSync = ConcurrentHashMap.newKeySet();

    public void markDirty(String groupId) {
        if (groupId != null && !groupId.isEmpty()) {
            pendingSync.add(groupId);
        }
    }

    /**
     * 取出所有待同步的组，并清空内部集合。
     *
     * @return 待同步组集合（可能为空）
     */
    public Set<String> takeAll() {
        Set<String> result = Collections.newSetFromMap(new ConcurrentHashMap<>());
        result.addAll(pendingSync);
        pendingSync.clear();
        return result;
    }

    /**
     * 检查是否有待同步的组。
     */
    public boolean hasPending() {
        return !pendingSync.isEmpty();
    }
}