package com.coobird.staticlogistics.storage.link;

import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.api.NodeRole;
import com.coobird.staticlogistics.logic.GlobalLogisticsManager;
import com.coobird.staticlogistics.storage.model.ContainerConfig;
import com.coobird.staticlogistics.storage.model.FaceConfigComposite;
import com.coobird.staticlogistics.storage.repository.ConfigRepository;
import com.coobird.staticlogistics.storage.repository.ContainerRepository;
import com.coobird.staticlogistics.storage.service.ContainerConfigService;
import com.coobird.staticlogistics.storage.service.FaceConfigService;
import com.coobird.staticlogistics.storage.sync.SyncManager;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.slf4j.Logger;

/**
 * 存档持久化 —— 把 LinkManager 的数据序列化/反序列化成 NBT。
 * 支持增量保存（只写脏键对应的 NBT），每 {@code FULL_SAVE_INTERVAL} 次增量保存后做一次全量保存。
 */
public class LinkManagerStorage extends SavedData {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final ServerLevel level;
    public final LinkManager linkManager;

    /**
     * 增量保存：缓存上一次完整保存的 CompoundTag
     * 每次保存只更新脏键对应的 NBT，避免全量序列化
     */
    private CompoundTag cachedTag;

    public LinkManagerStorage(ServerLevel level, LinkManager linkManager) {
        this.level = level;
        this.linkManager = linkManager;
        this.cachedTag = new CompoundTag();
    }

    /**
     * 保存数据：优先增量只写脏键，达到全量保存间隔或缓存为空时全量写
     */
    @Override
    public CompoundTag save(CompoundTag tag) {
        LongSet dirtyFaces = linkManager.drainDirtyFaces();
        LongSet dirtyContainers = linkManager.drainDirtyContainers();

        boolean needsFull = cachedTag.isEmpty() || linkManager.needsFullSave();
        if (needsFull) {
            linkManager.resetFullSaveCounter();
            LOGGER.debug("Performing full save (cachedEmpty={})", cachedTag.isEmpty());
            doFullSave();
        } else if (!dirtyFaces.isEmpty() || !dirtyContainers.isEmpty()) {
            doIncrementalSave(dirtyFaces, dirtyContainers);
        }

        // 复制缓存到输出
        for (String key : cachedTag.getAllKeys()) {
            tag.put(key, cachedTag.get(key));
        }
        return tag;
    }

    /**
     * 全量序列化所有非默认的面容配置、容器配置和全局管理器数据
     */
    private void doFullSave() {
        ConfigRepository configRepository = linkManager.getConfigRepository();
        ContainerRepository containerRepository = linkManager.getContainerRepository();

        CompoundTag fConfigs = new CompoundTag();
        configRepository.keySet().forEach(k -> {
            FaceConfigComposite v = configRepository.get(k);
            if (v != null && !v.isDefault()) {
                fConfigs.put(Long.toString(k), v.serializeNBT(level.registryAccess()));
            }
        });
        cachedTag.put("face_configs", fConfigs);

        CompoundTag cConfigs = new CompoundTag();
        containerRepository.keySet().forEach(k -> {
            ContainerConfig v = containerRepository.get(k);
            if (v != null && !v.isDefault()) {
                CompoundTag nbt = new CompoundTag();
                nbt.put("upgrades", v.getUpgrades().serializeNBT());
                cConfigs.put(Long.toString(k), nbt);
            }
        });
        cachedTag.put("container_configs", cConfigs);
    }

