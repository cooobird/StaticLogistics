package com.coobird.staticlogistics.logistics.node;

import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.api.NodeRole;
import com.coobird.staticlogistics.api.group.GroupRef;
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
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.slf4j.Logger;

import java.util.Set;

/**
 * 存档持久化 —— 把 LinkManager 的数据序列化/反序列化成 NBT
 */
public class LinkManagerStorage extends SavedData {
    private static final LinkMutationPermit DESERIALIZATION_PERMIT = new LinkMutationPermit();
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String QUARANTINED_FACES = "quarantined_face_configs";
    private static final String QUARANTINED_CONTAINERS = "quarantined_container_configs";

    private final ServerLevel level;
    public final LinkManager linkManager;

    /**
     * 增量保存：缓存上一次完整保存的 CompoundTag
     * 每次保存只更新脏键对应的 NBT，避免全量序列化
     */
    private CompoundTag cachedTag;
    /**
     * 无法解码的单条记录保留原始 NBT，防止后续自动保存静默删档。
     */
    private final CompoundTag quarantinedFaces = new CompoundTag();
    private final CompoundTag quarantinedContainers = new CompoundTag();

    public LinkManagerStorage(ServerLevel level, LinkManager linkManager) {
        this.level = level;
        this.linkManager = linkManager;
        this.cachedTag = new CompoundTag();
    }

    /**
     * 保存数据：优先增量只写脏键，达到全量保存间隔或缓存为空时全量写
     */
    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        Set<FaceAddress> dirtyFaces = linkManager.snapshotDirtyFaces();
        LongSet dirtyContainers = linkManager.snapshotDirtyContainers();

        boolean needsFull = cachedTag.isEmpty() || linkManager.needsFullSave();
        if (needsFull) {
            discardQuarantinedFaceKeys(dirtyFaces, quarantinedFaces);
            discardQuarantinedKeys(dirtyContainers, quarantinedContainers);
            CompoundTag replacement = createFullSave(provider);
            cachedTag = replacement;
            linkManager.resetFullSaveCounter();
        } else if (!dirtyFaces.isEmpty() || !dirtyContainers.isEmpty()) {
            CompoundTag candidate = cachedTag.copy();
            doIncrementalSave(candidate, dirtyFaces, dirtyContainers, provider);
            cachedTag = candidate;
        }

