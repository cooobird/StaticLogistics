package com.coobird.staticlogistics.logistics.node;

import com.coobird.staticlogistics.logistics.util.LogisticsConstants;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 活跃提供者索引，ticker 从这里取得当前需要调度的全部节点。
 * 索引本身是权威运行状态，不能按照缓存容量淘汰；仅数组快照属于派生缓存。
 *
 * <p>线程安全：add/remove 仅在服务器主线程调用（通过 LinkManager），
 * getActiveProviderKeysArray 可能被保存线程读取，使用 volatile 保证可见性。
 * 无需 ReadWriteLock —— 主线程单写 + volatile 读即可。
 */
public class CacheManager {
    private final Set<FaceAddress> activeProviders;

    /**
     * 缓存的 key 数组，只在 add/remove 时重建。
     * volatile 保证保存线程能读到最新值。
     */
    private volatile FaceAddress[] cachedActiveKeys = new FaceAddress[0];
    private boolean keysDirty = true;

    public CacheManager() {
        this.activeProviders = new LinkedHashSet<>(LogisticsConstants.Cache.getExpectedProviderCount());
    }

    /**
     * 添加 key 到权威索引并刷新数组快照。
     * 仅在主线程调用。
     */
    public void add(FaceAddress key) {
        if (activeProviders.add(key)) {
            keysDirty = true;
            rebuildArrayIfNeeded();
        }
    }

    /**
     * 移除 key，标记数组缓存失效。
     * 仅在主线程调用。
     */
    public void remove(FaceAddress key) {
        if (activeProviders.remove(key)) {
            keysDirty = true;
            rebuildArrayIfNeeded();
        }
    }

    /**
     * 返回当前活跃提供者 key 的快照副本（Set 视图），其他调用方使用。
     * ticker 高频路径请用 {@link #getActiveProviderKeysArray()}。
     */
    public Set<FaceAddress> getActiveProviderKeys() {
        return Set.copyOf(activeProviders);
    }

    /**
     * 返回缓存的 {@code long[]}，只在集合变更时重建。
     * ticker 高频路径用这个，避免每 tick 分配两份拷贝。
     */
    public FaceAddress[] getActiveProviderKeysArray() {
        return cachedActiveKeys;
    }

    private void rebuildArrayIfNeeded() {
        if (keysDirty) {
            cachedActiveKeys = activeProviders.toArray(FaceAddress[]::new);
            keysDirty = false;
        }
    }

    /**
     * 快速判空，不创建快照 —— ticker 空跑时避免所有分配。
     */
    public boolean hasProviders() {
        return !activeProviders.isEmpty();
    }
}