    /**
     * 增量序列化：只更新脏键对应的 NBT 条目，删除已移除的，其余保留不变
     */
    private void doIncrementalSave(LongSet dirtyFaces, LongSet dirtyContainers) {
        ConfigRepository configRepository = linkManager.getConfigRepository();

        // 增量更新面容配置
        if (!dirtyFaces.isEmpty()) {
            CompoundTag fConfigs = cachedTag.getCompound("face_configs").copy();
            for (long key : dirtyFaces) {
                String keyStr = Long.toString(key);
                FaceConfigComposite cfg = configRepository.get(key);
                if (cfg == null || cfg.isDefault()) {
                    fConfigs.remove(keyStr);
                } else {
                    fConfigs.put(keyStr, cfg.serializeNBT(level.registryAccess()));
                }
            }
            cachedTag.put("face_configs", fConfigs);
        }

        // 增量更新容器配置
        if (!dirtyContainers.isEmpty()) {
            ContainerRepository containerRepository = linkManager.getContainerRepository();
            CompoundTag cConfigs = cachedTag.getCompound("container_configs").copy();
            for (long key : dirtyContainers) {
                String keyStr = Long.toString(key);
                ContainerConfig cfg = containerRepository.get(key);
                if (cfg == null || cfg.isDefault()) {
                    cConfigs.remove(keyStr);
                } else {
                    CompoundTag nbt = new CompoundTag();
                    nbt.put("upgrades", cfg.getUpgrades().serializeNBT());
                    cConfigs.put(keyStr, nbt);
                }
            }
            cachedTag.put("container_configs", cConfigs);
        }
    }

    /**
     * 从 NBT 反序列化还原所有配置：面容配置、容器配置、全局管理器数据
     */
    public static LinkManagerStorage load(CompoundTag tag, HolderLookup.Provider provider, ServerLevel level, LinkManager linkManager) {
        LinkManagerStorage storage = new LinkManagerStorage(level, linkManager);
        // 缓存完整的加载标签用于后续增量保存
        storage.cachedTag = tag.copy();

        ConfigRepository configRepository = linkManager.getConfigRepository();
        ContainerRepository containerRepository = linkManager.getContainerRepository();
        FaceConfigService faceConfigService = linkManager.getFaceConfigService();
        ContainerConfigService containerConfigService = linkManager.getContainerConfigService();
        SyncManager syncManager = linkManager.getSyncManager();
        LinkChangeHandler changeHandler = linkManager.getChangeHandler();

        // 纯数据加载，不触发任何回调
        if (tag.contains("face_configs")) {
            CompoundTag fTag = tag.getCompound("face_configs");
            for (String keyStr : fTag.getAllKeys()) {
                try {
                    long key = Long.parseLong(keyStr);
                    LogisticsNode node = linkManager.createNodeFromKey(key);
                    FaceConfigComposite cfg = new FaceConfigComposite();
                    cfg.deserializeNBT(provider, fTag.getCompound(keyStr));
                    cfg.faceConfig.setPos(node.gPos().pos());
                    BlockPos pos = node.gPos().pos();
                    Direction face = node.face();
                    ContainerConfig cc = containerConfigService.getOrCreate(pos);
                    cfg.sharedContainerConfig = cc;
                    cc.linkFace(key);
                    configRepository.put(key, cfg);
                    syncManager.syncNode(pos, face, cfg);
                    linkManager.refreshLocalCache(key, pos, face, cfg);
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
                    BlockPos pos = BlockPos.of(key);
                    ContainerConfig cfg = containerConfigService.getOrCreate(pos);
                    CompoundTag nbt = cTag.getCompound(keyStr);
                    if (nbt.contains("upgrades")) {
                        cfg.getUpgrades().deserializeNBT(nbt.getCompound("upgrades"));
                    }
                } catch (Exception e) {
                    LOGGER.error("Failed to load container config for key: {}", keyStr, e);
                }
            }
        }

        // 重新注册所有已加载的节点到 GlobalLogisticsManager
        GlobalLogisticsManager glm = GlobalLogisticsManager.get(level.getServer());
        for (Long key : configRepository.keySet()) {
            FaceConfigComposite cfg = configRepository.get(key);
            if (cfg == null || cfg.isDefault()) continue;
            LogisticsNode node = linkManager.createNodeFromKey(key);
            NodeRole role = cfg.determineRole();
            if (role != NodeRole.NONE) {
                for (String gid : cfg.faceConfig.getGroupIds()) {
                    if (gid != null && !gid.isEmpty()) {
                        glm.registerNode(gid, node, role);
                    }
                }

            }
        }

        // 初始化版本计数器
        linkManager.initKeyVersions();

        // 所有数据加载完成后，设置 onDirty 回调
        for (var entry : configRepository.getAllEntries()) {
            long key = entry.getLongKey();
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
}
