package com.coobird.staticlogistics.content.event;

import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.api.group.GroupKey;
import com.coobird.staticlogistics.api.group.GroupRef;
import com.coobird.staticlogistics.logistics.blueprint.BlueprintUndoManager;
import com.coobird.staticlogistics.logistics.group.GroupService;
import com.coobird.staticlogistics.logistics.group.PermissionService;
import com.coobird.staticlogistics.logistics.group.PlayerGroupStore;
import com.coobird.staticlogistics.logistics.node.*;
import com.coobird.staticlogistics.network.SLNetwork;
import com.coobird.staticlogistics.network.s2c.S2CAccessSnapshotPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.*;

@Mod.EventBusSubscriber(modid = StaticLogistics.MODID)
public final class PlayerEvents {
    private PlayerEvents() {
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        refreshPlayerProfile(player);
        refreshClientState(player);
        for (ServerLevel level : player.server.getAllLevels()) {
            LinkManager.get(level).markOrphanScanNeeded();
        }
    }

    private static void refreshPlayerProfile(ServerPlayer player) {
        var profile = player.getGameProfile();
        UUID playerId = player.getUUID();
        for (ServerLevel level : player.server.getAllLevels()) {
            LinkManager manager = LinkManager.get(level);
            for (FaceAddress address : manager.getAllConfigKeys()) {
                var config = manager.getFaceConfig(address);
                if (config != null && playerId.equals(config.faceConfig.getOwner())) {
                    manager.refreshOwnerProfile(manager.createNodeFromKey(address), profile);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) refreshClientState(player);
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity().getServer() != null) {
            BlueprintUndoManager.get(event.getEntity().getServer()).clear(event.getEntity().getUUID());
        }
    }

    /**
     * 用新的权限作用域原子替换客户端旧投影。
     */
    public static void refreshClientState(ServerPlayer player) {
        List<FaceTopology> faces = new ArrayList<>();
        Set<ScopedTopologyLink> links = new LinkedHashSet<>();
        for (ServerLevel level : player.server.getAllLevels()) {
            LinkManager manager = LinkManager.get(level);
            for (FaceAddress address : manager.getAllConfigKeys()) {
                var config = manager.getFaceConfig(address);
                if (config == null || config.isDefault()
                    || !GroupService.canAccess(config.faceConfig.getOwner(), player)) continue;
                LogisticsNode source = address.toNode(level.dimension());
                faces.add(FaceTopology.from(level, source, config));
                config.getLinkedNodesByGroup().forEach((groupKey, targets) ->
                    targets.forEach(target -> {
                        ConnectionKey connection = new ConnectionKey(groupKey, source, target);
                        links.add(new ScopedTopologyLink(groupKey, source, target,
                            PlayerGroupStore.get(player.server).getConnectionName(connection)));
                    }));
            }
        }

        Set<UUID> owners = PermissionService.getInstance()
            .accessibleDirectoryOwners(player.getUUID());
        PlayerGroupStore store = PlayerGroupStore.get(player.server);
        Map<GroupKey, GroupRef> directory =
            new LinkedHashMap<>();
        for (UUID owner : owners) {
            store.getGroupRefs(owner).forEach(group -> directory.put(group.key(), group));
        }

        S2CAccessSnapshotPayload.pages(faces, List.copyOf(links),
                List.copyOf(directory.values()))
            .forEach(payload -> SLNetwork.HANDLER.sendToPlayer(player, payload));
    }
}
