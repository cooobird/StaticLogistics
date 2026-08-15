package com.coobird.staticlogistics.logistics.node;

import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.api.event.LogisticsNodeEvent;
import com.coobird.staticlogistics.api.group.GroupKey;
import com.coobird.staticlogistics.api.group.GroupRef;
import com.coobird.staticlogistics.content.item.LinkOperationHelper;
import com.coobird.staticlogistics.logistics.group.GlobalLogisticsManager;
import com.coobird.staticlogistics.logistics.node.persistence.ConfigRepository;
import com.coobird.staticlogistics.logistics.node.sync.SyncManager;
import com.coobird.staticlogistics.transfer.LogisticsTicker;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.common.NeoForge;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 面配置处理器 —— 管理单个面的物流配置 CRUD、级联删除、孤儿扫描。
 *
 * <p>职责：
 * <ul>
 *   <li>创建/获取/删除 {@link FaceConfigComposite}</li>
 *   <li>链接管理：添加/移除链接，级联删除（移除自己时清理所有关联节点的反向引用）</li>
 *   <li>孤儿扫描：检测方块实体已消失的面配置并清理</li>
 *   <li>缓存管理：维护 {@link CacheManager} 中的活跃节点</li>
 *   <li>网络同步：配置变更时通知客户端</li>
 * </ul>
 *
 * <p>线程安全：所有操作在服务器主线程上执行（由 {@link LinkManager} 委托调用）。
 * 级联删除期间通过 {@code suppressNetworkSync} 抑制网络同步，避免发送即将删除的配置。
 */
class FaceConfigHandler {
    private static final LinkMutationPermit MUTATION_PERMIT = new LinkMutationPermit();
    private final ServerLevel level;
    final FaceConfigService faceConfigService;
    final ConfigRepository configRepository;
    final SyncManager syncManager;
    private final CacheManager cacheManager;
    private final LinkChangeHandler changeHandler;
    private final LinkManager parent;
    private final Map<FaceAddress, Boolean> pendingRemovals = new ConcurrentHashMap<>();
    private final Object removalLock = new Object();
    private boolean orphanScanNeeded;
    private FaceAddress[] orphanKeys;
    private int orphanScanCursor;
    private static final int ORPHAN_SCAN_BATCH = 16;

    FaceConfigHandler(ServerLevel level, FaceConfigService faceConfigService, ConfigRepository configRepository,
                      CacheManager cacheManager, LinkChangeHandler changeHandler,
                      SyncManager syncManager, LinkManager parent) {
        this.level = level;
        this.faceConfigService = faceConfigService;
        this.configRepository = configRepository;
        this.cacheManager = cacheManager;
        this.changeHandler = changeHandler;
        this.syncManager = syncManager;
        this.parent = parent;
    }

    @Nullable
    public FaceConfigComposite getFaceConfig(FaceAddress key) {
        return faceConfigService.get(key);
    }

    public FaceConfigComposite getOrCreateFaceConfig(BlockPos pos, Direction face) {
        FaceAddress key = FaceAddress.of(pos, face);
        boolean isNew = !faceConfigService.exists(key);
        FaceConfigComposite config = faceConfigService.getOrCreate(pos, face);
        config.setOnDirty(cfg -> changeHandler.onFaceConfigChanged(key, pos, face, cfg));
        if (isNew) {
            config.setVersion(parent.nextVersion(key));
            bindCurrentEndpoint(config, pos);
        }
        return config;
    }

    public void claimOwner(LogisticsNode node, GameProfile profile) {
        if (node == null || profile == null) return;
        FaceConfigComposite config = getFaceConfig(FaceAddress.of(node));
        if (config == null || config.faceConfig.getOwner() != null) return;
        config.setOwner(MUTATION_PERMIT, profile.getId(), profile.getName(), profile);
    }

    public void refreshOwnerProfile(LogisticsNode node, GameProfile profile) {
        if (node == null || profile == null) return;
        FaceConfigComposite config = getFaceConfig(FaceAddress.of(node));
        if (config == null || !profile.getId().equals(config.faceConfig.getOwner())) return;
        config.setOwner(MUTATION_PERMIT, profile.getId(), profile.getName(), profile);
    }

