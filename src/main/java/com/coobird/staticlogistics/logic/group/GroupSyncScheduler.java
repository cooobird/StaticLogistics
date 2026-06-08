package com.coobird.staticlogistics.logic.group;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 管理需要延迟同步的组 ID 集合，用于 tick 中统一处理。
 *
 * <p>线程安全：markDirty 和 takeAll 均在服务器主线程调用。
 * 使用 volatile 引用 swap 避免竞态窗口。
 */
public class GroupSyncScheduler {
    // volatile swap：markDirty 写入 current，takeAll 取出 current 并替换为空 Set
    private volatile Set<String> current = new LinkedHashSet<>();

    public void markDirty(String groupId) {
        if (groupId != null && !groupId.isEmpty()) {
            current.add(groupId);
        }
    }

    /**
     * 原子取出所有待同步的组并替换为空集合。
     * 无竞态窗口：markDirty 写入的 groupId 不会丢失。
     */
    public Set<String> takeAll() {
        Set<String> taken = current;
        current = new LinkedHashSet<>();
        return taken;
    }

    public boolean hasPending() {
        return !current.isEmpty();
    }
}
