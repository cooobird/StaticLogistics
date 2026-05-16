package com.coobird.staticlogistics.storage.cache;

import com.coobird.staticlogistics.util.LogisticsConstants;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongSet;

import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class CacheManager {
    private final Long2ObjectLinkedOpenHashMap<Boolean> activeProviderCache;
    private final ReadWriteLock rwLock = new ReentrantReadWriteLock();
    private final java.util.concurrent.locks.Lock readLock = rwLock.readLock();
    private final java.util.concurrent.locks.Lock writeLock = rwLock.writeLock();

    public CacheManager() {
        this.activeProviderCache = new Long2ObjectLinkedOpenHashMap<>(16, LogisticsConstants.Cache.getCacheLoadFactor());
    }

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

    public boolean contains(long key) {
        readLock.lock();
        try {
            return activeProviderCache.containsKey(key);
        } finally {
            readLock.unlock();
        }
    }

    public LongSet getActiveProviderKeys() {
        readLock.lock();
        try {
            return activeProviderCache.keySet();
        } finally {
            readLock.unlock();
        }
    }

    public boolean isEmpty() {
        readLock.lock();
        try {
            return activeProviderCache.isEmpty();
        } finally {
            readLock.unlock();
        }
    }

    public int size() {
        readLock.lock();
        try {
            return activeProviderCache.size();
        } finally {
            readLock.unlock();
        }
    }

    public void clear() {
        writeLock.lock();
        try {
            activeProviderCache.clear();
        } finally {
            writeLock.unlock();
        }
    }

    private void evictIfNeeded() {
        while (activeProviderCache.size() > LogisticsConstants.Cache.getProviderCacheSize()) {
            long oldestKey = activeProviderCache.firstLongKey();
            activeProviderCache.remove(oldestKey);
        }
    }

    public int getMaxCacheSize() {
        return LogisticsConstants.Cache.getProviderCacheSize();
    }

    public double getUsageRatio() {
        readLock.lock();
        try {
            return (double) activeProviderCache.size() / LogisticsConstants.Cache.getProviderCacheSize();
        } finally {
            readLock.unlock();
        }
    }

}