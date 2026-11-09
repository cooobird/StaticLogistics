package com.coobird.staticlogistics.logistics.node;

import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.api.event.LogisticsNodeEvent;
import com.coobird.staticlogistics.api.group.GroupKey;
import com.coobird.staticlogistics.content.item.LinkOperationHelper;
import com.coobird.staticlogistics.logistics.group.GlobalLogisticsManager;
import com.coobird.staticlogistics.logistics.node.persistence.ContainerRepository;
import com.coobird.staticlogistics.logistics.node.persistence.EndpointRepository;
import com.coobird.staticlogistics.transfer.LogisticsTicker;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.*;
import java.util.stream.Collectors;

/// 节点、容器配置与升级物移交的统一生命周期用例。
/// 图层只产生孤儿候选；本服务决定是否删除面，并保证升级物移交发生在配置删除之前。
public final class NodeLifecycleService {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final LinkMutationPermit MUTATION_PERMIT = new LinkMutationPermit();
    private static final int ORPHAN_SCAN_BATCH = 16;
    private final LinkManager manager;
    private final ServerLevel level;
    private final EndpointRepository endpoints;
    private final ActiveProviderIndex activeProviders;
    /// 主线程内只防止级联删除重入，不伪装成跨线程同步容器。
    private final Set<EndpointAddress> pendingRemovals = new HashSet<>();
    private boolean orphanScanNeeded;
    private EndpointAddress[] orphanKeys;
    private int orphanScanCursor;
    private final ContainerRepository containers;
    private final UpgradeHandoff upgradeHandoff;
    private int handoffDepth;

    NodeLifecycleService(ServerLevel level, LinkManager manager, EndpointRepository endpoints,
                         ContainerRepository containers, ActiveProviderIndex activeProviders, UpgradeHandoff upgradeHandoff) {
        this.manager = manager;
        this.level = level;
        this.endpoints = endpoints;
        this.activeProviders = activeProviders;
        this.containers = containers;
        this.upgradeHandoff = upgradeHandoff;
    }

    boolean isHandoffInProgress() {
        return handoffDepth > 0;
    }

    /// 物理销毁一批方块；每个位置独立失败，其他位置继续处理。
    public RemovalReport destroyBlocks(Collection<BlockPos> positions) {
        int removed = 0;
        int failed = 0;
        for (BlockPos pos : new LinkedHashSet<>(positions)) {
            if (pos == null) continue;
            try {
                if (destroyBlock(pos)) removed++;
            } catch (RuntimeException exception) {
                failed++;
                LOGGER.error("Failed to destroy logistics lifecycle at {}", pos, exception);
            }
        }
        return new RemovalReport(removed, failed);
    }

    /// 显式删除一个面；过滤升级先移交，容器级升级不受影响。
    public boolean removeFace(EndpointAddress key) {
        FaceHandle face = findFace(key);
        if (face == null) return false;
        executeRemoval(List.of(face), null);
        return true;
    }

    /// 只删除真正没有边、角色和面过滤升级的孤儿面。
    public boolean removeOrphan(LogisticsNode node, EndpointConfig expected) {
        if (node == null || expected == null
            || !expected.getLinkedNodes().isEmpty()
            || expected.isGlobalInputEnabled()
            || expected.isGlobalOutputEnabled()
            || !expected.filterConfig.isDefault()) return false;
        FaceHandle live = findFace(EndpointAddress.of(node));
        if (live == null || live.config() != expected) return false;
        removeFaceAfterHandoff(live, true, true);
        return true;
    }

