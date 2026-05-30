package com.coobird.staticlogistics.core.service;

import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.core.manager.GlobalLogisticsManager;
import com.coobird.staticlogistics.storage.LinkManager;
import com.coobird.staticlogistics.storage.config.FaceConfigComposite;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 链接级联删除服务——删除一个节点的同时清除所有指向它和它指向的链接。
 */
public class LinkRemovalService {
    private final MinecraftServer server;
    private final GlobalLogisticsManager globalManager;

    public LinkRemovalService(MinecraftServer server, GlobalLogisticsManager globalManager) {
        this.server = server;
        this.globalManager = globalManager;
    }

    public void cascadeRemove(LogisticsNode selfNode, FaceConfigComposite selfConfig) {
        ServerLevel selfLevel = server.getLevel(selfNode.gPos().dimension());
        if (selfLevel == null) return;
        LinkManager selfMgr = LinkManager.get(selfLevel);

        List<LogisticsNode> targets = new ArrayList<>(selfConfig.getLinkedNodes());
        selfConfig.getLinkedNodes().clear();

        for (LogisticsNode target : targets) {
            ServerLevel targetLevel = server.getLevel(target.gPos().dimension());
            if (targetLevel == null) continue;
            LinkManager targetMgr = LinkManager.get(targetLevel);
            FaceConfigComposite targetCfg = targetMgr.getFaceConfig(target.toKey());
            if (targetCfg == null) continue;
            if (targetCfg.getLinkedNodes().remove(selfNode)) {
                if (targetCfg.getLinkedNodes().isEmpty()) {
                    targetCfg.setGlobalOutputEnabled(false);
                    targetCfg.setGlobalInputEnabled(false);
                }
                targetCfg.markDirty();
                targetMgr.cleanUpFaceIfNeeded(target, targetCfg);
            }
        }
        
        Set<LogisticsNode> sources = globalManager.getSourcesLinkedTo(selfNode);
        for (LogisticsNode source : sources) {
            ServerLevel sourceLevel = server.getLevel(source.gPos().dimension());
            if (sourceLevel == null) continue;
            LinkManager sourceMgr = LinkManager.get(sourceLevel);
            FaceConfigComposite sourceCfg = sourceMgr.getFaceConfig(source.toKey());
            if (sourceCfg == null) continue;
            if (sourceCfg.getLinkedNodes().remove(selfNode)) {
                if (sourceCfg.getLinkedNodes().isEmpty()) {
                    sourceCfg.setGlobalOutputEnabled(false);
                    sourceCfg.setGlobalInputEnabled(false);
                }
                sourceCfg.markDirty();
                sourceMgr.cleanUpFaceIfNeeded(source, sourceCfg);
            }
        }

        globalManager.markReverseLinksStale();
        selfMgr.removeFaceConfigDataOnly(selfNode.toKey());
        globalManager.cleanupOrphanedGroupIds(selfConfig.faceConfig.getOwner());
    }
}
