package com.coobird.staticlogistics.logic;

import com.coobird.staticlogistics.api.ILogisticsManager;
import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.api.LogisticsResource;
import com.coobird.staticlogistics.api.NodeRole;
import com.coobird.staticlogistics.api.event.LogisticsNodeEvent;
import com.coobird.staticlogistics.logic.group.GroupMemberService;
import com.coobird.staticlogistics.logic.group.GroupSyncScheduler;
import com.coobird.staticlogistics.logic.group.NodeGroupService;
import com.coobird.staticlogistics.logic.group.PlayerGroupStore;
import com.coobird.staticlogistics.logic.ticker.LogisticsTicker;
import com.coobird.staticlogistics.storage.link.LinkManager;
import com.coobird.staticlogistics.storage.model.FaceConfigComposite;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 全局物流管理器。
 * 管理所有节点注册/注销、组同步、传输游标等核心逻辑。
 * 每个 MinecraftServer 只有一个实例。
 */
public class GlobalLogisticsManager implements ILogisticsManager {
    private static final Map<MinecraftServer, GlobalLogisticsManager> INSTANCES = new ConcurrentHashMap<>();

    private final MinecraftServer server;
    private final NodeGroupService nodeGroupService;
    private final GroupMemberService groupMemberService;
    private final TransferCursorService cursorService;
    private final GroupSyncScheduler syncScheduler;

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

    @Override
    public void registerNode(String groupId, LogisticsNode node, NodeRole role) {
        nodeGroupService.register(groupId, node, role);
    }

    @Override
    public void unregisterNode(LogisticsNode node) {
        nodeGroupService.unregister(node);
        cursorService.removeCursor(node.toKey());
        groupMemberService.unregisterNodeFromAllChannels(node);
    }

    @Override
    public List<LogisticsNode> getReceivers(String groupId) {
        return groupMemberService.getReceivers(groupId);
    }

    @Override
    public List<LogisticsNode> getSenders(String groupId) {
        return groupMemberService.getSenders(groupId);
    }

    @Override
    public Set<String> getActiveGroups() {
        return Collections.unmodifiableSet(groupMemberService.getAllGroupIds());
    }

    @Override
    public String getGroupId(LogisticsNode node) {
        return nodeGroupService.getGroupId(node);
    }

    @Override
    public Map<LogisticsNode, NodeRole> getNodesInGroup(String groupId) {
        return nodeGroupService.getNodesInGroup(groupId);
    }

    @Override
    public int[] getCursor(long nodeKey, LogisticsResource<?> type) {
        return cursorService.getOrCreateCursor(nodeKey, type);
    }

    @Override
    public void syncGroupLinks(ServerLevel level, String groupId, @Nullable LogisticsNode triggerNode) {
        if (groupId != null) markGroupDirty(groupId);
    }

    /**
     * 查找指向 target 的所有源节点 —— 全量扫描所有维度的 FaceConfigComposite.linkedNodes。
     * 无增量索引，按需查询。适用于低频 API 调用。
     */
    public Set<LogisticsNode> getSourcesLinkedTo(LogisticsNode target) {
        Set<LogisticsNode> sources = new LinkedHashSet<>();
        for (ServerLevel level : server.getAllLevels()) {
            LinkManager mgr = LinkManager.get(level);
            for (long faceKey : mgr.getAllConfigKeys()) {
                FaceConfigComposite cfg = mgr.getFaceConfig(faceKey);
                if (cfg == null) continue;
                if (cfg.getLinkedNodes().contains(target)) {
                    sources.add(mgr.createNodeFromKey(faceKey));
                }
            }
        }
        return sources;
    }

    public int getNextRoundRobinIndex(long nodeKey, int poolSize) {
        return cursorService.getNextRoundRobinIndex(nodeKey, poolSize);
    }

