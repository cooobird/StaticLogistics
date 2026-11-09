package com.coobird.staticlogistics.logistics.group;

import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.api.group.GroupConstraints;
import com.coobird.staticlogistics.api.group.GroupKey;
import com.coobird.staticlogistics.api.group.GroupRef;
import com.coobird.staticlogistics.logistics.node.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.*;

/// 组重命名服务——遍历所有维度中属于旧组 ID 的配置，原子替换为新组 ID。
public class GroupRenameService {
    private final PermissionService permissionService;
    private final GlobalLogisticsManager globalManager;

    public GroupRenameService(PermissionService permissionService, GlobalLogisticsManager globalManager) {
        this.permissionService = permissionService;
        this.globalManager = globalManager;
    }

    public boolean renameGroupAsAdmin(Level level, Player player, UUID ownerId,
                                      String oldId, String newId) {
        if (!player.hasPermissions(2)) return false;
        if (oldId == null || newId == null || oldId.equals(newId) || newId.isEmpty()) return false;

        MinecraftServer server = level.getServer();
        if (server == null) return false;
        GroupRef group = PlayerGroupStore.get(server).findGroup(ownerId, oldId);
        if (group == null) return false;
        return renameGroup(level, player, group.key(), newId, true);
    }

    public boolean renameGroup(Level level, Player player, GroupKey groupKey, String newDisplayName) {
        return renameGroup(level, player, groupKey, newDisplayName, false);
    }

    private boolean renameGroup(Level level, Player player, GroupKey groupKey,
                                String newDisplayName, boolean administrative) {
        if (groupKey == null || newDisplayName == null || newDisplayName.isEmpty()) return false;
        try {
            newDisplayName = GroupConstraints.normalizeName(newDisplayName);
        } catch (IllegalArgumentException exception) {
            return false;
        }
        MinecraftServer server = level.getServer();
        if (server == null
            || (!administrative && !permissionService.canModify(groupKey.ownerId(), player))) return false;
        PlayerGroupStore store = PlayerGroupStore.get(server);
        GroupRef currentGroup = store.findGroup(groupKey);
        if (currentGroup == null) return false;
        if (currentGroup.displayName().equals(newDisplayName)) return false;
        GroupRef sameNameGroup = store.findGroup(groupKey.ownerId(), newDisplayName);
        if (sameNameGroup != null && !sameNameGroup.key().equals(groupKey)) {
            return mergeGroup(server, player, currentGroup, sameNameGroup, administrative);
        }
        if (!store.canRenameGroup(groupKey, newDisplayName)) return false;
        List<LogisticsNode> affectedNodes = new ArrayList<>();

        for (ServerLevel serverLevel : server.getAllLevels()) {
            LinkManager mgr = LinkManager.get(serverLevel);
            for (EndpointAddress key : mgr.getAllConfigKeys()) {
                EndpointConfig config = mgr.getEndpointConfig(key);
                if (config == null || !config.faceConfig.getGroupKeys().contains(groupKey)) continue;
                if (!administrative
                    && !permissionService.canModify(config.faceConfig.getOwner(), player)) return false;
                affectedNodes.add(mgr.createNodeFromKey(key));
            }
        }

        try (NodeMutationTransaction transaction = NodeMutationTransaction.begin(server)) {
            transaction.captureAll(affectedNodes);
            transaction.onRollback(() -> globalManager.updateGroupDisplayName(
                groupKey, currentGroup.displayName()));
            transaction.onRollback(() -> {
                if (!store.renameGroup(groupKey, currentGroup.displayName())) {
                    throw new IllegalStateException("Group directory rollback failed during rename");
                }
            });

            for (LogisticsNode node : affectedNodes) {
                LinkManager manager = LinkManager.get(server.getLevel(node.gPos().dimension()));
                manager.renameGroupMetadata(node, groupKey, newDisplayName);
            }
            if (!store.renameGroup(groupKey, newDisplayName)) {
                throw new IllegalStateException("Group directory changed during rename");
            }
            globalManager.updateGroupDisplayName(groupKey, newDisplayName);
            transaction.commit();
        }
        globalManager.syncGroupLinks(groupKey);
        return true;
    }

    /// 仅合并同一所有者下的两个稳定分组身份。
    private boolean mergeGroup(MinecraftServer server, Player player,
                               GroupRef source, GroupRef target, boolean administrative) {
        if (!source.key().ownerId().equals(target.key().ownerId())) return false;
        if (!administrative && !permissionService.canModify(source.key().ownerId(), player)) return false;

        PlayerGroupStore store = PlayerGroupStore.get(server);
        List<LogisticsNode> sourceNodes = new ArrayList<>();
        for (ServerLevel serverLevel : server.getAllLevels()) {
            LinkManager manager = LinkManager.get(serverLevel);
            for (EndpointAddress key : manager.getAllConfigKeys()) {
                EndpointConfig config = manager.getEndpointConfig(key);
                if (config == null
                    || !config.faceConfig.getGroupKeys().contains(source.key())) continue;
                if (!source.key().ownerId().equals(config.faceConfig.getOwner())
                    || (!administrative
                    && !permissionService.canModify(config.faceConfig.getOwner(), player))) return false;
                sourceNodes.add(manager.createNodeFromKey(key));
            }
        }

        Map<ConnectionKey, ConnectionKey> connectionMoves = new LinkedHashMap<>();
        for (LogisticsNode node : sourceNodes) {
            ServerLevel nodeLevel = server.getLevel(node.gPos().dimension());
            if (nodeLevel == null) continue;
            EndpointConfig config = LinkManager.get(nodeLevel)
                .getEndpointConfig(EndpointAddress.of(node));
            if (config == null) continue;
            for (LogisticsNode linked : config.getLinkedNodes(source.key())) {
                if (node.gPos().equals(linked.gPos())) continue;
                ConnectionKey before = new ConnectionKey(source.key(), node, linked);
                connectionMoves.put(before, new ConnectionKey(
                    target.key(), before.first(), before.second()));
            }
        }

        try (NodeMutationTransaction transaction = NodeMutationTransaction.begin(server)) {
            PlayerGroupStore.GroupDirectorySnapshot groupSnapshot =
                store.captureGroups(Set.of(source.key(), target.key()));
            PlayerGroupStore.ConnectionMetadataSnapshot metadataSnapshot =
                store.captureConnectionMetadata(
                    connectionMoves, Map.of(source.key(), target.key()));
            transaction.captureAll(sourceNodes);
            transaction.onRollback(() -> {
                store.restoreGroups(groupSnapshot);
                store.restoreConnectionMetadata(metadataSnapshot);
            });

            for (LogisticsNode node : sourceNodes) {
                ServerLevel nodeLevel = server.getLevel(node.gPos().dimension());
                if (nodeLevel == null) {
                    throw new IllegalStateException(
                        "Group merge dimension is unavailable: " + node.gPos().dimension().location());
                }
                LinkManager.get(nodeLevel).mergeGroupMetadata(node, source, target);
            }
            store.remapConnectionMetadata(connectionMoves, Map.of(source.key(), target.key()));
            if (!store.removeGroup(source.key())) {
                throw new IllegalStateException("Source group directory changed during merge");
            }
            transaction.commit();
        }

        globalManager.updateGroupDisplayName(target.key(), target.displayName());
        globalManager.retireGroupIdentity(source.key());
        globalManager.syncGroupLinks(source.key());
        globalManager.syncGroupLinks(target.key());
        return true;
    }
}
