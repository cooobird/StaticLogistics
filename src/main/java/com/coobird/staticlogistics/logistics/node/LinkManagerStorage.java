package com.coobird.staticlogistics.logistics.node;

import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.api.NodeRole;
import com.coobird.staticlogistics.logistics.group.GlobalLogisticsManager;
import com.coobird.staticlogistics.logistics.group.PlayerGroupStore;
import com.coobird.staticlogistics.logistics.node.persistence.ConfigKeys;
import com.coobird.staticlogistics.logistics.node.persistence.ConfigRepository;
import com.coobird.staticlogistics.logistics.node.persistence.ContainerRepository;
import com.coobird.staticlogistics.logistics.node.persistence.LogisticsDataMigration;
import com.coobird.staticlogistics.logistics.node.sync.SyncManager;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.slf4j.Logger;

import java.util.Set;

/**
 * 维度内物流配置的持久化入口。
 * 无法解析的独立条目会进入隔离区，后续保存不会静默丢弃原始数据。
 */
public class LinkManagerStorage extends SavedData {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final LinkMutationPermit DESERIALIZATION_PERMIT = new LinkMutationPermit();
    private static final String FACE_CONFIGS = "face_configs";
    private static final String CONTAINER_CONFIGS = "container_configs";
    private static final String QUARANTINED_FACES = "quarantined_face_configs";
    private static final String QUARANTINED_CONTAINERS = "quarantined_container_configs";

    private final ServerLevel level;
    public final LinkManager linkManager;
    private final CompoundTag quarantinedFaces = new CompoundTag();
    private final CompoundTag quarantinedContainers = new CompoundTag();
    private CompoundTag cachedTag;

    public LinkManagerStorage(ServerLevel level, LinkManager linkManager) {
        this.level = level;
        this.linkManager = linkManager;
        this.cachedTag = new CompoundTag();
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        Set<FaceAddress> dirtyFaces = linkManager.snapshotDirtyFaces();
        LongSet dirtyContainers = linkManager.snapshotDirtyContainers();

        boolean needsFull = cachedTag.isEmpty() || linkManager.needsFullSave();
        if (needsFull) {
            discardQuarantinedFaceKeys(dirtyFaces, quarantinedFaces);
            discardQuarantinedKeys(dirtyContainers, quarantinedContainers);
            LOGGER.debug("Performing full logistics save (cachedEmpty={})", cachedTag.isEmpty());
            doFullSave();
            linkManager.resetFullSaveCounter();
        } else if (!dirtyFaces.isEmpty() || !dirtyContainers.isEmpty()) {
            CompoundTag previous = cachedTag;
            cachedTag = cachedTag.copy();
            try {
                doIncrementalSave(dirtyFaces, dirtyContainers);
            } catch (RuntimeException exception) {
                cachedTag = previous;
                throw exception;
            }
        }

        for (String key : cachedTag.getAllKeys()) {
            Tag value = cachedTag.get(key);
            if (value != null) tag.put(key, value.copy());
        }
        linkManager.ackDirtyFaces(dirtyFaces);
        linkManager.ackDirtyContainers(dirtyContainers);
        return tag;
    }

    private void doFullSave() {
        ConfigRepository configRepository = linkManager.getConfigRepository();
        ContainerRepository containerRepository = linkManager.getContainerRepository();
        CompoundTag rebuilt = new CompoundTag();
        rebuilt.putInt(ConfigKeys.SCHEMA_VERSION, LogisticsDataMigration.CURRENT_STORAGE_SCHEMA_VERSION);

        CompoundTag faceConfigs = new CompoundTag();
        for (FaceAddress address : configRepository.keySet()) {
            FaceConfigComposite config = configRepository.get(address);
            if (config != null && !config.isDefault()) {
                faceConfigs.put(address.storageKey(), config.serializeNBT(level.registryAccess()));
            }
        }
        rebuilt.put(FACE_CONFIGS, faceConfigs);

        CompoundTag containerConfigs = new CompoundTag();
        for (long key : containerRepository.keySet()) {
            ContainerConfig config = containerRepository.get(key);
            if (config != null && !config.isDefault()) {
                CompoundTag containerTag = new CompoundTag();
                containerTag.put("upgrades", config.getUpgrades().serializeNBT());
                containerConfigs.put(Long.toString(key), containerTag);
            }
        }
        rebuilt.put(CONTAINER_CONFIGS, containerConfigs);
        if (!quarantinedFaces.isEmpty()) rebuilt.put(QUARANTINED_FACES, quarantinedFaces.copy());
        if (!quarantinedContainers.isEmpty()) {
            rebuilt.put(QUARANTINED_CONTAINERS, quarantinedContainers.copy());
        }
        cachedTag = rebuilt;
    }

