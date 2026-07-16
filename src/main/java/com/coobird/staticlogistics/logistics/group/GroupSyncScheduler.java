package com.coobird.staticlogistics.logistics.group;

import com.coobird.staticlogistics.api.group.GroupKey;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 管理需要延迟同步的组 ID 集合，用于 tick 中统一处理。
 *
 * <p>线程安全：标记和领取均可重入；每刻只领取有界数量，剩余项保留到下一刻。
 */
public class GroupSyncScheduler {
    private final Set<GroupKey> current = new LinkedHashSet<>();

    public synchronized void markDirty(GroupKey groupKey) {
        if (groupKey != null) {
            current.add(groupKey);
        }
    }

    /** 原子领取最多 {@code maximum} 个待同步分组。 */
    public synchronized Set<GroupKey> take(int maximum) {
        if (maximum <= 0 || current.isEmpty()) return Set.of();
        Set<GroupKey> taken = new LinkedHashSet<>();
        var iterator = current.iterator();
        while (iterator.hasNext() && taken.size() < maximum) {
            taken.add(iterator.next());
            iterator.remove();
        }
        return taken;
    }

    public synchronized boolean hasPending() {
        return !current.isEmpty();
    }
}