        // 复制缓存到输出
        for (String key : cachedTag.getAllKeys()) {
            tag.put(key, cachedTag.get(key).copy());
        }
        linkManager.ackDirtyFaces(dirtyFaces);
        linkManager.ackDirtyContainers(dirtyContainers);
        return tag;
    }

    /**
     * 全量序列化所有非默认的面配置、容器配置和全局管理器数据
     */
    private CompoundTag createFullSave(HolderLookup.Provider provider) {
        ConfigRepository configRepository = linkManager.getConfigRepository();
        ContainerRepository containerRepository = linkManager.getContainerRepository();
        CompoundTag replacement = new CompoundTag();
        replacement.putInt(ConfigKeys.SCHEMA_VERSION,
            LogisticsDataMigration.CURRENT_STORAGE_SCHEMA_VERSION);

        CompoundTag fConfigs = new CompoundTag();
        for (var entry : configRepository.getAllEntries()) {
            FaceConfigComposite v = entry.getValue();
            if (v != null && !v.isDefault()) {
                fConfigs.put(entry.getKey().storageKey(), v.serializeNBT(provider));
            }
        }
        replacement.put("face_configs", fConfigs);

        CompoundTag cConfigs = new CompoundTag();
        for (var entry : containerRepository.getAllEntries()) {
            ContainerConfig v = entry.getValue();
            if (v != null && !v.isDefault()) {
                CompoundTag nbt = new CompoundTag();
                nbt.put("upgrades", v.getUpgrades().serializeNBT(provider));
                cConfigs.put(Long.toString(entry.getLongKey()), nbt);
            }
        }
        replacement.put("container_configs", cConfigs);
        writeQuarantineSections(replacement);
        return replacement;
    }

    /**
     * 增量序列化：直接修改 cachedTag 中的子 CompoundTag，避免 copy() 分配。
     * dirtyFaces/dirtyContainers 是服务器主线程取得的稳定快照，
     * 只有序列化成功后才会从脏集合确认移除。
     */
    private void doIncrementalSave(CompoundTag candidate, Set<FaceAddress> dirtyFaces,
                                   LongSet dirtyContainers, HolderLookup.Provider provider) {
        ConfigRepository configRepository = linkManager.getConfigRepository();

        // 增量更新面配置 —— 直接修改 cachedTag 的子 tag，不 copy
        if (!dirtyFaces.isEmpty()) {
            CompoundTag fConfigs = candidate.getCompound("face_configs");
            if (fConfigs.isEmpty()) {
                fConfigs = new CompoundTag();
                candidate.put("face_configs", fConfigs);
            }
            for (FaceAddress key : dirtyFaces) {
                String keyStr = key.storageKey();
                quarantinedFaces.remove(keyStr);
                FaceConfigComposite cfg = configRepository.get(key);
                if (cfg == null || cfg.isDefault()) {
                    fConfigs.remove(keyStr);
                } else {
                    fConfigs.put(keyStr, cfg.serializeNBT(provider));
                }
            }
        }

        // 增量更新容器配置
        if (!dirtyContainers.isEmpty()) {
            ContainerRepository containerRepository = linkManager.getContainerRepository();
            CompoundTag cConfigs = candidate.getCompound("container_configs");
            if (cConfigs.isEmpty()) {
                cConfigs = new CompoundTag();
                candidate.put("container_configs", cConfigs);
            }
            for (long key : dirtyContainers) {
                String keyStr = Long.toString(key);
                quarantinedContainers.remove(keyStr);
                ContainerConfig cfg = containerRepository.get(key);
                if (cfg == null || cfg.isDefault()) {
                    cConfigs.remove(keyStr);
                } else {
                    CompoundTag nbt = new CompoundTag();
                    nbt.put("upgrades", cfg.getUpgrades().serializeNBT(provider));
                    cConfigs.put(keyStr, nbt);
                }
            }
        }
        writeQuarantineSections(candidate);
    }

    private void writeQuarantineSections(CompoundTag destination) {
        if (quarantinedFaces.isEmpty()) destination.remove(QUARANTINED_FACES);
        else destination.put(QUARANTINED_FACES, quarantinedFaces.copy());
        if (quarantinedContainers.isEmpty()) destination.remove(QUARANTINED_CONTAINERS);
        else destination.put(QUARANTINED_CONTAINERS, quarantinedContainers.copy());
    }

    private static void discardQuarantinedKeys(LongSet dirtyKeys, CompoundTag quarantine) {
        for (long key : dirtyKeys) quarantine.remove(Long.toString(key));
    }

    private static void discardQuarantinedFaceKeys(Set<FaceAddress> dirtyKeys, CompoundTag quarantine) {
        for (FaceAddress key : dirtyKeys) quarantine.remove(key.storageKey());
    }

    /**
     * 从 NBT 反序列化还原所有配置：面配置、容器配置、全局管理器数据
     */
    public static LinkManagerStorage load(CompoundTag tag, HolderLookup.Provider provider, ServerLevel level, LinkManager linkManager) {
        LogisticsDataMigration.MigrationResult migration =
            LogisticsDataMigration.migrateStorageWithReport(tag);
        CompoundTag migratedTag = migration.tag();
        if (!migration.appliedSteps().isEmpty()) {
            LOGGER.info("Migrated logistics storage for dimension {} using steps {}",
                level.dimension().location(), migration.appliedSteps());
        }
        LinkManagerStorage storage = new LinkManagerStorage(level, linkManager);
        // 首次保存强制从运行时权威对象重建，避免迁移输入中的旧字段继续传播。
        storage.cachedTag = new CompoundTag();
        copyQuarantineSection(migratedTag, QUARANTINED_FACES, storage.quarantinedFaces);
        copyQuarantineSection(migratedTag, QUARANTINED_CONTAINERS, storage.quarantinedContainers);
        if (!migration.appliedSteps().isEmpty()) storage.setDirty();

        ConfigRepository configRepository = linkManager.getConfigRepository();
        ContainerRepository containerRepository = linkManager.getContainerRepository();
        ContainerConfigService containerConfigService = linkManager.getContainerConfigService();
        SyncManager syncManager = linkManager.getSyncManager();
        LinkChangeHandler changeHandler = linkManager.getChangeHandler();

        if (migratedTag.contains("global_logistics", Tag.TAG_COMPOUND)) {
            PlayerGroupStore.get(level.getServer()).importLegacyStorage(
                migratedTag.getCompound("global_logistics"));
        }

        // 纯数据加载，不触发任何回调
        if (migratedTag.contains("face_configs")) {
            CompoundTag fTag = migratedTag.getCompound("face_configs");
            for (String keyStr : fTag.getAllKeys()) {
                try {
                    if (!fTag.contains(keyStr, Tag.TAG_COMPOUND)) {
                        throw new IllegalArgumentException("Face config entry must be a compound tag");
                    }
                    FaceAddress key = FaceAddress.parseStorageKey(keyStr);
                    LogisticsNode node = linkManager.createNodeFromKey(key);
                    FaceConfigComposite cfg = new FaceConfigComposite();
                    cfg.deserializeNBT(DESERIALIZATION_PERMIT, provider, fTag.getCompound(keyStr));
                    cfg.setPosition(node.gPos().pos());
                    BlockPos pos = node.gPos().pos();
                    Direction face = node.face();
                    ContainerConfig cc = containerConfigService.getOrCreate(pos);
                    cfg.attachContainerConfig(cc);
                    cc.linkFace(key);
                    configRepository.put(key, cfg);
                    syncManager.syncNode(pos, face, cfg);
                    linkManager.refreshLocalCache(key, pos, face, cfg);
                } catch (Exception e) {
                    storage.quarantinedFaces.put(keyStr, fTag.get(keyStr).copy());
                    storage.setDirty();
                    LOGGER.warn("Quarantined invalid face config key {}: {}", keyStr, e.getMessage());
                }
            }
        }
        if (migratedTag.contains("container_configs")) {
            CompoundTag cTag = migratedTag.getCompound("container_configs");
            for (String keyStr : cTag.getAllKeys()) {
                Long parsedKey = null;
                try {
                    long key = Long.parseLong(keyStr);
                    parsedKey = key;
                    if (!cTag.contains(keyStr, Tag.TAG_COMPOUND)) {
                        throw new IllegalArgumentException("Container config entry must be a compound tag");
                    }
                    BlockPos pos = BlockPos.of(key);
                    ContainerConfig cfg = containerConfigService.getOrCreate(pos);
                    CompoundTag nbt = cTag.getCompound(keyStr);
                    if (nbt.contains("upgrades")) {
                        cfg.getUpgrades().deserializeNBT(provider, nbt.getCompound("upgrades"));
                    }
                } catch (Exception e) {
                    if (parsedKey != null) {
                        containerRepository.remove(parsedKey.longValue());
                        BlockPos pos = BlockPos.of(parsedKey);
                        ContainerConfig replacement = containerConfigService.getOrCreate(pos);
                        for (var faceEntry : configRepository.getAllEntries()) {
                            FaceAddress address = faceEntry.getKey();
                            if (address.posLong() != parsedKey.longValue()) continue;
                            faceEntry.getValue().attachContainerConfig(replacement);
                            replacement.linkFace(address);
                        }
                    }
                    storage.quarantinedContainers.put(keyStr, cTag.get(keyStr).copy());
                    storage.setDirty();
                    LOGGER.warn("Quarantined invalid container config key {}: {}", keyStr, e.getMessage());
                }
            }
        }

        // 重新注册所有已加载的节点到 GlobalLogisticsManager
        GlobalLogisticsManager glm = GlobalLogisticsManager.get(level.getServer());
        for (var entry : configRepository.getAllEntries()) {
            FaceAddress key = entry.getKey();
            FaceConfigComposite cfg = entry.getValue();
            if (cfg == null || cfg.isDefault()) continue;
            LogisticsNode node = linkManager.createNodeFromKey(key);
            NodeRole role = cfg.determineRole();
            for (GroupRef group : cfg.faceConfig.getGroups()) {
                if (!group.displayName().isEmpty()) {
                    glm.registerNode(group, node, role);
                }
            }
        }

        // 初始化版本计数器
        linkManager.initKeyVersions();

        // 所有数据加载完成后，设置 onDirty 回调
        for (var entry : configRepository.getAllEntries()) {
            FaceAddress key = entry.getKey();
            FaceConfigComposite cfg = entry.getValue();
            if (cfg == null) continue;
            LogisticsNode node = linkManager.createNodeFromKey(key);
            BlockPos pos = node.gPos().pos();
            Direction face = node.face();
            cfg.setOnDirty(c -> changeHandler.onFaceConfigChanged(key, pos, face, c));
        }
        for (var entry : containerRepository.getAllEntries()) {
            ContainerConfig cfg = entry.getValue();
            if (cfg != null) {
                cfg.setOnDirty(changeHandler::onContainerConfigChanged);
            }
        }

        return storage;
    }

    private static void copyQuarantineSection(CompoundTag source, String key, CompoundTag destination) {
        if (!source.contains(key, Tag.TAG_COMPOUND)) return;
        CompoundTag saved = source.getCompound(key);
        for (String entryKey : saved.getAllKeys()) {
            Tag entry = saved.get(entryKey);
            if (entry != null) destination.put(entryKey, entry.copy());
        }
    }
}
