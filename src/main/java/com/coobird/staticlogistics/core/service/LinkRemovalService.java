package com.coobird.staticlogistics.core.service;

import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.core.manager.GlobalLogisticsManager;
import com.coobird.staticlogistics.storage.LinkManager;
import com.coobird.staticlogistics.storage.config.FaceConfigComposite;
import com.coobird.staticlogistics.storage.config.LinkConfig;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.util.HashSet;
import java.util.Set;

public class LinkRemovalService {
    private final MinecraftServer server;
    private final GlobalLogisticsManager globalManager;

    public LinkRemovalService(MinecraftServer server, GlobalLogisticsManager globalManager) {
        this.server = server;
        this.globalManager = globalManager;
    }

    public void cascadeRemove(LogisticsNode selfNode, FaceConfigComposite selfConfig) {
        boolean canSend = selfConfig.determineRole().canSend();
        boolean canReceive = selfConfig.determineRole().canReceive();

        if (canSend && !canReceive) {
            Set<LogisticsNode> allTargets = new HashSet<>();
            selfConfig.linkConfig.getAllSettings().values().forEach(data -> allTargets.addAll(data.linkedInputs));
            for (LogisticsNode target : allTargets) {
                completelyRemoveNode(target);
            }
        } else if (canReceive && !canSend) {
            Set<LogisticsNode> allSources = globalManager.getSourcesLinkedTo(selfNode);
            for (LogisticsNode source : allSources) {
                removeLinkTo(source, selfNode);
            }
        } else if (canSend && canReceive) {
            Set<LogisticsNode> allTargets = new HashSet<>();
            selfConfig.linkConfig.getAllSettings().values().forEach(data -> allTargets.addAll(data.linkedInputs));
            for (LogisticsNode target : allTargets) {
                completelyRemoveNode(target);
            }

            Set<LogisticsNode> allSources = globalManager.getSourcesLinkedTo(selfNode);
            for (LogisticsNode source : allSources) {
                removeLinkTo(source, selfNode);
            }
        }

        ServerLevel level = server.getLevel(selfNode.gPos().dimension());
        if (level != null) {
            globalManager.notifyNodeRemoved(level, selfNode);
        }
    }

    private void completelyRemoveNode(LogisticsNode node) {
        ServerLevel level = server.getLevel(node.gPos().dimension());
        if (level == null) return;
        LinkManager mgr = LinkManager.get(level);
        mgr.removeFaceConfigInternal(node.toKey(), false);
    }

    private void removeLinkTo(LogisticsNode source, LogisticsNode targetToRemove) {
        ServerLevel level = server.getLevel(source.gPos().dimension());
        if (level == null) return;
        LinkManager mgr = LinkManager.get(level);
        FaceConfigComposite cfg = mgr.getFaceConfig(source.toKey());
        if (cfg == null) return;

        boolean changed = false;
        for (LinkConfig.SideData data : cfg.linkConfig.getAllSettings().values()) {
            if (data.linkedInputs.remove(targetToRemove)) {
                changed = true;
                if (data.linkedInputs.isEmpty()) {
                    data.outputEnabled = false;
                    data.inputEnabled = false;
                    data.outputChannel = 0;
                    data.inputChannel = 0;
                }
            }
        }

        if (changed) {
            globalManager.removeIncomingLink(source, targetToRemove);

            if (cfg.linkConfig.isDefault()) {
                mgr.removeFaceConfigInternal(source.toKey(), false);
            } else {
                mgr.refreshLocalCache(source.toKey(), source.gPos().pos(), source.face(), cfg);
                mgr.syncConfigToClients(source.gPos().pos());
                mgr.setDirty();
            }
        }
    }
}