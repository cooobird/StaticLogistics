package com.coobird.staticlogistics.logistics.group;

import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.api.NodeRole;
import com.coobird.staticlogistics.api.group.GroupKey;

import java.util.*;

/**
 * 以稳定分组身份维护发送端和接收端成员。
 */
public class GroupMemberService {
    private final Map<GroupKey, GroupMembers> groups = new HashMap<>();

    private static final class GroupMembers {
        private final Set<LogisticsNode> senders = new LinkedHashSet<>();
        private final Set<LogisticsNode> receivers = new LinkedHashSet<>();

        void addNode(LogisticsNode node, NodeRole role) {
            senders.remove(node);
            receivers.remove(node);
            if (role == NodeRole.SENDER || role == NodeRole.BOTH) senders.add(node);
            if (role == NodeRole.RECEIVER || role == NodeRole.BOTH) receivers.add(node);
        }

        void removeNode(LogisticsNode node) {
            senders.remove(node);
            receivers.remove(node);
        }

        NodeRole getRoleOf(LogisticsNode node) {
            boolean sender = senders.contains(node);
            boolean receiver = receivers.contains(node);
            if (sender && receiver) return NodeRole.BOTH;
            if (sender) return NodeRole.SENDER;
            if (receiver) return NodeRole.RECEIVER;
            return NodeRole.NONE;
        }

        Map<LogisticsNode, NodeRole> nodeMap() {
            Map<LogisticsNode, NodeRole> result = new HashMap<>();
            for (LogisticsNode node : senders) result.put(node, NodeRole.SENDER);
            for (LogisticsNode node : receivers) {
                result.merge(node, NodeRole.RECEIVER,
                    (first, second) -> first == second ? first : NodeRole.BOTH);
            }
            return result;
        }
    }

    public void addNode(GroupKey groupKey, LogisticsNode node, NodeRole role) {
        groups.computeIfAbsent(groupKey, ignored -> new GroupMembers()).addNode(node, role);
    }

    public void removeNode(GroupKey groupKey, LogisticsNode node) {
        GroupMembers members = groups.get(groupKey);
        if (members == null) return;
        members.removeNode(node);
        if (members.senders.isEmpty() && members.receivers.isEmpty()) groups.remove(groupKey);
    }

    public NodeRole getRole(LogisticsNode node) {
        for (GroupMembers members : groups.values()) {
            NodeRole role = members.getRoleOf(node);
            if (role != NodeRole.NONE) return role;
        }
        return NodeRole.NONE;
    }

    public List<LogisticsNode> getSenders(GroupKey groupKey) {
        GroupMembers members = groups.get(groupKey);
        return members == null ? List.of() : new ArrayList<>(members.senders);
    }

    public List<LogisticsNode> getReceivers(GroupKey groupKey) {
        GroupMembers members = groups.get(groupKey);
        return members == null ? List.of() : new ArrayList<>(members.receivers);
    }

    public Map<LogisticsNode, NodeRole> getNodesInGroup(GroupKey groupKey) {
        GroupMembers members = groups.get(groupKey);
        return members == null ? Map.of() : members.nodeMap();
    }

    public Set<GroupKey> getAllGroupKeys() {
        return Collections.unmodifiableSet(groups.keySet());
    }
}
