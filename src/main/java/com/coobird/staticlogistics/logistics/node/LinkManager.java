package com.coobird.staticlogistics.logistics.node;

import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.api.group.GroupKey;
import com.coobird.staticlogistics.api.group.GroupRef;
import com.coobird.staticlogistics.content.item.LinkOperationHelper;
import com.coobird.staticlogistics.logistics.group.GlobalLogisticsManager;
import com.coobird.staticlogistics.logistics.group.PlayerGroupStore;
import com.coobird.staticlogistics.logistics.node.persistence.ContainerRepository;
import com.coobird.staticlogistics.logistics.node.persistence.EndpointRepository;
import com.coobird.staticlogistics.logistics.node.sync.NodeMembershipPublisher;
import com.coobird.staticlogistics.logistics.node.sync.PendingSyncBuffer;
import com.coobird.staticlogistics.network.sync.NetworkSyncManager;
import com.coobird.staticlogistics.transfer.CapabilityCache;
import com.coobird.staticlogistics.transfer.NodeQueryService;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Rotation;
import net.minecraftforge.common.capabilities.Capability;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.UnaryOperator;

/// 协调单个维度的节点配置、链接、持久化和同步。
public class LinkManager {
    private static final LinkMutationPermit MUTATION_PERMIT = new LinkMutationPermit();
    private static final Map<MinecraftServer, Map<ServerLevel, LinkManager>> RUNTIMES =
        new IdentityHashMap<>();
    private final EndpointRepository endpoints;
    private final NodeMembershipPublisher membershipPublisher;
    private final ServerLevel level;
    private final ContainerRepository containerRepository;
    private final NetworkSyncManager networkSync;
    private final ActiveProviderIndex activeProviders;
    private final CapabilityCache capabilityCache;
    private final LinkChangeHandler changeHandler;
    private final LinkGraphService linkGraphService;
    private final NodeLifecycleService lifecycleService;
    private boolean closed;

    final LinkManagerStorage storage;
    private final PendingSyncBuffer syncBuffer = new PendingSyncBuffer();

    /// 键级版本计数器：确保删除重建后新配置的版本号高于旧配置。
    private final Map<EndpointAddress, Long> keyVersions = new HashMap<>();

    public LinkManager(ServerLevel level) {
        this.level = level;
        this.storage = new LinkManagerStorage(this, level.registryAccess());
        this.containerRepository = new ContainerRepository();
        this.endpoints = new EndpointRepository(containerRepository);
        this.activeProviders = new ActiveProviderIndex();
        this.capabilityCache = new CapabilityCache(level);
        GlobalLogisticsManager globalManager = GlobalLogisticsManager.get(level.getServer());
        this.membershipPublisher = new NodeMembershipPublisher(globalManager);
        this.networkSync = new NetworkSyncManager(level);
        DropHandler dropHandler = new DropHandler(level);
        this.linkGraphService = new LinkGraphService(level.getServer(), globalManager);

        this.changeHandler = new LinkChangeHandler(level, membershipPublisher, this);

        this.lifecycleService = new NodeLifecycleService(
            level, this, endpoints, containerRepository, activeProviders, dropHandler::beginHandoff);
    }

    /// 版本管理
    public long nextVersion(EndpointAddress key) {
        return keyVersions.merge(key, 1L, Long::sum);
    }

    /// 将对象本地版本提升为该键的下一个单调版本。
    long normalizeVersion(EndpointAddress key, long observedVersion) {
        return keyVersions.compute(key, (ignored, current) -> {
            if (current == null) return Math.max(1L, observedVersion);
            return Math.max(current + 1L, observedVersion);
        });
    }

    /// 反序列化完成后统一恢复运行状态，存储层不直接发布事件或安装回调。
    void initializeLoadedState() {
        for (var entry : endpoints.getAllEntries()) {
            EndpointAddress key = entry.getKey();
            EndpointConfig config = entry.getValue();
            keyVersions.merge(key, config.getVersion(), Math::max);
            activeProviders.update(key, config);
        }
        for (var entry : endpoints.getAllEntries()) {
            membershipPublisher.syncNode(createNodeFromKey(entry.getKey()), entry.getValue());
        }
        for (var entry : endpoints.getAllEntries()) {
            bindEndpointChanges(entry.getKey(), entry.getValue());
        }
        for (var entry : containerRepository.getAllEntries()) {
            entry.getValue().setOnDirty(changeHandler::onContainerConfigChanged);
        }
    }

