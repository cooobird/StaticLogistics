package com.coobird.staticlogistics.logistics.node.sync;

import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.api.NodeRole;
import com.coobird.staticlogistics.api.event.LogisticsNodeEvent;
import com.coobird.staticlogistics.api.group.GroupKey;
import com.coobird.staticlogistics.api.group.GroupRef;
import com.coobird.staticlogistics.logistics.group.GlobalLogisticsManager;
import com.coobird.staticlogistics.logistics.node.FaceConfigComposite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.MinecraftForge;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public class SyncManager {
    private final ResourceKey<Level> dimension;
    private final GlobalLogisticsManager globalManager;

    public SyncManager(ResourceKey<Level> dimension, GlobalLogisticsManager globalManager) {
        this.dimension = dimension;
        this.globalManager = globalManager;
    }

    public void syncNode(BlockPos pos, Direction face, FaceConfigComposite config) {
        LogisticsNode node = new LogisticsNode(GlobalPos.of(dimension, pos), face);
        Set<GroupKey> previous = globalManager.getNodeGroupService().getAllGroupKeys(node);
        Map<GroupKey, GroupRef> desired = new LinkedHashMap<>();
        NodeRole desiredRole = config.determineRole();
        if (desiredRole != NodeRole.NONE) {
            config.faceConfig.getGroups().forEach(group -> desired.put(group.key(), group));
        }

        var removed = new ArrayList<LogisticsNodeEvent.NodeEntry>();
        for (GroupKey groupKey : previous) {
            if (!desired.containsKey(groupKey)) {
                removed.add(new LogisticsNodeEvent.NodeEntry(groupKey, node,
                    globalManager.getNodesInGroup(groupKey).getOrDefault(node, NodeRole.NONE)));
            }
        }
        post(removed, LogisticsNodeEvent.ChangeType.REMOVED);

        var added = new ArrayList<LogisticsNodeEvent.NodeEntry>();
        var changed = new ArrayList<LogisticsNodeEvent.NodeEntry>();
        for (GroupRef group : desired.values()) {
            LogisticsNodeEvent.NodeEntry entry =
                new LogisticsNodeEvent.NodeEntry(group, node, desiredRole);
            if (!previous.contains(group.key())) {
                added.add(entry);
            } else {
                NodeRole previousRole = globalManager.getNodesInGroup(group.key())
                    .getOrDefault(node, NodeRole.NONE);
                if (previousRole != desiredRole) changed.add(entry);
            }
        }
        post(added, LogisticsNodeEvent.ChangeType.ADDED);
        post(changed, LogisticsNodeEvent.ChangeType.CHANGED);
    }

    private void post(java.util.List<LogisticsNodeEvent.NodeEntry> entries,
                      LogisticsNodeEvent.ChangeType type) {
        if (!entries.isEmpty()) {
            MinecraftForge.EVENT_BUS.post(
                new LogisticsNodeEvent(globalManager.getServer(), entries, type));
        }
    }
}
