package com.coobird.staticlogistics.logic.group;

import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.api.NodeRole;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 管理每个组内的发送者/接收者集合。
 */
public class GroupMemberService {
    private final Map<String, GroupMembers> groups = new ConcurrentHashMap<>();

    private static class GroupMembers {
        final Set<LogisticsNode> senders = ConcurrentHashMap.newKeySet();
        final Set<LogisticsNode> receivers = ConcurrentHashMap.newKeySet();

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
            boolean isSender = senders.contains(node);
            boolean isReceiver = receivers.contains(node);
            if (isSender && isReceiver) return NodeRole.BOTH;
            if (isSender) return NodeRole.SENDER;
            if (isReceiver) return NodeRole.RECEIVER;
            return NodeRole.NONE;
        }

        Map<LogisticsNode, NodeRole> getNodeMap() {
            Map<LogisticsNode, NodeRole> map = new HashMap<>();
            for (LogisticsNode n : senders) map.put(n, NodeRole.SENDER);
            for (LogisticsNode n : receivers) {
                if (map.containsKey(n)) map.put(n, NodeRole.BOTH);
                else map.put(n, NodeRole.RECEIVER);
            }
            return map;
        }

        List<LogisticsNode> getReceivers() {
            return new ArrayList<>(receivers);
        }

        List<LogisticsNode> getSenders() {
            return new ArrayList<>(senders);
        }

        boolean isEmpty() {
            return senders.isEmpty() && receivers.isEmpty();
        }
    }

    public void addNode(String groupId, LogisticsNode node, NodeRole role) {
        groups.computeIfAbsent(groupId, k -> new GroupMembers()).addNode(node, role);
    }

    public void removeNode(String groupId, LogisticsNode node) {
        GroupMembers members = groups.get(groupId);
        if (members != null) {
            members.removeNode(node);
            if (members.isEmpty()) groups.remove(groupId);
        }
    }

    public NodeRole getRole(LogisticsNode node) {
        for (GroupMembers members : groups.values()) {
            NodeRole role = members.getRoleOf(node);
            if (role != NodeRole.NONE) return role;
        }
        return NodeRole.NONE;
    }

    public List<LogisticsNode> getSenders(String groupId) {
        GroupMembers members = groups.get(groupId);
        return members != null ? members.getSenders() : Collections.emptyList();
    }

    public List<LogisticsNode> getReceivers(String groupId) {
        GroupMembers members = groups.get(groupId);
        return members != null ? members.getReceivers() : Collections.emptyList();
    }

    public Map<LogisticsNode, NodeRole> getNodesInGroup(String groupId) {
        GroupMembers members = groups.get(groupId);
        return members != null ? members.getNodeMap() : Collections.emptyMap();
    }

    public Set<String> getAllGroupIds() {
        return groups.keySet();
    }
}