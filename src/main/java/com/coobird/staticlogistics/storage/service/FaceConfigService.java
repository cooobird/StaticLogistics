package com.coobird.staticlogistics.storage.service;

import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.storage.DropHandler;
import com.coobird.staticlogistics.storage.link.LinkManager;
import com.coobird.staticlogistics.storage.model.ContainerConfig;
import com.coobird.staticlogistics.storage.model.FaceConfigComposite;
import com.coobird.staticlogistics.storage.repository.ConfigRepository;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;

import javax.annotation.Nullable;

public class FaceConfigService {
    private final ConfigRepository repository;
    private final ServerLevel level;
    private final DropHandler dropHandler;
    private final ContainerConfigService containerConfigService;

    public FaceConfigService(ServerLevel level, ConfigRepository repository, DropHandler dropHandler,
                             ContainerConfigService containerConfigService) {
        this.level = level;
        this.repository = repository;
        this.dropHandler = dropHandler;
        this.containerConfigService = containerConfigService;
    }

    @Nullable
    public FaceConfigComposite get(long key) {
        return repository.get(key);
    }

    public FaceConfigComposite getOrCreate(BlockPos pos, Direction face) {
        long key = LinkManager.posToKey(pos, face);
        FaceConfigComposite config = repository.get(key);
        if (config == null) {
            config = new FaceConfigComposite();
            config.faceConfig.setPos(pos);
            ContainerConfig cc = containerConfigService.getOrCreate(pos);
            config.sharedContainerConfig = cc;
            cc.linkFace(key);
            repository.put(key, config);
        }
        return config;
    }

    public void remove(long key) {
        FaceConfigComposite config = repository.get(key);
        if (config == null) return;
        if (config.sharedContainerConfig != null) {
            config.sharedContainerConfig.unlinkFace(key);
        }
        repository.remove(key);
    }

    public boolean exists(long key) {
        return repository.containsKey(key);
    }
}