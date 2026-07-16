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
import com.coobird.staticlogistics.transfer.LogisticsTicker;
import com.coobird.staticlogistics.transfer.TransferCursorService;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 全局物流管理器——整个模组的大管家。
 * 管理所有节点注册/注销、组同步、反向链接索引、传输游标等核心逻辑。
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

    public GroupMemberService getGroupMemberService() {
        return groupMemberService;
    }

    public TransferCursorService getCursorService() {
        return cursorService;
    }

    public GroupSyncScheduler getSyncScheduler() {
        return syncScheduler;
    }

    public void registerNode(String groupId, LogisticsNode node, NodeRole role) {
        registerNode(GroupRef.migrated(null, groupId), node, role);
    }

    public void registerNode(GroupRef group, LogisticsNode node, NodeRole role) {
        if (group == null || group.displayName().isEmpty()) return;
        groupDisplayNames.put(group.key(), group.displayName());
        nodeGroupService.register(group.key(), node, role);
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
        GroupKey groupKey = nodeGroupService.getGroupKey(node);
        return groupKey == null ? null : groupDisplayNames.get(groupKey);
    }

    public Map<LogisticsNode, NodeRole> getNodesInGroup(String groupId) {
        Map<LogisticsNode, NodeRole> result = new LinkedHashMap<>();
        for (GroupKey groupKey : matchingGroupKeys(groupId)) {
            nodeGroupService.getNodesInGroup(groupKey).forEach((node, role) ->
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
        // 旧字符串接口无法表达所有者；存在歧义时必须拒绝跨所有者合并。
        return keys.size() == 1 ? keys : Set.of();
    }

    private static NodeRole mergeRoles(NodeRole first, NodeRole second) {
        if (first == second) return first;
        if (first == NodeRole.NONE) return second;
        if (second == NodeRole.NONE) return first;
        return NodeRole.BOTH;
    }

    public int[] getCursor(LogisticsNode node, net.minecraft.resources.ResourceLocation cursorType) {
        return cursorService.getOrCreateCursor(node, cursorType);
    }

    public void syncGroupLinks(ServerLevel level, String groupId, @Nullable LogisticsNode triggerNode) {
        if (groupId != null) markGroupDirty(groupId);
    }

    public void syncGroupLinks(GroupKey groupKey) {
        markGroupDirty(groupKey);
    }

    /**
     * 低频查询：扫描各维度的权威面配置，避免维护易失效的反向派生索引。
     */
    public Set<LogisticsNode> getSourcesLinkedTo(LogisticsNode target) {
        Set<LogisticsNode> sources = new LinkedHashSet<>();
        for (ServerLevel level : server.getAllLevels()) {
            LinkManager manager = LinkManager.get(level);
            for (com.coobird.staticlogistics.logistics.node.FaceAddress address
                : manager.getAllConfigKeys()) {
                FaceConfigComposite config = manager.getFaceConfig(address);
                if (config != null && config.getLinkedNodes().contains(target)) {
                    sources.add(manager.createNodeFromKey(address));
                }
            }
        }
        return Set.copyOf(sources);
    }

    public void markGroupDirty(String groupId) {
        for (GroupKey groupKey : matchingGroupKeys(groupId)) markGroupDirty(groupKey);
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
            if (groupSyncProgress.get(groupKey) != progress) continue;
            if (complete) groupSyncProgress.remove(groupKey);
            else syncScheduler.markDirty(groupKey);
        }
    }

    // 以可恢复游标分片检查组内链接，避免超大分组独占服务器线程。
    private boolean performSyncGroupLinks(GroupKey groupKey, GroupSyncProgress progress) {
        if (progress.nodes.isEmpty()) return true;
        Set<LogisticsNode> aliveNodes = Set.copyOf(progress.nodes);
        Map<ResourceKey<Level>, LinkManager> managerCache = new HashMap<>();
        int remainingWork = GROUP_SYNC_WORK_UNITS_PER_SLICE;
        while (remainingWork > 0 && progress.nodeIndex < progress.nodes.size()) {
            LogisticsNode source = progress.nodes.get(progress.nodeIndex);
            ServerLevel sourceLevel = server.getLevel(source.gPos().dimension());
            if (sourceLevel == null) {
                progress.advanceNode();
                remainingWork--;
                continue;
            }
            LinkManager sourceManager = managerCache.computeIfAbsent(
                source.gPos().dimension(), ignored -> LinkManager.get(sourceLevel));
            FaceConfigComposite sourceConfig = sourceManager.getFaceConfig(FaceAddress.of(source));
            if (sourceConfig == null) {
                progress.advanceNode();
                remainingWork--;
                continue;
            }
            if (progress.currentLinks == null) {
                progress.currentLinks = List.copyOf(sourceConfig.getLinkedNodes(groupKey));
                remainingWork--;
                if (remainingWork <= 0) break;
            }
            while (remainingWork > 0 && progress.linkIndex < progress.currentLinks.size()) {
                LogisticsNode linkedNode = progress.currentLinks.get(progress.linkIndex++);
                remainingWork--;
                ServerLevel linkedLevel = server.getLevel(linkedNode.gPos().dimension());
                FaceConfigComposite linkedConfig = linkedLevel == null ? null
                    : LinkManager.get(linkedLevel).getFaceConfig(FaceAddress.of(linkedNode));
                if (linkedConfig == null
                    || !linkedConfig.faceConfig.getGroupKeys().contains(groupKey)
                    || !aliveNodes.contains(linkedNode)
                    || !linkedConfig.getLinkedNodes(groupKey).contains(source)) {
                    sourceManager.removeLink(groupKey, source, linkedNode);
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
                    if (entry.role() == NodeRole.NONE) {
                        unregisterNode(entry.groupKey(), entry.node());
                        markGroupDirty(entry.groupKey());
                        continue;
                    }
                    FaceConfigComposite config = LinkManager.get(level)
                        .getFaceConfig(FaceAddress.of(entry.node()));
                    if (config == null) continue;
                    GroupRef group = config.faceConfig.getGroups().stream()
                        .filter(ref -> ref.key().equals(entry.groupKey())).findFirst()
                        .orElse(new GroupRef(entry.groupKey(), entry.displayName()));
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
        for (GroupKey groupKey : groups) markGroupDirty(groupKey);
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
            .map(GroupRef::key).collect(java.util.stream.Collectors.toSet());
        for (ServerLevel level : server.getAllLevels()) {
            LinkManager mgr = LinkManager.get(level);
            for (var address : mgr.getAllConfigKeys()) {
                FaceConfigComposite cfg = mgr.getFaceConfig(address);
                if (cfg == null) continue;
                if (!playerId.equals(cfg.faceConfig.getOwner())) continue;
                LogisticsNode node = address.toNode(level.dimension());
                for (GroupKey groupKey : new ArrayList<>(cfg.faceConfig.getGroupKeys())) {
                    if (!playerAllGroups.contains(groupKey)
                        && cfg.getLinkedNodes(groupKey).isEmpty()) {
                        LinkManager.get(level).removeNodeFromGroup(groupKey, node);
                    }
                }
            }
        }
    }

    /**
     * 解析边界显示名后，所有玩家删除都以稳定分组键执行。
     */
    public boolean removeGroup(Player actor, GroupKey groupKey) {
        if (actor == null || groupKey == null
            || !GroupService.canModify(groupKey.ownerId(), actor)) return false;

        PlayerGroupStore store = PlayerGroupStore.get(server);
        GroupRef group = store.findGroup(groupKey);
        if (group == null) return false;

        List<LogisticsNode> nodes = new ArrayList<>();
        for (ServerLevel level : server.getAllLevels()) {
            LinkManager manager = LinkManager.get(level);
            for (FaceAddress address : manager.getAllConfigKeys()) {
                FaceConfigComposite config = manager.getFaceConfig(address);
                if (config != null && config.faceConfig.getGroupKeys().contains(groupKey)) {
                    nodes.add(manager.createNodeFromKey(address));
                }
            }
        }

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
            retireGroupIdentity(groupKey);
            transaction.commit();
        }
        return true;
    }

    /**
     * 解析同一所有者下的现有分组，找不到时创建稳定分组标识。
     */
    public GroupRef resolveOrCreateGroup(UUID playerId, String displayName) {
        return PlayerGroupStore.get(server).resolveOrCreateGroup(playerId, displayName);
    }

    /**
     * 按所有者和显示名称查找稳定分组。
     */
    @Nullable
    public GroupRef findGroup(UUID playerId, String displayName) {
        return PlayerGroupStore.get(server).findGroup(playerId, displayName);
    }

}
