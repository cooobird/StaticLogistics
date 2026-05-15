package com.coobird.staticlogistics.storage;

import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.core.manager.GlobalLogisticsManager;
import com.coobird.staticlogistics.core.service.LinkRemovalService;
import com.coobird.staticlogistics.storage.config.ContainerConfig;
import com.coobird.staticlogistics.storage.config.FaceConfigComposite;
import com.coobird.staticlogistics.storage.sync.NetworkSyncManager;
import com.coobird.staticlogistics.storage.sync.SyncManager;
import com.coobird.staticlogistics.util.LogisticsCalculator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class LinkChangeHandler {
    private final ServerLevel level;
    private final SyncManager syncManager;
    private final NetworkSyncManager networkSyncManager;
    private final LinkManager linkManager;
    private final Runnable markDirty;
    private final LinkRemovalService linkRemovalService;
    private final GlobalLogisticsManager globalManager;

    public LinkChangeHandler(ServerLevel level, SyncManager syncManager,
                             NetworkSyncManager networkSyncManager,
                             LinkManager linkManager, Runnable markDirty,
                             GlobalLogisticsManager globalManager) {
        this.level = level;
        this.syncManager = syncManager;
        this.networkSyncManager = networkSyncManager;
        this.linkManager = linkManager;
        this.markDirty = markDirty;
        this.linkRemovalService = new LinkRemovalService(level.getServer(), globalManager);
        this.globalManager = globalManager;
    }

    public void onFaceConfigChanged(long key, BlockPos pos, Direction face, FaceConfigComposite cfg) {
        markDirty.run();
        if (cfg.isDefault()) {
            linkManager.removeFaceConfig(key);
            return;
        }

        LogisticsNode currentNode = LogisticsNode.fromKey(key, level.dimension());
        for (LogisticsNode target : cfg.getLinkedNodes()) {
            globalManager.addIncomingLink(currentNode, target);
        }

        autoSymmetrizeLinks(currentNode, cfg);
        linkManager.refreshLocalCache(key, pos, face, cfg);
        syncManager.syncNode(pos, face, cfg);
        networkSyncManager.syncToDimension(pos, face, cfg);
        linkManager.activateNode(key, pos, face, cfg);
    }

    public void onContainerConfigChanged(ContainerConfig config) {
        markDirty.run();

        validateAndCleanLinksForContainer(config);

        for (long faceKey : config.getLinkedFaceKeys()) {
            FaceConfigComposite faceCfg = linkManager.getFaceConfig(faceKey);
            if (faceCfg != null) {
                BlockPos pos = faceCfg.faceConfig.getPos();
                Direction face = Direction.from3DDataValue((int) (faceKey & 0x7));
                linkManager.refreshLocalCache(faceKey, pos, face, faceCfg);
                if (faceCfg.determineRole().canSend()) {
                    linkManager.activateNode(faceKey, pos, face, faceCfg);
                }
                networkSyncManager.syncToDimension(pos, face, faceCfg);
            }
        }
    }

    private void validateAndCleanLinksForContainer(ContainerConfig containerConfig) {
        BlockPos containerPos = containerConfig.getPos();
        if (containerPos == null) {
            for (long faceKey : containerConfig.getLinkedFaceKeys()) {
                processFace(faceKey, containerConfig);
            }
            return;
        }
        for (Direction face : Direction.values()) {
            long faceKey = LinkManager.posToKey(containerPos, face);
            processFace(faceKey, containerConfig);
        }
    }

    private void processFace(long faceKey, ContainerConfig containerConfig) {
        FaceConfigComposite faceCfg = linkManager.getFaceConfig(faceKey);
        if (faceCfg == null) return;

        LogisticsNode selfNode = LogisticsNode.fromKey(faceKey, level.dimension());
        BlockPos selfPos = selfNode.gPos().pos();

        boolean changed = false;
        Iterator<LogisticsNode> it = faceCfg.getLinkedNodes().iterator();
        while (it.hasNext()) {
            LogisticsNode target = it.next();
            boolean sameDim = target.gPos().dimension().equals(level.dimension());
            if ((!sameDim && !LogisticsCalculator.isDimensionEffective(containerConfig)) ||
                (sameDim && !LogisticsCalculator.isWithinRange(selfPos, target.gPos().pos(), containerConfig))) {
                removeLink(selfNode, target, faceCfg, it);
                changed = true;
            }
        }

        if (changed) {
            if (faceCfg.getLinkedNodes().isEmpty() && !faceCfg.isGlobalInputEnabled() && !faceCfg.isGlobalOutputEnabled()) {
                linkManager.removeFaceConfig(faceKey);
            } else {
                networkSyncManager.syncToDimension(selfPos, selfNode.face(), faceCfg);
                linkManager.refreshLocalCache(faceKey, selfPos, selfNode.face(), faceCfg);
                linkManager.setDirty();
            }
        }
    }

    private void removeLink(LogisticsNode source, LogisticsNode target, FaceConfigComposite sourceCfg, Iterator<LogisticsNode> iterator) {
        iterator.remove();
        linkManager.removeLink(source, target);
    }

    public void cascadeRemove(LogisticsNode selfNode, FaceConfigComposite selfConfig) {
        linkRemovalService.cascadeRemove(selfNode, selfConfig);
    }

    private void autoSymmetrizeLinks(LogisticsNode currentNode, FaceConfigComposite currentCfg) {
        List<LogisticsNode> remotes = new ArrayList<>(currentCfg.getLinkedNodes());
        for (LogisticsNode remoteNode : remotes) {
            ServerLevel remoteLevel = level.getServer().getLevel(remoteNode.gPos().dimension());
            if (remoteLevel == null) continue;
            LinkManager remoteMgr = LinkManager.get(remoteLevel);
            FaceConfigComposite remoteCfg = remoteMgr.getFaceConfig(remoteNode.toKey());
            if (remoteCfg == null) continue;
            if (!remoteCfg.getLinkedNodes().contains(currentNode)) {
                remoteCfg.getLinkedNodes().add(currentNode);
                globalManager.addIncomingLink(remoteNode, currentNode);
                remoteCfg.markDirty();
                remoteMgr.setDirty();
                remoteMgr.getNetworkSyncManager().syncToDimension(remoteNode.gPos().pos(), remoteNode.face(), remoteCfg);
            }
        }
    }
}