    public void addNodeToGroup(LogisticsNode node, GroupRef group) {
        if (node == null || group == null) return;
        FaceConfigComposite config = getFaceConfig(FaceAddress.of(node));
        if (config == null) return;
        config.addGroup(MUTATION_PERMIT, group);
    }

    public void renameGroupMetadata(LogisticsNode node, GroupKey groupKey, String displayName) {
        if (node == null || groupKey == null || displayName == null || displayName.isEmpty()) return;
        FaceConfigComposite config = getFaceConfig(FaceAddress.of(node));
        if (config == null) return;
        config.renameGroup(MUTATION_PERMIT, groupKey, displayName);
    }

    public void mergeGroupMetadata(LogisticsNode node,
                                   GroupRef source,
                                   GroupRef target) {
        if (node == null || source == null || target == null) return;
        FaceConfigComposite config = getFaceConfig(FaceAddress.of(node));
        if (config == null) return;
        config.mergeGroup(MUTATION_PERMIT, source, target);
    }

    public void restoreFaceSnapshot(LogisticsNode node, CompoundTag snapshot) {
        if (node == null || snapshot == null) return;
        FaceConfigComposite config = getFaceConfig(FaceAddress.of(node));
        if (config == null) {
            config = getOrCreateFaceConfig(node.gPos().pos(), node.face());
        }
        config.restoreSnapshot(MUTATION_PERMIT, level.registryAccess(), snapshot);
    }

