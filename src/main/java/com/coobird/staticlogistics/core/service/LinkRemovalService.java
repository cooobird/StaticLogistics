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
        for (LogisticsNode target : targets) {
            selfMgr.removeLink(selfNode, target);
        }

        Set<LogisticsNode> sources = globalManager.getSourcesLinkedTo(selfNode);
        for (LogisticsNode source : sources) {
            selfMgr.removeLink(source, selfNode);
        }

        selfMgr.removeFaceConfigInternal(selfNode.toKey(), false);
    }
}