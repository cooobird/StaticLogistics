package com.coobird.staticlogistics.logistics.group;

import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.api.group.GroupKey;
import com.coobird.staticlogistics.api.group.GroupRef;
import com.coobird.staticlogistics.content.event.PlayerEvents;
import com.coobird.staticlogistics.logistics.node.*;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/// 跨维度所有权迁移的唯一事务入口。
public final class OwnershipTransferService {
    private static final OwnershipMutationPermit MUTATION_PERMIT = new OwnershipMutationPermit();

    private final MinecraftServer server;
    private final GlobalLogisticsManager globalManager;

    public OwnershipTransferService(MinecraftServer server, GlobalLogisticsManager globalManager) {
        this.server = server;
        this.globalManager = globalManager;
    }

    public TransferResult transfer(CommandSourceStack source, UUID previousOwner,
                                   ServerPlayer newOwner, @Nullable String selectedGroupName) {
        if (source == null || !source.hasPermission(2) || previousOwner == null || newOwner == null) {
            return TransferResult.failed();
        }
        PlayerGroupStore store = PlayerGroupStore.get(server);
        GroupRef selectedGroup = selectedGroupName == null
            ? null : store.findGroup(previousOwner, selectedGroupName);
        if (selectedGroupName != null && selectedGroup == null) return TransferResult.failed();

        List<Target> targets = new ArrayList<>();
        Set<GroupRef> groups = new LinkedHashSet<>();
        if (selectedGroup == null) groups.addAll(store.getGroupRefs(previousOwner));
        else groups.add(selectedGroup);

        for (ServerLevel level : server.getAllLevels()) {
            LinkManager manager = LinkManager.get(level);
            for (EndpointAddress key : manager.getAllConfigKeys()) {
                EndpointConfig config = manager.getEndpointConfig(key);
                if (config == null || !previousOwner.equals(config.faceConfig.getOwner())) continue;
                if (selectedGroup != null
                    && !config.faceConfig.getGroupKeys().contains(selectedGroup.key())) continue;
                if (selectedGroup != null && config.faceConfig.getGroupKeys().size() != 1) {
                    source.sendFailure(Component.literal(
                        "Cannot transfer one group from a face that belongs to multiple groups"));
                    return TransferResult.failed();
                }
                config.validateOwnershipTransfer(previousOwner);
                groups.addAll(config.faceConfig.getGroups());
                targets.add(new Target(manager, key, config));
            }
        }
        if (groups.isEmpty()) return TransferResult.failed();

        try {
            Map<ConnectionKey, ConnectionKey> connectionMoves = new LinkedHashMap<>();
            Map<GroupKey, GroupKey> groupMoves = new LinkedHashMap<>();
            groups.forEach(group -> groupMoves.put(
                group.key(), group.key().withOwner(newOwner.getUUID())));
            for (Target target : targets) {
                for (GroupRef group : target.config().faceConfig.getGroups()) {
                    if (!groupMoves.containsKey(group.key())) continue;
                    for (LogisticsNode linked : target.config().getLinkedNodes(group.key())) {
                        if (target.node().gPos().equals(linked.gPos())) continue;
                        ConnectionKey before = new ConnectionKey(group.key(), target.node(), linked);
                        connectionMoves.put(before, new ConnectionKey(
                            groupMoves.get(group.key()), before.first(), before.second()));
                    }
                }
            }
            Set<GroupKey> affectedGroupKeys = new LinkedHashSet<>(groupMoves.keySet());
            affectedGroupKeys.addAll(groupMoves.values());
            PlayerGroupStore.GroupDirectorySnapshot groupSnapshot =
                store.captureGroups(affectedGroupKeys);
            PlayerGroupStore.ConnectionMetadataSnapshot metadataSnapshot =
                store.captureConnectionMetadata(connectionMoves, groupMoves);
            try (NodeMutationTransaction transaction = NodeMutationTransaction.begin(server)) {
                transaction.onRollback(() -> {
                    store.restoreGroups(groupSnapshot);
                    store.restoreConnectionMetadata(metadataSnapshot);
                });
                for (Target target : targets) transaction.capture(target.node());
                for (Target target : targets) {
                    target.config().transferOwnership(MUTATION_PERMIT, newOwner.getUUID(),
                        newOwner.getGameProfile().getName(), newOwner.getGameProfile());
                }
                store.transferGroups(previousOwner, newOwner.getUUID(), groups);
                store.remapConnectionMetadata(connectionMoves, groupMoves);
                transaction.commit();
            }
            groups.forEach(group -> globalManager.retireGroupIdentity(group.key()));
            refreshAffectedClients(previousOwner, newOwner.getUUID());
            return new TransferResult(true, groups.size(), targets.size());
        } catch (RuntimeException exception) {
            String message = "Ownership transfer failed: " + exception.getMessage();
            source.sendFailure(Component.literal(message));
            return TransferResult.failed();
        }
    }

    /// 所有权改变会同时改变旧、新权限域；两侧客户端都必须重新取得完整的权威投影。
    private void refreshAffectedClients(UUID previousOwner, UUID newOwner) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (PermissionService.getInstance().canAccess(previousOwner, player) || PermissionService.getInstance().canAccess(newOwner, player)) {
                PlayerEvents.requestClientStateRefresh(player);
            }
        }
    }

    private record Target(LinkManager manager, EndpointAddress key, EndpointConfig config) {
        private LogisticsNode node() {
            return manager.createNodeFromKey(key);
        }
    }

    public record TransferResult(boolean success, int groups, int nodes) {
        private static TransferResult failed() {
            return new TransferResult(false, 0, 0);
        }

        public int commandResult() {
            return success ? Math.max(1, nodes) : 0;
        }
    }
}
