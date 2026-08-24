package com.coobird.staticlogistics.logistics.node;

import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.api.group.GroupKey;
import com.coobird.staticlogistics.api.group.GroupRef;
import com.coobird.staticlogistics.content.item.LinkOperationHelper;
import com.coobird.staticlogistics.logistics.group.GlobalLogisticsManager;
import com.coobird.staticlogistics.logistics.group.PlayerGroupStore;
import com.coobird.staticlogistics.logistics.node.persistence.ConfigRepository;
import com.coobird.staticlogistics.logistics.node.persistence.ContainerRepository;
import com.coobird.staticlogistics.logistics.node.sync.PendingSyncBuffer;
import com.coobird.staticlogistics.logistics.node.sync.SyncManager;
import com.coobird.staticlogistics.logistics.node.sync.TopologySyncPort;
import com.coobird.staticlogistics.logistics.redstone.RedstoneControlStore;
import com.coobird.staticlogistics.transfer.CapabilityCache;
import com.coobird.staticlogistics.transfer.NodeQueryService;
import com.mojang.authlib.GameProfile;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.capabilities.BlockCapability;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.UnaryOperator;

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
 *   <li>{@link TopologySyncPort} — 拓扑网络输出端口</li>
 * </ul>
 */
public class LinkManager {
    private final FaceConfigHandler faceConfigHandler;
    private final ServerLevel level;
    private final ContainerRepository containerRepository;
    private final ContainerConfigService containerConfigService;
    private final TopologySyncPort topologySyncPort;
    private final CacheManager cacheManager;
    private final CapabilityCache capabilityCache;
    private final LinkChangeHandler changeHandler;
    private final LinkGraphService linkGraphService;
    private final NodeLifecycleService lifecycleService;

    private final LinkDirtyTracker dirtyTracker = new LinkDirtyTracker();
    private final LinkSaveScheduler saveScheduler = new LinkSaveScheduler();
    private final PendingSyncBuffer syncBuffer = new PendingSyncBuffer();

    // key 级版本计数器：确保删除重建后新配置的版本号高于旧配置
    private final Map<FaceAddress, Long> keyVersions = new HashMap<>();

