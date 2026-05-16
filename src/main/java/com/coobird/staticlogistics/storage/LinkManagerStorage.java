package com.coobird.staticlogistics.storage;

import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.core.manager.GlobalLogisticsManager;
import com.coobird.staticlogistics.storage.config.ContainerConfig;
import com.coobird.staticlogistics.storage.config.FaceConfigComposite;
import com.coobird.staticlogistics.storage.repository.ConfigRepository;
import com.coobird.staticlogistics.storage.repository.ContainerRepository;
import com.coobird.staticlogistics.storage.service.ContainerConfigService;
import com.coobird.staticlogistics.storage.service.FaceConfigService;
import com.coobird.staticlogistics.storage.sync.SyncManager;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.slf4j.Logger;

public class LinkManagerStorage extends SavedData {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final ServerLevel level;
    public final LinkManager linkManager;

    public LinkManagerStorage(ServerLevel level, LinkManager linkManager) {
        this.level = level;
        this.linkManager = linkManager;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        ConfigRepository configRepository = linkManager.getConfigRepository();
        ContainerRepository containerRepository = linkManager.getContainerRepository();

        CompoundTag fConfigs = new CompoundTag();
        configRepository.keySet().forEach(k -> {
            FaceConfigComposite v = configRepository.get(k);
            if (v != null && !v.isDefault()) {
                fConfigs.put(Long.toString(k), v.serializeNBT(provider));
            }
        });
        tag.put("face_configs", fConfigs);

        CompoundTag cConfigs = new CompoundTag();
        containerRepository.keySet().forEach(k -> {
            ContainerConfig v = containerRepository.get(k);
            if (v != null && !v.isDefault()) {
                CompoundTag nbt = new CompoundTag();
                nbt.put("upgrades", v.getUpgrades().serializeNBT(provider));
                cConfigs.put(Long.toString(k), nbt);
            }
        });
        tag.put("container_configs", cConfigs);

        CompoundTag globalTag = new CompoundTag();
        GlobalLogisticsManager.get(level.getServer()).save(globalTag);
        tag.put("global_logistics", globalTag);

        return tag;
    }

    public static LinkManagerStorage load(CompoundTag tag, HolderLookup.Provider provider, ServerLevel level, LinkManager linkManager) {
        LinkManagerStorage storage = new LinkManagerStorage(level, linkManager);
        ConfigRepository configRepository = linkManager.getConfigRepository();
        ContainerRepository containerRepository = linkManager.getContainerRepository();
        FaceConfigService faceConfigService = linkManager.getFaceConfigService();
        ContainerConfigService containerConfigService = linkManager.getContainerConfigService();
        SyncManager syncManager = linkManager.getSyncManager();

        if (tag.contains("face_configs")) {
            CompoundTag fTag = tag.getCompound("face_configs");
            for (String keyStr : fTag.getAllKeys()) {
                try {
                    long key = Long.parseLong(keyStr);
                    LogisticsNode node = linkManager.createNodeFromKey(key);
                    FaceConfigComposite cfg = new FaceConfigComposite();
                    cfg.deserializeNBT(provider, fTag.getCompound(keyStr));
                    cfg.faceConfig.setPos(node.gPos().pos());
                    ContainerConfig cc = containerConfigService.getOrCreate(node.gPos().pos());
                    cfg.sharedContainerConfig = cc;
                    cc.linkFace(key);
                    configRepository.put(key, cfg);
                    syncManager.syncNode(node.gPos().pos(), node.face(), cfg);
                    linkManager.refreshLocalCache(key, node.gPos().pos(), node.face(), cfg);
                } catch (Exception e) {
                    LOGGER.error("Failed to load face config for key: {}", keyStr, e);
                }
            }
        }
        if (tag.contains("container_configs")) {
            CompoundTag cTag = tag.getCompound("container_configs");
            for (String keyStr : cTag.getAllKeys()) {
                try {
                    long key = Long.parseLong(keyStr);
                    ContainerConfig cfg = new ContainerConfig();
                    BlockPos pos = BlockPos.of(key);
                    cfg.setPos(pos);
                    CompoundTag nbt = cTag.getCompound(keyStr);
                    if (nbt.contains("upgrades")) {
                        cfg.getUpgrades().deserializeNBT(provider, nbt.getCompound("upgrades"));
                    }
                    containerRepository.put(key, cfg);
                } catch (Exception e) {
                    LOGGER.error("Failed to load container config for key: {}", keyStr, e);
                }
            }
        }
        if (tag.contains("global_logistics")) {
            GlobalLogisticsManager.get(level.getServer()).load(tag.getCompound("global_logistics"));
        }
        return storage;
    }
}