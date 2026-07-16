package com.coobird.staticlogistics.logistics.node;

import com.coobird.staticlogistics.logistics.node.persistence.ContainerRepository;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

public class ContainerConfigService {
    private final ContainerRepository repository;

    public ContainerConfigService(ContainerRepository repository) {
        this.repository = repository;
    }

    public ContainerConfig getOrCreate(BlockPos pos) {
        long key = pos.asLong();
        ContainerConfig config = repository.get(key);
        if (config == null) {
            config = new ContainerConfig();
            config.setPos(pos);
            repository.put(key, config);
        }
        return config;
    }

    public ContainerConfig get(BlockPos pos) {
        return repository.get(pos.asLong());
    }

    public boolean removeIfUnused(BlockPos pos) {
        long posKey = pos.asLong();
        ContainerConfig config = repository.get(posKey);
        if (config == null || !config.getLinkedFaceKeys().isEmpty() || !config.isDefault()) return false;
        repository.remove(posKey);
        return true;
    }

    /**
     * 升级物已成功移交后，仅删除仍与快照一致的容器配置。
     */
    public boolean removeAfterHandoff(BlockPos pos, ContainerConfig expected) {
        long posKey = pos.asLong();
        ContainerConfig config = repository.get(posKey);
        if (config == null || config != expected) return false;
        repository.remove(posKey);
        return true;
    }

    /**
     * 仅供节点事务回滚：恢复容器升级快照。
     */
    void restoreSnapshot(BlockPos pos, CompoundTag upgrades) {
        ContainerConfig config = getOrCreate(pos);
        config.getUpgrades().deserializeNBT(upgrades.copy());
        config.markDirty();
    }
}
