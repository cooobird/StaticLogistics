package com.coobird.staticlogistics.logistics.group;

import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.api.NodeRole;
import com.coobird.staticlogistics.api.group.GroupKey;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 维护节点与稳定分组身份之间的双向成员索引。
 */
public class NodeGroupService {
    private final Map<LogisticsNode, Set<GroupKey>> nodeToGroups = new HashMap<>();
    private final GroupMemberService groupMemberService;
    private final Object lock = new Object();

    public NodeGroupService(GroupMemberService groupMemberService) {
        this.groupMemberService = groupMemberService;
    }

    public void register(GroupKey groupKey, LogisticsNode node, NodeRole role) {
        synchronized (lock) {
            if (groupKey == null) return;
            groupMemberService.addNode(groupKey, node, role);
            nodeToGroups.computeIfAbsent(node, ignored -> new HashSet<>()).add(groupKey);
        }
    }

    public void unregister(LogisticsNode node) {
        synchronized (lock) {
            Set<GroupKey> groups = nodeToGroups.remove(node);
            if (groups == null) return;
            for (GroupKey groupKey : groups) groupMemberService.removeNode(groupKey, node);
        }
    }

    public void unregister(GroupKey groupKey, LogisticsNode node) {
        synchronized (lock) {
            Set<GroupKey> groups = nodeToGroups.get(node);
            if (groups == null || !groups.remove(groupKey)) return;
            groupMemberService.removeNode(groupKey, node);
            if (groups.isEmpty()) nodeToGroups.remove(node);
        }
    }

    public GroupKey getGroupKey(LogisticsNode node) {
        Set<GroupKey> groups = nodeToGroups.get(node);
        return groups == null || groups.isEmpty() ? null : groups.iterator().next();
    }

    public Set<GroupKey> getAllGroupKeys(LogisticsNode node) {
        Set<GroupKey> groups = nodeToGroups.get(node);
        return groups == null ? Set.of() : Set.copyOf(groups);
    }

    public Map<LogisticsNode, NodeRole> getNodesInGroup(GroupKey groupKey) {
        return groupMemberService.getNodesInGroup(groupKey);
    }
}
