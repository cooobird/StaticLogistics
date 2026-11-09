package com.coobird.staticlogistics.content.event;

import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.api.group.GroupKey;
import com.coobird.staticlogistics.api.group.GroupRef;
import com.coobird.staticlogistics.config.SLConfig;
import com.coobird.staticlogistics.content.item.LinkConfiguratorItem;
import com.coobird.staticlogistics.content.item.StoredSelectionSync;
import com.coobird.staticlogistics.logistics.group.ConnectionCommandService;
import com.coobird.staticlogistics.logistics.group.LogisticsOperationStore;
import com.coobird.staticlogistics.logistics.group.PermissionService;
import com.coobird.staticlogistics.logistics.group.PlayerGroupStore;
import com.coobird.staticlogistics.logistics.node.*;
import com.coobird.staticlogistics.logistics.node.sync.PendingSyncBuffer;
import com.coobird.staticlogistics.network.s2c.S2CAccessSnapshotPayload;
import com.coobird.staticlogistics.network.s2c.S2CGroupHierarchyPayload;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import org.mesdag.portlib.network.PortPacketDistributor;

import java.util.*;

@EventBusSubscriber(modid = StaticLogistics.MODID)
public class PlayerEvents {
    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            SLConfig.syncToPlayer(sp);
            LogisticsOperationStore.get(sp.server).deliverQueuedItems(sp);
            refreshPlayerProfile(sp);
            refreshClientState(sp);
            new ConnectionCommandService(sp.server).resumePendingDirectoryDeletion(sp);
            for (ServerLevel level : sp.server.getAllLevels()) {
                LinkManager.get(level).markOrphanScanNeeded();
            }
        }
    }

    private static void refreshPlayerProfile(ServerPlayer player) {
        var profile = player.getGameProfile();
        var uuid = player.getUUID();
        for (ServerLevel level : player.server.getAllLevels()) {
            LinkManager mgr = LinkManager.get(level);
            for (EndpointAddress key : mgr.getAllConfigKeys()) {
                var cfg = mgr.getEndpointConfig(key);
                if (cfg != null && uuid.equals(cfg.faceConfig.getOwner())) {
                    mgr.refreshOwnerProfile(mgr.createNodeFromKey(key), profile);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            refreshClientState(sp);
        }
    }

    /// 以新的权限作用域替换客户端旧投影，随后按固定顺序发送完整权威状态。
    public static void refreshClientState(ServerPlayer player) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            var stack = player.getInventory().getItem(slot);
            if (stack.getItem() instanceof LinkConfiguratorItem) {
                StoredSelectionSync.publish(player, stack);
            }
        }
        List<EndpointTopology> faces = new ArrayList<>();
        Set<ScopedTopologyLink> linkSet = new LinkedHashSet<>();
        for (ServerLevel level : player.server.getAllLevels()) {
            LinkManager mgr = LinkManager.get(level);
            for (EndpointAddress key : mgr.getAllConfigKeys()) {
                var config = mgr.getEndpointConfig(key);
                if (config == null || config.isDefault()
                    || !PermissionService.getInstance().canAccess(config.faceConfig.getOwner(), player)) continue;
                LogisticsNode source = mgr.createNodeFromKey(key);
                faces.add(EndpointTopology.from(level, source, config));
                config.getLinkedNodesByGroup().forEach((groupKey, targets) ->
                    targets.forEach(target -> {
                        var connection = new ConnectionKey(
                            groupKey, source, target);
                        linkSet.add(new ScopedTopologyLink(
                            groupKey, source, target,
                            PlayerGroupStore.get(player.server).getConnectionName(connection)));
                    }));
            }
        }
        List<ScopedTopologyLink> links = List.copyOf(linkSet);

        Set<UUID> owners = PermissionService.getInstance()
            .accessibleDirectoryOwners(player);
        PlayerGroupStore store = PlayerGroupStore.get(player.server);
        Map<GroupKey, GroupRef> directory = new LinkedHashMap<>();
        for (UUID owner : owners) {
            store.getGroupRefs(owner).forEach(group -> directory.put(group.key(), group));
        }

        List<GroupRef> groups = List.copyOf(directory.values());
        long contentBoundary = PendingSyncBuffer.snapshotBoundary();
        S2CAccessSnapshotPayload.pages(faces, links, groups)
            .forEach(payload -> PortPacketDistributor.sendToPlayer(player, payload));
        /// 全量快照已覆盖之前冻结的内容，包括快照中已经不存在的端点。
        for (ServerLevel level : player.server.getAllLevels()) {
            LinkManager.get(level).snapshotDelivered(player, contentBoundary);
        }
    }

    /// 投影同步不参与领域事务；失败时按玩家合并并退避重试。
    public static void requestClientStateRefresh(ServerPlayer player) {
        if (player == null) return;
        MinecraftServer server = player.server;
        UUID playerId = player.getUUID();
        if (!server.isSameThread()) {
            server.execute(() -> {
                ServerPlayer current = server.getPlayerList().getPlayer(playerId);
                if (current != null) requestClientStateRefresh(current);
            });
            return;
        }
        NodeReconciliationQueue.schedule(server, new ClientProjectionKey(playerId), () -> {
            ServerPlayer current = server.getPlayerList().getPlayer(playerId);
            if (current != null) refreshClientState(current);
        });
    }

    public static void requestHierarchyRefresh(ServerPlayer player) {
        if (player == null) return;
        MinecraftServer server = player.server;
        UUID playerId = player.getUUID();
        if (!server.isSameThread()) {
            server.execute(() -> {
                ServerPlayer current = server.getPlayerList().getPlayer(playerId);
                if (current != null) requestHierarchyRefresh(current);
            });
            return;
        }
        NodeReconciliationQueue.schedule(server, new HierarchyProjectionKey(playerId), () -> {
            ServerPlayer current = server.getPlayerList().getPlayer(playerId);
            if (current != null) PortPacketDistributor.sendToPlayer(current,
                new S2CGroupHierarchyPayload(
                    hierarchyWithPermissions(current)));
        });
    }

    private static CompoundTag hierarchyWithPermissions(ServerPlayer player) {
        var data = PlayerGroupStore.get(player.server).getHierarchy(player.getUUID());
        var permissions = PermissionService.getInstance();
        var owners = new LinkedHashSet<>(permissions.accessibleDirectoryOwners(player));
        owners.addAll(permissions.teamMembersOf(player.getUUID()));
        player.server.getPlayerList().getPlayers().forEach(other -> owners.add(other.getUUID()));
        var entries = new CompoundTag();
        var ownerNames = new CompoundTag();
        var permissionSources = new CompoundTag();
        var profileCache = player.server.getProfileCache();
        for (UUID owner : owners) {
            entries.putInt(owner.toString(), permissions.canModify(owner, player) ? 2
                : permissions.canAccess(owner, player) ? 1 : 0);
            permissionSources.putInt(owner.toString(), permissions.teamGrant(owner, player));
            if (profileCache != null)
                profileCache.get(owner).ifPresent(profile -> ownerNames.putString(owner.toString(), profile.getName()));
        }
        data.put("owner_permissions", entries);
        data.put("owner_names", ownerNames);
        data.put("owner_team_permissions", permissionSources);
        var myGrants = new CompoundTag();
        PlayerGroupStore.get(player.server).grantsByOwner(player.getUUID())
            .forEach((recipient, permission) -> myGrants.putInt(recipient.toString(), permission));
        data.put("my_grants", myGrants);
        var grantNames = new CompoundTag();
        var cache = player.server.getProfileCache();
        if (cache != null) for (String id : myGrants.getAllKeys()) {
            try {
                cache.get(UUID.fromString(id)).ifPresent(profile -> grantNames.putString(id, profile.getName()));
            } catch (IllegalArgumentException ignored) {
            }
        }
        data.put("my_grant_names", grantNames);
        data.putBoolean("team_integration_active", permissions.teamIntegrationActive());
        data.putBoolean("my_team_access_enabled", PlayerGroupStore.get(player.server).teamAccessEnabled(player.getUUID()));
        return data;
    }

    private record ClientProjectionKey(UUID playerId) {
    }

    private record HierarchyProjectionKey(UUID playerId) {
    }
}
