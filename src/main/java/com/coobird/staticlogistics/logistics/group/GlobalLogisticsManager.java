package com.coobird.staticlogistics.logistics.group;

import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.api.NodeRole;
import com.coobird.staticlogistics.api.event.LogisticsNodeEvent;
import com.coobird.staticlogistics.api.group.GroupKey;
import com.coobird.staticlogistics.api.group.GroupRef;
import com.coobird.staticlogistics.logistics.node.*;
import com.coobird.staticlogistics.network.s2c.S2CGroupDirectoryPayload;
import com.coobird.staticlogistics.transfer.LogisticsTicker;
import com.coobird.staticlogistics.transfer.TransferCursorService;
import com.coobird.staticlogistics.transfer.TransferLogManager;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.mesdag.portlib.network.PortPacketDistributor;

import java.util.*;
import java.util.function.IntUnaryOperator;
import java.util.stream.Collectors;

/// 全局物流管理器。
/// 管理所有节点注册/注销、组同步、传输游标等核心逻辑。
/// 每个 MinecraftServer 只有一个实例。
public class GlobalLogisticsManager {
    private static final int GROUP_SYNCS_PER_TICK = 16;
    private static final int GROUP_SYNC_WORK_UNITS_PER_SLICE = 512;
    private static final Map<MinecraftServer, GlobalLogisticsManager> INSTANCES = new HashMap<>();

    private final MinecraftServer server;
    private final NodeGroupService nodeGroupService;
    private final TransferCursorService cursorService;
    private final Set<GroupKey> pendingGroupSyncs = new LinkedHashSet<>();
    private final Map<GroupKey, String> groupDisplayNames = new HashMap<>();
    private final Map<GroupKey, GroupSyncProgress> groupSyncProgress = new HashMap<>();
    private final TopologyIndex topologyIndex = new TopologyIndex();

    private GlobalLogisticsManager(MinecraftServer server) {
        this.server = server;
        this.nodeGroupService = new NodeGroupService();
        this.cursorService = new TransferCursorService();
    }

    /// 获取/创建指定服务器的全局管理器单例
    public static GlobalLogisticsManager get(MinecraftServer server) {
        return INSTANCES.computeIfAbsent(server, GlobalLogisticsManager::new);
    }

    /// 服务器关闭时释放管理器实例
    public static void release(MinecraftServer server) {
        INSTANCES.remove(server);
    }

    public NodeGroupService getNodeGroupService() {
        return nodeGroupService;
    }

    public void registerNode(GroupRef group, LogisticsNode node, NodeRole role) {
        if (group == null || group.displayName().isEmpty()) return;
        groupDisplayNames.put(group.key(), group.displayName());
        nodeGroupService.register(group.key(), node, role);
    }

    public void updateGroupDisplayName(GroupKey groupKey, String displayName) {
        if (groupKey != null && displayName != null && !displayName.isEmpty()) {
            groupDisplayNames.put(groupKey, displayName);
        }
    }

    public void retireGroupIdentity(GroupKey groupKey) {
        if (groupKey != null && nodeGroupService.getNodesInGroup(groupKey).isEmpty()
            && PlayerGroupStore.get(server).findGroup(groupKey) == null) {
            groupDisplayNames.remove(groupKey);
        }
    }

    public void unregisterNode(LogisticsNode node) {
        nodeGroupService.unregister(node);
        removeOutgoingLinks(node);
        cursorService.removeCursor(node);
        TransferLogManager.forgetNode(server, node);
    }

    public void unregisterNode(GroupKey groupKey, LogisticsNode node) {
        nodeGroupService.unregister(groupKey, node);
        if (nodeGroupService.getNodesInGroup(groupKey).isEmpty()
            && PlayerGroupStore.get(server).findGroup(groupKey) == null) {
            groupDisplayNames.remove(groupKey);
        }
        if (nodeGroupService.getAllGroupKeys(node).isEmpty()) {
            cursorService.removeCursor(node);
            TransferLogManager.forgetNode(server, node);
        }
    }

    public List<LogisticsNode> getReceivers(GroupKey groupKey) {
        return nodeGroupService.getReceivers(groupKey);
    }

