package com.coobird.staticlogistics.storage.repository;

import com.coobird.staticlogistics.storage.config.FaceConfigComposite;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Set;

public class ConfigRepository {
    private final Long2ObjectMap<FaceConfigComposite> faceConfigs = new Long2ObjectOpenHashMap<>();

    @Nullable
    public FaceConfigComposite get(long key) {
        return faceConfigs.get(key);
    }

    public void put(long key, FaceConfigComposite config) {
        faceConfigs.put(key, config);
    }

    @Nullable
    public FaceConfigComposite remove(long key) {
        return faceConfigs.remove(key);
    }

    public Collection<FaceConfigComposite> getAll() {
        return faceConfigs.values();
    }

    public boolean containsKey(long key) {
        return faceConfigs.containsKey(key);
    }

    public boolean isEmpty() {
        return faceConfigs.isEmpty();
    }

    public int size() {
        return faceConfigs.size();
    }

    public Set<Long> keySet() {
        return faceConfigs.keySet();
    }

    public Set<Long2ObjectMap.Entry<FaceConfigComposite>> getAllEntries() {
        return faceConfigs.long2ObjectEntrySet();
    }
}