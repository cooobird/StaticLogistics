package com.coobird.staticlogistics.logistics.group;

import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.api.NodeRole;
import com.coobird.staticlogistics.api.event.LogisticsNodeEvent;
import com.coobird.staticlogistics.api.group.GroupKey;
import com.coobird.staticlogistics.api.group.GroupRef;
import com.coobird.staticlogistics.logistics.node.FaceAddress;
import com.coobird.staticlogistics.logistics.node.FaceConfigComposite;
import com.coobird.staticlogistics.logistics.node.LinkManager;
import com.coobird.staticlogistics.logistics.node.NodeMutationTransaction;
import com.coobird.staticlogistics.network.SLNetwork;
import com.coobird.staticlogistics.network.s2c.S2CGroupDirectoryPayload;
import com.coobird.staticlogistics.transfer.LogisticsTicker;
import com.coobird.staticlogistics.transfer.TransferCursorService;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 全局物流管理器。
 * 管理所有节点注册/注销、组同步、传输游标等核心逻辑。
 * 每个 MinecraftServer 只有一个实例。
 */
public class GlobalLogisticsManager {
    private static final int GROUP_SYNCS_PER_TICK = 16;
    private static final int GROUP_SYNC_WORK_UNITS_PER_SLICE = 512;
    private static final Map<MinecraftServer, GlobalLogisticsManager> INSTANCES = new ConcurrentHashMap<>();

    private final MinecraftServer server;
    private final NodeGroupService nodeGroupService;
    private final GroupMemberService groupMemberService;
    private final TransferCursorService cursorService;
    private final GroupSyncScheduler syncScheduler;
    private final Map<GroupKey, String> groupDisplayNames = new HashMap<>();
    private final Map<GroupKey, GroupSyncProgress> groupSyncProgress = new HashMap<>();

    private GlobalLogisticsManager(MinecraftServer server) {
        this.server = server;
        this.groupMemberService = new GroupMemberService();
        this.nodeGroupService = new NodeGroupService(groupMemberService);
        this.cursorService = new TransferCursorService();
        this.syncScheduler = new GroupSyncScheduler();
    }

    // 获取/创建指定服务器的全局管理器单例
    public static GlobalLogisticsManager get(MinecraftServer server) {
        return INSTANCES.computeIfAbsent(server, GlobalLogisticsManager::new);
    }

    // 服务器关闭时释放管理器实例
    public static void release(MinecraftServer server) {
        INSTANCES.remove(server);
    }

    public NodeGroupService getNodeGroupService() {
        return nodeGroupService;
    }

    public void registerNode(String groupId, LogisticsNode node, NodeRole role) {
        registerNode(GroupRef.migrated(null, groupId), node, role);
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
        cursorService.removeCursor(node);
    }

    public void unregisterNode(GroupKey groupKey, LogisticsNode node) {
        nodeGroupService.unregister(groupKey, node);
        if (nodeGroupService.getNodesInGroup(groupKey).isEmpty()
            && PlayerGroupStore.get(server).findGroup(groupKey) == null) {
            groupDisplayNames.remove(groupKey);
        }
        if (nodeGroupService.getAllGroupKeys(node).isEmpty()) cursorService.removeCursor(node);
    }

    public List<LogisticsNode> getReceivers(String groupId) {
        return matchingGroupKeys(groupId).stream()
            .flatMap(key -> groupMemberService.getReceivers(key).stream()).distinct().toList();
    }

    public List<LogisticsNode> getReceivers(GroupKey groupKey) {
        return groupMemberService.getReceivers(groupKey);
    }

    public List<LogisticsNode> getSenders(String groupId) {
        return matchingGroupKeys(groupId).stream()
            .flatMap(key -> groupMemberService.getSenders(key).stream()).distinct().toList();
    }

    public List<LogisticsNode> getSenders(GroupKey groupKey) {
        return groupMemberService.getSenders(groupKey);
    }

