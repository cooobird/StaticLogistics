package com.coobird.staticlogistics.storage.repository;

import com.coobird.staticlogistics.storage.config.ContainerConfig;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

import java.util.Collection;
import java.util.Set;

public class ContainerRepository {
    private final Long2ObjectMap<ContainerConfig> containerConfigs = new Long2ObjectOpenHashMap<>();

    public ContainerConfig get(long key) {
        return containerConfigs.get(key);
    }

    public void put(long key, ContainerConfig config) {
        containerConfigs.put(key, config);
    }

    public ContainerConfig remove(long key) {
        return containerConfigs.remove(key);
    }

    public boolean containsKey(long key) {
        return containerConfigs.containsKey(key);
    }

    public boolean isEmpty() {
        return containerConfigs.isEmpty();
    }

    public int size() {
        return containerConfigs.size();
    }

    public Collection<ContainerConfig> getAll() {
        return containerConfigs.values();
    }

    public Set<Long> keySet() {
        return containerConfigs.keySet();
    }

    public Set<Long2ObjectMap.Entry<ContainerConfig>> getAllEntries() {
        return containerConfigs.long2ObjectEntrySet();
    }

    public void clear() {
        containerConfigs.clear();
    }
}