    /// 冻结本维度待删除的孤立面、共享容器和升级来源。
    public DisconnectedRemoval prepareDisconnectedRemoval(Collection<LogisticsNode> nodes) {
        if (nodes == null) {
            throw new IllegalArgumentException("Disconnected nodes are required");
        }
        LinkedHashSet<FaceHandle> disconnected = new LinkedHashSet<>();
        for (LogisticsNode node : nodes) {
            if (node == null) continue;
            FaceHandle face = findFace(EndpointAddress.of(node));
            if (face != null && face.config().getLinkedNodes().isEmpty()) disconnected.add(face);
        }
        if (disconnected.isEmpty()) {
            return new DisconnectedRemoval(List.of(), List.of(), List.of());
        }

        Set<EndpointAddress> removingKeys = disconnected.stream()
            .map(FaceHandle::key).collect(Collectors.toSet());
        LinkedHashSet<BlockPos> positions = disconnected.stream()
            .map(face -> face.node().gPos().pos())
            .collect(Collectors.toCollection(LinkedHashSet::new));
        List<ContainerHandle> containersToRemove = new ArrayList<>();
        for (BlockPos pos : positions) {
            ContainerHandle container = findContainer(pos);
            if (container != null
                && removingKeys.containsAll(container.config().getLinkedFaceKeys())) {
                containersToRemove.add(container);
            }
        }
        List<UpgradeSource> sources = new ArrayList<>();
        for (FaceHandle face : disconnected) {
            sources.add(new UpgradeSource(
                face.node().gPos().pos(), face.config().filterConfig.getUpgrades()));
        }
        for (ContainerHandle container : containersToRemove) {
            sources.add(new UpgradeSource(container.pos(), container.config().getUpgrades()));
        }
        return new DisconnectedRemoval(
            List.copyOf(disconnected), List.copyOf(containersToRemove), List.copyOf(sources));
    }

    /// 应用已经冻结的孤立端删除；升级移交由外层事务统一控制。
    public void applyDisconnectedRemoval(DisconnectedRemoval removal) {
        try (RemovalScope ignored = beginRemoval()) {
            for (FaceHandle face : removal.faces()) {
                removeFaceAfterHandoff(face, true, true);
            }
            for (ContainerHandle container : removal.containers()) {
                removeContainerAfterHandoff(container);
            }
        } catch (RuntimeException exception) {
            reconcile(removal.faces(), removal.containers());
            throw exception;
        }
    }

    private boolean destroyBlock(BlockPos pos) {
        List<FaceHandle> faceHandles = findFaces(pos);
        ContainerHandle container = findContainer(pos);
        if (faceHandles.isEmpty() && container == null) return false;

        executeRemoval(faceHandles, container == null ? List.of() : List.of(container), upgradeHandoff);
        return true;
    }

    private void executeRemoval(List<FaceHandle> faces, @Nullable ContainerHandle container) {
        executeRemoval(faces, container == null ? List.of() : List.of(container), upgradeHandoff);
    }

    private void executeRemoval(
        List<FaceHandle> faces,
        List<ContainerHandle> containers,
        UpgradeHandoff handoff
    ) {
        try {
            try (RemovalScope ignored = beginRemoval()) {
                List<UpgradeSource> sources = new ArrayList<>();
                for (FaceHandle face : faces) {
                    sources.add(new UpgradeSource(
                        face.node().gPos().pos(), face.config().filterConfig.getUpgrades()));
                }
                for (ContainerHandle container : containers) {
                    sources.add(new UpgradeSource(container.pos(), container.config().getUpgrades()));
                }
                try (HandoffReceipt receipt = handoff.begin(sources)) {
                    for (FaceHandle face : faces) {
                        removeFaceAfterHandoff(face, true, true);
                    }
                    for (ContainerHandle container : containers) {
                        removeContainerAfterHandoff(container);
                    }
                    receipt.commit();
                }
            }
        } catch (RuntimeException exception) {
            reconcile(faces, containers);
            throw exception;
        }
    }

    @Nullable
    private FaceHandle findFace(EndpointAddress key) {
        EndpointConfig config = endpoints.get(key);
        return config == null ? null : new FaceHandle(key, manager.createNodeFromKey(key), config);
    }