    public Set<String> getActiveGroups() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(groupDisplayNames.values()));
    }

    public Set<GroupKey> getActiveGroupKeys() {
        return groupMemberService.getAllGroupKeys();
    }

    public String getGroupId(LogisticsNode node) {
        GroupKey key = nodeGroupService.getGroupKey(node);
        return key == null ? null : groupDisplayNames.get(key);
    }

    public Map<LogisticsNode, NodeRole> getNodesInGroup(String groupId) {
        Map<LogisticsNode, NodeRole> result = new LinkedHashMap<>();
        for (GroupKey key : matchingGroupKeys(groupId)) {
            nodeGroupService.getNodesInGroup(key).forEach((node, role) ->
                result.merge(node, role, GlobalLogisticsManager::mergeRoles));
        }
        return result;
    }

    public Map<LogisticsNode, NodeRole> getNodesInGroup(GroupKey groupKey) {
        return nodeGroupService.getNodesInGroup(groupKey);
    }

    private Set<GroupKey> matchingGroupKeys(String displayName) {
        Set<GroupKey> keys = new LinkedHashSet<>();
        groupDisplayNames.forEach((key, name) -> {
            if (Objects.equals(name, displayName)) keys.add(key);
        });
        // 旧字符串 API 无法表达所有者；命中多个稳定身份时必须拒绝歧义。
        return keys.size() == 1 ? keys : Set.of();
    }

    private static NodeRole mergeRoles(NodeRole first, NodeRole second) {
        if (first == second) return first;
        if (first == NodeRole.NONE) return second;
        if (second == NodeRole.NONE) return first;
        return NodeRole.BOTH;
    }

    public int[] getCursor(LogisticsNode node, ResourceLocation cursorType) {
        return cursorService.getOrCreateCursor(node, cursorType);
    }

    public void syncGroupLinks(ServerLevel level, String groupId, @Nullable LogisticsNode triggerNode) {
        if (groupId != null) {
            for (GroupKey key : matchingGroupKeys(groupId)) markGroupDirty(key);
        }
    }

    public void syncGroupLinks(GroupKey groupKey) {
        markGroupDirty(groupKey);
    }

    /**
     * 查找指向 target 的所有源节点 —— 全量扫描所有维度的 FaceConfigComposite.linkedNodes。
     * 无增量索引，按需查询。适用于低频 API 调用。
     */
    public Set<LogisticsNode> getSourcesLinkedTo(LogisticsNode target) {
        Set<LogisticsNode> sources = new LinkedHashSet<>();
        for (ServerLevel level : server.getAllLevels()) {
            LinkManager mgr = LinkManager.get(level);
            for (FaceAddress faceKey : mgr.getAllConfigKeys()) {
                FaceConfigComposite cfg = mgr.getFaceConfig(faceKey);
                if (cfg == null) continue;
                if (cfg.getLinkedNodes().contains(target)) {
                    sources.add(mgr.createNodeFromKey(faceKey));
                }
            }
        }
        return sources;
    }

    public void markGroupDirty(String groupId) {
        for (GroupKey key : matchingGroupKeys(groupId)) markGroupDirty(key);
    }

    public void markGroupDirty(GroupKey groupKey) {
        if (groupKey == null) return;
        groupSyncProgress.remove(groupKey);
        syncScheduler.markDirty(groupKey);
        LogisticsTicker.wakeupGroup(server, groupKey);
    }

    // 每刻只处理有界数量，避免批量权限或目录变化占满服务器主线程。
    public void tick() {
        if (!syncScheduler.hasPending()) return;
        for (GroupKey groupKey : syncScheduler.take(GROUP_SYNCS_PER_TICK)) {
            GroupSyncProgress progress = groupSyncProgress.computeIfAbsent(groupKey,
                key -> new GroupSyncProgress(List.copyOf(getNodesInGroup(key).keySet())));
            boolean complete = performSyncGroupLinks(groupKey, progress);
            // 同步期间若再次被标脏，markGroupDirty 已替换进度并重新排队，旧切片不得覆盖新任务。
            if (groupSyncProgress.get(groupKey) != progress) continue;
            if (complete) {
                groupSyncProgress.remove(groupKey);
            } else {
                syncScheduler.markDirty(groupKey);
            }
        }
    }

    // 组内链接清理由可恢复游标分片执行，同时限制节点扫描和边检查，避免超大组独占主线程。
    private boolean performSyncGroupLinks(GroupKey groupKey, GroupSyncProgress progress) {
        if (progress.nodes.isEmpty()) return true;
        Set<LogisticsNode> aliveNodes = Set.copyOf(progress.nodes);
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
            FaceConfigComposite sCfg = sMgr.getFaceConfig(FaceAddress.of(source));
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
                FaceConfigComposite linkedCfg = linkedLevel == null ? null
                    : LinkManager.get(linkedLevel).getFaceConfig(FaceAddress.of(linkedNode));
                if (linkedCfg == null || !linkedCfg.faceConfig.getGroupKeys().contains(groupKey)
                    || !aliveNodes.contains(linkedNode)
                    || !linkedCfg.getLinkedNodes(groupKey).contains(source)) {
                    sMgr.removeLink(groupKey, source, linkedNode);
                }
            }
            if (progress.linkIndex >= progress.currentLinks.size()) progress.advanceNode();
        }
        return progress.nodeIndex >= progress.nodes.size();
    }

    private static final class GroupSyncProgress {
        private final List<LogisticsNode> nodes;
        private int nodeIndex;
        private List<LogisticsNode> currentLinks;
        private int linkIndex;

        private GroupSyncProgress(List<LogisticsNode> nodes) {
            this.nodes = nodes;
        }

        private void advanceNode() {
            nodeIndex++;
            currentLinks = null;
            linkIndex = 0;
        }
    }

    // 处理节点事件（添加/删除/修改），更新注册、标记脏数据
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
                    FaceConfigComposite config = LinkManager.get(level)
                        .getFaceConfig(FaceAddress.of(entry.node()));
                    if (config == null) continue;
                    GroupRef group = config.faceConfig.getGroups().stream()
                        .filter(ref -> ref.key().equals(entry.groupKey())).findFirst()
                        .orElse(new GroupRef(entry.groupKey(), ""));
                    // NONE 是暂停传输状态，不是拓扑删除事件。
                    registerNode(group, entry.node(), entry.role());
                    markGroupDirty(entry.groupKey());
                }
            }
        }
    }

    // 通知节点被移除：注销前获取所有关联组，注销后标记所有组脏
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

    // 为玩家自动分配下一个未使用的数字组 ID（单调递增，不复用）
    public synchronized String getNextGroupIdForPlayer(UUID playerId) {
        return PlayerGroupStore.get(server).getNextGroupIdForPlayer(playerId);
    }

    /**
     * 根源清理：遍历所有维度的玩家面配置，移除已无活跃节点的组ID（空组清零）。
     * 调用时机：删除链路/组操作后。
     * 注意：保留玩家手动创建的空分组（playerGroups 中的分组）。
     */
    public void cleanupOrphanedGroupIds(@Nullable UUID playerId) {
        if (playerId == null) return;
        Set<GroupKey> playerAllGroups = PlayerGroupStore.get(server).getGroupRefs(playerId).stream()
            .map(GroupRef::key).collect(Collectors.toSet());
        for (ServerLevel level : server.getAllLevels()) {
            LinkManager mgr = LinkManager.get(level);
            List<FaceAddress> toRemove = new ArrayList<>();
            for (FaceAddress key : mgr.getAllConfigKeys()) {
                FaceConfigComposite cfg = mgr.getFaceConfig(key);
                if (cfg == null) continue;
                UUID owner = cfg.faceConfig.getOwner();
                if (!playerId.equals(owner)) continue;
                // 移除无节点的组 ID（但保留玩家手动创建的空分组）
                for (GroupKey groupKey : new ArrayList<>(cfg.faceConfig.getGroupKeys())) {
                    if (nodeGroupService.getNodesInGroup(groupKey).isEmpty() && !playerAllGroups.contains(groupKey)) {
                        mgr.removeNodeFromGroup(groupKey, mgr.createNodeFromKey(key));
                    }
                }
                FaceConfigComposite current = mgr.getFaceConfig(key);
                if (current != null && !current.faceConfig.hasGroup()) {
                    toRemove.add(key);
                }
            }
            for (FaceAddress key : toRemove) {
                mgr.removeFaceConfig(key);
            }
        }
    }

    /**
     * 删除指定分组及其所有关联节点。
     * 统一处理空分组和有内容的分组。
     */
    public void removeGroup(Player actor, UUID ownerId, String groupId) {
        if (actor == null || ownerId == null || groupId == null || groupId.isEmpty()) return;
        PlayerGroupStore store = PlayerGroupStore.get(server);
        GroupRef group = store.findGroup(ownerId, groupId);
        if (group != null) removeGroup(actor, group.key());
    }

    /**
     * 解析边界显示名后，所有删除都以稳定分组键执行。
     */
    public boolean removeGroup(Player actor, GroupKey groupKey) {
        if (actor == null || groupKey == null
            || !GroupService.canModify(groupKey.ownerId(), actor)) return false;

        PlayerGroupStore store = PlayerGroupStore.get(server);
        GroupRef group = store.findGroup(groupKey);
        if (group == null) return false;

        List<LogisticsNode> nodes = collectGroupNodes(groupKey);

        try (NodeMutationTransaction transaction = NodeMutationTransaction.begin(server)) {
            transaction.captureAll(nodes);
            transaction.onRollback(() -> {
                store.registerClaimedGroups(groupKey.ownerId(), List.of(group));
                updateGroupDisplayName(groupKey, group.displayName());
            });

            for (LogisticsNode node : nodes) {
                ServerLevel nodeLevel = server.getLevel(node.gPos().dimension());
                if (nodeLevel == null) {
                    throw new IllegalStateException(
                        "Group deletion dimension is unavailable: " + node.gPos().dimension().location());
                }
                LinkManager.get(nodeLevel).removeNodeFromGroup(groupKey, node);
            }
            if (!store.removeGroup(groupKey)) {
                throw new IllegalStateException("Group directory changed during deletion");
            }
            groupDisplayNames.remove(groupKey);
            transaction.commit();
        }
        GroupSelectionInvalidator.clearOnlineSelections(server, groupKey);
        return true;
    }

    /**
     * 删除已经失去全部连接的分组。
     *
     * <p>该方法只供链接图在事务提交后调用。判断直接扫描权威面配置，不依赖可能仍在
     * 刷新的成员索引；玩家主动创建、但从未建立过连接的空分组不会经过此入口。
     */
    public boolean removeGroupIfEmpty(GroupKey groupKey) {
        if (groupKey == null || hasGroupLinks(groupKey)) return false;

        PlayerGroupStore store = PlayerGroupStore.get(server);
        GroupRef group = store.findGroup(groupKey);
        if (group == null) return false;
        List<LogisticsNode> nodes = collectGroupNodes(groupKey);

        try (NodeMutationTransaction transaction = NodeMutationTransaction.begin(server)) {
            transaction.captureAll(nodes);
            transaction.onRollback(() -> {
                store.registerClaimedGroups(groupKey.ownerId(), List.of(group));
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
            if (!store.removeGroup(groupKey)) {
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

    /**
     * 查询分组是否仍有至少一条边；任意一端保留引用都视为非空，避免损坏数据被误删。
     */
    private boolean hasGroupLinks(GroupKey groupKey) {
        for (ServerLevel level : server.getAllLevels()) {
            LinkManager manager = LinkManager.get(level);
            for (FaceAddress address : manager.getAllConfigKeys()) {
                FaceConfigComposite config = manager.getFaceConfig(address);
                if (config != null && !config.getLinkedNodes(groupKey).isEmpty()) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 只同步发生变化的所有者目录，避免底层生命周期变更依赖某个具体 GUI 命令入口。
     */
    private void syncGroupDirectory(UUID ownerId) {
        Set<GroupRef> groups = PlayerGroupStore.get(server).getGroupRefs(ownerId);
        S2CGroupDirectoryPayload payload = new S2CGroupDirectoryPayload(ownerId, groups);
        server.getPlayerList().getPlayers().stream()
            .filter(player -> GroupService.canAccess(ownerId, player))
            .forEach(player -> SLNetwork.HANDLER.sendToPlayer(player, payload));
    }

    /**
     * 从维度级权威配置收集分组节点，避免运行时索引漂移导致删除不完整。
     */
    private List<LogisticsNode> collectGroupNodes(GroupKey groupKey) {
        List<LogisticsNode> nodes = new ArrayList<>();
        for (ServerLevel level : server.getAllLevels()) {
            LinkManager manager = LinkManager.get(level);
            for (FaceAddress key : manager.getAllConfigKeys()) {
                FaceConfigComposite config = manager.getFaceConfig(key);
                if (config != null && config.faceConfig.getGroupKeys().contains(groupKey)) {
                    nodes.add(manager.createNodeFromKey(key));
                }
            }
        }
        return List.copyOf(nodes);
    }

    /**
     * 收集指定分组的所有面配置条目（用于客户端同步清理）。
     */
    public List<GlobalLogisticsManager.FaceEntry> collectGroupFaceConfigs(String groupId) {
        List<FaceEntry> result = new ArrayList<>();
        if (groupId == null || groupId.isEmpty()) return result;
        for (ServerLevel level : server.getAllLevels()) {
            LinkManager mgr = LinkManager.get(level);
            for (FaceAddress key : mgr.getAllConfigKeys()) {
                FaceConfigComposite cfg = mgr.getFaceConfig(key);
                if (cfg != null && cfg.faceConfig.getGroupIds().contains(groupId)) {
                    result.add(new FaceEntry(GlobalPos.of(level.dimension(), key.pos()), key.face()));
                }
            }
        }
        return result;
    }

    public List<GlobalLogisticsManager.FaceEntry> collectGroupFaceConfigs(UUID ownerId, String displayName) {
        GroupRef group = PlayerGroupStore.get(server).findGroup(ownerId, displayName);
        if (group == null) group = GroupRef.migrated(ownerId, displayName);
        List<FaceEntry> result = new ArrayList<>();
        for (ServerLevel level : server.getAllLevels()) {
            LinkManager mgr = LinkManager.get(level);
            for (FaceAddress key : mgr.getAllConfigKeys()) {
                FaceConfigComposite cfg = mgr.getFaceConfig(key);
                if (cfg != null && cfg.faceConfig.getGroupKeys().contains(group.key())) {
                    result.add(new FaceEntry(GlobalPos.of(level.dimension(), key.pos()), key.face()));
                }
            }
        }
        return result;
    }

    public record FaceEntry(GlobalPos pos, Direction face) {
    }

    /**
     * 添加分组（玩家创建的分组，不管有没有链接）
     */
    public void addGroup(UUID playerId, String groupId) {
        PlayerGroupStore.get(server).addGroup(playerId, groupId);
    }

    public GroupRef resolveOrCreateGroup(UUID playerId, String displayName) {
        return PlayerGroupStore.get(server).resolveOrCreateGroup(playerId, displayName);
    }

    @Nullable
    public GroupRef findGroup(UUID playerId, String displayName) {
        return PlayerGroupStore.get(server).findGroup(playerId, displayName);
    }

    /**
     * 获取玩家的所有分组
     */
    public Set<String> getGroups(UUID playerId) {
        return PlayerGroupStore.get(server).getGroups(playerId);
    }
}