    public void removeFaceConfigDataOnly(FaceAddress key) {
        FaceConfigComposite config = faceConfigService.get(key);
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

    /**
     * 升级物已由生命周期服务成功移交后，执行纯配置删除。
     * 调用方不得绕过生命周期服务直接销毁仍持有升级物的面。
     */
    void removeFaceAfterHandoff(FaceAddress key, FaceConfigComposite expected,
                                boolean doCascade, boolean sendPacket) {
        synchronized (removalLock) {
            if (pendingRemovals.containsKey(key)) return;
            pendingRemovals.put(key, true);
        }
        try {
            FaceConfigComposite config = faceConfigService.get(key);
            if (config == null) return;
            if (config != expected) {
                throw new IllegalStateException(
                    "Face configuration changed during lifecycle removal for key " + key);
            }
            LogisticsNode selfNode = parent.createNodeFromKey(key);
            // 级联服务只调用统一链接删除入口，避免在遍历期间直接修改集合。
            if (doCascade) {
                parent.cascadeRemove(selfNode, config);
            }
            faceConfigService.remove(key);
            var removedEntries = config.faceConfig.getGroups().stream()
                .map(group -> new LogisticsNodeEvent.NodeEntry(group, selfNode, config.determineRole()))
                .toList();
            Runnable publishRemovalEffects = () -> {
                cacheManager.remove(key);
                parent.invalidateCapabilityCache(selfNode.gPos().pos(), selfNode.face());
                if (removedEntries.isEmpty()) {
                    GlobalLogisticsManager.get(level.getServer()).notifyNodeRemoved(level, selfNode);
                } else {
                    NeoForge.EVENT_BUS.post(new LogisticsNodeEvent(
                        level.getServer(), removedEntries, LogisticsNodeEvent.ChangeType.REMOVED));
                }
                LogisticsTicker.wakeup(level, key);
                parent.markFaceDirty(key);
            };
            if (!NodeMutationTransaction.defer(level.getServer(),
                new DeferredRemovalEffectsKey(parent, selfNode), publishRemovalEffects)) {
                publishRemovalEffects.run();
            }
            if (sendPacket) {
                parent.scheduleNetworkRemoval(
                    selfNode, parent.nextVersion(key), config.faceConfig.getOwner());
            }
        } finally {
            pendingRemovals.remove(key);
        }
    }

    public void refreshLocalCache(FaceAddress key, BlockPos pos, Direction face, FaceConfigComposite config) {
        if (config.faceConfig.hasGroup() && config.determineRole().canSend()) cacheManager.add(key);
        else cacheManager.remove(key);
    }

    public void activateNode(FaceAddress key, BlockPos pos, Direction face, FaceConfigComposite config) {
        refreshLocalCache(key, pos, face, config);
        LogisticsTicker.wakeup(level, key);
    }

    void markOrphanScanNeeded() {
        orphanScanNeeded = true;
    }

    boolean isOrphanScanNeeded() {
        return orphanScanNeeded;
    }

    void validateOrphanedConfigs() {
        Set<FaceAddress> keys = configRepository.keySet();
        int size = keys.size();
        if (size == 0) {
            orphanScanNeeded = false;
            orphanKeys = null;
            orphanScanCursor = 0;
            return;
        }
        if (orphanKeys == null || orphanKeys.length != size) {
            orphanKeys = keys.toArray(FaceAddress[]::new);
            orphanScanCursor = 0;
        }
        if (orphanScanCursor >= orphanKeys.length) {
            orphanScanNeeded = false;
            orphanScanCursor = 0;
            return;
        }
        int end = Math.min(orphanScanCursor + ORPHAN_SCAN_BATCH, orphanKeys.length);
        MinecraftServer server = level.getServer();
        Set<BlockPos> removedPositions = new LinkedHashSet<>();
        Set<LogisticsNode> missingBlockTargets = new LinkedHashSet<>();
        Set<LogisticsNode> missingFaceTargets = new LinkedHashSet<>();
        for (int i = orphanScanCursor; i < end; i++) {
            FaceAddress key = orphanKeys[i];
            FaceConfigComposite cfg = faceConfigService.get(key);
            if (cfg == null) continue;
            LogisticsNode node = parent.createNodeFromKey(key);
            BlockPos pos = node.gPos().pos();
            if (level.getChunkSource().hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) {
                FaceConfigComposite.EndpointFingerprint currentFingerprint = currentEndpointFingerprint(pos);
                if (currentFingerprint == null) {
                    removedPositions.add(pos);
                    continue;
                }
                FaceConfigComposite.EndpointFingerprint savedFingerprint = cfg.getEndpointFingerprint();
                if (savedFingerprint == null) {
                    cfg.bindEndpoint(MUTATION_PERMIT, currentFingerprint);
                } else if (!savedFingerprint.equals(currentFingerprint)) {
                    removedPositions.add(pos);
                    continue;
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
                    FaceConfigComposite targetConfig = LinkManager.get(targetLevel)
                        .getFaceConfig(FaceAddress.of(target));
                    var targetState = targetLevel.getBlockState(targetPos);
                    if (targetState.isAir()) {
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
                        parent.removeNodeFromGroup(groupKey, node);
                    }
                }
            }
        }
        if (!removedPositions.isEmpty()) {
            parent.onBlocksRemovedBulk(removedPositions);
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
            parent.purgeInboundReferences(missingTargets);
        }
        if (end >= orphanKeys.length) {
            orphanScanNeeded = false;
            orphanKeys = null;
            orphanScanCursor = 0;
        } else {
            orphanScanCursor = end;
        }
    }

    Set<FaceAddress> getAllConfigKeys() {
        return Set.copyOf(configRepository.keySet());
    }

    private void bindCurrentEndpoint(FaceConfigComposite config, BlockPos pos) {
        FaceConfigComposite.EndpointFingerprint fingerprint = currentEndpointFingerprint(pos);
        if (fingerprint != null) config.bindEndpoint(MUTATION_PERMIT, fingerprint);
    }

    @Nullable
    private FaceConfigComposite.EndpointFingerprint currentEndpointFingerprint(BlockPos pos) {
        var blockEntity = level.getBlockEntity(pos);
        if (blockEntity == null || level.getBlockState(pos).isAir()) return null;
        return new FaceConfigComposite.EndpointFingerprint(
            BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()),
            BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(blockEntity.getType()));
    }

    private record DeferredRemovalEffectsKey(LinkManager manager, LogisticsNode node) {
    }
}