    private void doIncrementalSave(Set<FaceAddress> dirtyFaces, LongSet dirtyContainers) {
        ConfigRepository configRepository = linkManager.getConfigRepository();
        if (!dirtyFaces.isEmpty()) {
            CompoundTag faceConfigs = cachedTag.getCompound(FACE_CONFIGS).copy();
            for (FaceAddress address : dirtyFaces) {
                String key = address.storageKey();
                FaceConfigComposite config = configRepository.get(address);
                if (config == null || config.isDefault()) {
                    faceConfigs.remove(key);
                } else {
                    faceConfigs.put(key, config.serializeNBT(level.registryAccess()));
                }
                quarantinedFaces.remove(key);
            }
            cachedTag.put(FACE_CONFIGS, faceConfigs);
        }

        if (!dirtyContainers.isEmpty()) {
            ContainerRepository containerRepository = linkManager.getContainerRepository();
            CompoundTag containerConfigs = cachedTag.getCompound(CONTAINER_CONFIGS).copy();
            for (long key : dirtyContainers) {
                String storageKey = Long.toString(key);
                ContainerConfig config = containerRepository.get(key);
                if (config == null || config.isDefault()) {
                    containerConfigs.remove(storageKey);
                } else {
                    CompoundTag containerTag = new CompoundTag();
                    containerTag.put("upgrades", config.getUpgrades().serializeNBT());
                    containerConfigs.put(storageKey, containerTag);
                }
                quarantinedContainers.remove(storageKey);
            }
            cachedTag.put(CONTAINER_CONFIGS, containerConfigs);
        }

        cachedTag.putInt(ConfigKeys.SCHEMA_VERSION, LogisticsDataMigration.CURRENT_STORAGE_SCHEMA_VERSION);
        updateQuarantineSections();
    }