    private List<FaceHandle> findFaces(BlockPos pos) {
        List<FaceHandle> result = new ArrayList<>();
        ContainerConfig container = containers.get(pos);
        if (container == null) return List.of();
        for (EndpointAddress key : container.getLinkedFaceKeys()) {
            FaceHandle handle = findFace(key);
            if (handle != null) result.add(handle);
        }
        return List.copyOf(result);
    }

    @Nullable
    private ContainerHandle findContainer(BlockPos pos) {
        ContainerConfig config = containers.get(pos);
        return config == null ? null : new ContainerHandle(pos, config);
    }

    private RemovalScope beginRemoval() {
        handoffDepth++;
        return () -> {
            if (handoffDepth <= 0) {
                throw new IllegalStateException("Lifecycle handoff scope is not active");
            }
            handoffDepth--;
        };
    }

    private void removeFaceAfterHandoff(FaceHandle face, boolean cascade, boolean tombstone) {
        removeFaceAfterHandoff(face.key(), face.config(), cascade, tombstone);
    }

    private void removeContainerAfterHandoff(ContainerHandle container) {
        if (!containers.removeAfterHandoff(container.pos(), container.config())) {
            throw new IllegalStateException(
                "Container configuration changed during lifecycle removal at " + container.pos());
        }
        manager.markContainerDirty(container.pos().asLong());
    }

    private void reconcile(Collection<FaceHandle> faceHandles,
                           Collection<ContainerHandle> containers) {
        for (FaceHandle face : faceHandles) {
            if (endpoints.get(face.key()) == face.config()) face.config().markDirty();
        }
        for (ContainerHandle container : containers) {
            if (this.containers.get(container.pos()) == container.config()) {
                container.config().markDirty();
            }
        }
    }

    public void removeFaceConfigDataOnly(EndpointAddress key) {
        EndpointConfig config = endpoints.get(key);
        if (config == null) return;
        if (!config.getLinkedNodes().isEmpty()
            || config.isGlobalInputEnabled()
            || config.isGlobalOutputEnabled()
            || config.faceConfig.hasGroup()
            || !config.filterConfig.isDefault()) {
            throw new IllegalStateException(
                "Cannot remove face data while lifecycle state is still attached for key " + key);
        }
        removeFaceAfterHandoff(key, config, false, false);
    }

    /// 升级物移交成功后再删除配置；事务回滚使用同一入口撤销临时端点。
    void removeFaceAfterHandoff(EndpointAddress key, EndpointConfig expected,
                                boolean doCascade, boolean sendPacket) {
        if (!pendingRemovals.add(key)) return;
        try {
            EndpointConfig config = endpoints.get(key);
            if (config == null) return;
            if (config != expected) {
                throw new IllegalStateException(
                    "Face configuration changed during lifecycle removal for key " + key);
            }
            LogisticsNode selfNode = manager.createNodeFromKey(key);
            /// 级联服务只调用统一链接删除入口，避免在遍历期间直接修改集合。
            if (doCascade) {
                manager.cascadeRemove(selfNode, config);
            }
            endpoints.remove(key);
            var removedEntries = config.faceConfig.getGroups().stream()
                .map(group -> new LogisticsNodeEvent.NodeEntry(group, selfNode, config.determineRole()))
                .toList();
            Runnable publishRemovalEffects = () -> {
                /// 地址可能已在事务中恢复或重建；旧删除事件不得清理新端点的成员关系与缓存。
                if (endpoints.get(key) != null) return;
                GlobalLogisticsManager.get(level.getServer()).removeOutgoingLinks(selfNode);
                activeProviders.remove(key);
                manager.invalidateCapabilityCache(selfNode.gPos().pos(), selfNode.face());
                if (removedEntries.isEmpty()) {
                    GlobalLogisticsManager.get(level.getServer()).notifyNodeRemoved(level, selfNode);
                } else {
                    MinecraftForge.EVENT_BUS.post(new LogisticsNodeEvent(
                        level.getServer(), removedEntries, LogisticsNodeEvent.ChangeType.REMOVED));
                }
                LogisticsTicker.wakeup(level, key);
                manager.markFaceDirty(key);
            };
            if (!NodeMutationTransaction.defer(level.getServer(),
                new DeferredRemovalEffectsKey(manager, selfNode), publishRemovalEffects)) {
                publishRemovalEffects.run();
            }
            if (sendPacket) {
                manager.scheduleNetworkRemoval(
                    selfNode, manager.nextVersion(key), config.faceConfig.getOwner());
            }
        } finally {
            pendingRemovals.remove(key);
        }
    }

