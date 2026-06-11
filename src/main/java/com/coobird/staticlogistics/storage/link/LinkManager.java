package com.coobird.staticlogistics.storage.link;

import com.coobird.staticlogistics.api.ILinkManager;
import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.logic.GlobalLogisticsManager;
import com.coobird.staticlogistics.storage.DropHandler;
import com.coobird.staticlogistics.storage.model.ContainerConfig;
import com.coobird.staticlogistics.storage.model.FaceConfigComposite;
import com.coobird.staticlogistics.storage.repository.ConfigRepository;
import com.coobird.staticlogistics.storage.repository.ContainerRepository;
import com.coobird.staticlogistics.storage.service.ContainerConfigService;
import com.coobird.staticlogistics.storage.service.FaceConfigService;
import com.coobird.staticlogistics.storage.sync.NetworkSyncManager;
import com.coobird.staticlogistics.storage.sync.SyncManager;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * 物流链接管理器 —— 维度级物流配置的核心 facade。
 * <p>
 * 职责委托：
 * <ul>
 *   <li>{@link FaceConfigHandler} — 面配置 CRUD、级联删除、孤儿扫描</li>
 *   <li>{@link ContainerConfigService} — 容器配置 CRUD</li>
 *   <li>{@link CacheManager} — 活跃节点缓存</li>
 *   <li>{@link LinkDirtyTracker} — 脏数据追踪</li>
 *   <li>{@link LinkSaveScheduler} — 异步保存调度</li>
 *   <li>{@link PendingSyncBuffer} — 延迟批量网络同步</li>
 *   <li>{@link NetworkSyncManager} — 网络包发送</li>
 * </ul>
 */
public class LinkManager implements ILinkManager {
    private final FaceConfigHandler faceConfigHandler;
    private final ServerLevel level;
    private final ContainerRepository containerRepository;
    private final ContainerConfigService containerConfigService;
    private final NetworkSyncManager networkSyncManager;
    private final CacheManager cacheManager;
    private final LinkChangeHandler changeHandler;

    private final LinkDirtyTracker dirtyTracker = new LinkDirtyTracker();
    private final LinkSaveScheduler saveScheduler = new LinkSaveScheduler();
    private final PendingSyncBuffer syncBuffer = new PendingSyncBuffer();

    // key 级版本计数器：确保删除重建后新配置的版本号高于旧配置
    private final Map<Long, Long> keyVersions = new HashMap<>();

    public LinkManager(ServerLevel level) {
        this.level = level;
        ConfigRepository configRepository = new ConfigRepository();
        this.containerRepository = new ContainerRepository();
        this.cacheManager = new CacheManager();
        SyncManager syncManager = new SyncManager(level.dimension(), GlobalLogisticsManager.get(level.getServer()));
        this.networkSyncManager = new NetworkSyncManager(level);
        DropHandler dropHandler = new DropHandler(level);

        this.containerConfigService = new ContainerConfigService(level, containerRepository);
        FaceConfigService faceConfigService = new FaceConfigService(configRepository, containerConfigService);
        this.containerConfigService.setFaceConfigService(faceConfigService);

        this.changeHandler = new LinkChangeHandler(level, syncManager, networkSyncManager, this,
            GlobalLogisticsManager.get(level.getServer()));

        this.faceConfigHandler = new FaceConfigHandler(level, faceConfigService, configRepository,
            cacheManager, changeHandler, dropHandler, networkSyncManager, syncManager, this);
    }

    // 版本管理
    public long nextVersion(long key) {
        return keyVersions.merge(key, 1L, Long::sum);
    }

    public void initKeyVersions() {
        for (long key : getAllConfigKeys()) {
            FaceConfigComposite cfg = getFaceConfig(key);
            if (cfg != null) {
                keyVersions.merge(key, cfg.getVersion(), Math::max);
            }
        }
    }

    // 脏数据追踪（委托 LinkDirtyTracker）

    public void markDirtyBatch(Runnable operation) {
        operation.run();
        saveScheduler.scheduleSave();
    }

    @Override
    public void markFaceDirty(long faceKey) {
        dirtyTracker.markFaceDirty(faceKey);
        saveScheduler.scheduleSave();
    }

    @Override
    public void markContainerDirty(long containerKey) {
        dirtyTracker.markContainerDirty(containerKey);
        saveScheduler.scheduleSave();
    }

    LongSet drainDirtyFaces() {
        return dirtyTracker.drainDirtyFaces();
    }

    LongSet drainDirtyContainers() {
        return dirtyTracker.drainDirtyContainers();
    }

    // 保存调度（委托 LinkSaveScheduler）

    void setStorage(LinkManagerStorage storage) {
        saveScheduler.setStorage(storage);
    }

    boolean needsFullSave() {
        return saveScheduler.needsFullSave();
    }

    void resetFullSaveCounter() {
        saveScheduler.resetFullSaveCounter();
    }