    public List<LogisticsNode> getSenders(GroupKey groupKey) {
        return nodeGroupService.getSenders(groupKey);
    }

    public Set<String> getActiveGroups() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(groupDisplayNames.values()));
    }

    public Set<GroupKey> getActiveGroupKeys() {
        return nodeGroupService.getAllGroupKeys();
    }

    public String getGroupDisplayName(GroupKey groupKey) {
        return groupDisplayNames.get(groupKey);
    }

    public Map<LogisticsNode, NodeRole> getNodesInGroup(GroupKey groupKey) {
        return nodeGroupService.getNodesInGroup(groupKey);
    }

    public int getAndUpdateCursor(LogisticsNode node, ResourceLocation cursorType, IntUnaryOperator update) {
        return cursorService.getAndUpdateCursor(node, cursorType, update);
    }

    public int getAndUpdateDistributionCursor(LogisticsNode node, ResourceLocation type, long priority, IntUnaryOperator update) {
        return cursorService.getAndUpdateDistributionCursor(node, type, priority, update);
    }

    public void syncGroupLinks(GroupKey groupKey) {
        markGroupDirty(groupKey);
    }

    public void syncOutgoingLinks(LogisticsNode source,
                                  Map<GroupKey, Set<LogisticsNode>> targetsByGroup) {
        topologyIndex.replaceOutgoing(source, targetsByGroup);
    }

    public void removeOutgoingLinks(LogisticsNode source) {
        topologyIndex.removeOutgoing(source);
    }

    /// 入站索引由面拓扑变更同步维护，查询成本只与目标及直接来源数量有关。
    public Set<LogisticsNode> getSourcesLinkedTo(LogisticsNode target) {
        return topologyIndex.sourcesLinkedTo(target);
    }

    public Map<LogisticsNode, Set<LogisticsNode>> getSourcesLinkedTo(Collection<LogisticsNode> targets) {
        return topologyIndex.sourcesLinkedTo(targets);
    }

    public Set<ConnectionKey> getConnections(GroupKey group) {
        return topologyIndex.connections(group);
    }

    public boolean hasRoute(GroupKey group, LogisticsNode source, LogisticsNode target) {
        return topologyIndex.hasRoute(group, source, target);
    }

    public Set<LogisticsNode> getLinkedNodes(GroupKey group, LogisticsNode node) {
        return topologyIndex.linkedNodes(group, node);
    }

    public void markGroupDirty(GroupKey groupKey) {
        if (groupKey == null) return;
        groupSyncProgress.remove(groupKey);
        pendingGroupSyncs.add(groupKey);
        LogisticsTicker.wakeupGroup(server, groupKey);
    }

    /// 每刻只处理有界数量，避免批量权限或目录变化占满服务器主线程。
    public void tick() {
        if (pendingGroupSyncs.isEmpty()) return;
        List<GroupKey> batch = pendingGroupSyncs.stream().limit(GROUP_SYNCS_PER_TICK).toList();
        pendingGroupSyncs.removeAll(batch);
        for (GroupKey groupKey : batch) {
            GroupSyncProgress progress = groupSyncProgress.computeIfAbsent(groupKey,
                key -> new GroupSyncProgress(List.copyOf(getNodesInGroup(key).keySet())));
            boolean complete = performSyncGroupLinks(groupKey, progress);
            /// 同步期间若再次被标脏，markGroupDirty 已替换进度并重新排队，旧切片不得覆盖新任务。
            if (groupSyncProgress.get(groupKey) != progress) continue;
            if (complete) {
                groupSyncProgress.remove(groupKey);
            } else {
                pendingGroupSyncs.add(groupKey);
            }
        }
    }

    /// 组内链接清理由可恢复游标分片执行，同时限制节点扫描和边检查，避免超大组独占主线程。
    private boolean performSyncGroupLinks(GroupKey groupKey, GroupSyncProgress progress) {
        if (progress.nodes.isEmpty()) return true;
        Map<ResourceKey<Level>, LinkManager> mgrCache = new HashMap<>();
        int remainingWork = GROUP_SYNC_WORK_UNITS_PER_SLICE;
        while (remainingWork > 0 && progress.nodeIndex < progress.nodes.size()) {
            LogisticsNode source = progress.nodes.get(progress.nodeIndex);
            ServerLevel sLevel = server.getLevel(source.gPos().dimension());
            if (sLevel == null) {
                progress.advanceNode();
                remainingWork--;
                continue;
            }
            LinkManager sMgr = mgrCache.computeIfAbsent(source.gPos().dimension(), k -> LinkManager.get(sLevel));
            EndpointConfig sCfg = sMgr.getEndpointConfig(EndpointAddress.of(source));
            if (sCfg == null) {
                progress.advanceNode();
                remainingWork--;
                continue;
            }
            if (progress.currentLinks == null) {
                progress.currentLinks = List.copyOf(sCfg.getLinkedNodes(groupKey));
                remainingWork--;
                if (remainingWork <= 0) break;
            }
            while (remainingWork > 0 && progress.linkIndex < progress.currentLinks.size()) {
                LogisticsNode linkedNode = progress.currentLinks.get(progress.linkIndex++);
                remainingWork--;
                ServerLevel linkedLevel = server.getLevel(linkedNode.gPos().dimension());
                EndpointConfig linkedCfg = linkedLevel == null ? null : LinkManager.get(linkedLevel).getEndpointConfig(EndpointAddress.of(linkedNode));
                if (linkedCfg == null || !linkedCfg.faceConfig.getGroupKeys().contains(groupKey)
                    || !progress.aliveNodes.contains(linkedNode)
                    || !linkedCfg.isLinkedTo(groupKey, source)) {
                    sMgr.removeLink(groupKey, source, linkedNode);
                }
            }
            if (progress.linkIndex >= progress.currentLinks.size()) progress.advanceNode();
        }
        return progress.nodeIndex >= progress.nodes.size();
    }

    private static final class GroupSyncProgress {
        private final List<LogisticsNode> nodes;
        private final Set<LogisticsNode> aliveNodes;
        private int nodeIndex;
        private List<LogisticsNode> currentLinks;
        private int linkIndex;

        private GroupSyncProgress(List<LogisticsNode> nodes) {
            this.nodes = nodes;
            this.aliveNodes = Set.copyOf(nodes);
        }

        private void advanceNode() {
            nodeIndex++;
            currentLinks = null;
            linkIndex = 0;
        }
    }

    /// 处理节点事件（添加/删除/修改），更新注册、标记脏数据
    public void handleNodeEvent(LogisticsNodeEvent event, ServerLevel level) {
        for (LogisticsNodeEvent.NodeEntry entry : event.getAffectedEntries()) {
            switch (event.getType()) {
                case ADDED -> {
                    GroupRef group = new GroupRef(entry.groupKey(), entry.displayName());
                    registerNode(group, entry.node(), entry.role());
                    markGroupDirty(entry.groupKey());
                }
                case REMOVED -> {
                    unregisterNode(entry.groupKey(), entry.node());
                    markGroupDirty(entry.groupKey());
                }
                case CHANGED -> {
                    EndpointConfig config = LinkManager.get(level).getEndpointConfig(EndpointAddress.of(entry.node()));
                    if (config == null) continue;
                    GroupRef group = config.faceConfig.getGroups().stream()
                        .filter(ref -> ref.key().equals(entry.groupKey())).findFirst()
                        .orElse(new GroupRef(entry.groupKey(), ""));
                    /// NONE 是暂停传输状态，不是拓扑删除事件。
                    registerNode(group, entry.node(), entry.role());
                    markGroupDirty(entry.groupKey());
                }
            }
        }
    }

    /// 通知节点被移除：注销前获取所有关联组，注销后标记所有组脏
    public void notifyNodeRemoved(ServerLevel level, LogisticsNode removedNode) {
        Set<GroupKey> groups = nodeGroupService.getAllGroupKeys(removedNode);
        unregisterNode(removedNode);
        for (GroupKey groupKey : groups) {
            markGroupDirty(groupKey);
        }
    }

    @Nullable
    public ServerLevel getLevel(ResourceKey<Level> dimension) {
        return server.getLevel(dimension);
    }

    public MinecraftServer getServer() {
        return server;
    }

    /// 根源清理：遍历所有维度的玩家面配置，移除已无活跃节点的组ID（空组清零）。
    /// 调用时机：删除链路/组操作后。
    /// 注意：保留玩家手动创建的空分组（playerGroups 中的分组）。
    public void cleanupOrphanedGroupIds(@Nullable UUID playerId) {
        if (playerId == null) return;
        Set<GroupKey> playerAllGroups = PlayerGroupStore.get(server).getGroupRefs(playerId).stream()
            .map(GroupRef::key).collect(Collectors.toSet());
        for (ServerLevel level : server.getAllLevels()) {
            LinkManager mgr = LinkManager.get(level);
            List<EndpointAddress> toRemove = new ArrayList<>();
            for (EndpointAddress key : mgr.getAllConfigKeys()) {
                EndpointConfig cfg = mgr.getEndpointConfig(key);
                if (cfg == null) continue;
                UUID owner = cfg.faceConfig.getOwner();
                if (!playerId.equals(owner)) continue;
                /// 移除无节点的组 ID（但保留玩家手动创建的空分组）
                for (GroupKey groupKey : new ArrayList<>(cfg.faceConfig.getGroupKeys())) {
                    if (nodeGroupService.getNodesInGroup(groupKey).isEmpty() && !playerAllGroups.contains(groupKey)) {
                        mgr.removeNodeFromGroup(groupKey, mgr.createNodeFromKey(key));
                    }
                }
                EndpointConfig current = mgr.getEndpointConfig(key);
                if (current != null && !current.faceConfig.hasGroup()) {
                    toRemove.add(key);
                }
            }
            for (EndpointAddress key : toRemove) {
                mgr.removeFaceConfig(key);
            }
        }
    }

    /// 解析边界显示名后，所有删除都以稳定分组键执行。
    public boolean removeGroup(ServerPlayer actor, GroupKey groupKey) {
        if (actor == null || groupKey == null
            || !PermissionService.getInstance().canModify(groupKey.ownerId(), actor)) return false;
        PlayerGroupStore store = PlayerGroupStore.get(server);
        if (store.findGroup(groupKey) == null) return false;
        LogisticsOperationStore operations = LogisticsOperationStore.get(server);
        if (!operations.beginGroupDeletion(groupKey)) return false;
        if (new ConnectionCommandService(server).resumePendingGroupDeletion(groupKey)) return true;
        operations.completeGroupDeletion(groupKey);
        return false;
    }

    /// 删除已经失去全部连接的分组。
    /// 该方法只供链接图在事务提交后调用。判断直接扫描权威面配置，不依赖可能仍在
    /// 刷新的成员索引；玩家主动创建、但从未建立过连接的空分组不会经过此入口。
    public boolean removeGroupIfEmpty(GroupKey groupKey) {
        if (groupKey == null || LogisticsOperationStore.get(server).isGroupDeletionPending(groupKey)
            || hasGroupLinks(groupKey)) return false;

        PlayerGroupStore store = PlayerGroupStore.get(server);
        GroupRef group = store.findGroup(groupKey);
        if (group == null) return false;
        List<LogisticsNode> nodes = collectGroupNodes(groupKey);
        Set<ConnectionKey> connections = collectGroupConnections(groupKey, nodes);

        try (NodeMutationTransaction transaction = NodeMutationTransaction.begin(server)) {
            PlayerGroupStore.GroupDirectorySnapshot groupSnapshot = store.captureGroups(Set.of(groupKey));
            Map<ConnectionKey, ConnectionKey> connectionScope = connections.stream().collect(Collectors.toMap(key -> key, key -> key));
            PlayerGroupStore.ConnectionMetadataSnapshot metadataSnapshot = store.captureConnectionMetadata(connectionScope, Map.of(groupKey, groupKey));
            transaction.captureAll(nodes);
            transaction.onRollback(() -> {
                store.restoreGroups(groupSnapshot);
                store.restoreConnectionMetadata(metadataSnapshot);
                updateGroupDisplayName(groupKey, group.displayName());
            });
            for (LogisticsNode node : nodes) {
                ServerLevel nodeLevel = server.getLevel(node.gPos().dimension());
                if (nodeLevel == null) {
                    throw new IllegalStateException(
                        "Empty group dimension is unavailable: "
                            + node.gPos().dimension().location());
                }
                LinkManager.get(nodeLevel).removeNodeFromGroup(groupKey, node);
            }
            if (!store.removeGroup(groupKey, connections)) {
                throw new IllegalStateException(
                    "Empty group directory changed during deletion");
            }
            groupDisplayNames.remove(groupKey);
            transaction.commit();
        }
        GroupSelectionInvalidator.clearOnlineSelections(server, groupKey);
        syncGroupDirectory(groupKey.ownerId());
        return true;
    }

    boolean completePendingGroupDeletion(GroupKey groupKey) {
        if (!LogisticsOperationStore.get(server).isGroupDeletionPending(groupKey)) return false;
        PlayerGroupStore store = PlayerGroupStore.get(server);
        if (store.findGroup(groupKey) != null && !store.removeGroup(groupKey, Set.of())) return false;
        groupDisplayNames.remove(groupKey);
        LogisticsOperationStore.get(server).completeGroupDeletion(groupKey);
        GroupSelectionInvalidator.clearOnlineSelections(server, groupKey);
        syncGroupDirectory(groupKey.ownerId());
        return true;
    }

    private Set<ConnectionKey> collectGroupConnections(GroupKey groupKey,
                                                       Collection<LogisticsNode> nodes) {
        Set<ConnectionKey> connections = new LinkedHashSet<>();
        for (LogisticsNode node : nodes) {
            ServerLevel nodeLevel = server.getLevel(node.gPos().dimension());
            if (nodeLevel == null) continue;
            EndpointConfig config = LinkManager.get(nodeLevel).getEndpointConfig(EndpointAddress.of(node));
            if (config == null) continue;
            for (LogisticsNode linked : config.getLinkedNodes(groupKey)) {
                if (!node.gPos().equals(linked.gPos())) {
                    connections.add(new ConnectionKey(groupKey, node, linked));
                }
            }
        }
        return connections;
    }

    /// 查询分组是否仍有至少一条边；任意一端保留引用都视为非空，避免损坏数据被误删。
    private boolean hasGroupLinks(GroupKey groupKey) {
        for (ServerLevel level : server.getAllLevels()) {
            LinkManager manager = LinkManager.get(level);
            for (EndpointAddress address : manager.getAllConfigKeys()) {
                EndpointConfig config = manager.getEndpointConfig(address);
                if (config != null && !config.getLinkedNodes(groupKey).isEmpty()) {
                    return true;
                }
            }
        }
        return false;
    }

    /// 只同步发生变化的所有者目录，避免底层生命周期变更依赖某个具体 GUI 命令入口。
    private void syncGroupDirectory(UUID ownerId) {
        Set<GroupRef> groups = PlayerGroupStore.get(server).getGroupRefs(ownerId);
        S2CGroupDirectoryPayload payload = new S2CGroupDirectoryPayload(ownerId, groups);
        server.getPlayerList().getPlayers().stream()
            .filter(player -> PermissionService.getInstance().canAccess(ownerId, player))
            .forEach(player -> PortPacketDistributor.sendToPlayer(player, payload));
    }

    /// 从维度级权威配置收集分组节点，避免运行时索引漂移导致删除不完整。
    private List<LogisticsNode> collectGroupNodes(GroupKey groupKey) {
        List<LogisticsNode> nodes = new ArrayList<>();
        for (ServerLevel level : server.getAllLevels()) {
            LinkManager manager = LinkManager.get(level);
            for (EndpointAddress key : manager.getAllConfigKeys()) {
                EndpointConfig config = manager.getEndpointConfig(key);
                if (config != null && config.faceConfig.getGroupKeys().contains(groupKey)) {
                    nodes.add(manager.createNodeFromKey(key));
                }
            }
        }
        return List.copyOf(nodes);
    }

}