    void markOrphanScanNeeded() {
        orphanScanNeeded = true;
    }

    boolean isOrphanScanNeeded() {
        return orphanScanNeeded || orphanKeys != null;
    }

    void validateOrphanedConfigs() {
        /// 一轮只冻结一次键集合；删除端点不会重启扫描，扫描期间的新请求留到下一轮。
        if (orphanKeys == null) {
            if (!orphanScanNeeded) return;
            orphanScanNeeded = false;
            orphanKeys = endpoints.keySet().toArray(EndpointAddress[]::new);
            orphanScanCursor = 0;
        }
        if (orphanKeys.length == 0) {
            orphanKeys = null;
            return;
        }
        int end = Math.min(orphanScanCursor + ORPHAN_SCAN_BATCH, orphanKeys.length);
        MinecraftServer server = level.getServer();
        Set<BlockPos> removedPositions = new LinkedHashSet<>();
        Set<LogisticsNode> missingBlockTargets = new LinkedHashSet<>();
        Set<LogisticsNode> missingFaceTargets = new LinkedHashSet<>();
        for (int i = orphanScanCursor; i < end; i++) {
            EndpointAddress key = orphanKeys[i];
            EndpointConfig cfg = endpoints.get(key);
            if (cfg == null) continue;
            LogisticsNode node = manager.createNodeFromKey(key);
            BlockPos pos = node.gPos().pos();
            boolean mountedOnCreate = false;
            if (level.getChunkSource().hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) {
                EndpointConfig.EndpointFingerprint currentFingerprint = currentEndpointFingerprint(pos);
                if (currentFingerprint == null && !mountedOnCreate) {
                    removedPositions.add(pos);
                    continue;
                }
                if (currentFingerprint != null) {
                    EndpointConfig.EndpointFingerprint savedFingerprint = cfg.getEndpointFingerprint();
                    if (savedFingerprint == null) {
                        cfg.bindEndpoint(MUTATION_PERMIT, currentFingerprint);
                    } else if (!savedFingerprint.equals(currentFingerprint)) {
                        removedPositions.add(pos);
                        continue;
                    }
                }
            }
            if (!removedPositions.contains(pos)) {
                for (LogisticsNode target : cfg.getLinkedNodes()) {
                    ServerLevel targetLevel = server.getLevel(target.gPos().dimension());
                    BlockPos targetPos = target.gPos().pos();
                    if (targetLevel == null
                        || !targetLevel.getChunkSource().hasChunk(targetPos.getX() >> 4, targetPos.getZ() >> 4)) {
                        continue;
                    }
                    EndpointConfig targetConfig = LinkManager.get(targetLevel)
                        .getEndpointConfig(EndpointAddress.of(target));
                    var targetState = targetLevel.getBlockState(targetPos);
                    if (targetState.isAir()
                        && !(false)) {
                        missingBlockTargets.add(target);
                    } else if (targetConfig == null) {
                        missingFaceTargets.add(target);
                    }
                }
                for (GroupKey groupKey
                    : new ArrayList<>(cfg.faceConfig.getGroupKeys())) {
                    if (GlobalLogisticsManager.get(server).getNodeGroupService()
                        .getNodesInGroup(groupKey).isEmpty()
                        && cfg.getLinkedNodes(groupKey).isEmpty()) {
                        manager.removeNodeFromGroup(groupKey, node);
                    }
                }
            }
        }
        if (!removedPositions.isEmpty()) {
            manager.onBlocksRemovedBulk(removedPositions);
            for (BlockPos pos : removedPositions) {
                LinkOperationHelper.cleanStoredNodesForPos(level, pos);
            }
        }
        Map<ServerLevel, Set<BlockPos>> missingPositionsByLevel = new LinkedHashMap<>();
        for (LogisticsNode target : missingBlockTargets) {
            ServerLevel targetLevel = server.getLevel(target.gPos().dimension());
            if (targetLevel == null) continue;
            missingPositionsByLevel.computeIfAbsent(targetLevel, ignored -> new LinkedHashSet<>())
                .add(target.gPos().pos());
        }
        missingPositionsByLevel.forEach((targetLevel, positions) -> {
            LinkManager.get(targetLevel).onBlocksRemovedBulk(positions);
            positions.forEach(pos -> LinkOperationHelper.cleanStoredNodesForPos(targetLevel, pos));
        });
        if (!missingBlockTargets.isEmpty() || !missingFaceTargets.isEmpty()) {
            Set<LogisticsNode> missingTargets = new LinkedHashSet<>(missingBlockTargets);
            missingTargets.addAll(missingFaceTargets);
            manager.purgeInboundReferences(missingTargets);
        }
        if (end >= orphanKeys.length) {
            orphanKeys = null;
            orphanScanCursor = 0;
        } else {
            orphanScanCursor = end;
        }
    }

