package com.coobird.staticlogistics.core.manager;

import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.api.NodeRole;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 维护节点到所属组的映射，以及组内节点角色。
 * 支持一个节点属于多个组。
 * 线程安全：所有修改操作通过 synchronized 保证原子性。
 */
public class NodeGroupService {
    private final Map<LogisticsNode, Set<String>> nodeToGroups = new ConcurrentHashMap<>();
    private final GroupMemberService groupMemberService;
    private final Object lock = new Object();

    public NodeGroupService(GroupMemberService groupMemberService) {
        this.groupMemberService = groupMemberService;
    }

    /**
     * 注册节点到指定组。支持多组——已存在其他组时不会移除，只追加。
     */
    public void register(String groupId, LogisticsNode node, NodeRole role) {
        synchronized (lock) {
            if (groupId == null || groupId.isEmpty()) return;
            groupMemberService.addNode(groupId, node, role);
            nodeToGroups.computeIfAbsent(node, k -> ConcurrentHashMap.newKeySet()).add(groupId);
        }
    }

    /**
     * 注销节点（从所有已注册的组中移除）
     */
    public void unregister(LogisticsNode node) {
        synchronized (lock) {
            Set<String> groups = nodeToGroups.remove(node);
            if (groups != null) {
                for (String gid : groups) {
                    groupMemberService.removeNode(gid, node);
                }
            }
        }
    }

    /**
     * 返回节点所属的第一个组 ID（向后兼容），未注册则返回 null。
     */
    public String getGroupId(LogisticsNode node) {
        Set<String> groups = nodeToGroups.get(node);
        return groups != null && !groups.isEmpty() ? groups.iterator().next() : null;
    }

    /**
     * 返回节点所属的所有组 ID 副本，未注册则返回空集合。
     */
    public Set<String> getAllGroupIds(LogisticsNode node) {
        Set<String> groups = nodeToGroups.get(node);
        return groups != null ? Set.copyOf(groups) : Set.of();
    }

    /**
     * 返回指定组内所有节点及其角色。
     */
    public Map<LogisticsNode, NodeRole> getNodesInGroup(String groupId) {
        return groupMemberService.getNodesInGroup(groupId);
    }

}