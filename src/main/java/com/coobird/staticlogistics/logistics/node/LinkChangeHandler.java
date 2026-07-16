package com.coobird.staticlogistics.logistics.node;

import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.logistics.node.sync.SyncManager;
import com.coobird.staticlogistics.transfer.LogisticsCalculator;
import com.coobird.staticlogistics.transfer.NodeQueryService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;

import java.util.List;

/**
 * 链接变更处理器 —— 当面容配置或容器配置发生变更时，负责同步链接、校验有效性、级联删除等。
 */
public class LinkChangeHandler {
    private final ServerLevel level;
    private final SyncManager syncManager;
    private final LinkManager linkManager;

    public LinkChangeHandler(ServerLevel level, SyncManager syncManager,
                             LinkManager linkManager) {
        this.level = level;
        this.syncManager = syncManager;
        this.linkManager = linkManager;
    }

    /**
     * 面容配置变更回调：标记脏数据、自动对称链接、刷新缓存、同步到客户端
     */
    public void onFaceConfigChanged(FaceAddress address, BlockPos pos, Direction face,
                                    FaceConfigComposite cfg) {
        if (linkManager.isLifecycleRemovalInProgress()) return;
        if (linkManager.getFaceConfig(address) != cfg) return;
        if (NodeMutationTransaction.defer(level.getServer(),
            new FaceChangeKey(linkManager, address),
            () -> onFaceConfigChanged(address, pos, face, cfg))) return;
        NodeQueryService.invalidateNode(level.getServer(), linkManager.createNodeFromKey(address));
        cfg.setVersion(linkManager.normalizeVersion(address, cfg.getVersion()));
        linkManager.markFaceDirty(address);
        if (cfg.isDefault()) {
            linkManager.removeFaceConfig(address);
            return;
        }

        LogisticsNode currentNode = linkManager.createNodeFromKey(address);
        autoSymmetrizeLinks(currentNode, cfg);
        linkManager.refreshLocalCache(address, pos, face, cfg);
        syncManager.syncNode(pos, face, cfg);
        if (cfg.faceConfig.hasGroup()) {
            linkManager.scheduleNetworkSync(currentNode);
        }
        linkManager.activateNode(address, pos, face, cfg);
    }

    /**
     * 容器配置变更回调：校验该容器所有链接是否仍在有效范围内，并刷新缓存
     */
    public void onContainerConfigChanged(ContainerConfig config) {
        if (linkManager.isLifecycleRemovalInProgress()) return;
        if (NodeMutationTransaction.defer(level.getServer(),
            new ContainerChangeKey(linkManager, config), () -> onContainerConfigChanged(config))) return;
        linkManager.markContainerDirty(config.getPos().asLong());
        validateAndCleanLinksForContainer(config);
        for (FaceAddress address : config.getLinkedFaceKeys()) {
            FaceConfigComposite faceCfg = linkManager.getFaceConfig(address);
            if (faceCfg != null) {
                NodeQueryService.invalidateNode(level.getServer(), linkManager.createNodeFromKey(address));
                faceCfg.setVersion(linkManager.normalizeVersion(address, faceCfg.getVersion()));
                linkManager.markFaceDirty(address);
                BlockPos pos = faceCfg.faceConfig.getPos();
                Direction face = address.face();
                linkManager.refreshLocalCache(address, pos, face, faceCfg);
                if (faceCfg.determineRole().canSend()) {
                    linkManager.activateNode(address, pos, face, faceCfg);
                }
                linkManager.scheduleNetworkSync(linkManager.createNodeFromKey(address));
            }
        }
    }

    /**
     * 遍历该容器所有面，检查链接节点是否超出范围或跨维度无效，超出的就移除
     */
    private void validateAndCleanLinksForContainer(ContainerConfig containerConfig) {
        BlockPos containerPos = containerConfig.getPos();
        if (containerPos == null) {
            for (FaceAddress address : containerConfig.getLinkedFaceKeys()) {
                processFace(address, containerConfig);
            }
            return;
        }
        for (Direction face : Direction.values()) {
            processFace(FaceAddress.of(containerPos, face), containerConfig);
        }
    }

    private void processFace(FaceAddress address, ContainerConfig containerConfig) {
        FaceConfigComposite faceCfg = linkManager.getFaceConfig(address);
        if (faceCfg == null) return;
        LogisticsNode selfNode = linkManager.createNodeFromKey(address);
        BlockPos selfPos = selfNode.gPos().pos();
        boolean changed = false;
        for (LogisticsNode target : List.copyOf(faceCfg.getLinkedNodes())) {
            boolean sameDim = target.isInSameDimension(level.dimension());
            if ((!sameDim && !LogisticsCalculator.isDimensionEffective(containerConfig)) ||
                (sameDim && LogisticsCalculator.isOutOfRange(selfPos, target.gPos().pos(), containerConfig))) {
                linkManager.removeLink(selfNode, target);
                changed = true;
            }
        }
        if (changed) {
            if (faceCfg.getLinkedNodes().isEmpty() && !faceCfg.isGlobalInputEnabled() && !faceCfg.isGlobalOutputEnabled()) {
                linkManager.removeFaceConfig(address);
            } else {
                linkManager.scheduleNetworkSync(selfNode);
                linkManager.refreshLocalCache(address, selfPos, selfNode.face(), faceCfg);
                linkManager.markFaceDirty(address);
            }
        }
    }

    /**
     * 自动对称链接：如果对面节点没连回来，就帮它补上反向链接
     */
    private void autoSymmetrizeLinks(LogisticsNode currentNode, FaceConfigComposite currentCfg) {
        linkManager.repairReciprocalEdges(currentNode, currentCfg);
    }

    private record FaceChangeKey(LinkManager manager, FaceAddress address) {
    }

    private record ContainerChangeKey(LinkManager manager, ContainerConfig config) {
    }
}
