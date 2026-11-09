package com.coobird.staticlogistics.logistics.node.persistence;

import com.coobird.staticlogistics.logistics.node.ContainerConfig;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;

import java.util.List;
import java.util.Map;
import java.util.Set;

/// 维度内共享容器配置的唯一存储与创建入口。
public final class ContainerRepository {
    public ContainerConfig get(BlockPos pos) {
        return get(pos.asLong());
    }

    public ContainerConfig getOrCreate(BlockPos pos) {
        ContainerConfig config = get(pos);
        if (config == null) {
            config = new ContainerConfig();
            put(pos.asLong(), config);
        }
        return config;
    }

    /// 升级移交成功后，只删除仍为同一对象的配置，避免删除新建容器。
    public boolean removeAfterHandoff(BlockPos pos, ContainerConfig expected) {
        if (expected == null || get(pos) != expected) return false;
        remove(pos.asLong());
        return true;
    }

    private final Long2ObjectMap<ContainerConfig> containerConfigs = new Long2ObjectOpenHashMap<>();

    public ContainerConfig get(long key) {
        return containerConfigs.get(key);
    }

    public void put(long key, ContainerConfig config) {
        ContainerConfig previous = containerConfigs.get(key);
        if (previous != null && previous != config) {
            throw new IllegalStateException("Cannot replace an attached container configuration at " + key);
        }
        config.setPos(BlockPos.of(key));
        containerConfigs.put(key, config);
    }

    public ContainerConfig remove(long key) {
        return containerConfigs.remove(key);
    }

    public Set<Long> keySet() {
        return Set.copyOf(containerConfigs.keySet());
    }

    public List<Map.Entry<Long, ContainerConfig>> getAllEntries() {
        return containerConfigs.long2ObjectEntrySet().stream()
            .map(entry -> Map.entry(entry.getLongKey(), entry.getValue()))
            .toList();
    }
}
