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
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class GlobalLogisticsManager implements ILogisticsManager {
    private static final Map<MinecraftServer, GlobalLogisticsManager> INSTANCES = new ConcurrentHashMap<>();

    private final MinecraftServer server;
    private final NodeGroupService nodeGroupService;
    private final GroupMemberService groupMemberService;
    private final TransferCursorService cursorService;
    private final GroupSyncScheduler syncScheduler;
    private final Map<UUID, Integer> playerNextGroupCounter = new ConcurrentHashMap<>();

    private GlobalLogisticsManager(MinecraftServer server) {
        this.server = server;
        this.groupMemberService = new GroupMemberService();
        this.nodeGroupService = new NodeGroupService(groupMemberService);
        this.cursorService = new TransferCursorService();
        this.syncScheduler = new GroupSyncScheduler();
    }

    public static GlobalLogisticsManager get(MinecraftServer server) {
        return INSTANCES.computeIfAbsent(server, GlobalLogisticsManager::new);
    }

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

    public Set<LogisticsNode> getSourcesLinkedTo(LogisticsNode target) {
        Set<LogisticsNode> sources = ConcurrentHashMap.newKeySet();
        for (ServerLevel level : server.getAllLevels()) {
            LinkManager mgr = LinkManager.get(level);
            for (long key : mgr.getAllConfigKeys()) {
                FaceConfigComposite cfg = mgr.getFaceConfig(key);
                if (cfg != null && cfg.getLinkedNodes().contains(target)) {
                    sources.add(mgr.createNodeFromKey(key));
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

    public void tick() {
        if (!syncScheduler.hasPending()) return;
        for (String groupId : syncScheduler.takeAll()) {
            performSyncGroupLinks(groupId);
        }
    }

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
                sMgr.markDirtyBatch(() -> {
                });
            }
        }
    }

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

    public void save(CompoundTag tag) {
        CompoundTag counterTag = new CompoundTag();
        playerNextGroupCounter.forEach((uuid, counter) -> counterTag.putInt(uuid.toString(), counter));
        tag.put("player_group_counter", counterTag);
    }

    public void load(CompoundTag tag) {
        if (tag.contains("player_group_counter")) {
            CompoundTag counterTag = tag.getCompound("player_group_counter");
            for (String key : counterTag.getAllKeys()) {
                playerNextGroupCounter.put(UUID.fromString(key), counterTag.getInt(key));
            }
        }
    }
}