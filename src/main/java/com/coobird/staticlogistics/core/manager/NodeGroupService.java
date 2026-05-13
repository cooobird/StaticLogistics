package com.coobird.staticlogistics.core.manager;

import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.api.NodeRole;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 维护节点到所属组的映射，以及组内节点角色。
 * 职责：节点的注册/注销，组 ID 查询，组内所有节点及角色。
 */
public class NodeGroupService {
    private final Map<LogisticsNode, String> nodeToGroup = new ConcurrentHashMap<>();
    private final GroupMemberService groupMemberService;

    public NodeGroupService(GroupMemberService groupMemberService) {
        this.groupMemberService = groupMemberService;
    }

    /**
     * 注册节点到指定组，若已存在且组不同则先注销。
     */
    public void register(String groupId, LogisticsNode node, NodeRole role) {
        if (groupId == null || groupId.isEmpty()) {
            unregister(node);
            return;
        }
        String currentGroup = nodeToGroup.get(node);
        if (currentGroup != null && !currentGroup.equals(groupId)) {
            unregister(node);
        }
        groupMemberService.addNode(groupId, node, role);
        nodeToGroup.put(node, groupId);
    }

    /**
     * 注销节点（从原组中移除）
     */
    public void unregister(LogisticsNode node) {
        String groupId = nodeToGroup.remove(node);
        if (groupId != null) {
            groupMemberService.removeNode(groupId, node);
        }
    }

    /**
     * 返回节点所属的组 ID，未注册则返回 null。
     */
    public String getGroupId(LogisticsNode node) {
        return nodeToGroup.get(node);
    }

    /**
     * 返回指定组内所有节点及其角色。
     */
    public Map<LogisticsNode, NodeRole> getNodesInGroup(String groupId) {
        return groupMemberService.getNodesInGroup(groupId);
    }

    /**
     * 检查节点是否已注册。
     */
    public boolean containsNode(LogisticsNode node) {
        return nodeToGroup.containsKey(node);
    }

    /**
     * 获取所有已注册节点的集合（用于调试/清理）。
     */
    public Set<LogisticsNode> getAllNodes() {
        return nodeToGroup.keySet();
    }
}