    void bindCurrentEndpoint(EndpointConfig config, BlockPos pos) {
        EndpointConfig.EndpointFingerprint fingerprint = currentEndpointFingerprint(pos);
        if (fingerprint != null) config.bindEndpoint(MUTATION_PERMIT, fingerprint);
    }

    @Nullable
    private EndpointConfig.EndpointFingerprint currentEndpointFingerprint(BlockPos pos) {
        var blockEntity = level.getBlockEntity(pos);
        if (blockEntity == null || level.getBlockState(pos).isAir()) return null;
        return new EndpointConfig.EndpointFingerprint(
            BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()),
            BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(blockEntity.getType()));
    }

    private record DeferredRemovalEffectsKey(LinkManager manager, LogisticsNode node) {
    }

    public record FaceHandle(EndpointAddress key, LogisticsNode node, EndpointConfig config) {
    }

    public record ContainerHandle(BlockPos pos, ContainerConfig config) {
    }

    /// 一批待移交的升级物来源。
    /// 默认构造器覆盖整个物品处理器；槽位范围构造器用于只移交某一侧过滤器，
    /// 避免关闭输入或输出时误取另一侧仍在使用的过滤器。
    public record UpgradeSource(
        BlockPos pos,
        IItemHandler inventory,
        int firstSlot,
        int slotLimit
    ) {
        public UpgradeSource(BlockPos pos, IItemHandler inventory) {
            this(pos, inventory, 0, inventory == null ? 0 : inventory.getSlots());
        }

        public UpgradeSource {
            if (pos == null || inventory == null) {
                throw new IllegalArgumentException("Upgrade source fields must not be null");
            }
            if (firstSlot < 0 || slotLimit < firstSlot || slotLimit > inventory.getSlots()) {
                throw new IllegalArgumentException("Upgrade source slot range is invalid");
            }
        }
    }

    public record DisconnectedRemoval(
        List<FaceHandle> faces,
        List<ContainerHandle> containers,
        List<UpgradeSource> sources
    ) {
        public DisconnectedRemoval {
            faces = List.copyOf(faces);
            containers = List.copyOf(containers);
            sources = List.copyOf(sources);
        }
    }

    public record RemovalReport(int removedPositions, int failedPositions) {
    }

    @FunctionalInterface
    public interface UpgradeHandoff {
        HandoffReceipt begin(List<UpgradeSource> sources);
    }

    public interface HandoffReceipt extends AutoCloseable {
        void commit();

        @Override
        void close();
    }

    @FunctionalInterface
    private interface RemovalScope extends AutoCloseable {
        @Override
        void close();
    }
}
