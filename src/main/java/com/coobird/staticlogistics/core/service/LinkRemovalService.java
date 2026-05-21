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

    // 级联删除：先断开 outgoing 链接，再断开所有 incoming 链接，最后删除数据
    public void cascadeRemove(LogisticsNode selfNode, FaceConfigComposite selfConfig) {
        ServerLevel selfLevel = server.getLevel(selfNode.gPos().dimension());
        if (selfLevel == null) return;
        LinkManager selfMgr = LinkManager.get(selfLevel);

        List<LogisticsNode> targets = new ArrayList<>(selfConfig.getLinkedNodes());
        for (LogisticsNode target : targets) {
            selfMgr.removeLink(selfNode, target);
        }

        Set<LogisticsNode> sources = globalManager.getSourcesLinkedTo(selfNode);
        for (LogisticsNode source : sources) {
            selfMgr.removeLink(source, selfNode);
        }

        selfMgr.removeFaceConfigDataOnly(selfNode.toKey());

        // 根源清理：移除所有残留空组ID
        globalManager.cleanupOrphanedGroupIds(selfConfig.faceConfig.getOwner());
    }
}