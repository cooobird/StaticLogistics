package com.coobird.staticlogistics.storage.cache;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;

public class CacheManager {
    private final LongSet activeProviderCache = new LongOpenHashSet();

    public void add(long key) {
        activeProviderCache.add(key);
    }

    public void remove(long key) {
        activeProviderCache.remove(key);
    }

    public boolean contains(long key) {
        return activeProviderCache.contains(key);
    }

    public LongSet getActiveProviderKeys() {
        return activeProviderCache;
    }

    public boolean isEmpty() {
        return activeProviderCache.isEmpty();
    }

    public int size() {
        return activeProviderCache.size();
    }

    public void clear() {
        activeProviderCache.clear();
    }

}