    private void bindEndpointChanges(EndpointAddress key, EndpointConfig config) {
        config.setOnDirty(changed -> changeHandler.onEndpointConfigChanged(key, changed));
    }

    public void markFaceDirty(EndpointAddress faceKey) {
        storage.markFaceDirty(faceKey);
    }

    public void markContainerDirty(long containerKey) {
        storage.markContainerDirty(containerKey);
    }

    private void shutdown() {
        if (closed) return;
        closed = true;
        GlobalLogisticsManager global = GlobalLogisticsManager.get(level.getServer());
        for (EndpointAddress key : endpoints.keySet()) {
            global.unregisterNode(createNodeFromKey(key));
        }
        capabilityCache.shutdown();
    }

    public void scheduleNetworkSync(LogisticsNode node) {
        if (NodeMutationTransaction.defer(level.getServer(), new DeferredNetworkKey(this, node),
            () -> scheduleNetworkSync(node))) return;
        EndpointConfig cfg = getEndpointConfig(EndpointAddress.of(node));
        if (cfg == null) return;
        syncBuffer.schedule(level, node, cfg);
    }

    void scheduleNetworkRemoval(LogisticsNode node, long version, @Nullable UUID ownerId) {
        if (NodeMutationTransaction.defer(level.getServer(), new DeferredNetworkKey(this, node),
            () -> scheduleNetworkRemoval(node, version, ownerId))) return;
        EndpointConfig current = getEndpointConfig(EndpointAddress.of(node));
        if (current != null) {
            /// 删除意图延迟执行期间可能发生重建，客户端应收到最终存在的端点。
            syncBuffer.schedule(level, node, current);
        } else {
            syncBuffer.scheduleRemoval(node, version, ownerId);
        }
    }

    public void flushPendingNetworkSync(long deadline) {
        if (System.nanoTime() - deadline >= 0L) return;
        syncBuffer.flush(networkSync::enqueueBroadcast);
        networkSync.flushPending(deadline);
    }

    public void snapshotDelivered(ServerPlayer player, long contentBoundary) {
        networkSync.snapshotDelivered(player, contentBoundary);
    }

    boolean isLifecycleRemovalInProgress() {
        return lifecycleService.isHandoffInProgress();
    }

    private record DeferredNetworkKey(LinkManager manager, LogisticsNode node) {
    }

    ContainerRepository getContainerRepository() {
        return containerRepository;
    }

    EndpointRepository getEndpointRepository() {
        return endpoints;
    }

    public LogisticsNode createNodeFromKey(EndpointAddress key) {
        return key.toNode(level.dimension());
    }

    @Nullable
    public EndpointConfig getEndpointConfig(EndpointAddress key) {
        return endpoints.get(key);
    }

    public EndpointConfig getOrCreateEndpointConfig(BlockPos pos, Direction face) {
        return getOrCreateEndpointConfig(EndpointAddress.of(pos, face));
    }

    public EndpointConfig getOrCreateChannel(LogisticsNode node) {
        return getOrCreateEndpointConfig(EndpointAddress.of(node));
    }

    private EndpointConfig getOrCreateEndpointConfig(EndpointAddress key) {
        BlockPos pos = key.pos();
        getOrCreateContainerConfig(pos);
        boolean isNew = !endpoints.containsKey(key);
        EndpointConfig config = endpoints.getOrCreate(key);
        bindEndpointChanges(key, config);
        if (isNew) {
            config.setVersion(nextVersion(key));
            lifecycleService.bindCurrentEndpoint(config, pos);
        }
        return config;
    }

    public void claimOwner(LogisticsNode node, GameProfile profile) {
        if (node == null || profile == null) return;
        EndpointConfig config = getEndpointConfig(EndpointAddress.of(node));
        if (config == null || config.faceConfig.getOwner() != null) return;
        config.setOwner(MUTATION_PERMIT, profile.getId(), profile.getName(), profile);
    }

