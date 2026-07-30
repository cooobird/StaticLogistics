package com.coobird.staticlogistics.logistics.node.persistence;

import com.coobird.staticlogistics.logistics.node.FaceAddress;
import com.coobird.staticlogistics.logistics.node.FaceConfigComposite;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class ConfigRepository {
    private final Map<FaceAddress, FaceConfigComposite> faceConfigs = new HashMap<>();

    @Nullable
    public FaceConfigComposite get(FaceAddress key) {
        return faceConfigs.get(key);
    }

    public void put(FaceAddress key, FaceConfigComposite config) {
        faceConfigs.put(key, config);
    }

    @Nullable
    public FaceConfigComposite remove(FaceAddress key) {
        return faceConfigs.remove(key);
    }

    public Collection<FaceConfigComposite> getAll() {
        return faceConfigs.values();
    }

    public boolean containsKey(FaceAddress key) {
        return faceConfigs.containsKey(key);
    }

    public Set<FaceAddress> keySet() {
        return Set.copyOf(faceConfigs.keySet());
    }

    public Set<Map.Entry<FaceAddress, FaceConfigComposite>> getAllEntries() {
        return faceConfigs.entrySet();
    }
}