    public LinkManager(ServerLevel level) {
        this.level = level;
        ConfigRepository configRepository = new ConfigRepository();
        this.containerRepository = new ContainerRepository();
        this.cacheManager = new CacheManager();
        this.capabilityCache = new CapabilityCache(level);
        GlobalLogisticsManager globalManager = GlobalLogisticsManager.get(level.getServer());
        SyncManager syncManager = new SyncManager(level.dimension(), globalManager);
        this.topologySyncPort = TopologySyncPort.create(level);
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
    public long nextVersion(FaceAddress key) {
        return keyVersions.merge(key, 1L, Long::sum);
    }

    /**
     * 将对象本地版本提升为该键的下一个单调版本。
     */
    long normalizeVersion(FaceAddress key, long observedVersion) {
        return keyVersions.compute(key, (ignored, current) -> {
            if (current == null) return Math.max(1L, observedVersion);
            return Math.max(current + 1L, observedVersion);
        });
    }

    public void initKeyVersions() {
        for (FaceAddress key : getAllConfigKeys()) {
            FaceConfigComposite cfg = getFaceConfig(key);
            if (cfg != null) {
                keyVersions.merge(key, cfg.getVersion(), Math::max);
            }
        }
    }

    public void markFaceDirty(FaceAddress faceKey) {
        dirtyTracker.markFaceDirty(faceKey);
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
        capabilityCache.shutdown();
        saveScheduler.shutdown();
    }

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

    LinkChangeHandler getChangeHandler() {
        return changeHandler;
    }

    boolean isLifecycleRemovalInProgress() {
        return lifecycleService.isHandoffInProgress();
    }

    private record DeferredNetworkKey(LinkManager manager, LogisticsNode node) {
    }

    private record DeferredRedstoneSyncKey(LinkManager manager, GroupKey groupKey) {
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

    SyncManager getSyncManager() {
        return faceConfigHandler.syncManager;
    }

    public LogisticsNode createNodeFromKey(FaceAddress key) {
        return key.toNode(level.dimension());
    }

    @Nullable
    public FaceConfigComposite getFaceConfig(FaceAddress key) {
        return faceConfigHandler.getFaceConfig(key);
    }

    public FaceConfigComposite getOrCreateFaceConfig(BlockPos pos, Direction face) {
        return faceConfigHandler.getOrCreateFaceConfig(pos, face);
    }

    public void claimOwner(LogisticsNode node, GameProfile profile) {
        faceConfigHandler.claimOwner(node, profile);
    }

    public void refreshOwnerProfile(LogisticsNode node, GameProfile profile) {
        faceConfigHandler.refreshOwnerProfile(node, profile);
    }

    public void addNodeToGroup(LogisticsNode node, GroupRef group) {
        faceConfigHandler.addNodeToGroup(node, group);
    }

    public void renameGroupMetadata(LogisticsNode node, GroupKey groupKey, String displayName) {
        faceConfigHandler.renameGroupMetadata(node, groupKey, displayName);
    }

    public void mergeGroupMetadata(LogisticsNode node, GroupRef source, GroupRef target) {
        faceConfigHandler.mergeGroupMetadata(node, source, target);
    }

    public void restoreFaceSnapshot(LogisticsNode node, CompoundTag snapshot) {
        faceConfigHandler.restoreFaceSnapshot(node, snapshot);
    }

    /**
     * 恢复“修改前不存在此面”的状态，不移交尚未提交的升级物。
     */
    public void restoreFaceAbsence(LogisticsNode node) {
        if (node == null) return;
        FaceConfigComposite config = getFaceConfig(FaceAddress.of(node));
        if (config == null) return;
        faceConfigHandler.removeFaceAfterHandoff(FaceAddress.of(node), config, true, true);
    }

    /**
     * 恢复容器升级快照；修改前不存在时直接丢弃尚未提交的临时配置。
     */
    public void restoreContainerSnapshot(BlockPos pos, boolean existed, @Nullable CompoundTag upgrades) {
        if (pos == null) return;
        if (existed) {
            if (upgrades == null) {
                throw new IllegalArgumentException("Existing container snapshot must contain upgrades");
            }
            ContainerConfig config = getOrCreateContainerConfig(pos);
            config.getUpgrades().deserializeNBT(level.registryAccess(), upgrades.copy());
            config.markDirty();
            markContainerDirty(pos.asLong());
            return;
        }
        ContainerConfig current = getContainerConfig(pos);
        if (current != null && !containerConfigService.removeAfterHandoff(pos, current)) {
            throw new IllegalStateException("Container snapshot changed during rollback at " + pos);
        }
        markContainerDirty(pos.asLong());
    }

    public void removeLink(LogisticsNode source, LogisticsNode target) {
        linkGraphService.removeEdge(source, target);
    }

    public void addLink(GroupKey groupKey, LogisticsNode source, LogisticsNode target) {
        linkGraphService.addEdge(groupKey, source, target);
    }

    public void removeLink(GroupKey groupKey, LogisticsNode source, LogisticsNode target) {
        linkGraphService.removeEdge(groupKey, source, target);
    }

    public void removeLinkWithoutCleanup(GroupKey groupKey, LogisticsNode source, LogisticsNode target) {
        linkGraphService.removeEdgeWithoutCleanup(groupKey, source, target);
    }

    public NodeLifecycleService.DisconnectedRemoval prepareDisconnectedRemoval(
        Collection<LogisticsNode> nodes
    ) {
        return lifecycleService.prepareDisconnectedRemoval(nodes);
    }

    public void applyDisconnectedRemoval(NodeLifecycleService.DisconnectedRemoval removal) {
        lifecycleService.applyDisconnectedRemoval(removal);
    }

    public void removeNodeFromGroup(GroupKey groupKey, LogisticsNode node) {
        linkGraphService.removeNodeFromGroup(groupKey, node);
    }

    public void removeNodeFromGroupWithoutCleanup(GroupKey groupKey, LogisticsNode node) {
        linkGraphService.removeNodeFromGroupWithoutCleanup(groupKey, node);
    }

    boolean purgeInboundReferences(LogisticsNode removedNode) {
        return linkGraphService.purgeInboundReferences(removedNode);
    }

    boolean purgeInboundReferences(Collection<LogisticsNode> removedNodes) {
        return linkGraphService.purgeInboundReferences(removedNodes);
    }

    void cascadeRemove(LogisticsNode node, FaceConfigComposite config) {
        linkGraphService.cascadeRemove(node, config);
    }

    void repairReciprocalEdges(LogisticsNode node, FaceConfigComposite config) {
        linkGraphService.repairReciprocalEdges(node, config);
    }

    public void cleanUpFaceIfNeeded(LogisticsNode node, FaceConfigComposite cfg) {
        lifecycleService.removeOrphan(node, cfg);
    }

    public void removeFaceConfig(FaceAddress key) {
        lifecycleService.removeFace(key);
    }

    public void removeFaceConfigDataOnly(FaceAddress key) {
        faceConfigHandler.removeFaceConfigDataOnly(key);
    }

    public void refreshLocalCache(FaceAddress key, BlockPos pos, Direction face, FaceConfigComposite cfg) {
        faceConfigHandler.refreshLocalCache(key, pos, face, cfg);
    }

    public void activateNode(FaceAddress key, BlockPos pos, Direction face, FaceConfigComposite cfg) {
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
        onBlocksRemovedBulk(List.of(pos));
        LinkOperationHelper.cleanStoredNodesForPos(level, pos);
    }

    @Nullable
    public ContainerConfig getContainerConfig(BlockPos pos) {
        return containerConfigService.get(pos);
    }

    public ContainerConfig getOrCreateContainerConfig(BlockPos pos) {
        ContainerConfig config = containerConfigService.getOrCreate(pos);
        config.setOnDirty(changeHandler::onContainerConfigChanged);
        return config;
    }

    public Set<FaceAddress> getActiveProviderKeys() {
        return cacheManager.getActiveProviderKeys();
    }

    public FaceAddress[] getActiveProviderKeysArray() {
        return cacheManager.getActiveProviderKeysArray();
    }

    public <C> C getCapability(BlockPos pos, Direction face,
                               BlockCapability<C, Direction> capability) {
        return capabilityCache.get(pos, face, capability);
    }

    public void invalidateCapabilityCache(BlockPos pos, Direction face) {
        capabilityCache.invalidateFace(pos, face);
    }

    public void invalidateCapabilityCache(BlockPos pos) {
        capabilityCache.invalidateBlock(pos);
    }

    public Set<FaceAddress> getAllConfigKeys() {
        return faceConfigHandler.getAllConfigKeys();
    }

    /**
     * 返回具有面配置或容器配置的全部方块位置。
     */
    public Set<BlockPos> getAllConfiguredBlockPositions() {
        Set<BlockPos> positions = new HashSet<>();
        for (FaceAddress key : getAllConfigKeys()) positions.add(key.pos());
        for (long key : containerRepository.keySet()) positions.add(BlockPos.of(key));
        return positions;
    }

    public void syncToPlayer(ServerPlayer player) {
        // 直接从 repository 收集非默认配置，避免遍历所有 key 再逐个 getFaceConfig
        List<Map.Entry<FaceAddress, FaceConfigComposite>> nonDefault = new ArrayList<>();
        for (var entry : getConfigRepository().getAllEntries()) {
            FaceConfigComposite cfg = entry.getValue();
            if (cfg != null && !cfg.isDefault()) {
                nonDefault.add(Map.entry(entry.getKey(), cfg));
            }
        }
        if (!nonDefault.isEmpty()) {
            topologySyncPort.syncBulkToPlayer(player, nonDefault);
        }
    }

    public void onBlocksRemovedBulk(Collection<BlockPos> positions) {
        if (positions.isEmpty()) return;
        for (BlockPos pos : positions) capabilityCache.invalidateBlock(pos);
        lifecycleService.destroyBlocks(positions);
        saveScheduler.scheduleSave();
    }

    /**
     * 在同一维度内搬移一个容器的全部面配置。用于 Sable 在世界与 plot 间转移方块时保留物流身份。
     */
    public boolean relocateBlock(BlockPos oldPos, BlockPos newPos) {
        return relocateBlock(oldPos, newPos, Rotation.NONE);
    }

    /**
     * 在同一维度内搬移一个容器的全部面配置，并按照结构装配时的旋转同步面朝向。
     */
    public boolean relocateBlock(BlockPos oldPos, BlockPos newPos, Rotation rotation) {
        Rotation appliedRotation = rotation == null ? Rotation.NONE : rotation;
        return relocateBlock(oldPos, newPos, appliedRotation::rotate);
    }

    /**
     * 搬移一个容器，并使用任意三维结构变换同步其面朝向。
     */
    public boolean relocateBlock(BlockPos oldPos, BlockPos newPos,
                                 UnaryOperator<Direction> faceTransform) {
        if (oldPos == null || newPos == null) return false;
        return relocateBlocks(Map.of(oldPos, newPos), faceTransform);
    }

    /**
     * 原子迁移整座刚性结构，并正确处理节点坐标互换形成的循环。
     */
    public boolean relocateBlocks(Map<BlockPos, BlockPos> positionMoves,
                                  UnaryOperator<Direction> faceTransform) {
        if (positionMoves == null || positionMoves.isEmpty()) return false;
        UnaryOperator<Direction> appliedTransform = faceTransform == null
            ? UnaryOperator.identity() : faceTransform;

        Map<FaceAddress, FaceAddress> faceMoves = new LinkedHashMap<>();
        Map<LogisticsNode, LogisticsNode> nodeMoves = new LinkedHashMap<>();
        Map<BlockPos, ContainerConfig> movingContainers = new LinkedHashMap<>();
        for (var positionMove : positionMoves.entrySet()) {
            BlockPos oldPos = positionMove.getKey();
            BlockPos newPos = positionMove.getValue();
            if (oldPos == null || newPos == null) continue;
            for (Direction face : Direction.values()) {
                FaceAddress oldKey = FaceAddress.of(oldPos, face);
                if (getFaceConfig(oldKey) == null) continue;
                FaceAddress newKey = FaceAddress.of(newPos, appliedTransform.apply(face));
                faceMoves.put(oldKey, newKey);
                nodeMoves.put(createNodeFromKey(oldKey), createNodeFromKey(newKey));
            }
            ContainerConfig container = containerRepository.get(oldPos.asLong());
            if (container != null) movingContainers.put(oldPos, container);
        }
        Set<FaceAddress> movingKeys = faceMoves.keySet();
        Set<FaceAddress> destinations = new HashSet<>();
        for (FaceAddress newKey : faceMoves.values()) {
            if (!destinations.add(newKey)) return false;
            if (!movingKeys.contains(newKey) && getFaceConfig(newKey) != null) return false;
        }
        if (faceMoves.isEmpty() && movingContainers.isEmpty()) return false;
        Set<Long> movingContainerPositions = new HashSet<>();
        movingContainers.keySet().forEach(pos -> movingContainerPositions.add(pos.asLong()));
        Set<Long> containerDestinations = new HashSet<>();
        for (BlockPos oldPos : movingContainers.keySet()) {
            BlockPos newPos = positionMoves.get(oldPos);
            if (newPos == null || !containerDestinations.add(newPos.asLong())) return false;
            if (!movingContainerPositions.contains(newPos.asLong())
                && containerRepository.get(newPos.asLong()) != null) return false;
        }
        boolean facesChanged = faceMoves.entrySet().stream().anyMatch(move -> !move.getKey().equals(move.getValue()));
        boolean positionsChanged = positionMoves.entrySet().stream()
            .anyMatch(move -> move.getKey() != null && !move.getKey().equals(move.getValue()));
        if (!facesChanged && !positionsChanged) return false;

        // 所有维度中的入站与出站引用必须在移动面之前统一换址。
        for (ServerLevel serverLevel : level.getServer().getAllLevels()) {
            LinkManager manager = LinkManager.get(serverLevel);
            for (FaceAddress key : manager.getAllConfigKeys()) {
                FaceConfigComposite config = manager.getFaceConfig(key);
                if (config != null && config.remapLinkedNodes(nodeMoves)) {
                    manager.markFaceDirty(key);
                    // 客户端只公开双端互惠的连接；静止端也必须同步其已换址的远端引用。
                    // 对正在移动的旧端点，后续删除消息会覆盖这里排队的旧坐标同步。
                    manager.scheduleNetworkSync(manager.createNodeFromKey(key));
                }
            }
        }

        movingContainers.keySet().forEach(pos -> containerRepository.remove(pos.asLong()));
        for (var movingContainer : movingContainers.entrySet()) {
            BlockPos oldPos = movingContainer.getKey();
            BlockPos newPos = positionMoves.get(oldPos);
            ContainerConfig container = movingContainer.getValue();
            container.remapLinkedFaces(faceMoves);
            container.setPos(newPos);
            containerRepository.put(newPos.asLong(), container);
            markContainerDirty(oldPos.asLong());
            markContainerDirty(newPos.asLong());
        }

        GlobalLogisticsManager global = GlobalLogisticsManager.get(level.getServer());
        Map<FaceAddress, FaceConfigComposite> movingConfigs = new LinkedHashMap<>();
        for (FaceAddress oldKey : faceMoves.keySet()) {
            FaceConfigComposite config = getFaceConfig(oldKey);
            if (config != null) movingConfigs.put(oldKey, config);
        }
        movingConfigs.keySet().forEach(getConfigRepository()::remove);
        movingConfigs.forEach((oldKey, config) -> {
            cacheManager.remove(oldKey);
            LogisticsNode oldNode = createNodeFromKey(oldKey);
            for (var group : List.copyOf(config.faceConfig.getGroups())) {
                global.unregisterNode(group.key(), oldNode);
            }
        });
        for (var move : faceMoves.entrySet()) {
            FaceAddress oldKey = move.getKey();
            FaceAddress newKey = move.getValue();
            FaceConfigComposite config = movingConfigs.get(oldKey);
            if (config == null) continue;
            LogisticsNode oldNode = createNodeFromKey(oldKey);
            LogisticsNode newNode = createNodeFromKey(newKey);
            BlockPos newPos = newKey.pos();
            var groups = List.copyOf(config.faceConfig.getGroups());
            var role = config.determineRole();

            getConfigRepository().put(newKey, config);
            config.setPosition(newPos);
            config.setOnDirty(changed -> changeHandler.onFaceConfigChanged(
                newKey, newPos, newKey.face(), changed));
            refreshLocalCache(newKey, newPos, newKey.face(), config);
            keyVersions.put(newKey, Math.max(
                keyVersions.getOrDefault(newKey, 0L), config.getVersion()));

            for (var group : groups) {
                global.registerNode(group, newNode, role);
                global.markGroupDirty(group.key());
            }
            NodeQueryService.invalidateNode(level.getServer(), oldNode);
            NodeQueryService.invalidateNode(level.getServer(), newNode);
            scheduleNetworkRemoval(oldNode, nextVersion(oldKey), config.faceConfig.getOwner());
            scheduleNetworkSync(newNode);
            markFaceDirty(oldKey);
            markFaceDirty(newKey);
        }
        PlayerGroupStore.get(level.getServer()).remapConnectionNodes(nodeMoves);
        Map<GlobalPos, GlobalPos> controllerMoves = new LinkedHashMap<>();
        for (var positionMove : positionMoves.entrySet()) {
            BlockPos oldPos = positionMove.getKey();
            BlockPos newPos = positionMove.getValue();
            if (oldPos == null || newPos == null) continue;
            controllerMoves.put(
                GlobalPos.of(level.dimension(), oldPos),
                GlobalPos.of(level.dimension(), newPos));
            invalidateCapabilityCache(oldPos);
            invalidateCapabilityCache(newPos);
        }
        Set<GroupKey> changedControlGroups = RedstoneControlStore.get(level.getServer())
            .remapNodes(level.getServer(), nodeMoves, controllerMoves);
        for (GroupKey groupKey : changedControlGroups) {
            Runnable sync = () -> topologySyncPort.syncRedstoneGroups(Set.of(groupKey));
            if (!NodeMutationTransaction.defer(level.getServer(),
                new DeferredRedstoneSyncKey(this, groupKey), sync)) {
                sync.run();
            }
        }
        return true;
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
