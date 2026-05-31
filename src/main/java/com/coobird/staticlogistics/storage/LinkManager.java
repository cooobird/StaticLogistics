package com.coobird.staticlogistics.storage;

import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.core.manager.GlobalLogisticsManager;
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
import com.coobird.staticlogistics.util.LogisticsConstants;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class LinkManager {
    private static final ScheduledExecutorService SAVER = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "LinkManager-Saver");
        t.setDaemon(true);
        return t;
    });
    private static volatile boolean isShutdown = false;
    private static final Logger LOGGER = LogUtils.getLogger();

    private final FaceConfigHandler faceConfigHandler;
    private final ServerLevel level;
    private final Object dirtyLock = new Object();
    private LinkManagerStorage storage;
    private ScheduledFuture<?> pendingSave;
    private final LongSet dirtyFaceKeys = new LongOpenHashSet();
    private final LongSet dirtyContainerKeys = new LongOpenHashSet();
    private final AtomicInteger incrementalSaveCounter = new AtomicInteger(0);
    private static final int FULL_SAVE_INTERVAL = 100;
    private final ContainerRepository containerRepository;
    private final ContainerConfigService containerConfigService;
    private final NetworkSyncManager networkSyncManager;
    private final CacheManager cacheManager;

    public LinkManager(ServerLevel level) {
        this.level = level;
        ConfigRepository configRepository = new ConfigRepository();
        this.containerRepository = new ContainerRepository();
        this.cacheManager = new CacheManager();
        SyncManager syncManager = new SyncManager(level.dimension(), GlobalLogisticsManager.get(level.getServer()));
        this.networkSyncManager = new NetworkSyncManager(level);
        DropHandler dropHandler = new DropHandler(level);

        this.containerConfigService = new ContainerConfigService(level, containerRepository);
        FaceConfigService faceConfigService = new FaceConfigService(level, configRepository, dropHandler, containerConfigService);
        this.containerConfigService.setFaceConfigService(faceConfigService);

        LinkChangeHandler changeHandler = new LinkChangeHandler(level, syncManager, networkSyncManager, this,
            GlobalLogisticsManager.get(level.getServer()));

        this.faceConfigHandler = new FaceConfigHandler(level, faceConfigService, configRepository,
            cacheManager, changeHandler, dropHandler, networkSyncManager, syncManager, this);
    }

    FaceConfigHandler getFaceConfigHandler() {
        return faceConfigHandler;
    }

    ContainerRepository getContainerRepository() {
        return containerRepository;
    }

    ContainerConfigService getContainerConfigService() {
        return containerConfigService;
    }

    NetworkSyncManager getNetworkSyncManager() {
        return networkSyncManager;
    }

    ConfigRepository getConfigRepository() {
        return faceConfigHandler.configRepository;
    }

    FaceConfigService getFaceConfigService() {
        return faceConfigHandler.faceConfigService;
    }

    SyncManager getSyncManager() {
        return faceConfigHandler.syncManager;
    }

    void setStorage(LinkManagerStorage storage) {
        this.storage = storage;
    }

    public void markDirtyBatch(Runnable operation) {
        operation.run();
        scheduleSave();
    }

    public void markFaceDirty(long faceKey) {
        synchronized (dirtyLock) {
            dirtyFaceKeys.add(faceKey);
        }
        scheduleSave();
    }

    public void markContainerDirty(long containerKey) {
        synchronized (dirtyLock) {
            dirtyContainerKeys.add(containerKey);
        }
        scheduleSave();
    }

    LongSet drainDirtyFaces() {
        synchronized (dirtyLock) {
            if (dirtyFaceKeys.isEmpty()) return new LongOpenHashSet();
            LongSet copy = new LongOpenHashSet(dirtyFaceKeys);
            dirtyFaceKeys.clear();
            return copy;
        }
    }

    LongSet drainDirtyContainers() {
        synchronized (dirtyLock) {
            if (dirtyContainerKeys.isEmpty()) return new LongOpenHashSet();
            LongSet copy = new LongOpenHashSet(dirtyContainerKeys);
            dirtyContainerKeys.clear();
            return copy;
        }
    }

    boolean needsFullSave() {
        return incrementalSaveCounter.incrementAndGet() >= FULL_SAVE_INTERVAL;
    }

    void resetFullSaveCounter() {
        incrementalSaveCounter.set(0);
    }

    private void scheduleSave() {
        if (storage == null || isShutdown) return;
        try {
            if (pendingSave != null && !pendingSave.isDone()) pendingSave.cancel(false);
            pendingSave = SAVER.schedule(() -> {
                try {
                    if (storage != null && !isShutdown) storage.setDirty();
                } catch (Exception e) {
                    LOGGER.error("Error during save", e);
                } finally {
                    pendingSave = null;
                }
            }, 1, TimeUnit.SECONDS);
        } catch (RejectedExecutionException e) {
            LOGGER.warn("Save rejected, executor shutdown", e);
        }
    }

    public void shutdown() {
        if (pendingSave != null) {
            pendingSave.cancel(false);
            pendingSave = null;
        }
    }

    public static void shutdownSaver() {
        if (isShutdown) return;
        isShutdown = true;
        try {
            SAVER.shutdown();
            if (!SAVER.awaitTermination(LogisticsConstants.Thread.SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS))
                SAVER.shutdownNow();
        } catch (InterruptedException e) {
            SAVER.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    public static long posToKey(BlockPos pos) {
        return pos.asLong();
    }

    public static long posToKey(BlockPos pos, Direction face) {
        return LogisticsNode.posToKey(pos, face);
    }

    public LogisticsNode createNodeFromKey(long key) {
        return LogisticsNode.fromKey(key, level.dimension());
    }

    @Nullable
    public FaceConfigComposite getFaceConfig(long key) {
        return faceConfigHandler.getFaceConfig(key);
    }

    public FaceConfigComposite getOrCreateFaceConfig(BlockPos pos, Direction face) {
        return faceConfigHandler.getOrCreateFaceConfig(pos, face);
    }

    public void removeLink(LogisticsNode source, LogisticsNode target) {
        faceConfigHandler.removeLink(source, target);
    }

    public void cleanUpFaceIfNeeded(LogisticsNode node, FaceConfigComposite cfg) {
        faceConfigHandler.cleanUpFaceIfNeeded(node, cfg);
    }

    public void removeFaceConfig(long key) {
        faceConfigHandler.removeFaceConfig(key);
    }

    public void removeFaceConfigDataOnly(long key) {
        faceConfigHandler.removeFaceConfigDataOnly(key);
    }

    public void refreshLocalCache(long key, BlockPos pos, Direction face, FaceConfigComposite cfg) {
        faceConfigHandler.refreshLocalCache(key, pos, face, cfg);
    }

    public void activateNode(long key, BlockPos pos, Direction face, FaceConfigComposite cfg) {
        faceConfigHandler.activateNode(key, pos, face, cfg);
    }

    public void markOrphanScanNeeded() {
        faceConfigHandler.markOrphanScanNeeded();
    }

    public boolean isOrphanScanNeeded() {
        return faceConfigHandler.isOrphanScanNeeded();
    }

    public void validateOrphanedConfigs() {
        faceConfigHandler.validateOrphanedConfigs();
    }

    public void onBlockRemoved(BlockPos pos) {
        faceConfigHandler.onBlockRemoved(pos);
    }

    @Nullable
    public ContainerConfig getContainerConfig(BlockPos pos) {
        return containerConfigService.get(pos);
    }

    public ContainerConfig getOrCreateContainerConfig(BlockPos pos) {
        ContainerConfig config = containerConfigService.getOrCreate(pos);
        config.setOnDirty(mgr -> {
        });
        return config;
    }

    public LongSet getActiveProviderKeys() {
        return cacheManager.getActiveProviderKeys();
    }

    public long[] getActiveProviderKeysArray() {
        return cacheManager.getActiveProviderKeysArray();
    }

    public boolean hasActiveProviders() {
        return cacheManager.hasProviders();
    }

    public Set<Long> getAllConfigKeys() {
        return faceConfigHandler.getAllConfigKeys();
    }

    public void syncToPlayer(ServerPlayer player) {
        List<Map.Entry<Long, FaceConfigComposite>> nonDefault = new ArrayList<>();
        networkSyncManager.syncBulkToPlayer(player, nonDefault);
    }

    public void syncConfigToClients(BlockPos pos) {
        for (Direction face : Direction.values()) {
            FaceConfigComposite cfg = getFaceConfig(posToKey(pos, face));
            if (cfg != null) syncNodeToDimension(createNodeFromKey(posToKey(pos, face)));
        }
    }

    public void syncNodeToDimension(LogisticsNode node) {
        for (ServerPlayer player : level.players()) syncNodeToPlayer(player, node);
    }

    public void syncNodeToPlayer(ServerPlayer player, LogisticsNode node) {
        FaceConfigComposite cfg = getFaceConfig(node.toKey());
        if (cfg != null) networkSyncManager.syncToPlayer(player, node.gPos().pos(), node.face(), cfg);
    }

    public void onBlocksRemovedBulk(Collection<BlockPos> positions) {
        if (positions.isEmpty()) return;
        List<BlockPos> list = new ArrayList<>(positions);
        if (list.size() > 100) {
            List<BlockPos> deferred = new ArrayList<>(list.subList(100, list.size()));
            list = new ArrayList<>(list.subList(0, 100));
            for (BlockPos pos : deferred) {
                try {
                    containerConfigService.removeIfUnused(pos);
                } catch (Exception ignored) {
                }
            }
            markOrphanScanNeeded();
        }
        for (BlockPos pos : list) {
            for (Direction face : Direction.values()) {
                long key = posToKey(pos, face);
                if (faceConfigHandler.getFaceConfig(key) != null) faceConfigHandler.removeFaceConfig(key);
            }
            try {
                containerConfigService.removeIfUnused(pos);
            } catch (Exception ignored) {
            }
        }
        scheduleSave();
    }

    public static LinkManager get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
            new SavedData.Factory<>(
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