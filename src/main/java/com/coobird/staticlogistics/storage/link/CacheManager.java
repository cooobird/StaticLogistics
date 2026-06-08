package com.coobird.staticlogistics.storage.link;

import com.coobird.staticlogistics.util.LogisticsConstants;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;

/**
 * 活跃节点缓存，ticker 从这里拿当前在跑的节点列表。
 * 缓存了一份数组，只有增删节点时才重建，平时直接返回引用。
 *
 * <p>线程安全：add/remove/evict 仅在服务器主线程调用（通过 LinkManager），
 * getActiveProviderKeysArray 可能被保存线程读取，使用 volatile 保证可见性。
 * 无需 ReadWriteLock —— 主线程单写 + volatile 读即可。
 */
public class CacheManager {
    private final Long2ObjectLinkedOpenHashMap<Boolean> activeProviderCache;

    /**
     * 缓存的 key 数组，只在 add/remove/evict 时重建。
     * volatile 保证保存线程能读到最新值。
     */
    private volatile long[] cachedActiveKeys = new long[0];
    private boolean keysDirty = true;

    public CacheManager() {
        this.activeProviderCache = new Long2ObjectLinkedOpenHashMap<>(16, LogisticsConstants.Cache.getCacheLoadFactor());
    }

    /**
     * 添加 key 到缓存，超出上限时淘汰最旧的。
     * 标记数组缓存失效。
     * 仅在主线程调用。
     */
    public void add(long key) {
        activeProviderCache.putAndMoveToLast(key, true);
        evictIfNeeded();
        rebuildArrayIfNeeded();
    }

    /**
     * 移除 key，标记数组缓存失效。
     * 仅在主线程调用。
     */
    public void remove(long key) {
        activeProviderCache.remove(key);
        rebuildArrayIfNeeded();
    }

    /**
     * 返回当前活跃提供者 key 的快照副本（Set 视图），其他调用方使用。
     * ticker 高频路径请用 {@link #getActiveProviderKeysArray()}。
     */
    public LongSet getActiveProviderKeys() {
        return new LongOpenHashSet(activeProviderCache.keySet());
    }

    /**
     * 返回缓存的 {@code long[]}，只在集合变更时重建。
     * ticker 高频路径用这个，避免每 tick 分配两份拷贝。
     */
    public long[] getActiveProviderKeysArray() {
        return cachedActiveKeys;
    }

    private void rebuildArrayIfNeeded() {
        if (keysDirty) {
            cachedActiveKeys = activeProviderCache.keySet().toLongArray();
            keysDirty = false;
        }
    }

    /**
     * 快速判空，不创建快照 —— ticker 空跑时避免所有分配。
     */
    public boolean hasProviders() {
        return !activeProviderCache.isEmpty();
    }

    /**
     * 超出缓存上限时移除最久未使用的条目
     */
    private void evictIfNeeded() {
        while (activeProviderCache.size() > LogisticsConstants.Cache.getProviderCacheSize()) {
            long oldestKey = activeProviderCache.firstLongKey();
            activeProviderCache.remove(oldestKey);
            keysDirty = true;
        }
    }
}
