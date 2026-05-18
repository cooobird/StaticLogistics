package com.coobird.staticlogistics.storage;

import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.core.manager.GlobalLogisticsManager;
import com.coobird.staticlogistics.item.util.LinkOperationHelper;
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
import com.coobird.staticlogistics.util.CapabilityCache;
import com.coobird.staticlogistics.util.LogisticsConstants;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
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

/**
 * 链接管理器 —— 核心调度中心，管理所有面容/容器配置、缓存、同步与持久化。
 * 每个 ServerLevel 绑定一个实例，通过 {@code LinkManager.get(level)} 获取。
 */
public class LinkManager {
    private static final ScheduledExecutorService SAVER = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "LinkManager-Saver");
        t.setDaemon(true);
        t.setUncaughtExceptionHandler((thread, throwable) -> {
            LogUtils.getLogger().error("Uncaught exception in LinkManager-Saver thread", throwable);
        });
        return t;
    });
    private static volatile boolean isShutdown = false;
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
    private final Map<Long, Boolean> pendingRemovals = new ConcurrentHashMap<>();
    private final Object removalLock = new Object();

    private LinkManagerStorage storage;
    private ScheduledFuture<?> pendingSave;


    private final LongSet dirtyFaceKeys = new LongOpenHashSet();

    private final LongSet dirtyContainerKeys = new LongOpenHashSet();
    private int incrementalSaveCounter = 0;
    private static final int FULL_SAVE_INTERVAL = 100;

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

        this.changeHandler = new LinkChangeHandler(level, syncManager, networkSyncManager, this, GlobalLogisticsManager.get(level.getServer()));
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

    public void markDirtyBatch(Runnable operation) {
        operation.run();
        scheduleSave();
    }

    /**
     * 标记面容配置变更（增量保存）
     */
    public void markFaceDirty(long faceKey) {
        dirtyFaceKeys.add(faceKey);
        scheduleSave();
    }

    /**
     * 标记容器配置变更（增量保存）
     */
    public void markContainerDirty(long containerKey) {
        dirtyContainerKeys.add(containerKey);
        scheduleSave();
    }

    LongSet drainDirtyFaces() {
        if (dirtyFaceKeys.isEmpty()) return new LongOpenHashSet();
        LongSet copy = new LongOpenHashSet(dirtyFaceKeys);
        dirtyFaceKeys.clear();
        return copy;
    }

    LongSet drainDirtyContainers() {
        if (dirtyContainerKeys.isEmpty()) return new LongOpenHashSet();
        LongSet copy = new LongOpenHashSet(dirtyContainerKeys);
        dirtyContainerKeys.clear();
        return copy;
    }

    boolean needsFullSave() {
        return ++incrementalSaveCounter >= FULL_SAVE_INTERVAL;
    }

    void resetFullSaveCounter() {
        incrementalSaveCounter = 0;
    }

    private void scheduleSave() {
        if (storage == null || isShutdown) return;
        try {
            if (pendingSave != null && !pendingSave.isDone()) {
                pendingSave.cancel(false);
            }
            pendingSave = SAVER.schedule(() -> {
                try {
                    if (storage != null && !isShutdown) {
                        storage.setDirty();
                    }
                } catch (Exception e) {
                    LOGGER.error("Error during save operation", e);
                } finally {
                    pendingSave = null;
                }
            }, 1, TimeUnit.SECONDS);
        } catch (RejectedExecutionException e) {
            LOGGER.warn("Save task rejected, executor may be shutdown", e);
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
            if (!SAVER.awaitTermination(LogisticsConstants.Thread.SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                LOGGER.warn("Saver did not terminate in time, forcing shutdown");
                SAVER.shutdownNow();
                if (!SAVER.awaitTermination(LogisticsConstants.Thread.FORCE_SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                    LOGGER.error("Saver did not terminate after forced shutdown");
                }
            }
        } catch (InterruptedException e) {
            LOGGER.error("Interrupted during saver shutdown", e);
            SAVER.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    public static boolean isSaverShutdown() {
        return isShutdown;
    }

    /**
     * 坐标编码为一个 long 键
     */
    public static long posToKey(BlockPos pos) {
        return pos.asLong();
    }

    /**
     * 坐标+面编码为 long 键（低3位存面方向）
     */
    public static long posToKey(BlockPos pos, Direction face) {
        return (pos.asLong() << LogisticsConstants.Storage.FACE_BITS) | (face.get3DDataValue() & LogisticsConstants.Storage.FACE_MASK);
    }

    /**
     * 从 long 键反推出 LogisticsNode
     */
    public LogisticsNode createNodeFromKey(long key) {
        return LogisticsNode.fromKey(key, level.dimension());
    }

    /**
     * 获取或创建面容配置，同时绑定脏回调
     */
    public FaceConfigComposite getOrCreateFaceConfig(BlockPos pos, Direction face) {
        long key = posToKey(pos, face);
        FaceConfigComposite config = faceConfigService.getOrCreate(pos, face);
        config.setOnDirty(cfg -> changeHandler.onFaceConfigChanged(key, pos, face, cfg));
        return config;
    }

    /**
     * 获取或创建容器配置，同时绑定脏回调
     */
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

    /**
     * 双向移除两个节点之间的链接，清理反向链接和全局开关
     */
    public void removeLink(LogisticsNode source, LogisticsNode target) {
        if (source == null || target == null) return;

        FaceConfigComposite sourceCfg = getFaceConfig(source.toKey());
        FaceConfigComposite targetCfg = getFaceConfig(target.toKey());
        if (sourceCfg == null || targetCfg == null) return;

        boolean sourceRemoved = sourceCfg.getLinkedNodes().remove(target);
        boolean targetRemoved = targetCfg.getLinkedNodes().remove(source);
        if (!sourceRemoved && !targetRemoved) return;

        GlobalLogisticsManager.get(level.getServer()).removeReverseLink(source.toKey(), target.toKey());
        if (targetRemoved)
            GlobalLogisticsManager.get(level.getServer()).removeReverseLink(target.toKey(), source.toKey());

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

        syncNodeToDimension(source);
        syncNodeToDimension(target);

        markFaceDirty(source.toKey());
        markFaceDirty(target.toKey());

        cleanUpFaceIfNeeded(source, sourceCfg);
        cleanUpFaceIfNeeded(target, targetCfg);
    }

    private void cleanUpFaceIfNeeded(LogisticsNode node, FaceConfigComposite cfg) {
        if (cfg.getLinkedNodes().isEmpty() && !cfg.isGlobalInputEnabled() && !cfg.isGlobalOutputEnabled()) {
            removeFaceConfigInternal(node.toKey(), false, true);
        }
    }

    /**
     * 删除面容配置（含级联和网络同步）
     */
    public void removeFaceConfig(long key) {
        removeFaceConfigInternal(key, true, true);
    }

    /**
     * 仅删除面容数据，不做级联也不发包
     */
    public void removeFaceConfigDataOnly(long key) {
        removeFaceConfigInternal(key, false, false);
    }

    private void removeFaceConfigInternal(long key, boolean doCascade, boolean sendPacket) {
        synchronized (removalLock) {
            if (pendingRemovals.containsKey(key)) return;
            pendingRemovals.put(key, true);
        }
        try {
            FaceConfigComposite config = faceConfigService.get(key);
            if (config == null) return;
            LogisticsNode selfNode = createNodeFromKey(key);
            if (doCascade) {
                changeHandler.cascadeRemove(selfNode, config);
            }
            faceConfigService.remove(key);
            cacheManager.remove(key);
            GlobalLogisticsManager.get(level.getServer()).notifyNodeRemoved(level, selfNode);
            GlobalLogisticsManager.get(level.getServer()).removeAllReverseLinksFor(key);
            LogisticsTicker.wakeup(level, key);
            if (sendPacket) {
                networkSyncManager.syncRemovalToDimension(selfNode.gPos().pos(), selfNode.face());
            }
            markFaceDirty(key);
        } finally {
            pendingRemovals.remove(key);
        }
    }

    /**
     * 刷新活跃提供者缓存：有分组且角色能发送就加入缓存，否则移除
     */
    public void refreshLocalCache(long key, BlockPos pos, Direction face, FaceConfigComposite config) {
        if (config.faceConfig.hasGroup() && config.determineRole().canSend()) {
            cacheManager.add(key);
        } else {
            cacheManager.remove(key);
        }
    }

    /**
     * 激活节点：刷新缓存并唤醒 ticker 开始工作
     */
    public void activateNode(long key, BlockPos pos, Direction face, FaceConfigComposite config) {
        refreshLocalCache(key, pos, face, config);
        LogisticsTicker.wakeup(level, key);
    }

    public NetworkSyncManager getNetworkSyncManager() {
        return networkSyncManager;
    }

    /**
     * 把当前维度所有非默认配置批量同步给一个玩家（登录时用）
     */
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
                syncNodeToDimension(createNodeFromKey(posToKey(pos, face)));
            }
        }
    }

    /**
     * 把单个节点的配置同步给当前维度所有玩家
     */
    public void syncNodeToDimension(LogisticsNode node) {
        for (ServerPlayer player : level.players()) {
            syncNodeToPlayer(player, node);
        }
    }

    public void syncNodeToPlayer(ServerPlayer player, LogisticsNode node) {
        FaceConfigComposite cfg = getFaceConfig(node.toKey());
        if (cfg != null) {
            networkSyncManager.syncToPlayer(player, node.gPos().pos(), node.face(), cfg);
        }
    }


    public LongSet getActiveProviderKeys() {
        return cacheManager.getActiveProviderKeys();
    }

    /**
     * 快速检查是否有活跃提供者，避免在 ticker 空转时创建快照
     */
    public boolean hasActiveProviders() {
        return cacheManager.hasProviders();
    }

    public Set<Long> getAllConfigKeys() {
        return configRepository.keySet();
    }

    /**
     * 单个方块被破坏时：清理掉落物和配置
     */
    public void onBlockRemoved(BlockPos pos) {
        onBlocksRemovedBulk(List.of(pos));
        LinkOperationHelper.cleanStoredNodesForPos(level, pos);
    }

    /**
     * 批量方块被破坏时：掉落升级卡、移除面容配置、广播同步
     */
    public void onBlocksRemovedBulk(Collection<BlockPos> positions) {
        if (positions.isEmpty()) return;
        List<BlockPos> list = new ArrayList<>(positions);
        List<BlockPos> failedDrops = new ArrayList<>();
        List<BlockPos> failedConfigs = new ArrayList<>();
        List<BlockPos> failedSync = new ArrayList<>();

        try {
            dropHandler.handleBulkDrops(list);
        } catch (Exception e) {
            LOGGER.error("Failed to handle bulk drops at positions {}: {}", list, e.getMessage(), e);
            failedDrops.addAll(list);
        }

        List<GlobalPos> removedPositions = new ArrayList<>();
        List<Direction> removedFaces = new ArrayList<>();

        for (BlockPos pos : list) {
            boolean posFailed = false;
            for (Direction face : Direction.values()) {
                long key = posToKey(pos, face);
                if (faceConfigService.get(key) != null) {
                    try {
                        removeFaceConfigInternal(key, true, false);
                    } catch (Exception e) {
                        LOGGER.error("Failed to remove face config at {} {}: {}", pos, face, e.getMessage(), e);
                        posFailed = true;
                        continue;
                    }
                    removedPositions.add(GlobalPos.of(level.dimension(), pos));
                    removedFaces.add(face);
                }
            }
            if (posFailed) {
                failedConfigs.add(pos);
            }
            try {
                containerConfigService.removeIfUnused(pos);
            } catch (Exception e) {
                LOGGER.error("Failed to clean container config at {}: {}", pos, e.getMessage(), e);
                failedConfigs.add(pos);
            }
        }

        if (!removedPositions.isEmpty()) {
            try {
                networkSyncManager.syncRemovalBulkToDimension(removedPositions, removedFaces);
            } catch (Exception e) {
                LOGGER.error("Failed to sync bulk removal: {}", e.getMessage(), e);
                failedSync.addAll(removedPositions.stream().map(GlobalPos::pos).toList());
            }
        }

        try {
            capabilityCache.clearForLevel(level.dimension());
        } catch (Exception e) {
            LOGGER.error("Failed to clear capability cache: {}", e.getMessage(), e);
        }

        scheduleSave();

        if (!failedDrops.isEmpty() || !failedConfigs.isEmpty() || !failedSync.isEmpty()) {
            logFailureSummary(failedDrops, failedConfigs, failedSync);
        }
    }

    private void logFailureSummary(List<BlockPos> failedDrops, List<BlockPos> failedConfigs, List<BlockPos> failedSync) {
        if (!failedDrops.isEmpty()) {
            LOGGER.warn("Failed to drop items at {} positions", failedDrops.size());
        }
        if (!failedConfigs.isEmpty()) {
            LOGGER.warn("Failed to clean configs at {} positions", failedConfigs.size());
        }
        if (!failedSync.isEmpty()) {
            LOGGER.warn("Failed to sync removal for {} positions", failedSync.size());
        }
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