package com.coobird.staticlogistics.logistics.node;

import com.coobird.staticlogistics.logistics.node.persistence.ConfigKeys;
import com.coobird.staticlogistics.logistics.node.persistence.ContainerRepository;
import com.coobird.staticlogistics.logistics.node.persistence.EndpointRepository;
import com.coobird.staticlogistics.logistics.node.persistence.LogisticsDataFormat;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.slf4j.Logger;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/// 存档持久化 —— 把 LinkManager 的数据序列化/反序列化成 NBT
public class LinkManagerStorage extends SavedData {
    private static final LinkMutationPermit DESERIALIZATION_PERMIT = new LinkMutationPermit();
    private static final int FULL_SAVE_INTERVAL = 100;
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String QUARANTINED_FACES = "quarantined_face_configs";
    private static final String QUARANTINED_CONTAINERS = "quarantined_container_configs";

    public final LinkManager linkManager;
    private RuntimeException loadFailure;

    /// 增量保存：缓存上一次完整保存的 CompoundTag
    /// 每次保存只更新脏键对应的 NBT，避免全量序列化
    private CompoundTag cachedTag;
    private final Set<EndpointAddress> dirtyFaceKeys = new LinkedHashSet<>();
    private final LongSet dirtyContainerKeys = new LongOpenHashSet();
    private int incrementalSaveCounter;
    /// 无法解码的单条记录保留原始 NBT，防止后续自动保存静默删档。
    private final CompoundTag quarantinedFaces = new CompoundTag();
    private final CompoundTag quarantinedContainers = new CompoundTag();

    LinkManagerStorage(LinkManager linkManager) {
        this.linkManager = linkManager;
        this.cachedTag = new CompoundTag();
    }

    /// 变更登记和保存均在服务器主线程完成；成功输出之前保留脏记录。
    void markFaceDirty(EndpointAddress key) {
        dirtyFaceKeys.add(key);
        setDirty();
    }

    void markContainerDirty(long key) {
        dirtyContainerKeys.add(key);
        setDirty();
    }

    /// 保存数据：优先增量只写脏键，达到全量保存间隔或缓存为空时全量写
    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        requireLoaded();
        Set<EndpointAddress> dirtyFaces = Set.copyOf(dirtyFaceKeys);
        LongSet dirtyContainers = new LongOpenHashSet(dirtyContainerKeys);

        boolean needsFull = cachedTag.isEmpty() || ++incrementalSaveCounter >= FULL_SAVE_INTERVAL;
        if (needsFull) {
            CompoundTag replacement = createFullSave(provider);
            discardQuarantinedFaceKeys(dirtyFaces, quarantinedFaces);
            discardQuarantinedKeys(dirtyContainers, quarantinedContainers);
            writeQuarantineSections(replacement);
            cachedTag = replacement;
            incrementalSaveCounter = 0;
        } else if (!dirtyFaces.isEmpty() || !dirtyContainers.isEmpty()) {
            doIncrementalSave(dirtyFaces, dirtyContainers, provider);
        }

