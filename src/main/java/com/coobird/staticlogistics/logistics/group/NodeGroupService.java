package com.coobird.staticlogistics.logistics.group;

import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.api.NodeRole;
import com.coobird.staticlogistics.api.group.GroupKey;

import java.util.*;

/// 服务端主线程维护分组成员与反向归属；角色只保存一份，关闭的节点仍是分组成员。
public final class NodeGroupService {
    private final Map<GroupKey, LinkedHashMap<LogisticsNode, NodeRole>> groups = new HashMap<>();
    private final Map<LogisticsNode, Set<GroupKey>> nodeToGroups = new HashMap<>();

    public void register(GroupKey group, LogisticsNode node, NodeRole role) {
        if (group == null) return;
        groups.computeIfAbsent(group, ignored -> new LinkedHashMap<>()).put(node, role);
        nodeToGroups.computeIfAbsent(node, ignored -> new HashSet<>()).add(group);
    }

    public void unregister(LogisticsNode node) {
        for (GroupKey group : getAllGroupKeys(node)) unregister(group, node);
    }

    public void unregister(GroupKey group, LogisticsNode node) {
        Set<GroupKey> memberships = nodeToGroups.get(node);
        if (memberships == null || !memberships.remove(group)) return;
        if (memberships.isEmpty()) nodeToGroups.remove(node);
        Map<LogisticsNode, NodeRole> members = groups.get(group);
        if (members != null) {
            members.remove(node);
            if (members.isEmpty()) groups.remove(group);
        }
    }

    public Set<GroupKey> getAllGroupKeys(LogisticsNode node) {
        Set<GroupKey> memberships = nodeToGroups.get(node);
        return memberships == null ? Set.of() : Set.copyOf(memberships);
    }

    public Set<GroupKey> getAllGroupKeys() {
        return Set.copyOf(groups.keySet());
    }

    public Map<LogisticsNode, NodeRole> getNodesInGroup(GroupKey group) {
        Map<LogisticsNode, NodeRole> members = groups.get(group);
        return members == null ? Map.of() : Map.copyOf(members);
    }

    public List<LogisticsNode> getSenders(GroupKey group) {
        return membersWithRole(group, NodeRole.SENDER);
    }

    public List<LogisticsNode> getReceivers(GroupKey group) {
        return membersWithRole(group, NodeRole.RECEIVER);
    }

    private List<LogisticsNode> membersWithRole(GroupKey group, NodeRole role) {
        Map<LogisticsNode, NodeRole> members = groups.get(group);
        if (members == null) return List.of();
        return members.entrySet().stream()
            .filter(entry -> entry.getValue() == role || entry.getValue() == NodeRole.BOTH)
            .map(Map.Entry::getKey).toList();
    }
}
