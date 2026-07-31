package com.coobird.staticlogistics.logistics.node;

import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.logistics.group.GlobalLogisticsManager;
import com.coobird.staticlogistics.logistics.node.persistence.ConfigRepository;
import com.coobird.staticlogistics.logistics.node.persistence.ContainerRepository;
import com.coobird.staticlogistics.logistics.node.sync.PendingSyncBuffer;
import com.coobird.staticlogistics.logistics.node.sync.SyncManager;
import com.coobird.staticlogistics.logistics.node.sync.TopologySyncPort;
import com.coobird.staticlogistics.network.sync.NetworkSyncManager;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
 *   <li>{@link LinkSaveScheduler} — 主线程脏状态登记</li>
 *   <li>{@link PendingSyncBuffer} — 延迟批量网络同步</li>
 *   <li>{@link TopologySyncPort} — 拓扑网络输出端口</li>
 * </ul>
 */
@SuppressWarnings("try")
public class LinkManager {
    private final FaceConfigHandler faceConfigHandler;
    private final ServerLevel level;
    private final ContainerRepository containerRepository;
    private final ContainerConfigService containerConfigService;
    private final TopologySyncPort topologySyncPort;
    private final CacheManager cacheManager;
    private final LinkChangeHandler changeHandler;
    private final LinkGraphService linkGraphService;
    private final NodeLifecycleService lifecycleService;

    private final LinkDirtyTracker dirtyTracker = new LinkDirtyTracker();
    private final LinkSaveScheduler saveScheduler = new LinkSaveScheduler();
    private final PendingSyncBuffer syncBuffer = new PendingSyncBuffer();

    private final Map<FaceAddress, Long> keyVersions = new HashMap<>();

    public LinkManager(ServerLevel level) {
        this.level = level;
        ConfigRepository configRepository = new ConfigRepository();
        this.containerRepository = new ContainerRepository();
        this.cacheManager = new CacheManager();
        GlobalLogisticsManager globalManager = GlobalLogisticsManager.get(level.getServer());
        SyncManager syncManager = new SyncManager(level.dimension(), globalManager);
        this.topologySyncPort = new NetworkSyncManager(level);
        DropHandler dropHandler = new DropHandler(level);
        this.linkGraphService = new LinkGraphService(level.getServer(), globalManager);

        this.containerConfigService = new ContainerConfigService(containerRepository);
        FaceConfigService faceConfigService = new FaceConfigService(configRepository, containerConfigService);

        this.changeHandler = new LinkChangeHandler(level, syncManager, this);

        this.faceConfigHandler = new FaceConfigHandler(level, faceConfigService, configRepository,
            cacheManager, changeHandler, syncManager, this);
        this.lifecycleService = new NodeLifecycleService(
            this, faceConfigHandler, containerConfigService, dropHandler::beginHandoff);
    }

    // 版本管理

    public long nextVersion(FaceAddress address) {
        return keyVersions.merge(address, 1L, Long::sum);
    }

    /**
     * 将对象本地版本提升为该面的下一个单调版本。
     */
    long normalizeVersion(FaceAddress address, long observedVersion) {
        return keyVersions.compute(address, (ignored, current) -> {
            if (current == null) return Math.max(1L, observedVersion);
            return Math.max(current + 1L, observedVersion);
        });
    }

    public void initKeyVersions() {
        for (FaceAddress address : getAllConfigKeys()) {
            FaceConfigComposite cfg = getFaceConfig(address);
            if (cfg != null) {
                keyVersions.merge(address, cfg.getVersion(), Math::max);
            }
        }
    }

    // 脏数据追踪（委托 LinkDirtyTracker）

    public void markDirtyBatch(Runnable operation) {
        operation.run();
        saveScheduler.scheduleSave();
    }

    public void markFaceDirty(FaceAddress address) {
        dirtyTracker.markFaceDirty(address);
        saveScheduler.scheduleSave();
    }

    public void markContainerDirty(long containerKey) {
        dirtyTracker.markContainerDirty(containerKey);
        saveScheduler.scheduleSave();
    }

    Set<FaceAddress> snapshotDirtyFaces() {
        return dirtyTracker.snapshotDirtyFaces();
    }

    LongSet snapshotDirtyContainers() {
        return dirtyTracker.snapshotDirtyContainers();
    }

    void ackDirtyFaces(Set<FaceAddress> keys) {
        dirtyTracker.ackDirtyFaces(keys);
    }

