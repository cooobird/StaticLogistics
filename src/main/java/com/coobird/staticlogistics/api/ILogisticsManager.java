package com.coobird.staticlogistics.api;

import com.coobird.staticlogistics.api.type.TransferType;
import net.minecraft.server.level.ServerLevel;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 物流管理器的核心接口，负责节点的注册、注销、频道查询和链接同步
 */
public interface ILogisticsManager {

    /**
     * 把一个节点注册到指定频道里，同时指定它的角色（发/收/双向）
     */
    void registerNode(String groupId, LogisticsNode node, NodeRole role);

    /**
     * 注销一个节点
     */
    void unregisterNode(LogisticsNode node);

    /**
     * 获取指定频道的所有发送节点
     */
    List<LogisticsNode> getReceivers(String groupId);

    /**
     * 获取指定频道的所有接收节点
     */
    List<LogisticsNode> getSenders(String groupId);

    /**
     * 获取当前存在的所有活跃频道 ID
     */
    Set<String> getActiveGroups();

    /**
     * 根据位置信息查找节点所属的频道 ID
     *
     * @return 频道 ID，若不存在则返回 null
     */
    String getGroupId(LogisticsNode node);

    /**
     * 获取指定组中的所有节点及其角色
     */
    Map<LogisticsNode, NodeRole> getNodesInGroup(String groupId);

    /**
     * 获取指定节点针对特定传输类型的轮询游标
     *
     * @param nodeKey 节点的唯一 Key (LogisticsNode.toKey())
     * @param type    传输类型
     * @return 长度为 1 的 int 数组，用于在传输过程中持久化索引
     */
    int[] getCursor(long nodeKey, TransferType type);

    /**
     * 同步指定组中的所有节点之间的链接
     */
    void syncGroupLinks(ServerLevel level, String groupId, LogisticsNode triggerNode);
}