    public static LinkManagerStorage load(
        CompoundTag source,
        HolderLookup.Provider provider,
        ServerLevel level,
        LinkManager linkManager
    ) {
        LinkManagerStorage storage = new LinkManagerStorage(level, linkManager);
        LogisticsDataMigration.MigrationResult migration = LogisticsDataMigration.migrateStorageWithReport(source);
        CompoundTag tag = migration.tag();

        copyCompound(tag, QUARANTINED_FACES, storage.quarantinedFaces);
        copyCompound(tag, QUARANTINED_CONTAINERS, storage.quarantinedContainers);
        if (!migration.appliedSteps().isEmpty()) {
            LOGGER.info("Migrated logistics storage schema from {} to {} using {}",
                migration.sourceVersion(), migration.targetVersion(), migration.appliedSteps());
            storage.setDirty();
        }

        if (tag.contains("global_logistics", Tag.TAG_COMPOUND)
            && PlayerGroupStore.get(level.getServer()).importLegacyStorage(tag.getCompound("global_logistics"))) {
            LOGGER.info("Imported legacy embedded player group storage");
            storage.setDirty();
        }

        ConfigRepository configRepository = linkManager.getConfigRepository();
        ContainerRepository containerRepository = linkManager.getContainerRepository();
        ContainerConfigService containerConfigService = linkManager.getContainerConfigService();
        SyncManager syncManager = linkManager.getSyncManager();
        LinkChangeHandler changeHandler = linkManager.getChangeHandler();

        CompoundTag faceConfigs = tag.getCompound(FACE_CONFIGS);
        for (String key : faceConfigs.getAllKeys()) {
            Tag rawValue = faceConfigs.get(key);
            try {
                if (!faceConfigs.contains(key, Tag.TAG_COMPOUND)) {
                    throw new IllegalStateException("Face config entry must be a compound tag");
                }
                FaceAddress address = FaceAddress.parseStorageKey(key);
                LogisticsNode node = address.toNode(level.dimension());
                FaceConfigComposite config = new FaceConfigComposite();
                config.deserializeNBT(DESERIALIZATION_PERMIT, provider,
                    LogisticsDataMigration.migrateFace(faceConfigs.getCompound(key)));
                config.setPosition(address.pos());

                ContainerConfig containerConfig = containerConfigService.getOrCreate(address.pos());
                config.attachContainerConfig(containerConfig);
                containerConfig.linkFace(address);
                configRepository.put(address, config);
                syncManager.syncNode(address.pos(), address.face(), config);

                linkManager.refreshLocalCache(address, address.pos(), address.face(), config);
            } catch (Exception exception) {
                quarantine(storage.quarantinedFaces, key, rawValue);
                storage.setDirty();
                LOGGER.warn("Quarantined invalid face config entry: {}", key, exception);
            }
        }

        CompoundTag containerConfigs = tag.getCompound(CONTAINER_CONFIGS);
        for (String key : containerConfigs.getAllKeys()) {
            Tag rawValue = containerConfigs.get(key);
            long containerKey = Long.MIN_VALUE;
            try {
                if (!containerConfigs.contains(key, Tag.TAG_COMPOUND)) {
                    throw new IllegalStateException("Container config entry must be a compound tag");
                }
                containerKey = Long.parseLong(key);
                BlockPos pos = BlockPos.of(containerKey);
                ContainerConfig config = containerConfigService.getOrCreate(pos);
                CompoundTag containerTag = containerConfigs.getCompound(key);
                if (containerTag.contains("upgrades")) {
                    if (!containerTag.contains("upgrades", Tag.TAG_COMPOUND)) {
                        throw new IllegalStateException("Container upgrades must be a compound tag");
                    }
                    config.getUpgrades().deserializeNBT(containerTag.getCompound("upgrades"));
                }
            } catch (Exception exception) {
                if (containerKey != Long.MIN_VALUE) {
                    containerRepository.remove(containerKey);
                    BlockPos pos = BlockPos.of(containerKey);
                    ContainerConfig cleanConfig = containerConfigService.getOrCreate(pos);
                    for (FaceAddress address : configRepository.keySet()) {
                        if (!address.pos().equals(pos)) continue;
                        FaceConfigComposite faceConfig = configRepository.get(address);
                        if (faceConfig != null) {
                            faceConfig.attachContainerConfig(cleanConfig);
                            cleanConfig.linkFace(address);
                        }
                    }
                }
                quarantine(storage.quarantinedContainers, key, rawValue);
                storage.setDirty();
                LOGGER.warn("Quarantined invalid container config entry: {}", key, exception);
            }
        }

        GlobalLogisticsManager globalManager = GlobalLogisticsManager.get(level.getServer());
        for (FaceAddress address : configRepository.keySet()) {
            FaceConfigComposite config = configRepository.get(address);
            if (config == null || config.isDefault()) continue;
            NodeRole role = config.determineRole();
            if (role == NodeRole.NONE) continue;
            LogisticsNode node = address.toNode(level.dimension());
            for (var group : config.faceConfig.getGroups()) {
                globalManager.registerNode(group, node, role);
            }
        }

        linkManager.initKeyVersions();
        for (var entry : configRepository.getAllEntries()) {
            FaceAddress address = entry.getKey();
            FaceConfigComposite config = entry.getValue();
            if (config == null) continue;
            config.setOnDirty(changed -> changeHandler.onFaceConfigChanged(
                address, address.pos(), address.face(), changed));
        }
        for (var entry : containerRepository.getAllEntries()) {
            ContainerConfig config = entry.getValue();
            if (config != null) config.setOnDirty(changeHandler::onContainerConfigChanged);
        }

        // 第一次保存强制从内存模型完整重建，避免旧字段和已迁移条目残留。
        storage.cachedTag = new CompoundTag();
        return storage;
    }

    private void updateQuarantineSections() {
        if (quarantinedFaces.isEmpty()) cachedTag.remove(QUARANTINED_FACES);
        else cachedTag.put(QUARANTINED_FACES, quarantinedFaces.copy());
        if (quarantinedContainers.isEmpty()) cachedTag.remove(QUARANTINED_CONTAINERS);
        else cachedTag.put(QUARANTINED_CONTAINERS, quarantinedContainers.copy());
    }

    private static void discardQuarantinedKeys(LongSet dirtyKeys, CompoundTag quarantine) {
        for (long key : dirtyKeys) quarantine.remove(Long.toString(key));
    }

    private static void discardQuarantinedFaceKeys(Set<FaceAddress> dirtyKeys, CompoundTag quarantine) {
        for (FaceAddress address : dirtyKeys) quarantine.remove(address.storageKey());
    }

    private static void copyCompound(CompoundTag source, String key, CompoundTag target) {
        if (!source.contains(key, Tag.TAG_COMPOUND)) return;
        CompoundTag values = source.getCompound(key);
        for (String entryKey : values.getAllKeys()) {
            Tag value = values.get(entryKey);
            if (value != null) target.put(entryKey, value.copy());
        }
    }

    private static void quarantine(CompoundTag target, String key, Tag value) {
        if (value != null) target.put(key, value.copy());
    }
}
