package com.coobird.staticlogistics.storage;

import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.api.type.TransferType;
import com.coobird.staticlogistics.core.manager.GlobalLogisticsManager;
import com.coobird.staticlogistics.core.registration.TransferRegistries;
import com.coobird.staticlogistics.core.service.LinkRemovalService;
import com.coobird.staticlogistics.storage.config.ContainerConfig;
import com.coobird.staticlogistics.storage.config.FaceConfigComposite;
import com.coobird.staticlogistics.storage.config.LinkConfig;
import com.coobird.staticlogistics.storage.sync.NetworkSyncManager;
import com.coobird.staticlogistics.storage.sync.SyncManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.List;

/**
 * 处理 FaceConfig 和 ContainerConfig 变更后的业务逻辑，
 */
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

    /**
     * 当 FaceConfig 脏数据被标记时调用（配置发生变更）
     */
    public void onFaceConfigChanged(long key, BlockPos pos, Direction face, FaceConfigComposite cfg) {
        markDirty.run();

        if (cfg.isDefault()) {
            linkManager.removeFaceConfig(key);
            return;
        }

        LogisticsNode currentNode = LogisticsNode.fromKey(key, level.dimension());
        // 建立正向索引（用于级联删除）
        for (LinkConfig.SideData data : cfg.linkConfig.getAllSettings().values()) {
            for (LogisticsNode target : data.linkedInputs) {
                globalManager.addIncomingLink(currentNode, target);
            }
        }

        // 自动对称链接
        autoSymmetrizeLinks(currentNode, cfg);

        // 更新本地缓存（是否作为发送者）
        linkManager.refreshLocalCache(key, pos, face, cfg);
        // 同步到 GlobalLogisticsManager（节点角色）
        syncManager.syncNode(pos, face, cfg);
        // 同步到客户端
        networkSyncManager.syncToDimension(pos, face, cfg);
        // 唤醒节点，立即尝试传输
        linkManager.activateNode(key, pos, face, cfg);
    }

    /**
     * 当 ContainerConfig 脏数据被标记时调用（升级槽变化）
     */
    public void onContainerConfigChanged(ContainerConfig config) {
        markDirty.run();

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

    /**
     * 级联删除：根据节点角色删除相关链路。
     */
    public void cascadeRemove(LogisticsNode selfNode, FaceConfigComposite selfConfig) {
        linkRemovalService.cascadeRemove(selfNode, selfConfig);
    }

    /**
     * 自动对称链接：确保每个 linkedInputs 中的远程节点也包含指向当前节点的反向链接。
     */
    private void autoSymmetrizeLinks(LogisticsNode currentNode, FaceConfigComposite currentCfg) {
        for (TransferType type : TransferRegistries.getAllActive()) {
            LinkConfig.SideData currentData = currentCfg.linkConfig.getSettings(type);
            List<LogisticsNode> remotes = new ArrayList<>(currentData.linkedInputs);

            for (LogisticsNode remoteNode : remotes) {
                ServerLevel remoteLevel = level.getServer().getLevel(remoteNode.gPos().dimension());
                if (remoteLevel == null) continue;

                LinkManager remoteMgr = LinkManager.get(remoteLevel);
                FaceConfigComposite remoteCfg = remoteMgr.getFaceConfig(remoteNode.toKey());
                if (remoteCfg == null) continue;

                LinkConfig.SideData remoteData = remoteCfg.linkConfig.getSettings(type);
                if (!remoteData.linkedInputs.contains(currentNode)) {
                    remoteData.linkedInputs.add(currentNode);
                    globalManager.addIncomingLink(remoteNode, currentNode);
                    remoteCfg.markDirty();
                    remoteMgr.setDirty();
                    remoteMgr.getNetworkSyncManager().syncToDimension(remoteNode.gPos().pos(), remoteNode.face(), remoteCfg);
                }
            }
        }
    }
}