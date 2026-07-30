package com.coobird.staticlogistics.logistics.group;

import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.api.NodeRole;
import com.coobird.staticlogistics.api.group.GroupKey;

import java.util.*;

/**
 * 管理每个组内的发送者/接收者集合。
 *
 * <p>线程安全：
 * <ul>
 *   <li>groups: 主线程单线程访问，使用 HashMap</li>
 *   <li>GroupMembers.senders/receivers: 主线程单线程访问，使用 LinkedHashSet</li>
 * </ul>
 */
public class GroupMemberService {
    private final Map<GroupKey, GroupMembers> groups = new HashMap<>();

    private static class GroupMembers {
        /**
         * 分组拓扑成员独立于当前传输角色；NONE 节点仍然必须留在这里。
         */
        final Set<LogisticsNode> nodes = new LinkedHashSet<>();
        final Set<LogisticsNode> senders = new LinkedHashSet<>();
        final Set<LogisticsNode> receivers = new LinkedHashSet<>();

        void addNode(LogisticsNode node, NodeRole role) {
            nodes.add(node);
            senders.remove(node);
            receivers.remove(node);
            if (role == NodeRole.SENDER || role == NodeRole.BOTH) senders.add(node);
            if (role == NodeRole.RECEIVER || role == NodeRole.BOTH) receivers.add(node);
        }

        void removeNode(LogisticsNode node) {
            nodes.remove(node);
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
            for (LogisticsNode node : nodes) map.put(node, NodeRole.NONE);
            for (LogisticsNode n : senders) map.put(n, NodeRole.SENDER);
            for (LogisticsNode n : receivers) {
                map.put(n, senders.contains(n) ? NodeRole.BOTH : NodeRole.RECEIVER);
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
            return nodes.isEmpty();
        }
    }

    public void addNode(GroupKey groupKey, LogisticsNode node, NodeRole role) {
        groups.computeIfAbsent(groupKey, k -> new GroupMembers()).addNode(node, role);
    }

    public void removeNode(GroupKey groupKey, LogisticsNode node) {
        GroupMembers members = groups.get(groupKey);
        if (members != null) {
            members.removeNode(node);
            if (members.isEmpty()) groups.remove(groupKey);
        }
    }

    public List<LogisticsNode> getSenders(GroupKey groupKey) {
        GroupMembers members = groups.get(groupKey);
        return members != null ? members.getSenders() : Collections.emptyList();
    }

    public List<LogisticsNode> getReceivers(GroupKey groupKey) {
        GroupMembers members = groups.get(groupKey);
        return members != null ? members.getReceivers() : Collections.emptyList();
    }

    public Map<LogisticsNode, NodeRole> getNodesInGroup(GroupKey groupKey) {
        GroupMembers members = groups.get(groupKey);
        return members != null ? members.getNodeMap() : Collections.emptyMap();
    }

    public Set<GroupKey> getAllGroupKeys() {
        return Collections.unmodifiableSet(groups.keySet());
    }
}
