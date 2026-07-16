package com.coobird.staticlogistics.logistics.node;

import com.coobird.staticlogistics.logistics.node.persistence.ConfigRepository;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import javax.annotation.Nullable;

public class FaceConfigService {
    private final ConfigRepository repository;
    private final ContainerConfigService containerConfigService;

    public FaceConfigService(ConfigRepository repository, ContainerConfigService containerConfigService) {
        this.repository = repository;
        this.containerConfigService = containerConfigService;
    }

    @Nullable
    public FaceConfigComposite get(FaceAddress address) {
        FaceConfigComposite config = repository.get(address);
        if (config != null && config.getContainerConfig() == null) {
            config.attachContainerConfig(containerConfigService.get(address.pos()));
        }
        return config;
    }

    public FaceConfigComposite getOrCreate(BlockPos pos, Direction face) {
        FaceAddress address = FaceAddress.of(pos, face);
        FaceConfigComposite config = repository.get(address);
        if (config == null) {
            config = new FaceConfigComposite();
            config.setPosition(pos);
            ContainerConfig cc = containerConfigService.getOrCreate(pos);
            config.attachContainerConfig(cc);
            cc.linkFace(address);
            repository.put(address, config);
        }
        return config;
    }

    public void remove(FaceAddress address) {
        FaceConfigComposite config = repository.get(address);
        if (config == null) return;
        config.detachContainerConfig(address);
        repository.remove(address);
    }

    public boolean exists(FaceAddress address) {
        return repository.containsKey(address);
    }
}