    public void registerNodeToChannel(LogisticsResource<?> type, int channel, LogisticsNode node) {
        groupMemberService.registerNodeToChannel(type, channel, node);
    }

    public void unregisterNodeFromAllChannels(LogisticsNode node) {
        groupMemberService.unregisterNodeFromAllChannels(node);
    }

    public List<LogisticsNode> getReceiversForChannel(LogisticsResource<?> type, int channel) {
        return groupMemberService.getReceiversForChannel(type, channel);
    }

    public void markGroupDirty(String groupId) {
        syncScheduler.markDirty(groupId);
        if (groupId != null) {
            LogisticsTicker.wakeupGroup(server, groupId);
        }
    }

    // 每个 tick 处理所有待同步的组（清除无效链接等）
    public void tick() {
        if (!syncScheduler.hasPending()) return;
        for (String groupId : syncScheduler.takeAll()) {
            performSyncGroupLinks(groupId);
        }
    }

    // 执行组内链接同步：清理指向已注销节点的死链接
    private void performSyncGroupLinks(String groupId) {
        Map<LogisticsNode, NodeRole> groupNodeMap = getNodesInGroup(groupId);
        if (groupNodeMap.isEmpty()) return;
        Set<LogisticsNode> aliveNodes = groupNodeMap.keySet();
        Map<ResourceKey<Level>, LinkManager> mgrCache = new HashMap<>();
        for (LogisticsNode source : aliveNodes) {
            ServerLevel sLevel = server.getLevel(source.gPos().dimension());
            if (sLevel == null) continue;
            LinkManager sMgr = mgrCache.computeIfAbsent(source.gPos().dimension(), k -> LinkManager.get(sLevel));
            FaceConfigComposite sCfg = sMgr.getFaceConfig(source.toKey());
            if (sCfg == null) continue;
            boolean anyChanged = false;
            Iterator<LogisticsNode> it = sCfg.getLinkedNodes().iterator();
            while (it.hasNext()) {
                LogisticsNode linkedNode = it.next();
                if (!aliveNodes.contains(linkedNode)) {
                    it.remove();
                    anyChanged = true;
                }
            }
            if (anyChanged) {
                sMgr.refreshLocalCache(source.toKey(), source.gPos().pos(), source.face(), sCfg);
                sMgr.syncConfigToClients(source.gPos().pos());
                sMgr.markFaceDirty(source.toKey());
            }
        }
    }

    // 处理节点事件（添加/删除/修改），更新注册、频道索引、标记脏数据
    public void handleNodeEvent(LogisticsNodeEvent event, ServerLevel level) {
        for (LogisticsNodeEvent.NodeEntry entry : event.getAffectedEntries()) {
            switch (event.getType()) {
                case ADDED -> {
                    registerNode(entry.groupId(), entry.node(), entry.role());
                    updateChannelRegistration(level, entry.node());
                    markGroupDirty(entry.groupId());
                }
                case REMOVED -> notifyNodeRemoved(level, entry.node());
                case CHANGED -> {
                    FaceConfigComposite config = LinkManager.get(level).getFaceConfig(entry.node().toKey());
                    if (config != null) {
                        registerNode(entry.groupId(), entry.node(), config.determineRole());
                        updateChannelRegistration(level, entry.node());
                        markGroupDirty(entry.groupId());
                    }
                }
            }
        }
    }

    private void updateChannelRegistration(ServerLevel level, LogisticsNode node) {
        unregisterNodeFromAllChannels(node);
        FaceConfigComposite config = LinkManager.get(level).getFaceConfig(node.toKey());
        if (config != null) {
            int inputChannel = config.linkConfig.getInputChannel();
            if (inputChannel != 0) {
                for (var type : TransferRegistries.getAllActive()) {
                    registerNodeToChannel(type, inputChannel, node);
                }
            }
        }
    }