        /// 复制缓存到输出
        for (String key : cachedTag.getAllKeys()) {
            tag.put(key, cachedTag.get(key).copy());
        }
        dirtyFaceKeys.removeAll(dirtyFaces);
        dirtyContainerKeys.removeAll(dirtyContainers);
        return tag;
    }

    /// 全量序列化所有非默认的面配置、容器配置和全局管理器数据
    private CompoundTag createFullSave(HolderLookup.Provider provider) {
        EndpointRepository endpoints = linkManager.getEndpointRepository();
        ContainerRepository containerRepository = linkManager.getContainerRepository();
        CompoundTag replacement = new CompoundTag();
        replacement.putInt(ConfigKeys.SCHEMA_VERSION, LogisticsDataFormat.CURRENT_STORAGE_SCHEMA_VERSION);

        CompoundTag fConfigs = new CompoundTag();
        for (var entry : endpoints.getAllEntries()) {
            EndpointConfig v = entry.getValue();
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
                cConfigs.put(Long.toString(entry.getKey()), nbt);
            }
        }
        replacement.put("container_configs", cConfigs);
        return replacement;
    }

    /// 先独立序列化全部脏记录，全部成功后再替换缓存；无需深拷贝未变化的记录。
    /// 输出仍保留一份独立快照，避免后续保存修改已经交给存档系统的数据。
    private void doIncrementalSave(Set<EndpointAddress> dirtyFaces,
                                   LongSet dirtyContainers, HolderLookup.Provider provider) {
        Map<String, CompoundTag> faces = new LinkedHashMap<>();
        for (EndpointAddress key : dirtyFaces) {
            EndpointConfig config = linkManager.getEndpointRepository().get(key);
            faces.put(key.storageKey(), config == null || config.isDefault() ? null : config.serializeNBT(provider));
        }
        Map<String, CompoundTag> containers = new LinkedHashMap<>();
        for (long key : dirtyContainers) {
            ContainerConfig config = linkManager.getContainerRepository().get(key);
            CompoundTag value = null;
            if (config != null && !config.isDefault()) {
                value = new CompoundTag();
                value.put("upgrades", config.getUpgrades().serializeNBT(provider));
            }
            containers.put(Long.toString(key), value);
        }
        applyRecords(cachedTag.getCompound("face_configs"), faces);
        applyRecords(cachedTag.getCompound("container_configs"), containers);
        discardQuarantinedFaceKeys(dirtyFaces, quarantinedFaces);
        discardQuarantinedKeys(dirtyContainers, quarantinedContainers);
        writeQuarantineSections(cachedTag);
    }

    private static void applyRecords(CompoundTag records, Map<String, CompoundTag> replacements) {
        replacements.forEach((key, value) -> {
            if (value == null) records.remove(key);
            else records.put(key, value);
        });
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

    private static void discardQuarantinedFaceKeys(Set<EndpointAddress> dirtyKeys, CompoundTag quarantine) {
        for (EndpointAddress key : dirtyKeys) quarantine.remove(key.storageKey());
    }

    /// 从 NBT 反序列化还原所有配置：面配置、容器配置、全局管理器数据
    public static LinkManagerStorage load(CompoundTag tag, HolderLookup.Provider provider, ServerLevel level, LinkManager linkManager) {
        try {
            return readStorage(tag, provider, level, linkManager);
        } catch (RuntimeException failure) {
            linkManager.storage.loadFailure = failure;
            return linkManager.storage;
        }
    }

    /// 在 SavedData 的异常捕获范围之外拒绝加载，避免旧数据被空存档覆盖。
    void requireLoaded() {
        if (loadFailure != null) throw new IllegalStateException(
            "Cannot load Static Logistics data. This version requires the current storage format; the original data has been preserved.", loadFailure);
    }

    private static LinkManagerStorage readStorage(CompoundTag tag, HolderLookup.Provider provider, ServerLevel level, LinkManager linkManager) {
        LogisticsDataFormat.validateStorage(tag);
        CompoundTag migratedTag = tag;
        LinkManagerStorage storage = linkManager.storage;
        /// 保存时从运行时数据重建。
        storage.cachedTag = new CompoundTag();
        copyQuarantineSection(migratedTag, QUARANTINED_FACES, storage.quarantinedFaces);
        copyQuarantineSection(migratedTag, QUARANTINED_CONTAINERS, storage.quarantinedContainers);

        EndpointRepository endpoints = linkManager.getEndpointRepository();
        ContainerRepository containerRepository = linkManager.getContainerRepository();

        /// 先在独立对象中解码共享容器，成功后再发布；失败记录不进入运行时仓库。
        if (migratedTag.contains("container_configs")) {
            CompoundTag cTag = migratedTag.getCompound("container_configs");
            for (String keyStr : cTag.getAllKeys()) {
                try {
                    long key = Long.parseLong(keyStr);
                    if (!cTag.contains(keyStr, Tag.TAG_COMPOUND)) {
                        throw new IllegalArgumentException("Container config entry must be a compound tag");
                    }
                    ContainerConfig config = new ContainerConfig();
                    CompoundTag nbt = cTag.getCompound(keyStr);
                    if (nbt.contains("upgrades")) {
                        config.getUpgrades().deserializeNBT(provider, nbt.getCompound("upgrades"));
                    }
                    containerRepository.put(key, config);
                } catch (Exception e) {
                    storage.quarantinedContainers.put(keyStr, cTag.get(keyStr).copy());
                    storage.setDirty();
                    LOGGER.warn("Quarantined invalid container config key {}: {}", keyStr, e.getMessage());
                }
            }
        }

        /// 容器加载完成后再绑定端点；缺失或隔离的容器记录由仓库创建默认共享配置。
        if (migratedTag.contains("face_configs")) {
            CompoundTag fTag = migratedTag.getCompound("face_configs");
            for (String keyStr : fTag.getAllKeys()) {
                try {
                    if (!fTag.contains(keyStr, Tag.TAG_COMPOUND)) {
                        throw new IllegalArgumentException("Face config entry must be a compound tag");
                    }
                    EndpointAddress key = EndpointAddress.parseStorageKey(keyStr);
                    EndpointConfig config = new EndpointConfig();
                    config.deserializeNBT(DESERIALIZATION_PERMIT, provider, fTag.getCompound(keyStr));
                    endpoints.put(key, config);
                } catch (Exception e) {
                    storage.quarantinedFaces.put(keyStr, fTag.get(keyStr).copy());
                    storage.setDirty();
                    LOGGER.warn("Quarantined invalid face config key {}: {}", keyStr, e.getMessage());
                }
            }
        }

        linkManager.initializeLoadedState();

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
