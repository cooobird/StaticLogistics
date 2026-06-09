package com.coobird.staticlogistics.api;

import net.minecraft.server.level.ServerLevel;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 物流管理器的核心接口，负责节点的注册、注销、频道查询和链接同步
 */
public interface ILogisticsManager {

    void registerNode(String groupId, LogisticsNode node, NodeRole role);

    void unregisterNode(LogisticsNode node);

    List<LogisticsNode> getReceivers(String groupId);

    List<LogisticsNode> getSenders(String groupId);

    Set<String> getActiveGroups();

    String getGroupId(LogisticsNode node);

    Map<LogisticsNode, NodeRole> getNodesInGroup(String groupId);

    int[] getCursor(long nodeKey, LogisticsResource<?> type);

    void syncGroupLinks(ServerLevel level, String groupId, LogisticsNode triggerNode);
}