    // 通知节点被移除：注销前获取所有关联组，注销后标记所有组脏
    public void notifyNodeRemoved(ServerLevel level, LogisticsNode removedNode) {
        Set<String> groups = nodeGroupService.getAllGroupIds(removedNode);
        unregisterNode(removedNode);
        for (String gid : groups) {
            if (gid != null && !gid.isEmpty()) {
                markGroupDirty(gid);
                LogisticsTicker.wakeupGroup(server, gid);
            }
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
        Set<String> playerAllGroups = getGroups(playerId);
        for (ServerLevel level : server.getAllLevels()) {
            LinkManager mgr = LinkManager.get(level);
            List<Long> toRemove = new ArrayList<>();
            for (long key : mgr.getAllConfigKeys()) {
                FaceConfigComposite cfg = mgr.getFaceConfig(key);
                if (cfg == null) continue;
                UUID owner = cfg.faceConfig.getOwner();
                if (!playerId.equals(owner)) continue;
                // 移除无节点的组 ID（但保留玩家手动创建的空分组）
                for (String gid : new java.util.ArrayList<>(cfg.faceConfig.getGroupIds())) {
                    if (nodeGroupService.getNodesInGroup(gid).isEmpty() && !playerAllGroups.contains(gid)) {
                        cfg.faceConfig.removeGroupId(gid);
                        cfg.markDirty();
                        mgr.markFaceDirty(key);
                    }
                }
                if (!cfg.faceConfig.hasGroup()) {
                    toRemove.add(key);
                }
            }
            for (long key : toRemove) {
                mgr.removeFaceConfig(key);
            }
        }
    }

    /**
     * 删除指定分组及其所有关联节点。
     * 统一处理空分组和有内容的分组。
     */
    public void removeGroup(UUID playerId, String groupId) {
        if (groupId == null || groupId.isEmpty()) return;

        // 从 PlayerGroupStore 移除（触发持久化）
        PlayerGroupStore.get(server).removeGroup(playerId, groupId);

        // 从 nodeGroupService 注销所有节点
        Map<LogisticsNode, NodeRole> nodes = nodeGroupService.getNodesInGroup(groupId);
        for (LogisticsNode node : new ArrayList<>(nodes.keySet())) {
            nodeGroupService.unregister(node);
        }

        // 清理面配置中的组引用（只移除组ID，不删除FaceConfig）
        for (ServerLevel level : server.getAllLevels()) {
            LinkManager mgr = LinkManager.get(level);
            for (long key : mgr.getAllConfigKeys()) {
                FaceConfigComposite cfg = mgr.getFaceConfig(key);
                if (cfg != null && cfg.faceConfig.getGroupIds().contains(groupId)) {
                    cfg.faceConfig.removeGroupId(groupId);
                    cfg.markDirty();
                    mgr.markFaceDirty(key);
                }
            }
        }

        // 清理无组的面配置（通过cleanupOrphanedGroupIds统一处理）
        cleanupOrphanedGroupIds(playerId);
    }

    /**
     * 收集指定分组的所有面配置条目（用于客户端同步清理）。
     */
    public List<GlobalLogisticsManager.FaceEntry> collectGroupFaceConfigs(String groupId) {
        List<FaceEntry> result = new ArrayList<>();
        if (groupId == null || groupId.isEmpty()) return result;
        for (ServerLevel level : server.getAllLevels()) {
            LinkManager mgr = LinkManager.get(level);
            for (long key : mgr.getAllConfigKeys()) {
                FaceConfigComposite cfg = mgr.getFaceConfig(key);
                if (cfg != null && cfg.faceConfig.getGroupIds().contains(groupId)) {
                    result.add(new FaceEntry(
                        GlobalPos.of(level.dimension(), LogisticsNode.keyToPos(key)),
                        LogisticsNode.keyToFace(key)));
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

    /**
     * 获取玩家的所有分组
     */
    public Set<String> getGroups(UUID playerId) {
        return PlayerGroupStore.get(server).getGroups(playerId);
    }
}