    public void refreshOwnerProfile(LogisticsNode node, GameProfile profile) {
        if (node == null || profile == null) return;
        EndpointConfig config = getEndpointConfig(EndpointAddress.of(node));
        if (config == null || !profile.getId().equals(config.faceConfig.getOwner())) return;
        config.setOwner(MUTATION_PERMIT, profile.getId(), profile.getName(), profile);
    }

    public void addNodeToGroup(LogisticsNode node, GroupRef group) {
        if (node == null || group == null) return;
        EndpointConfig config = getEndpointConfig(EndpointAddress.of(node));
        if (config == null) return;
        config.addGroup(MUTATION_PERMIT, group);
    }

    public void renameGroupMetadata(LogisticsNode node, GroupKey groupKey, String displayName) {
        if (node == null || groupKey == null || displayName == null || displayName.isEmpty()) return;
        EndpointConfig config = getEndpointConfig(EndpointAddress.of(node));
        if (config == null) return;
        config.renameGroup(MUTATION_PERMIT, groupKey, displayName);
    }

    public void mergeGroupMetadata(LogisticsNode node,
                                   GroupRef source,
                                   GroupRef target) {
        if (node == null || source == null || target == null) return;
        EndpointConfig config = getEndpointConfig(EndpointAddress.of(node));
        if (config == null) return;
        config.mergeGroup(MUTATION_PERMIT, source, target);
    }

    public void restoreFaceSnapshot(LogisticsNode node, CompoundTag snapshot) {
        if (node == null || snapshot == null) return;
        EndpointConfig config = getEndpointConfig(EndpointAddress.of(node));
        if (config == null) {
            config = getOrCreateEndpointConfig(EndpointAddress.of(node));
        }
        config.restoreSnapshot(MUTATION_PERMIT, level.registryAccess(), snapshot);
    }

    /// 恢复“修改前不存在此面”的状态，不移交尚未提交的升级物。
    public void restoreFaceAbsence(LogisticsNode node) {
        if (node == null) return;
        EndpointConfig config = getEndpointConfig(EndpointAddress.of(node));
        if (config == null) return;
        lifecycleService.removeFaceAfterHandoff(EndpointAddress.of(node), config, true, true);
    }

