package com.coobird.staticlogistics.storage;

import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.core.manager.GlobalLogisticsManager;
import com.coobird.staticlogistics.server.ticker.LogisticsTicker;
import com.coobird.staticlogistics.storage.cache.CacheManager;
import com.coobird.staticlogistics.storage.config.FaceConfigComposite;
import com.coobird.staticlogistics.storage.persistence.DropHandler;
import com.coobird.staticlogistics.storage.repository.ConfigRepository;
import com.coobird.staticlogistics.storage.service.FaceConfigService;
import com.coobird.staticlogistics.storage.sync.NetworkSyncManager;
import com.coobird.staticlogistics.storage.sync.SyncManager;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

class FaceConfigHandler {
    private final ServerLevel level;
    final FaceConfigService faceConfigService;
    final ConfigRepository configRepository;
    final SyncManager syncManager;
    private final CacheManager cacheManager;
    private final LinkChangeHandler changeHandler;
    private final DropHandler dropHandler;
    private final NetworkSyncManager networkSyncManager;
    private final LinkManager parent;
    private final Map<Long, Boolean> pendingRemovals = new ConcurrentHashMap<>();
    private final Object removalLock = new Object();
    private boolean orphanScanNeeded;
    private Long[] orphanKeys;
    private int orphanScanCursor;
    private static final int ORPHAN_SCAN_BATCH = 16;

    FaceConfigHandler(ServerLevel level, FaceConfigService faceConfigService, ConfigRepository configRepository,
                      CacheManager cacheManager, LinkChangeHandler changeHandler, DropHandler dropHandler,
                      NetworkSyncManager networkSyncManager, SyncManager syncManager, LinkManager parent) {
        this.level = level;
        this.faceConfigService = faceConfigService;
        this.configRepository = configRepository;
        this.cacheManager = cacheManager;
        this.changeHandler = changeHandler;
        this.dropHandler = dropHandler;
        this.networkSyncManager = networkSyncManager;
        this.syncManager = syncManager;
        this.parent = parent;
    }

    @Nullable
    public FaceConfigComposite getFaceConfig(long key) {
        return faceConfigService.get(key);
    }

    public FaceConfigComposite getOrCreateFaceConfig(BlockPos pos, Direction face) {
        long key = LinkManager.posToKey(pos, face);
        FaceConfigComposite config = faceConfigService.getOrCreate(pos, face);
        config.setOnDirty(cfg -> changeHandler.onFaceConfigChanged(key, pos, face, cfg));
        return config;
    }

    public void removeLink(LogisticsNode source, LogisticsNode target) {
        if (source == null || target == null) return;
        FaceConfigComposite sourceCfg = getFaceConfig(source.toKey());
        if (sourceCfg == null) return;
        ServerLevel targetLevel = target.isInSameDimension(level.dimension())
            ? level : level.getServer().getLevel(target.gPos().dimension());
        if (targetLevel == null) return;
        LinkManager targetMgr = LinkManager.get(targetLevel);
        FaceConfigComposite targetCfg = targetMgr.getFaceConfigHandler().getFaceConfig(target.toKey());
        if (targetCfg == null) return;
        if (!sourceCfg.getLinkedNodes().remove(target) && !targetCfg.getLinkedNodes().remove(source)) return;
        GlobalLogisticsManager.get(level.getServer()).markReverseLinksStale();
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
        parent.syncNodeToDimension(source);
        targetMgr.syncNodeToDimension(target);
        parent.markFaceDirty(source.toKey());
        targetMgr.markFaceDirty(target.toKey());
        cleanUpFaceIfNeeded(source, sourceCfg);
        targetMgr.getFaceConfigHandler().cleanUpFaceIfNeeded(target, targetCfg);
    }

    public void cleanUpFaceIfNeeded(LogisticsNode node, FaceConfigComposite cfg) {
        if (cfg.getLinkedNodes().isEmpty() && !cfg.isGlobalInputEnabled() && !cfg.isGlobalOutputEnabled())
            removeFaceConfigInternal(node.toKey(), false, true);
    }

    public void removeFaceConfig(long key) {
        removeFaceConfigInternal(key, true, true);
    }

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
            LogisticsNode selfNode = parent.createNodeFromKey(key);
            List<LogisticsNode> affectedNodes = doCascade ? List.copyOf(config.getLinkedNodes()) : List.of();
            if (doCascade) changeHandler.cascadeRemove(selfNode, config);
            dropHandler.dropFilterUpgrades(selfNode.gPos().pos(), config.filterConfig.getUpgrades());
            faceConfigService.remove(key);
            cacheManager.remove(key);
            GlobalLogisticsManager.get(level.getServer()).notifyNodeRemoved(level, selfNode);
            GlobalLogisticsManager.get(level.getServer()).markReverseLinksStale();
            LogisticsTicker.wakeup(level, key);
            parent.markFaceDirty(key);
            if (sendPacket) networkSyncManager.syncRemovalToDimension(selfNode.gPos().pos(), selfNode.face());
            for (LogisticsNode node : affectedNodes) {
                ServerLevel nodeLevel = level.getServer().getLevel(node.gPos().dimension());
                if (nodeLevel != null) LinkManager.get(nodeLevel).syncNodeToDimension(node);
            }
        } finally {
            pendingRemovals.remove(key);
        }
    }

    public void refreshLocalCache(long key, BlockPos pos, Direction face, FaceConfigComposite config) {
        if (config.faceConfig.hasGroup() && config.determineRole().canSend()) cacheManager.add(key);
        else cacheManager.remove(key);
    }

    public void activateNode(long key, BlockPos pos, Direction face, FaceConfigComposite config) {
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
        Set<Long> keys = configRepository.keySet();
        int size = keys.size();
        if (size == 0) {
            orphanScanNeeded = false;
            orphanKeys = null;
            orphanScanCursor = 0;
            return;
        }
        if (orphanKeys == null || orphanKeys.length != size) {
            orphanKeys = keys.toArray(new Long[0]);
            orphanScanCursor = 0;
        }
        if (orphanScanCursor >= orphanKeys.length) {
            orphanScanNeeded = false;
            orphanScanCursor = 0;
            return;
        }
        int end = Math.min(orphanScanCursor + ORPHAN_SCAN_BATCH, orphanKeys.length);
        MinecraftServer server = level.getServer();
        for (int i = orphanScanCursor; i < end; i++) {
            long key = orphanKeys[i];
            FaceConfigComposite cfg = faceConfigService.get(key);
            if (cfg == null) continue;
            LogisticsNode node = parent.createNodeFromKey(key);
            if (level.getBlockEntity(node.gPos().pos()) == null) {
                removeFaceConfigInternal(key, true, true);
            } else {
                for (String gid : new ArrayList<>(cfg.faceConfig.getGroupIds())) {
                    if (GlobalLogisticsManager.get(server).getNodeGroupService().getNodesInGroup(gid).isEmpty()) {
                        cfg.faceConfig.removeGroupId(gid);
                        cfg.markDirty();
                        parent.markFaceDirty(key);
                    }
                }
            }
        }
        orphanScanCursor = end >= orphanKeys.length ? 0 : end;
    }

    void onBlockRemoved(BlockPos pos) {
        parent.onBlocksRemovedBulk(List.of(pos));
        com.coobird.staticlogistics.item.util.LinkOperationHelper.cleanStoredNodesForPos(level, pos);
    }

    Set<Long> getAllConfigKeys() {
        return configRepository.keySet();
    }
}