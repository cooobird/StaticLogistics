package com.coobird.staticlogistics.storage;

import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.logic.GlobalLogisticsManager;
import com.coobird.staticlogistics.storage.model.ContainerConfig;
import com.coobird.staticlogistics.storage.model.FaceConfigComposite;
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
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
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

    // 延迟批量网络同步：缓存当前 tick 内所有需要同步的面容配置，tick 结束时批量刷出
    public record PendingSyncEntry(BlockPos pos, Direction face, FaceConfigComposite config) {
    }

    private final Map<ResourceKey<Level>, List<PendingSyncEntry>> pendingNetworkSync = new HashMap<>();
    private volatile boolean isFlushingNetworkSync = false;

    // cascade 期间抑制 scheduleNetworkSync，避免将即将删除的配置入队
    private volatile boolean suppressNetworkSync = false;

    // key 级版本计数器：确保删除重建后新配置的版本号高于旧配置
    private final Map<Long, Integer> keyVersions = new ConcurrentHashMap<>();

    /**
     * 获取 key 的下一个版本号（单调递增，跨配置对象持久）
     */
    public int nextVersion(long key) {
        return keyVersions.merge(key, 1, Integer::sum);
    }

    /**
     * 从已加载的面配置中初始化版本计数器（世界加载时调用）
     */
    public void initKeyVersions() {
        for (long key : getAllConfigKeys()) {
            FaceConfigComposite cfg = getFaceConfig(key);
            if (cfg != null) {
                keyVersions.merge(key, cfg.getVersion(), Math::max);
            }
        }
    }

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

    private synchronized void scheduleSave() {
        if (storage == null || isShutdown) return;
        try {
            if (pendingSave != null && !pendingSave.isDone()) pendingSave.cancel(false);
            pendingSave = SAVER.schedule(() -> {
                try {
                    if (storage != null && !isShutdown) storage.setDirty();
                } catch (Exception e) {
                    LOGGER.error("Error during save", e);
                } finally {
                    synchronized (this) {
                        pendingSave = null;
                    }
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

    /**
     * 发送面配置移除包到追踪该区块的所有客户端
     */
    public void syncRemovalToDimension(BlockPos pos, Direction face) {
        networkSyncManager.syncRemovalToDimension(pos, face);
    }

    public void syncToPlayer(ServerPlayer player) {
        List<Map.Entry<Long, FaceConfigComposite>> nonDefault = new ArrayList<>();
        for (long key : getAllConfigKeys()) {
            FaceConfigComposite cfg = getFaceConfig(key);
            if (cfg != null && !cfg.isDefault()) {
                nonDefault.add(Map.entry(key, cfg));
            }
        }
        networkSyncManager.syncBulkToPlayer(player, nonDefault);
    }

    public void syncConfigToClients(BlockPos pos) {
        for (Direction face : Direction.values()) {
            FaceConfigComposite cfg = getFaceConfig(posToKey(pos, face));
            if (cfg != null) scheduleNetworkSync(createNodeFromKey(posToKey(pos, face)));
        }
    }

    /**
     * 将面配置的网络同步延迟到当前 tick 结束时批量发送。
     * 单次变更和批量操作（如蓝图粘贴）统一走此路径，减少网络包数量。
     * cascade 期间（suppressNetworkSync=true）跳过入队，由 removal 包负责通知客户端。
     */
    public void scheduleNetworkSync(LogisticsNode node) {
        if (suppressNetworkSync) return;
        FaceConfigComposite cfg = getFaceConfig(node.toKey());
        if (cfg == null) return;
        ResourceKey<Level> dim = level.dimension();
        synchronized (pendingNetworkSync) {
            pendingNetworkSync.computeIfAbsent(dim, k -> new ArrayList<>())
                .add(new PendingSyncEntry(node.gPos().pos(), node.face(), cfg));
        }
    }

    /**
     * 设置是否抑制 scheduleNetworkSync（cascade 期间使用）
     */
    public void setSuppressNetworkSync(boolean suppress) {
        this.suppressNetworkSync = suppress;
    }

    /**
     * 在 tick 结束时调用，将所有待同步的面配置以批量包发送给对应维度的玩家。
     * 过滤掉已被删除的面配置（仓库中不存在的），避免发送过期数据。
     */
    public void flushPendingNetworkSync() {
        if (isFlushingNetworkSync) return;
        Map<ResourceKey<Level>, List<PendingSyncEntry>> toSend;
        synchronized (pendingNetworkSync) {
            if (pendingNetworkSync.isEmpty()) return;
            toSend = new HashMap<>(pendingNetworkSync);
            pendingNetworkSync.clear();
        }
        isFlushingNetworkSync = true;
        try {
            for (var entry : toSend.entrySet()) {
                // 过滤：只发送仍在仓库中的面配置（双重保险）
                List<PendingSyncEntry> valid = entry.getValue().stream()
                    .filter(e -> {
                        long key = posToKey(e.pos(), e.face());
                        FaceConfigComposite live = faceConfigHandler.configRepository.get(key);
                        return live != null && !live.isDefault();
                    })
                    .toList();
                if (!valid.isEmpty()) {
                    networkSyncManager.syncBulkToDimension(valid);
                }
            }
        } finally {
            isFlushingNetworkSync = false;
        }
    }

    /**
     * 供 removeLink 等场景使用的直接同步方法，跳过延迟队列。
     * 用于需要立即通知客户端的情况（如级联删除时的远程节点同步）。
     */
    public void syncNodeToDimensionDirect(LogisticsNode node) {
        FaceConfigComposite cfg = getFaceConfig(node.toKey());
        if (cfg != null) {
            for (ServerPlayer player : level.players()) {
                networkSyncManager.syncToPlayer(player, node.gPos().pos(), node.face(), cfg);
            }
        }
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