    /// 恢复容器升级快照；修改前不存在时直接丢弃尚未提交的临时配置。
    public void restoreContainerSnapshot(BlockPos pos, boolean existed, @Nullable CompoundTag upgrades) {
        if (pos == null) return;
        if (existed) {
            if (upgrades == null) {
                throw new IllegalArgumentException("Existing container snapshot must contain upgrades");
            }
            ContainerConfig config = getOrCreateContainerConfig(pos);
            config.getUpgrades().deserializeNBT(upgrades.copy());
            config.markDirty();
            markContainerDirty(pos.asLong());
            return;
        }
        ContainerConfig current = getContainerConfig(pos);
        if (current != null && !containerRepository.removeAfterHandoff(pos, current)) {
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

    void cascadeRemove(LogisticsNode node, EndpointConfig config) {
        linkGraphService.cascadeRemove(node, config);
    }

    public void cleanUpFaceIfNeeded(LogisticsNode node, EndpointConfig cfg) {
        lifecycleService.removeOrphan(node, cfg);
    }

    public boolean removeFaceConfig(EndpointAddress key) {
        return lifecycleService.removeFace(key);
    }

    public void removeFaceConfigDataOnly(EndpointAddress key) {
        lifecycleService.removeFaceConfigDataOnly(key);
    }

    void refreshLocalCache(EndpointAddress key, EndpointConfig config) {
        activeProviders.update(key, config);
    }

    public void markOrphanScanNeeded() {
        lifecycleService.markOrphanScanNeeded();
    }

    public boolean isOrphanScanNeeded() {
        return lifecycleService.isOrphanScanNeeded();
    }

    public void validateOrphanedConfigs() {
        lifecycleService.validateOrphanedConfigs();
    }

    public void onBlockRemoved(BlockPos pos) {
        onBlocksRemovedBulk(List.of(pos));
        LinkOperationHelper.cleanStoredNodesForPos(level, pos);
    }

    @Nullable
    public ContainerConfig getContainerConfig(BlockPos pos) {
        return containerRepository.get(pos);
    }

    public ContainerConfig getOrCreateContainerConfig(BlockPos pos) {
        ContainerConfig config = containerRepository.getOrCreate(pos);
        config.setOnDirty(changeHandler::onContainerConfigChanged);
        return config;
    }

    public List<EndpointAddress> getActiveProviderKeys() {
        return activeProviders.snapshot();
    }

    public <C> C getCapability(BlockPos pos, Direction face,
                               Capability<C> capability) {
        return capabilityCache.get(pos, face, capability);
    }

    public void invalidateCapabilityCache(BlockPos pos, Direction face) {
        capabilityCache.invalidateFace(pos, face);
    }

    public void invalidateCapabilityCache(BlockPos pos) {
        capabilityCache.invalidateBlock(pos);
    }

    public Set<EndpointAddress> getAllConfigKeys() {
        return endpoints.keySet();
    }

    public List<EndpointAddress> getConfiguredBlockRepresentatives() {
        return endpoints.blockRepresentatives();
    }

    /// 返回具有面配置或容器配置的全部方块位置。
    public Set<BlockPos> getAllConfiguredBlockPositions() {
        Set<BlockPos> positions = new HashSet<>();
        for (EndpointAddress key : getAllConfigKeys()) positions.add(key.pos());
        for (long key : containerRepository.keySet()) positions.add(BlockPos.of(key));
        return positions;
    }

    public void onBlocksRemovedBulk(Collection<BlockPos> positions) {
        if (positions.isEmpty()) return;
        for (BlockPos pos : positions) capabilityCache.invalidateBlock(pos);
        lifecycleService.destroyBlocks(positions);
        storage.setDirty();
    }

    /// 在同一维度内搬移容器的面配置。
    public boolean relocateBlock(BlockPos oldPos, BlockPos newPos) {
        return relocateBlock(oldPos, newPos, Rotation.NONE);
    }

    /// 在同一维度内搬移一个容器的全部面配置，并按照结构装配时的旋转同步面朝向。
    public boolean relocateBlock(BlockPos oldPos, BlockPos newPos, Rotation rotation) {
        Rotation appliedRotation = rotation == null ? Rotation.NONE : rotation;
        return relocateBlock(oldPos, newPos, appliedRotation::rotate);
    }

    /// 搬移一个容器，并使用任意三维结构变换同步其面朝向。
    public boolean relocateBlock(BlockPos oldPos, BlockPos newPos,
                                 UnaryOperator<Direction> faceTransform) {
        if (oldPos == null || newPos == null) return false;
        return relocateBlocks(Map.of(oldPos, newPos), faceTransform);
    }

    /// 原子迁移整座刚性结构，并正确处理节点坐标互换形成的循环。
    public boolean relocateBlocks(Map<BlockPos, BlockPos> positionMoves,
                                  UnaryOperator<Direction> faceTransform) {
        if (positionMoves == null || positionMoves.isEmpty()) return false;
        UnaryOperator<Direction> appliedTransform = faceTransform == null
            ? UnaryOperator.identity() : faceTransform;

        Map<EndpointAddress, EndpointAddress> faceMoves = new LinkedHashMap<>();
        Map<LogisticsNode, LogisticsNode> nodeMoves = new LinkedHashMap<>();
        Map<BlockPos, ContainerConfig> movingContainers = new LinkedHashMap<>();
        for (var positionMove : positionMoves.entrySet()) {
            BlockPos oldPos = positionMove.getKey();
            BlockPos newPos = positionMove.getValue();
            if (oldPos == null || newPos == null) continue;
            ContainerConfig sourceContainer = getContainerConfig(oldPos);
            for (EndpointAddress oldKey : sourceContainer == null ? Set.<EndpointAddress>of() : sourceContainer.getLinkedFaceKeys()) {
                Direction face = oldKey.face();
                if (getEndpointConfig(oldKey) == null) continue;
                EndpointAddress newKey = new EndpointAddress(newPos.asLong(), appliedTransform.apply(face), oldKey.channel());
                faceMoves.put(oldKey, newKey);
                nodeMoves.put(createNodeFromKey(oldKey), createNodeFromKey(newKey));
            }
            ContainerConfig container = containerRepository.get(oldPos.asLong());
            if (container != null) movingContainers.put(oldPos, container);
        }
        Set<EndpointAddress> movingKeys = faceMoves.keySet();
        Set<EndpointAddress> destinations = new HashSet<>();
        for (EndpointAddress newKey : faceMoves.values()) {
            if (!destinations.add(newKey)) return false;
            if (!movingKeys.contains(newKey) && getEndpointConfig(newKey) != null) return false;
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

        try (NodeMutationTransaction transaction = NodeMutationTransaction.begin(level.getServer())) {
            PlayerGroupStore groupStore = PlayerGroupStore.get(level.getServer());
            GlobalLogisticsManager global = GlobalLogisticsManager.get(level.getServer());
            Map<GlobalPos, GlobalPos> containerMoves = new LinkedHashMap<>();
            positionMoves.forEach((before, after) -> {
                if (before != null && after != null) {
                    containerMoves.put(GlobalPos.of(level.dimension(), before), GlobalPos.of(level.dimension(), after));
                }
            });
            Set<LogisticsNode> affectedSources = new LinkedHashSet<>(nodeMoves.keySet());
            global.getSourcesLinkedTo(nodeMoves.keySet()).values()
                .forEach(affectedSources::addAll);
            Map<ConnectionKey, ConnectionKey> connectionMoves =
                collectConnectionMoves(containerMoves, affectedSources);
            PlayerGroupStore.ConnectionMetadataSnapshot metadataSnapshot =
                groupStore.captureConnectionMetadata(connectionMoves, Map.of());
            transaction.onRollback(() -> groupStore.restoreConnectionMetadata(metadataSnapshot));
            for (var move : faceMoves.entrySet()) {
                transaction.captureState(createNodeFromKey(move.getKey()));
                transaction.captureState(createNodeFromKey(move.getValue()));
            }
            for (var move : positionMoves.entrySet()) {
                if (move.getKey() == null || move.getValue() == null) continue;
                transaction.captureContainer(level, move.getKey());
                transaction.captureContainer(level, move.getValue());
            }
            /// 只修改移动节点和反向索引给出的直接来源，不再扫描全服端点。
            for (LogisticsNode source : affectedSources) {
                ServerLevel sourceLevel = level.getServer().getLevel(source.gPos().dimension());
                if (sourceLevel == null) continue;
                LinkManager manager = get(sourceLevel);
                EndpointAddress address = EndpointAddress.of(source);
                EndpointConfig config = manager.getEndpointConfig(address);
                if (config == null) continue;
                transaction.capture(source);
                if (config.remapLinkedNodes(nodeMoves)) {
                    manager.markFaceDirty(address);
                    manager.scheduleNetworkSync(source);
                }
            }

            Map<EndpointAddress, EndpointConfig> movingConfigs = new LinkedHashMap<>();
            for (EndpointAddress oldKey : faceMoves.keySet()) {
                EndpointConfig config = getEndpointConfig(oldKey);
                if (config != null) movingConfigs.put(oldKey, config);
            }
            movingConfigs.keySet().forEach(getEndpointRepository()::remove);

            movingContainers.keySet().forEach(pos -> containerRepository.remove(pos.asLong()));
            for (var movingContainer : movingContainers.entrySet()) {
                BlockPos oldPos = movingContainer.getKey();
                BlockPos newPos = positionMoves.get(oldPos);
                ContainerConfig container = movingContainer.getValue();
                containerRepository.put(newPos.asLong(), container);
                markContainerDirty(oldPos.asLong());
                markContainerDirty(newPos.asLong());
                /// 移动使旧位置的恢复任务失效；为新位置明确安排提交后的状态更新。
                changeHandler.onContainerConfigChanged(container);
            }

            movingConfigs.forEach((oldKey, config) -> {
                activeProviders.remove(oldKey);
                LogisticsNode oldNode = createNodeFromKey(oldKey);
                global.removeOutgoingLinks(oldNode);
                for (var group : List.copyOf(config.faceConfig.getGroups())) {
                    global.unregisterNode(group.key(), oldNode);
                }
            });
            for (var move : faceMoves.entrySet()) {
                EndpointAddress oldKey = move.getKey();
                EndpointAddress newKey = move.getValue();
                EndpointConfig config = movingConfigs.get(oldKey);
                if (config == null) continue;
                LogisticsNode oldNode = createNodeFromKey(oldKey);
                LogisticsNode newNode = createNodeFromKey(newKey);
                var groups = List.copyOf(config.faceConfig.getGroups());
                var role = config.determineRole();

                getEndpointRepository().put(newKey, config);
                bindEndpointChanges(newKey, config);
                refreshLocalCache(newKey, config);
                keyVersions.put(newKey, Math.max(
                    keyVersions.getOrDefault(newKey, 0L), config.getVersion()));

                for (var group : groups) {
                    global.registerNode(group, newNode, role);
                    global.markGroupDirty(group.key());
                }
                global.syncOutgoingLinks(newNode, config.getLinkedNodesByGroup());
                NodeQueryService.invalidateNode(level.getServer(), oldNode);
                NodeQueryService.invalidateNode(level.getServer(), newNode);
                scheduleNetworkRemoval(oldNode, nextVersion(oldKey), config.faceConfig.getOwner());
                scheduleNetworkSync(newNode);
                markFaceDirty(oldKey);
                markFaceDirty(newKey);
            }
            for (var positionMove : positionMoves.entrySet()) {
                BlockPos oldPos = positionMove.getKey();
                BlockPos newPos = positionMove.getValue();
                if (oldPos == null || newPos == null) continue;
                invalidateCapabilityCache(oldPos);
                invalidateCapabilityCache(newPos);
            }
            groupStore.remapConnectionMetadata(connectionMoves, Map.of());
            transaction.commit();
            return true;
        }
    }

    private Map<ConnectionKey, ConnectionKey> collectConnectionMoves(
        Map<GlobalPos, GlobalPos> positionMoves,
        Collection<LogisticsNode> affectedSources
    ) {
        Map<ConnectionKey, ConnectionKey> moves = new LinkedHashMap<>();
        if (positionMoves.isEmpty()) return moves;
        for (LogisticsNode source : affectedSources) {
            ServerLevel sourceLevel = level.getServer().getLevel(source.gPos().dimension());
            if (sourceLevel == null) continue;
            EndpointConfig config = get(sourceLevel)
                .getEndpointConfig(EndpointAddress.of(source));
            if (config == null) continue;
            for (GroupKey group : config.faceConfig.getGroupKeys()) {
                for (LogisticsNode linked : config.getLinkedNodes(group)) {
                    if (source.gPos().equals(linked.gPos())) continue;
                    ConnectionKey before = new ConnectionKey(group, source, linked);
                    GlobalPos first = positionMoves.getOrDefault(before.first(), before.first());
                    GlobalPos second = positionMoves.getOrDefault(before.second(), before.second());
                    if (!first.equals(before.first()) || !second.equals(before.second())) {
                        moves.put(before, new ConnectionKey(group, first, second));
                    }
                }
            }
        }
        return moves;
    }

    public static LinkManager get(ServerLevel level) {
        Map<ServerLevel, LinkManager> levels = RUNTIMES.computeIfAbsent(
            level.getServer(), ignored -> new IdentityHashMap<>());
        LinkManager existing = levels.get(level);
        if (existing != null) return existing;
        LinkManagerStorage loaded = level.getDataStorage().computeIfAbsent(
            tag -> {
                LinkManager mgr = new LinkManager(level);
                return LinkManagerStorage.load(tag, level.registryAccess(), level, mgr);
            },
            () -> new LinkManager(level).storage,
            "static_logistics_configs_v2"
        );
        loaded.requireLoaded();
        LinkManager created = loaded.linkManager;
        levels.put(level, created);
        return created;
    }

    public static void release(ServerLevel level) {
        Map<ServerLevel, LinkManager> levels = RUNTIMES.get(level.getServer());
        LinkManager manager = levels == null ? null : levels.remove(level);
        if (manager != null) manager.shutdown();
        if (levels != null && levels.isEmpty()) RUNTIMES.remove(level.getServer());
        NodeQueryService.release(level);
    }

    public static void release(MinecraftServer server) {
        Map<ServerLevel, LinkManager> levels = RUNTIMES.remove(server);
        if (levels != null) levels.values().forEach(LinkManager::shutdown);
        NodeQueryService.release(server);
    }
}
