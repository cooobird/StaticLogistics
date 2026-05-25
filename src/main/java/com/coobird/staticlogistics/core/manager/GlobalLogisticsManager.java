package com.coobird.staticlogistics.core.manager;

import com.coobird.staticlogistics.api.ILogisticsManager;
import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.api.NodeRole;
import com.coobird.staticlogistics.api.event.LogisticsNodeEvent;
import com.coobird.staticlogistics.api.type.TransferType;
import com.coobird.staticlogistics.core.registration.TransferRegistries;
import com.coobird.staticlogistics.server.ticker.LogisticsTicker;
import com.coobird.staticlogistics.storage.LinkManager;
import com.coobird.staticlogistics.storage.config.FaceConfigComposite;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 全局物流管理器——整个模组的大管家。
 * 管理所有节点注册/注销、组同步、反向链接索引、传输游标等核心逻辑。
 * 每个 MinecraftServer 只有一个实例。
 */
public class GlobalLogisticsManager implements ILogisticsManager {
    private static final Map<MinecraftServer, GlobalLogisticsManager> INSTANCES = new ConcurrentHashMap<>();

    private final MinecraftServer server;
    private final NodeGroupService nodeGroupService;
    private final GroupMemberService groupMemberService;
    private final TransferCursorService cursorService;
    private final GroupSyncScheduler syncScheduler;
    private final Map<UUID, Integer> playerNextGroupCounter = new ConcurrentHashMap<>();

    /**
     * 反向链接索引：target 的 nodeKey → 所有指向它的 source nodeKey 集合。
     * 这是从 linkedNodes 派生的只读缓存，不再手动同步——外部只需调 markReverseLinksStale()，
     * 下次访问时自动从 linkedNodes 重建。
     */
    private final Map<Long, LongSet> reverseLinks = new ConcurrentHashMap<>();
    private volatile boolean reverseLinksStale = false;

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
    public int[] getCursor(long nodeKey, TransferType type) {
        return cursorService.getOrCreateCursor(nodeKey, type);
    }

    @Override
    public void syncGroupLinks(ServerLevel level, String groupId, @Nullable LogisticsNode triggerNode) {
        if (groupId != null) markGroupDirty(groupId);
    }

    /**
     * 标记反向链接索引已失效。外部修改 linkedNodes 后必须调用此方法。
     * 不再需要手动维护 addReverseLink / removeReverseLink。
     */
    public void markReverseLinksStale() {
        reverseLinksStale = true;
    }

    /**
     * O(1) 查找指向 target 的所有源节点（自动按需重建索引）。
     */
    public Set<LogisticsNode> getSourcesLinkedTo(LogisticsNode target) {
        ensureReverseLinksFresh();
        Set<LogisticsNode> sources = ConcurrentHashMap.newKeySet();
        LongSet sourceKeys = reverseLinks.get(target.toKey());
        if (sourceKeys == null || sourceKeys.isEmpty()) return sources;
        for (long sourceKey : sourceKeys) {
            ServerLevel sourceLevel = server.getLevel(LogisticsNode.fromKey(sourceKey, target.gPos().dimension()).gPos().dimension());
            if (sourceLevel != null) {
                FaceConfigComposite cfg = LinkManager.get(sourceLevel).getFaceConfig(sourceKey);
                if (cfg != null && cfg.getLinkedNodes().contains(target)) {
                    sources.add(LinkManager.get(sourceLevel).createNodeFromKey(sourceKey));
                }
            }
        }
        return sources;
    }

    /**
     * 从所有维度的 FaceConfigComposite.linkedNodes 重建反向链接索引。
     * linkedNodes 才是真正的数据源，索引只是缓存。
     */
    private void ensureReverseLinksFresh() {
        if (!reverseLinksStale) return;
        synchronized (this) {
            if (!reverseLinksStale) return; // 双重检查
            reverseLinks.clear();
            for (ServerLevel level : server.getAllLevels()) {
                LinkManager mgr = LinkManager.get(level);
                for (long faceKey : mgr.getAllConfigKeys()) {
                    FaceConfigComposite cfg = mgr.getFaceConfig(faceKey);
                    if (cfg == null) continue;
                    long sourceKey = LinkManager.posToKey(cfg.faceConfig.getPos(),
                        LogisticsNode.fromKey(faceKey, level.dimension()).face());
                    for (LogisticsNode linked : cfg.getLinkedNodes()) {
                        reverseLinks.computeIfAbsent(linked.toKey(), k -> new LongOpenHashSet()).add(sourceKey);
                    }
                }
            }
            reverseLinksStale = false;
        }
    }