    @Override
    public void shutdown() {
        saveScheduler.shutdown();
    }

    public static void shutdownSaver() {
        LinkSaveScheduler.shutdownSaver();
    }

    // 网络同步（委托 PendingSyncBuffer）

    public void scheduleNetworkSync(LogisticsNode node) {
        FaceConfigComposite cfg = getFaceConfig(node.toKey());
        if (cfg == null) return;
        syncBuffer.schedule(node, cfg);
    }

    public void setSuppressNetworkSync(boolean suppress) {
        syncBuffer.setSuppress(suppress);
    }

    public void flushPendingNetworkSync() {
        syncBuffer.flush(networkSyncManager, faceConfigHandler);
    }

    // 面配置 CRUD（委托 FaceConfigHandler）
    FaceConfigHandler getFaceConfigHandler() {
        return faceConfigHandler;
    }

    LinkChangeHandler getChangeHandler() {
        return changeHandler;
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

    public static long posToKey(BlockPos pos) {
        return pos.asLong();
    }

    public static long posToKey(BlockPos pos, Direction face) {
        return LogisticsNode.posToKey(pos, face);
    }

    public LogisticsNode createNodeFromKey(long key) {
        return LogisticsNode.fromKey(key, level.dimension());
    }

    @Override
    @Nullable
    public FaceConfigComposite getFaceConfig(long key) {
        return faceConfigHandler.getFaceConfig(key);
    }

    @Override
    public FaceConfigComposite getOrCreateFaceConfig(BlockPos pos, Direction face) {
        return faceConfigHandler.getOrCreateFaceConfig(pos, face);
    }

    @Override
    public void removeLink(LogisticsNode source, LogisticsNode target) {
        faceConfigHandler.removeLink(source, target);
    }

    @Override
    public void cleanUpFaceIfNeeded(LogisticsNode node, FaceConfigComposite cfg) {
        faceConfigHandler.cleanUpFaceIfNeeded(node, cfg);
    }

    @Override
    public void removeFaceConfig(long key) {
        faceConfigHandler.removeFaceConfig(key);
    }

    @Override
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

    // 容器配置（委托 ContainerConfigService）

    @Override
    @Nullable
    public ContainerConfig getContainerConfig(BlockPos pos) {
        return containerConfigService.get(pos);
    }

    @Override
    public ContainerConfig getOrCreateContainerConfig(BlockPos pos) {
        ContainerConfig config = containerConfigService.getOrCreate(pos);
        config.setOnDirty(changeHandler::onContainerConfigChanged);
        return config;
    }

    // 缓存（委托 CacheManager）

    public LongSet getActiveProviderKeys() {
        return cacheManager.getActiveProviderKeys();
    }

    public long[] getActiveProviderKeysArray() {
        return cacheManager.getActiveProviderKeysArray();
    }

    @Override
    public boolean hasActiveProviders() {
        return cacheManager.hasProviders();
    }

    @Override
    public Set<Long> getAllConfigKeys() {
        return faceConfigHandler.getAllConfigKeys();
    }

    // 网络同步直接操作

    public void syncRemovalToDimension(BlockPos pos, Direction face) {
        networkSyncManager.syncRemovalToDimension(pos, face);
    }

    @Override
    public void syncToPlayer(ServerPlayer player) {
        // 直接从 repository 收集非默认配置，避免遍历所有 key 再逐个 getFaceConfig
        List<Map.Entry<Long, FaceConfigComposite>> nonDefault = new ArrayList<>();
        for (var entry : getConfigRepository().getAllEntries()) {
            FaceConfigComposite cfg = entry.getValue();
            if (cfg != null && !cfg.isDefault()) {
                nonDefault.add(Map.entry(entry.getLongKey(), cfg));
            }
        }
        if (!nonDefault.isEmpty()) {
            networkSyncManager.syncBulkToPlayer(player, nonDefault);
        }
    }

    @Override
    public void syncConfigToClients(BlockPos pos) {
        for (Direction face : Direction.values()) {
            FaceConfigComposite cfg = getFaceConfig(posToKey(pos, face));
            if (cfg != null) scheduleNetworkSync(createNodeFromKey(posToKey(pos, face)));
        }
    }

    @Override
    public void syncNodeToDimensionDirect(LogisticsNode node) {
        FaceConfigComposite cfg = getFaceConfig(node.toKey());
        if (cfg != null) {
            for (ServerPlayer player : level.players()) {
                networkSyncManager.syncToPlayer(player, node.gPos().pos(), node.face(), cfg);
            }
        }
    }

    @Override
    public void syncNodeToPlayer(ServerPlayer player, LogisticsNode node) {
        FaceConfigComposite cfg = getFaceConfig(node.toKey());
        if (cfg != null) networkSyncManager.syncToPlayer(player, node.gPos().pos(), node.face(), cfg);
    }

    // 批量操作

    @Override
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
        saveScheduler.scheduleSave();
    }

    // 工厂

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
