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
import com.coobird.staticlogistics.util.CapabilityCache;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.*;

public class LinkManager {
    private static final ScheduledExecutorService SAVER = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "LinkManager-Saver");
        t.setDaemon(true);
        return t;
    });
    private static final Logger LOGGER = LogUtils.getLogger();

    private final ConfigRepository configRepository;
    private final ContainerRepository containerRepository;
    private final CacheManager cacheManager;
    private final SyncManager syncManager;
    private final NetworkSyncManager networkSyncManager;
    private final DropHandler dropHandler;
    private final CapabilityCache capabilityCache;
    private final FaceConfigService faceConfigService;
    private final ContainerConfigService containerConfigService;
    private final LinkChangeHandler changeHandler;
    private final ServerLevel level;
    private final Set<Long> pendingRemovals = ConcurrentHashMap.newKeySet();

    private LinkManagerStorage storage;
    private ScheduledFuture<?> pendingSave;

    public LinkManager(ServerLevel level) {
        this.level = level;
        this.configRepository = new ConfigRepository();
        this.containerRepository = new ContainerRepository();
        this.cacheManager = new CacheManager();
        this.syncManager = new SyncManager(level.dimension(), GlobalLogisticsManager.get(level.getServer()));
        this.networkSyncManager = new NetworkSyncManager(level);
        this.dropHandler = new DropHandler(level);
        this.capabilityCache = new CapabilityCache();

        this.containerConfigService = new ContainerConfigService(level, containerRepository);
        this.faceConfigService = new FaceConfigService(level, configRepository, dropHandler, containerConfigService);
        this.containerConfigService.setFaceConfigService(this.faceConfigService);

        this.changeHandler = new LinkChangeHandler(level, syncManager, networkSyncManager, this, this::markDirty, GlobalLogisticsManager.get(level.getServer()));
    }

    ConfigRepository getConfigRepository() {
        return configRepository;
    }

    ContainerRepository getContainerRepository() {
        return containerRepository;
    }

    FaceConfigService getFaceConfigService() {
        return faceConfigService;
    }

    ContainerConfigService getContainerConfigService() {
        return containerConfigService;
    }

    SyncManager getSyncManager() {
        return syncManager;
    }

    void setStorage(LinkManagerStorage storage) {
        this.storage = storage;
    }

    public void markDirty() {
        if (storage == null) return;
        if (pendingSave != null && !pendingSave.isDone()) {
            pendingSave.cancel(false);
        }
        pendingSave = SAVER.schedule(() -> {
            if (storage != null) {
                storage.setDirty();
            }
            pendingSave = null;
        }, 1, TimeUnit.SECONDS);
    }

    public void flush() {
        if (pendingSave != null && !pendingSave.isDone()) {
            pendingSave.cancel(false);
        }
        if (storage != null) {
            storage.setDirty();
        }
        pendingSave = null;
    }

    public void shutdown() {
        if (pendingSave != null) {
            pendingSave.cancel(false);
            pendingSave = null;
        }
    }

    public static void shutdownSaver() {
        SAVER.shutdownNow();
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

    public CapabilityCache getCapabilityCache() {
        return capabilityCache;
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

        networkSyncManager.syncToDimension(source.gPos().pos(), source.face(), sourceCfg);
        networkSyncManager.syncToDimension(target.gPos().pos(), target.face(), targetCfg);

        cleanUpFaceIfNeeded(source, sourceCfg);
        cleanUpFaceIfNeeded(target, targetCfg);
    }

    private void cleanUpFaceIfNeeded(LogisticsNode node, FaceConfigComposite cfg) {
        if (cfg.getLinkedNodes().isEmpty() && !cfg.isGlobalInputEnabled() && !cfg.isGlobalOutputEnabled()) {
            removeFaceConfigInternal(node.toKey(), false, true);
        }
    }

    public void removeFaceConfig(long key) {
        removeFaceConfigInternal(key, true, true);
    }

    public void removeFaceConfigDataOnly(long key) {
        removeFaceConfigInternal(key, false, false);
    }

    private void removeFaceConfigInternal(long key, boolean doCascade, boolean sendPacket) {
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
            if (sendPacket) {
                networkSyncManager.syncRemovalToDimension(selfNode.gPos().pos(), selfNode.face());
            }
            markDirty();
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
        if (changed) markDirty();
    }

    public void onBlockRemoved(BlockPos pos) {
        onBlocksRemovedBulk(List.of(pos));
    }

    public void onBlocksRemovedBulk(Collection<BlockPos> positions) {
        if (positions.isEmpty()) return;
        List<BlockPos> list = new ArrayList<>(positions);

        try {
            dropHandler.handleBulkDrops(list);
        } catch (Exception e) {
            LOGGER.error("Failed to handle bulk drops at positions {}: {}", list, e.getMessage(), e);
        }

        List<GlobalPos> removedPositions = new ArrayList<>();
        List<Direction> removedFaces = new ArrayList<>();

        for (BlockPos pos : list) {
            for (Direction face : Direction.values()) {
                long key = posToKey(pos, face);
                if (faceConfigService.get(key) != null) {
                    try {
                        removeFaceConfigInternal(key, true, false);
                    } catch (Exception e) {
                        LOGGER.error("Failed to remove face config at {} {}: {}", pos, face, e.getMessage(), e);
                        continue;
                    }
                    removedPositions.add(GlobalPos.of(level.dimension(), pos));
                    removedFaces.add(face);
                }
            }
            try {
                containerConfigService.removeIfUnused(pos);
            } catch (Exception e) {
                LOGGER.error("Failed to clean container config at {}: {}", pos, e.getMessage(), e);
            }
        }

        if (!removedPositions.isEmpty()) {
            try {
                networkSyncManager.syncRemovalBulkToDimension(removedPositions, removedFaces);
            } catch (Exception e) {
                LOGGER.error("Failed to sync bulk removal: {}", e.getMessage(), e);
            }
        }

        try {
            capabilityCache.clearForLevel(level.dimension());
        } catch (Exception e) {
            LOGGER.error("Failed to clear capability cache: {}", e.getMessage(), e);
        }
        markDirty();
    }

    public static LinkManager get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
            new net.minecraft.world.level.saveddata.SavedData.Factory<>(
                () -> {
                    LinkManager mgr = new LinkManager(level);
                    LinkManagerStorage stor = new LinkManagerStorage(level, mgr);
                    mgr.setStorage(stor);
                    return stor;
                },
                (tag, provider) -> {
                    LinkManager mgr = new LinkManager(level);
                    LinkManagerStorage stor = LinkManagerStorage.load(tag, provider, level, mgr);
                    mgr.setStorage(stor);
                    return stor;
                }
            ),
            "static_logistics_configs"
        ).linkManager;
    }
}