    public int getNextRoundRobinIndex(long nodeKey, int poolSize) {
        return cursorService.getNextRoundRobinIndex(nodeKey, poolSize);
    }

    public void registerNodeToChannel(TransferType type, int channel, LogisticsNode node) {
        groupMemberService.registerNodeToChannel(type, channel, node);
    }

    public void unregisterNodeFromAllChannels(LogisticsNode node) {
        groupMemberService.unregisterNodeFromAllChannels(node);
    }

    public List<LogisticsNode> getReceiversForChannel(TransferType type, int channel) {
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
        // 如果本轮清理了死链接，标记反向索引失效
        markReverseLinksStale();
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
                for (TransferType type : TransferRegistries.getAllActive()) {
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
        Set<Integer> used = getNumericGroupIdsForPlayer(playerId);

        // 没有任何数字组 → 重置计数器，从1开始
        if (used.isEmpty()) {
            playerNextGroupCounter.put(playerId, 1);
            return "1";
        }

        // 有已有组 → 从记录的最大值往上找，不回落复用
        int counter = Math.max(
            playerNextGroupCounter.getOrDefault(playerId, 0),
            used.stream().max(Integer::compareTo).orElse(0));
        int next = counter + 1;
        while (used.contains(next)) {
            next++;
        }
        playerNextGroupCounter.put(playerId, next);
        return Integer.toString(next);
    }

    private Set<Integer> getNumericGroupIdsForPlayer(UUID playerId) {
        Set<Integer> ids = new HashSet<>();
        for (ServerLevel level : server.getAllLevels()) {
            LinkManager mgr = LinkManager.get(level);
            for (long key : mgr.getAllConfigKeys()) {
                FaceConfigComposite cfg = mgr.getFaceConfig(key);
                if (cfg != null && playerId.equals(cfg.faceConfig.getOwner())) {
                    for (String gid : cfg.faceConfig.getGroupIds()) {
                        if (gid != null && gid.matches("\\d+")) {
                            ids.add(Integer.parseInt(gid));
                        }
                    }
                }
            }
        }
        return ids;
    }

    /**
     * 根源清理：遍历所有维度的玩家面配置，移除已无活跃节点的组ID（空组清零）。
     * 调用时机：删除链路/组操作后。
     */
    public void cleanupOrphanedGroupIds(@Nullable UUID playerId) {
        if (playerId == null) return;
        for (ServerLevel level : server.getAllLevels()) {
            LinkManager mgr = LinkManager.get(level);
            for (long key : mgr.getAllConfigKeys()) {
                FaceConfigComposite cfg = mgr.getFaceConfig(key);
                if (cfg == null) continue;
                UUID owner = cfg.faceConfig.getOwner();
                if (!playerId.equals(owner)) continue;
                for (String gid : new java.util.ArrayList<>(cfg.faceConfig.getGroupIds())) {
                    if (nodeGroupService.getNodesInGroup(gid).isEmpty()) {
                        cfg.faceConfig.removeGroupId(gid);
                        cfg.markDirty();
                        mgr.markFaceDirty(key);
                    }
                }
            }
        }
    }

    /**
     * 删除指定分组及其所有关联节点。
     */
    public void removeGroup(String groupId) {
        if (groupId == null || groupId.isEmpty()) return;
        Map<LogisticsNode, NodeRole> nodes = nodeGroupService.getNodesInGroup(groupId);
        for (LogisticsNode node : new ArrayList<>(nodes.keySet())) {
            nodeGroupService.unregister(node);
        }
        // 清理面配置中的组引用
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
        markReverseLinksStale();
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

    // 持久化保存计数器到 NBT
    public void save(CompoundTag tag) {
        CompoundTag counterTag = new CompoundTag();
        playerNextGroupCounter.forEach((uuid, counter) -> counterTag.putInt(uuid.toString(), counter));
        tag.put("player_group_counter", counterTag);
    }

    // 从 NBT 恢复计数器数据
    public void load(CompoundTag tag) {
        if (tag.contains("player_group_counter")) {
            CompoundTag counterTag = tag.getCompound("player_group_counter");
            for (String key : counterTag.getAllKeys()) {
                playerNextGroupCounter.put(UUID.fromString(key), counterTag.getInt(key));
            }
        }
    }
}