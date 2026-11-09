package com.coobird.staticlogistics.logistics.node.persistence;

import com.coobird.staticlogistics.logistics.node.ContainerConfig;
import com.coobird.staticlogistics.logistics.node.EndpointAddress;
import com.coobird.staticlogistics.logistics.node.EndpointConfig;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/// 端点配置的唯一存储入口，同时维护端点与共享容器的双向关联。
/// 集合只提供只读视图，新增、替换、删除都必须经过本类。
public final class EndpointRepository {
    private final Map<EndpointAddress, EndpointConfig> faceConfigs = new HashMap<>();
    private final ContainerRepository containers;
    private List<EndpointAddress> blockRepresentatives;
    private Set<EndpointAddress> keys;

    public EndpointRepository(ContainerRepository containers) {
        this.containers = containers;
    }

    @Nullable
    public EndpointConfig get(EndpointAddress key) {
        return faceConfigs.get(key);
    }

    public EndpointConfig getOrCreate(EndpointAddress key) {
        EndpointConfig config = get(key);
        if (config == null) {
            config = new EndpointConfig();
            put(key, config);
        }
        return config;
    }

    public void put(EndpointAddress key, EndpointConfig config) {
        ContainerConfig container = containers.getOrCreate(key.pos());
        EndpointConfig previous = faceConfigs.get(key);
        if (previous != null) previous.detachContainerConfig(key);
        config.setPosition(key.pos());
        config.attachContainerConfig(container);
        container.linkFace(key);
        faceConfigs.put(key, config);
        if (previous == null) {
            blockRepresentatives = null;
            keys = null;
        }
    }

    @Nullable
    public EndpointConfig remove(EndpointAddress key) {
        EndpointConfig config = faceConfigs.remove(key);
        if (config != null) {
            config.detachContainerConfig(key);
            blockRepresentatives = null;
            keys = null;
        }
        return config;
    }

    public boolean containsKey(EndpointAddress key) {
        return faceConfigs.containsKey(key);
    }

    public Set<EndpointAddress> keySet() {
        if (keys == null) keys = Set.copyOf(faceConfigs.keySet());
        return keys;
    }

    /// 仅端点增删时重建；不可变快照允许迁移过程修改原存储。
    public List<EndpointAddress> blockRepresentatives() {
        if (blockRepresentatives == null) {
            Map<BlockPos, EndpointAddress> byBlock = new HashMap<>();
            for (EndpointAddress key : faceConfigs.keySet()) byBlock.putIfAbsent(key.pos(), key);
            blockRepresentatives = List.copyOf(byBlock.values());
        }
        return blockRepresentatives;
    }

    public List<Map.Entry<EndpointAddress, EndpointConfig>> getAllEntries() {
        return faceConfigs.entrySet().stream()
            .map(entry -> Map.entry(entry.getKey(), entry.getValue()))
            .toList();
    }
}
