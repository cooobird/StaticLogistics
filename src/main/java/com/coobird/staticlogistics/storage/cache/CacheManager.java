package com.coobird.staticlogistics.storage.cache;

import com.coobird.staticlogistics.util.LogisticsConstants;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;

import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * 活跃节点缓存，ticker 从这里拿当前在跑的节点列表。
 * 缓存了一份数组，只有增删节点时才重建，平时直接返回引用。
 */
public class CacheManager {
    private final Long2ObjectLinkedOpenHashMap<Boolean> activeProviderCache;
    private final ReadWriteLock rwLock = new ReentrantReadWriteLock();
    private final java.util.concurrent.locks.Lock readLock = rwLock.readLock();
    private final java.util.concurrent.locks.Lock writeLock = rwLock.writeLock();

    /**
     * 缓存的 key 数组，只在 add/remove/evict 时重建
     */
    private volatile long[] cachedActiveKeys;
    private volatile boolean keysDirty = true;

    public CacheManager() {
        this.activeProviderCache = new Long2ObjectLinkedOpenHashMap<>(16, LogisticsConstants.Cache.getCacheLoadFactor());
    }

    /**
     * 添加 key 到缓存，超出上限时淘汰最旧的。
     * 标记数组缓存失效。
     */
    public void add(long key) {
        writeLock.lock();
        try {
            activeProviderCache.putAndMoveToLast(key, true);
            evictIfNeeded();
            keysDirty = true;
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * 移除 key，标记数组缓存失效。
     */
    public void remove(long key) {
        writeLock.lock();
        try {
            activeProviderCache.remove(key);
            keysDirty = true;
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * 返回当前活跃提供者 key 的快照副本（Set 视图），其他调用方使用。
     * ticker 高频路径请用 {@link #getActiveProviderKeysArray()}。
     */
    public LongSet getActiveProviderKeys() {
        readLock.lock();
        try {
            return new LongOpenHashSet(activeProviderCache.keySet());
        } finally {
            readLock.unlock();
        }
    }

    /**
     * 返回缓存的 {@code long[]}，只在集合变更时重建。
     * ticker 高频路径用这个，避免每 tick 分配两份拷贝。
     */
    public long[] getActiveProviderKeysArray() {
        if (keysDirty) {
            rebuildArray();
        }
        return cachedActiveKeys;
    }

    private void rebuildArray() {
        writeLock.lock();
        try {
            if (keysDirty) { // 双重检查
                cachedActiveKeys = activeProviderCache.keySet().toLongArray();
                keysDirty = false;
            }
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * 快速判空，不创建快照 —— ticker 空跑时避免所有分配。
     */
    public boolean hasProviders() {
        readLock.lock();
        try {
            return !activeProviderCache.isEmpty();
        } finally {
            readLock.unlock();
        }
    }

    /**
     * 超出缓存上限时移除最久未使用的条目
     */
    private void evictIfNeeded() {
        while (activeProviderCache.size() > LogisticsConstants.Cache.getProviderCacheSize()) {
            long oldestKey = activeProviderCache.firstLongKey();
            activeProviderCache.remove(oldestKey);
        }
    }
}
