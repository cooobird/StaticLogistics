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
     * 反向链接索引：target 的 nodeKey → 所有指向它的 source nodeKey 集合
     * 以前找"谁连向我"要遍历所有维度所有面，现在直接查这个 Map，O(1)
     */
    private final Map<Long, LongSet> reverseLinks = new ConcurrentHashMap<>();

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
     * 注册一条反向链接：source 指向 target
     */
    public void addReverseLink(long sourceNodeKey, long targetNodeKey) {
        reverseLinks.computeIfAbsent(targetNodeKey, k -> new LongOpenHashSet()).add(sourceNodeKey);
    }

    /**
     * 移除一条反向链接：source → target
     */
    public void removeReverseLink(long sourceNodeKey, long targetNodeKey) {
        LongSet sources = reverseLinks.get(targetNodeKey);
        if (sources != null) {
            sources.remove(sourceNodeKey);
            if (sources.isEmpty()) {
                reverseLinks.remove(targetNodeKey);
            }
        }
    }

    /**
     * 移除某个节点的所有反向链接（节点被删除时调用）
     */
    public void removeAllReverseLinksFor(long nodeKey) {
        // 作为target：清除所有指向它的源
        reverseLinks.remove(nodeKey);
        // 作为source：从所有target的反向列表移除
        for (LongSet sources : reverseLinks.values()) {
            sources.remove(nodeKey);
        }
        reverseLinks.values().removeIf(LongSet::isEmpty);
    }

    /**
     * O(1) 查找指向 target 的所有源节点
     */
    public Set<LogisticsNode> getSourcesLinkedTo(LogisticsNode target) {
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
                    removeReverseLink(source.toKey(), linkedNode.toKey());
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
                for (TransferType type : TransferRegistries.getAllActive()) {
                    registerNodeToChannel(type, inputChannel, node);
                }
            }
        }
    }

    // 通知节点被移除：注销并标记组需要同步
    public void notifyNodeRemoved(ServerLevel level, LogisticsNode removedNode) {
        String groupId = getGroupId(removedNode);
        unregisterNode(removedNode);
        if (groupId != null && !groupId.isEmpty()) {
            markGroupDirty(groupId);
            LogisticsTicker.wakeupGroup(server, groupId);
        }
    }

    @Nullable
    public ServerLevel getLevel(ResourceKey<Level> dimension) {
        return server.getLevel(dimension);
    }

    public MinecraftServer getServer() {
        return server;
    }

    // 为玩家自动分配下一个未使用的数字组 ID
    public synchronized String getNextGroupIdForPlayer(UUID playerId) {
        Set<Integer> used = getNumericGroupIdsForPlayer(playerId);
        int next = 1;
        while (used.contains(next)) {
            next++;
        }
        return Integer.toString(next);
    }

    private Set<Integer> getNumericGroupIdsForPlayer(UUID playerId) {
        Set<Integer> ids = new HashSet<>();
        for (ServerLevel level : server.getAllLevels()) {
            LinkManager mgr = LinkManager.get(level);
            for (long key : mgr.getAllConfigKeys()) {
                FaceConfigComposite cfg = mgr.getFaceConfig(key);
                if (cfg != null && playerId.equals(cfg.faceConfig.getOwner())) {
                    String gid = cfg.faceConfig.getGroupId();
                    if (gid != null && gid.matches("\\d+")) {
                        ids.add(Integer.parseInt(gid));
                    }
                }
            }
        }
        return ids;
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