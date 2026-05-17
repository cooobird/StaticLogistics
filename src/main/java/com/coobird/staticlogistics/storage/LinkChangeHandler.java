package com.coobird.staticlogistics.storage;

import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.core.manager.GlobalLogisticsManager;
import com.coobird.staticlogistics.core.service.LinkRemovalService;
import com.coobird.staticlogistics.storage.config.ContainerConfig;
import com.coobird.staticlogistics.storage.config.FaceConfigComposite;
import com.coobird.staticlogistics.storage.sync.NetworkSyncManager;
import com.coobird.staticlogistics.storage.sync.SyncManager;
import com.coobird.staticlogistics.util.LogisticsCalculator;
import com.coobird.staticlogistics.util.LogisticsConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * 链接变更处理器 —— 当面容配置或容器配置发生变更时，负责同步链接、校验有效性、级联删除等。
 */
public class LinkChangeHandler {
    private final ServerLevel level;
    private final SyncManager syncManager;
    private final NetworkSyncManager networkSyncManager;
    private final LinkManager linkManager;
    private final LinkRemovalService linkRemovalService;

    public LinkChangeHandler(ServerLevel level, SyncManager syncManager,
                             NetworkSyncManager networkSyncManager,
                             LinkManager linkManager,
                             GlobalLogisticsManager globalManager) {
        this.level = level;
        this.syncManager = syncManager;
        this.networkSyncManager = networkSyncManager;
        this.linkManager = linkManager;
        this.linkRemovalService = new LinkRemovalService(level.getServer(), globalManager);
    }

    /**
     * 面容配置变更回调：标记脏数据、自动对称链接、刷新缓存、同步到客户端
     */
    public void onFaceConfigChanged(long key, BlockPos pos, Direction face, FaceConfigComposite cfg) {
        linkManager.markFaceDirty(key);
        if (cfg.isDefault()) {
            linkManager.removeFaceConfig(key);
            return;
        }

        LogisticsNode currentNode = linkManager.createNodeFromKey(key);
        autoSymmetrizeLinks(currentNode, cfg);
        linkManager.refreshLocalCache(key, pos, face, cfg);
        syncManager.syncNode(pos, face, cfg);
        linkManager.syncNodeToDimension(currentNode);
        linkManager.activateNode(key, pos, face, cfg);
    }

    /**
     * 容器配置变更回调：校验该容器所有链接是否仍在有效范围内，并刷新缓存
     */
    public void onContainerConfigChanged(ContainerConfig config) {
        linkManager.markContainerDirty(config.getPos().asLong());
        validateAndCleanLinksForContainer(config);
        for (long faceKey : config.getLinkedFaceKeys()) {
            FaceConfigComposite faceCfg = linkManager.getFaceConfig(faceKey);
            if (faceCfg != null) {
                BlockPos pos = faceCfg.faceConfig.getPos();
                Direction face = Direction.from3DDataValue((int) (faceKey & LogisticsConstants.Storage.FACE_MASK));
                linkManager.refreshLocalCache(faceKey, pos, face, faceCfg);
                if (faceCfg.determineRole().canSend()) {
                    linkManager.activateNode(faceKey, pos, face, faceCfg);
                }
                linkManager.syncNodeToDimension(linkManager.createNodeFromKey(faceKey));
            }
        }
    }

    /**
     * 遍历该容器所有面，检查链接节点是否超出范围或跨维度无效，超出的就移除
     */
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
        LogisticsNode selfNode = linkManager.createNodeFromKey(faceKey);
        BlockPos selfPos = selfNode.gPos().pos();
        boolean changed = false;
        Iterator<LogisticsNode> it = faceCfg.getLinkedNodes().iterator();
        while (it.hasNext()) {
            LogisticsNode target = it.next();
            boolean sameDim = target.isInSameDimension(level.dimension());
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
                linkManager.syncNodeToDimension(selfNode);
                linkManager.refreshLocalCache(faceKey, selfPos, selfNode.face(), faceCfg);
                linkManager.markFaceDirty(faceKey);
            }
        }
    }

    private void removeLink(LogisticsNode source, LogisticsNode target, FaceConfigComposite sourceCfg, Iterator<LogisticsNode> iterator) {
        iterator.remove();
        linkManager.removeLink(source, target);
    }

    /**
     * 级联删除：移除自己时，把所有相连节点中指向自己的引用也一并删掉
     */
    public void cascadeRemove(LogisticsNode selfNode, FaceConfigComposite selfConfig) {
        linkRemovalService.cascadeRemove(selfNode, selfConfig);
    }

    /**
     * 自动对称链接：如果对面节点没连回来，就帮它补上反向链接
     */
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
                GlobalLogisticsManager.get(level.getServer()).addReverseLink(remoteNode.toKey(), currentNode.toKey());
                remoteCfg.markDirty();
                remoteMgr.markFaceDirty(remoteNode.toKey());
                remoteMgr.syncNodeToDimension(remoteNode);
            }
        }
    }
}