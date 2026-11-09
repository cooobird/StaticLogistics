package com.coobird.staticlogistics.logistics.node.sync;

import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.api.NodeRole;
import com.coobird.staticlogistics.api.event.LogisticsNodeEvent;
import com.coobird.staticlogistics.api.group.GroupKey;
import com.coobird.staticlogistics.api.group.GroupRef;
import com.coobird.staticlogistics.logistics.group.GlobalLogisticsManager;
import com.coobird.staticlogistics.logistics.node.EndpointConfig;
import net.minecraftforge.common.MinecraftForge;

import java.util.*;

/// 将权威面配置转换为精确的节点成员关系差异事件。
public final class NodeMembershipPublisher {
    private final GlobalLogisticsManager globalManager;

    public NodeMembershipPublisher(GlobalLogisticsManager globalManager) {
        this.globalManager = globalManager;
    }

    public void syncNode(LogisticsNode node, EndpointConfig config) {
        globalManager.syncOutgoingLinks(node, config.getLinkedNodesByGroup());
        Set<GroupKey> previous = globalManager.getNodeGroupService().getAllGroupKeys(node);
        Map<GroupKey, GroupRef> desired = new LinkedHashMap<>();
        NodeRole desiredRole = config.determineRole();
        /// 分组成员关系属于持久拓扑；NONE 只表示暂停传输，不能注销节点并连带清除边。
        config.faceConfig.getGroups().forEach(group -> desired.put(group.key(), group));

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
            LogisticsNodeEvent.NodeEntry entry = new LogisticsNodeEvent.NodeEntry(group, node, desiredRole);
            if (!previous.contains(group.key())) {
                added.add(entry);
            } else {
                NodeRole previousRole = globalManager.getNodesInGroup(group.key()).get(node);
                if (previousRole != desiredRole) changed.add(entry);
            }
        }
        post(added, LogisticsNodeEvent.ChangeType.ADDED);
        post(changed, LogisticsNodeEvent.ChangeType.CHANGED);
    }

    private void post(List<LogisticsNodeEvent.NodeEntry> entries,
                      LogisticsNodeEvent.ChangeType type) {
        if (!entries.isEmpty()) {
            MinecraftForge.EVENT_BUS.post(new LogisticsNodeEvent(globalManager.getServer(), entries, type));
        }
    }
}
