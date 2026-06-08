package com.coobird.staticlogistics.storage.service;

import com.coobird.staticlogistics.storage.model.ContainerConfig;
import com.coobird.staticlogistics.storage.repository.ContainerRepository;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

public class ContainerConfigService {
    private final ContainerRepository repository;
    private final ServerLevel level;
    private FaceConfigService faceConfigService;

    public ContainerConfigService(ServerLevel level, ContainerRepository repository) {
        this.level = level;
        this.repository = repository;
    }

    public void setFaceConfigService(FaceConfigService faceConfigService) {
        this.faceConfigService = faceConfigService;
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

    public void removeIfUnused(BlockPos pos) {
        long posKey = pos.asLong();
        ContainerConfig config = repository.get(posKey);
        if (config == null) return;
        // ContainerConfig.linkedFaceKeys 已维护了关联的面 key，直接判断是否为空
        if (config.getLinkedFaceKeys().isEmpty()) {
            repository.remove(posKey);
        }
    }
}