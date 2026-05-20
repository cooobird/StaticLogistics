package com.coobird.staticlogistics.storage.cache;

import com.coobird.staticlogistics.util.LogisticsConstants;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;

import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * 活跃提供者缓存管理器 —— 基于 LRU（最近最少使用）的线程安全缓存。
 * 只缓存有分组且角色为发送方的节点，ticker 从这里快速查找活跃提供者。
 */
public class CacheManager {
    private final Long2ObjectLinkedOpenHashMap<Boolean> activeProviderCache;
    private final ReadWriteLock rwLock = new ReentrantReadWriteLock();
    private final java.util.concurrent.locks.Lock readLock = rwLock.readLock();
    private final java.util.concurrent.locks.Lock writeLock = rwLock.writeLock();

    public CacheManager() {
        this.activeProviderCache = new Long2ObjectLinkedOpenHashMap<>(16, LogisticsConstants.Cache.getCacheLoadFactor());
    }

    /**
     * 添加 key 到缓存（移到最后），超出上限时淘汰最旧的
     */
    public void add(long key) {
        writeLock.lock();
        try {
            activeProviderCache.putAndMoveToLast(key, true);
            evictIfNeeded();
        } finally {
            writeLock.unlock();
        }
    }

    public void remove(long key) {
        writeLock.lock();
        try {
            activeProviderCache.remove(key);
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * 返回当前活跃提供者 key 的快照副本，防止遍历时并发写入导致异常
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
     * 快速判空，不创建快照 — ticker 空跑时避免内存分配
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
