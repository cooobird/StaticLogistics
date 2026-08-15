package com.coobird.staticlogistics.logistics.node;

import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.api.group.GroupKey;
import com.coobird.staticlogistics.api.group.GroupRef;
import com.coobird.staticlogistics.content.item.LinkOperationHelper;
import com.coobird.staticlogistics.logistics.group.GlobalLogisticsManager;
import com.coobird.staticlogistics.logistics.node.persistence.ConfigRepository;
import com.coobird.staticlogistics.logistics.node.sync.SyncManager;
import com.coobird.staticlogistics.transfer.CapabilityCache;
import com.coobird.staticlogistics.transfer.LogisticsTicker;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

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
    public FaceConfigComposite getFaceConfig(FaceAddress address) {
        return faceConfigService.get(address);
    }

    public FaceConfigComposite getOrCreateFaceConfig(BlockPos pos, Direction face) {
        FaceAddress address = FaceAddress.of(pos, face);
        boolean isNew = !faceConfigService.exists(address);
        FaceConfigComposite config = faceConfigService.getOrCreate(pos, face);
        config.setOnDirty(cfg -> changeHandler.onFaceConfigChanged(address, pos, face, cfg));
        if (isNew) {
            // 新建配置继承 LinkManager 的全局版本计数器，确保版本号单调递增
            config.setVersion(parent.nextVersion(address));
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

    public void renameGroupMetadata(LogisticsNode node,
                                    GroupKey groupKey,
                                    String displayName) {
        FaceConfigComposite config = getFaceConfig(FaceAddress.of(node));
        if (config != null) config.renameGroup(MUTATION_PERMIT, groupKey, displayName);
    }

    public void mergeGroupMetadata(LogisticsNode node,
                                   GroupRef source, GroupRef target) {
        FaceConfigComposite config = getFaceConfig(FaceAddress.of(node));
        if (config != null) config.mergeGroup(MUTATION_PERMIT, source, target);
    }

    public void addNodeToGroup(LogisticsNode node,
                               GroupRef group) {
        FaceConfigComposite config = getFaceConfig(FaceAddress.of(node));
        if (config != null) config.addGroup(MUTATION_PERMIT, group);
    }

    public void restoreFaceSnapshot(LogisticsNode node, CompoundTag snapshot) {
        if (node == null || snapshot == null) return;
        FaceConfigComposite config = getFaceConfig(FaceAddress.of(node));
        if (config == null) {
            config = getOrCreateFaceConfig(node.gPos().pos(), node.face());
        }
        config.restoreSnapshot(MUTATION_PERMIT, level.registryAccess(), snapshot);
    }

    public void removeFaceConfigDataOnly(FaceAddress address) {
        FaceConfigComposite config = faceConfigService.get(address);
        if (config == null) return;
        if (!config.getLinkedNodes().isEmpty()
            || config.isGlobalInputEnabled()
            || config.isGlobalOutputEnabled()
            || config.faceConfig.hasGroup()
            || !config.filterConfig.isDefault()) {
            throw new IllegalStateException(
                "Cannot remove face data while lifecycle state is still attached for key " + address);
        }
        removeFaceAfterHandoff(address, config, false, false);
    }

    void removeFaceAfterHandoff(FaceAddress address, FaceConfigComposite expected,
                                boolean doCascade, boolean sendPacket) {
        synchronized (removalLock) {
            if (pendingRemovals.containsKey(address)) return;
            pendingRemovals.put(address, true);
        }
        try {
            FaceConfigComposite config = faceConfigService.get(address);
            if (config == null) return;
            if (config != expected) {
                throw new IllegalStateException(
                    "Face configuration changed during lifecycle removal for key " + address);
            }
            LogisticsNode selfNode = parent.createNodeFromKey(address);
            List<LogisticsNode> affectedNodes = doCascade ? List.copyOf(config.getLinkedNodes()) : List.of();
            if (doCascade) {
                parent.cascadeRemove(selfNode, config);
            }
            faceConfigService.remove(address);
            Runnable publishRemoval = () -> {
                cacheManager.remove(address);
                CapabilityCache.clearPosition(level, selfNode.gPos().pos());
                GlobalLogisticsManager.get(level.getServer()).notifyNodeRemoved(level, selfNode);
                LogisticsTicker.wakeup(level, address);
                parent.markFaceDirty(address);
                if (sendPacket) {
                    parent.scheduleNetworkRemoval(
                        selfNode, parent.nextVersion(address), config.faceConfig.getOwner());
                }
                for (LogisticsNode node : affectedNodes) {
                    ServerLevel nodeLevel = level.getServer().getLevel(node.gPos().dimension());
                    if (nodeLevel != null) LinkManager.get(nodeLevel).syncNodeToDimensionDirect(node);
                }
            };
            if (!NodeMutationTransaction.defer(level.getServer(),
                new DeferredRemovalKey(parent, selfNode), publishRemoval)) {
                publishRemoval.run();
            }
        } finally {
            pendingRemovals.remove(address);
        }
    }

    private record DeferredRemovalKey(LinkManager manager, LogisticsNode node) {
    }

    public void refreshLocalCache(FaceAddress address, BlockPos pos, Direction face,
                                  FaceConfigComposite config) {
        if (config.faceConfig.hasGroup() && config.determineRole().canSend()) cacheManager.add(address);
        else cacheManager.remove(address);
    }

    public void activateNode(FaceAddress address, BlockPos pos, Direction face,
                             FaceConfigComposite config) {
        refreshLocalCache(address, pos, face, config);
        LogisticsTicker.wakeup(level, address);
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
            FaceAddress address = orphanKeys[i];
            FaceConfigComposite cfg = faceConfigService.get(address);
            if (cfg == null) continue;
            LogisticsNode node = parent.createNodeFromKey(address);
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
                    if (targetLevel.getBlockState(targetPos).isAir()) {
                        missingBlockTargets.add(target);
                    } else if (LinkManager.get(targetLevel)
                        .getFaceConfig(FaceAddress.of(target)) == null) {
                        missingFaceTargets.add(target);
                    }
                }
                for (var groupKey : new ArrayList<>(cfg.faceConfig.getGroupKeys())) {
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
            ForgeRegistries.BLOCKS.getKey(level.getBlockState(pos).getBlock()),
            ForgeRegistries.BLOCK_ENTITY_TYPES.getKey(blockEntity.getType()));
    }
}