    void ackDirtyContainers(LongSet keys) {
        dirtyTracker.ackDirtyContainers(keys);
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

    public void shutdown() {
        saveScheduler.shutdown();
    }

    // 网络同步（委托 PendingSyncBuffer）

    public void scheduleNetworkSync(LogisticsNode node) {
        if (NodeMutationTransaction.defer(level.getServer(), new DeferredNetworkKey(this, node),
            () -> scheduleNetworkSync(node))) return;
        FaceConfigComposite cfg = getFaceConfig(FaceAddress.of(node));
        if (cfg == null) return;
        syncBuffer.schedule(level, node, cfg);
    }

    void scheduleNetworkRemoval(LogisticsNode node, long version, @Nullable UUID ownerId) {
        if (NodeMutationTransaction.defer(level.getServer(), new DeferredNetworkKey(this, node),
            () -> scheduleNetworkRemoval(node, version, ownerId))) return;
        syncBuffer.scheduleRemoval(node, version, ownerId);
    }

    public void flushPendingNetworkSync() {
        syncBuffer.flush(topologySyncPort);
    }

    private record DeferredNetworkKey(LinkManager manager, LogisticsNode node) {
    }

    // 面配置 CRUD（委托 FaceConfigHandler）

    FaceConfigHandler getFaceConfigHandler() {
        return faceConfigHandler;
    }

    LinkChangeHandler getChangeHandler() {
        return changeHandler;
    }

    boolean isLifecycleRemovalInProgress() {
        return lifecycleService.isHandoffInProgress();
    }

    ContainerRepository getContainerRepository() {
        return containerRepository;
    }

    ContainerConfigService getContainerConfigService() {
        return containerConfigService;
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

    public LogisticsNode createNodeFromKey(FaceAddress address) {
        return address.toNode(level.dimension());
    }

    @Nullable
    public FaceConfigComposite getFaceConfig(FaceAddress address) {
        return getConfigRepository().get(address);
    }

    public FaceConfigComposite getOrCreateFaceConfig(BlockPos pos, Direction face) {
        return faceConfigHandler.getOrCreateFaceConfig(pos, face);
    }

    public void removeLink(LogisticsNode source, LogisticsNode target) {
        linkGraphService.removeEdge(source, target);
    }

    public void cleanUpFaceIfNeeded(LogisticsNode node, FaceConfigComposite cfg) {
        lifecycleService.removeOrphan(node, cfg);
    }

    public void removeFaceConfig(FaceAddress address) {
        lifecycleService.removeFace(address);
    }

    public void removeFaceConfigDataOnly(FaceAddress address) {
        faceConfigHandler.removeFaceConfigDataOnly(address);
    }

    public void restoreFaceSnapshot(LogisticsNode node, net.minecraft.nbt.CompoundTag snapshot) {
        faceConfigHandler.restoreFaceSnapshot(node, snapshot);
    }

    /**
     * 恢复“修改前不存在此面”的状态，不移交尚未提交的临时升级物。
     */
    public void restoreFaceAbsence(LogisticsNode node) {
        if (node == null) return;
        FaceConfigComposite config = getFaceConfig(FaceAddress.of(node));
        if (config == null) return;
        faceConfigHandler.removeFaceAfterHandoff(FaceAddress.of(node), config, true, true);
    }

    /**
     * 恢复容器升级快照；原先不存在时仅删除事务创建的临时配置。
     */
    public void restoreContainerSnapshot(BlockPos pos, boolean existed, @Nullable CompoundTag upgrades) {
        if (pos == null) return;
        if (existed) {
            if (upgrades == null) {
                throw new IllegalArgumentException("Existing container snapshot must contain upgrades");
            }
            containerConfigService.restoreSnapshot(pos, upgrades);
            markContainerDirty(pos.asLong());
            return;
        }
        ContainerConfig current = getContainerConfig(pos);
        if (current != null && !containerConfigService.removeAfterHandoff(pos, current)) {
            throw new IllegalStateException("Container snapshot changed during rollback at " + pos);
        }
        markContainerDirty(pos.asLong());
    }

    public void renameGroupMetadata(LogisticsNode node,
                                    com.coobird.staticlogistics.api.group.GroupKey groupKey,
                                    String displayName) {
        faceConfigHandler.renameGroupMetadata(node, groupKey, displayName);
    }

    public void mergeGroupMetadata(LogisticsNode node,
                                   com.coobird.staticlogistics.api.group.GroupRef source,
                                   com.coobird.staticlogistics.api.group.GroupRef target) {
        faceConfigHandler.mergeGroupMetadata(node, source, target);
    }

    public void claimOwner(LogisticsNode node, com.mojang.authlib.GameProfile profile) {
        faceConfigHandler.claimOwner(node, profile);
    }

    public void refreshOwnerProfile(LogisticsNode node, com.mojang.authlib.GameProfile profile) {
        faceConfigHandler.refreshOwnerProfile(node, profile);
    }

    public void addNodeToGroup(LogisticsNode node,
                               com.coobird.staticlogistics.api.group.GroupRef group) {
        faceConfigHandler.addNodeToGroup(node, group);
    }

    public void addLink(com.coobird.staticlogistics.api.group.GroupKey groupKey,
                        LogisticsNode first, LogisticsNode second) {
        linkGraphService.addEdge(groupKey, first, second);
    }

    public void removeLink(com.coobird.staticlogistics.api.group.GroupKey groupKey,
                           LogisticsNode first, LogisticsNode second) {
        linkGraphService.removeEdge(groupKey, first, second);
    }

    public void removeLinkWithoutCleanup(
        com.coobird.staticlogistics.api.group.GroupKey groupKey,
        LogisticsNode first,
        LogisticsNode second
    ) {
        linkGraphService.removeEdgeWithoutCleanup(groupKey, first, second);
    }

    public NodeLifecycleService.DisconnectedRemoval prepareDisconnectedRemoval(
        Collection<LogisticsNode> nodes
    ) {
        return lifecycleService.prepareDisconnectedRemoval(nodes);
    }

    public void applyDisconnectedRemoval(NodeLifecycleService.DisconnectedRemoval removal) {
        lifecycleService.applyDisconnectedRemoval(removal);
    }

    public void removeNodeFromGroup(com.coobird.staticlogistics.api.group.GroupKey groupKey,
                                    LogisticsNode node) {
        linkGraphService.removeNodeFromGroup(groupKey, node);
    }

    void cascadeRemove(LogisticsNode node, FaceConfigComposite config) {
        linkGraphService.cascadeRemove(node, config);
    }

    void repairReciprocalEdges(LogisticsNode node, FaceConfigComposite config) {
        linkGraphService.repairReciprocalEdges(node, config);
    }

    public void refreshLocalCache(FaceAddress address, BlockPos pos, Direction face,
                                  FaceConfigComposite cfg) {
        faceConfigHandler.refreshLocalCache(address, pos, face, cfg);
    }

    public void activateNode(FaceAddress address, BlockPos pos, Direction face,
                             FaceConfigComposite cfg) {
        faceConfigHandler.activateNode(address, pos, face, cfg);
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
        lifecycleService.destroyBlocks(List.of(pos));
        com.coobird.staticlogistics.content.item.LinkOperationHelper.cleanStoredNodesForPos(level, pos);
    }

    // 容器配置（委托 ContainerConfigService）

    @Nullable
    public ContainerConfig getContainerConfig(BlockPos pos) {
        return containerConfigService.get(pos);
    }

    public ContainerConfig getOrCreateContainerConfig(BlockPos pos) {
        ContainerConfig config = containerConfigService.getOrCreate(pos);
        config.setOnDirty(changeHandler::onContainerConfigChanged);
        return config;
    }

    // 缓存（委托 CacheManager）

    public Set<FaceAddress> getActiveProviderKeys() {
        return cacheManager.getActiveProviderKeys();
    }

    public FaceAddress[] getActiveProviderKeysArray() {
        return cacheManager.getActiveProviderKeysArray();
    }

    public boolean hasActiveProviders() {
        return cacheManager.hasProviders();
    }

    public Set<FaceAddress> getAllConfigKeys() {
        return faceConfigHandler.getAllConfigKeys();
    }

    // 网络同步直接操作

    public void syncToPlayer(ServerPlayer player) {
        List<Map.Entry<FaceAddress, FaceConfigComposite>> nonDefault = new ArrayList<>();
        for (var entry : getConfigRepository().getAllEntries()) {
            FaceConfigComposite cfg = entry.getValue();
            if (cfg != null && !cfg.isDefault()) {
                FaceAddress address = entry.getKey();
                nonDefault.add(Map.entry(address, cfg));
            }
        }
        if (!nonDefault.isEmpty()) {
            topologySyncPort.syncBulkToPlayer(player, nonDefault);
        }
    }

    public void syncConfigToClients(BlockPos pos) {
        for (Direction face : Direction.values()) {
            FaceAddress address = FaceAddress.of(pos, face);
            FaceConfigComposite cfg = getFaceConfig(address);
            if (cfg != null) scheduleNetworkSync(createNodeFromKey(address));
        }
    }

    public void syncNodeToDimensionDirect(LogisticsNode node) {
        FaceConfigComposite cfg = getFaceConfig(FaceAddress.of(node));
        if (cfg != null) {
            for (ServerPlayer player : level.players()) {
                topologySyncPort.syncToPlayer(player, node.gPos().pos(), node.face(), cfg);
            }
        }
    }

    public void syncNodeToPlayer(ServerPlayer player, LogisticsNode node) {
        FaceConfigComposite cfg = getFaceConfig(FaceAddress.of(node));
        if (cfg != null) topologySyncPort.syncToPlayer(player, node.gPos().pos(), node.face(), cfg);
    }

    // 批量操作

    public void onBlocksRemovedBulk(Collection<BlockPos> positions) {
        if (positions.isEmpty()) return;
        lifecycleService.destroyBlocks(positions);
        saveScheduler.scheduleSave();
    }

    // 工厂

    public static LinkManager get(ServerLevel level) {
        LinkManagerStorage stor = level.getDataStorage().computeIfAbsent(
            tag -> {
                LinkManager mgr = new LinkManager(level);
                LinkManagerStorage stor2 = new LinkManagerStorage(level, mgr);
                mgr.setStorage(stor2);
                return stor2;
            },
            () -> {
                LinkManager mgr = new LinkManager(level);
                LinkManagerStorage stor2 = new LinkManagerStorage(level, mgr);
                mgr.setStorage(stor2);
                return stor2;
            },
            "static_logistics_configs"
        );
        return stor.linkManager;
    }
}
