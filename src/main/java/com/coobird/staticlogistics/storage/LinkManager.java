package com.coobird.staticlogistics.storage;

import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.core.manager.GlobalLogisticsManager;
import com.coobird.staticlogistics.server.ticker.LogisticsTicker;
import com.coobird.staticlogistics.storage.cache.CacheManager;
import com.coobird.staticlogistics.storage.config.ContainerConfig;
import com.coobird.staticlogistics.storage.config.FaceConfigComposite;
import com.coobird.staticlogistics.storage.persistence.DropHandler;
import com.coobird.staticlogistics.storage.repository.ConfigRepository;
import com.coobird.staticlogistics.storage.repository.ContainerRepository;
import com.coobird.staticlogistics.storage.service.ContainerConfigService;
import com.coobird.staticlogistics.storage.service.FaceConfigService;
import com.coobird.staticlogistics.storage.sync.NetworkSyncManager;
import com.coobird.staticlogistics.storage.sync.SyncManager;
import com.coobird.staticlogistics.transfer.handler.TransferUtils;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class LinkManager extends SavedData {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final ConfigRepository configRepository;
    private final ContainerRepository containerRepository;

    private final CacheManager cacheManager;
    private final SyncManager syncManager;
    private final NetworkSyncManager networkSyncManager;
    private final DropHandler dropHandler;

    private final FaceConfigService faceConfigService;
    private final ContainerConfigService containerConfigService;
    private final LinkChangeHandler changeHandler;

    private final ServerLevel level;
    private final Set<Long> pendingRemovals = ConcurrentHashMap.newKeySet();

    public LinkManager(ServerLevel level) {
        this.level = level;
        this.configRepository = new ConfigRepository();
        this.containerRepository = new ContainerRepository();
        this.cacheManager = new CacheManager();
        this.syncManager = new SyncManager(level.dimension(), GlobalLogisticsManager.get(level.getServer()));
        this.networkSyncManager = new NetworkSyncManager(level);
        this.dropHandler = new DropHandler(level);

        this.containerConfigService = new ContainerConfigService(level, containerRepository);
        this.faceConfigService = new FaceConfigService(level, configRepository, dropHandler, containerConfigService);
        this.containerConfigService.setFaceConfigService(this.faceConfigService);

        this.changeHandler = new LinkChangeHandler(level, syncManager, networkSyncManager, this, this::setDirty, GlobalLogisticsManager.get(level.getServer()));
    }

    public static long posToKey(BlockPos pos) {
        return pos.asLong();
    }

    public static long posToKey(BlockPos pos, Direction face) {
        return (pos.asLong() << 3) | (face.get3DDataValue() & 0x7);
    }

    public FaceConfigComposite getOrCreateFaceConfig(BlockPos pos, Direction face) {
        long key = posToKey(pos, face);
        FaceConfigComposite config = faceConfigService.getOrCreate(pos, face);
        config.setOnDirty(cfg -> changeHandler.onFaceConfigChanged(key, pos, face, cfg));
        return config;
    }

    public ContainerConfig getOrCreateContainerConfig(BlockPos pos) {
        ContainerConfig config = containerConfigService.getOrCreate(pos);
        config.setOnDirty(changeHandler::onContainerConfigChanged);
        return config;
    }

    @Nullable
    public ContainerConfig getContainerConfig(BlockPos pos) {
        return containerConfigService.get(pos);
    }

    @Nullable
    public FaceConfigComposite getFaceConfig(long key) {
        return faceConfigService.get(key);
    }

    public void removeLink(LogisticsNode source, LogisticsNode target) {
        if (source == null || target == null) return;

        FaceConfigComposite sourceCfg = getFaceConfig(source.toKey());
        FaceConfigComposite targetCfg = getFaceConfig(target.toKey());
        if (sourceCfg == null || targetCfg == null) return;

        boolean sourceRemoved = sourceCfg.getLinkedNodes().remove(target);
        boolean targetRemoved = targetCfg.getLinkedNodes().remove(source);

        if (!sourceRemoved && !targetRemoved) return;

        if (sourceCfg.getLinkedNodes().isEmpty()) {
            sourceCfg.setGlobalOutputEnabled(false);
            sourceCfg.setGlobalInputEnabled(false);
        }
        if (targetCfg.getLinkedNodes().isEmpty()) {
            targetCfg.setGlobalOutputEnabled(false);
            targetCfg.setGlobalInputEnabled(false);
        }

        sourceCfg.markDirty();
        targetCfg.markDirty();

        GlobalLogisticsManager global = GlobalLogisticsManager.get(level.getServer());
        global.removeIncomingLink(source, target);

        networkSyncManager.syncToDimension(source.gPos().pos(), source.face(), sourceCfg);
        networkSyncManager.syncToDimension(target.gPos().pos(), target.face(), targetCfg);

        cleanUpFaceIfNeeded(source, sourceCfg);
        cleanUpFaceIfNeeded(target, targetCfg);
    }

    private void cleanUpFaceIfNeeded(LogisticsNode node, FaceConfigComposite cfg) {
        if (cfg.getLinkedNodes().isEmpty() && !cfg.isGlobalInputEnabled() && !cfg.isGlobalOutputEnabled()) {
            removeFaceConfigInternal(node.toKey(), false);
        }
    }

    public void removeFaceConfig(long key) {
        removeFaceConfigInternal(key, true);
    }

    public void removeFaceConfigInternal(long key, boolean doCascade) {
        if (!pendingRemovals.add(key)) return;
        try {
            FaceConfigComposite config = faceConfigService.get(key);
            if (config == null) return;
            LogisticsNode selfNode = LogisticsNode.fromKey(key, level.dimension());
            if (doCascade) {
                changeHandler.cascadeRemove(selfNode, config);
            }
            faceConfigService.remove(key);
            cacheManager.remove(key);
            GlobalLogisticsManager.get(level.getServer()).notifyNodeRemoved(level, selfNode);
            LogisticsTicker.wakeup(level, key);
            networkSyncManager.syncRemovalToDimension(selfNode.gPos().pos(), selfNode.face());
            setDirty();
        } finally {
            pendingRemovals.remove(key);
        }
    }

    public void refreshLocalCache(long key, BlockPos pos, Direction face, FaceConfigComposite config) {
        if (config.faceConfig.hasGroup() && config.determineRole().canSend()) {
            cacheManager.add(key);
        } else {
            cacheManager.remove(key);
        }
    }

    public void activateNode(long key, BlockPos pos, Direction face, FaceConfigComposite config) {
        refreshLocalCache(key, pos, face, config);
        LogisticsTicker.wakeup(level, key);
    }

    public NetworkSyncManager getNetworkSyncManager() {
        return networkSyncManager;
    }

    public void syncToPlayer(ServerPlayer player) {
        List<Map.Entry<Long, FaceConfigComposite>> nonDefaultConfigs = new ArrayList<>();
        for (var entry : configRepository.getAllEntries()) {
            FaceConfigComposite config = entry.getValue();
            if (!config.isDefault()) {
                nonDefaultConfigs.add(entry);
            }
        }
        networkSyncManager.syncBulkToPlayer(player, nonDefaultConfigs);
    }

    public void syncConfigToClients(BlockPos pos) {
        for (Direction face : Direction.values()) {
            FaceConfigComposite cfg = faceConfigService.get(posToKey(pos, face));
            if (cfg != null) {
                networkSyncManager.syncToDimension(pos, face, cfg);
            }
        }
    }

    public LongSet getActiveProviderKeys() {
        return cacheManager.getActiveProviderKeys();
    }

    public Set<Long> getAllConfigKeys() {
        return configRepository.keySet();
    }

    public void validateAllLinks() {
        boolean changed = false;
        for (long key : configRepository.keySet()) {
            FaceConfigComposite cfg = faceConfigService.get(key);
            if (cfg == null) continue;
            boolean faceChanged = false;
            LogisticsNode sourceNode = LogisticsNode.fromKey(key, level.dimension());
            Iterator<LogisticsNode> it = cfg.getLinkedNodes().iterator();
            while (it.hasNext()) {
                LogisticsNode target = it.next();
                ServerLevel targetLevel = level.getServer().getLevel(target.gPos().dimension());
                if (targetLevel != null && !TransferUtils.hasLogisticsCapability(targetLevel, target.gPos().pos(), target.face())) {
                    GlobalLogisticsManager.get(level.getServer()).removeIncomingLink(sourceNode, target);
                    it.remove();
                    faceChanged = true;
                }
            }
            if (faceChanged) {
                changed = true;
                networkSyncManager.syncToDimension(sourceNode.gPos().pos(), sourceNode.face(), cfg);
                if (cfg.isDefault()) {
                    removeFaceConfig(key);
                }
            }
        }
        if (changed) setDirty();
    }

    public void onBlockRemoved(BlockPos pos) {
        onBlocksRemovedBulk(List.of(pos));
    }

    public void onBlocksRemovedBulk(Collection<BlockPos> positions) {
        if (positions.isEmpty()) return;
        List<BlockPos> list = new ArrayList<>(positions);
        dropHandler.handleBulkDrops(list);
        for (BlockPos pos : list) {
            for (Direction face : Direction.values()) {
                long key = posToKey(pos, face);
                if (faceConfigService.get(key) != null) {
                    removeFaceConfigInternal(key, true);
                }
            }
            containerConfigService.removeIfUnused(pos);
            networkSyncManager.syncRemovalToDimension(pos);
        }
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
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

    public static LinkManager load(CompoundTag tag, HolderLookup.Provider provider, ServerLevel level) {
        LinkManager mgr = new LinkManager(level);
        if (tag.contains("face_configs")) {
            CompoundTag fTag = tag.getCompound("face_configs");
            for (String keyStr : fTag.getAllKeys()) {
                try {
                    long key = Long.parseLong(keyStr);
                    LogisticsNode node = LogisticsNode.fromKey(key, level.dimension());
                    FaceConfigComposite cfg = new FaceConfigComposite();
                    cfg.deserializeNBT(provider, fTag.getCompound(keyStr));
                    cfg.faceConfig.setPos(node.gPos().pos());
                    ContainerConfig cc = mgr.containerConfigService.getOrCreate(node.gPos().pos());
                    cfg.sharedContainerConfig = cc;
                    cc.linkFace(key);
                    mgr.configRepository.put(key, cfg);
                    for (LogisticsNode target : cfg.getLinkedNodes()) {
                        GlobalLogisticsManager.get(level.getServer()).addIncomingLink(node, target);
                    }
                    mgr.syncManager.syncNode(node.gPos().pos(), node.face(), cfg);
                    mgr.refreshLocalCache(key, node.gPos().pos(), node.face(), cfg);
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
                    mgr.containerRepository.put(key, cfg);
                } catch (Exception e) {
                    LOGGER.error("Failed to load container config for key: {}", keyStr, e);
                }
            }
        }
        if (tag.contains("global_logistics")) {
            GlobalLogisticsManager.get(level.getServer()).load(tag.getCompound("global_logistics"));
        }
        mgr.validateAllLinks();
        return mgr;
    }

    public static LinkManager get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
            new SavedData.Factory<>(() -> new LinkManager(level), (t, p) -> load(t, p, level)),
            "static_logistics_configs"
        );
    }
}