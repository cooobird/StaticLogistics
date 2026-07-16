package com.coobird.staticlogistics.content.event;

import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.logistics.group.PlayerGroupStore;
import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.api.group.GroupRef;
import com.coobird.staticlogistics.logistics.group.GroupService;
import com.coobird.staticlogistics.logistics.group.PermissionService;
import com.coobird.staticlogistics.logistics.node.FaceTopology;
import com.coobird.staticlogistics.logistics.node.ScopedTopologyLink;
import com.coobird.staticlogistics.logistics.util.LogisticsConstants;
import com.coobird.staticlogistics.network.s2c.S2CAccessSnapshotPayload;
import com.coobird.staticlogistics.logistics.node.LinkManager;
import com.coobird.staticlogistics.logistics.node.FaceAddress;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@EventBusSubscriber(modid = StaticLogistics.MODID)
public class PlayerEvents {
    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            refreshPlayerProfile(sp);
            refreshClientState(sp);
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
            for (FaceAddress key : mgr.getAllConfigKeys()) {
                var cfg = mgr.getFaceConfig(key);
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

    /** 以新的权限作用域替换客户端旧投影，随后按固定顺序发送完整权威状态。 */
    public static void refreshClientState(ServerPlayer player) {
        List<FaceTopology> faces = new ArrayList<>();
        Set<ScopedTopologyLink> linkSet = new LinkedHashSet<>();
        for (ServerLevel level : player.server.getAllLevels()) {
            LinkManager mgr = LinkManager.get(level);
            for (FaceAddress key : mgr.getAllConfigKeys()) {
                var config = mgr.getFaceConfig(key);
                if (config == null || config.isDefault()
                    || !GroupService.canAccess(config.faceConfig.getOwner(), player)) continue;
                LogisticsNode source = mgr.createNodeFromKey(key);
                faces.add(FaceTopology.from(source, config));
                config.getLinkedNodesByGroup().forEach((groupKey, targets) ->
                    targets.forEach(target -> linkSet.add(
                        new ScopedTopologyLink(groupKey, source, target))));
            }
        }
        List<ScopedTopologyLink> links = List.copyOf(linkSet);

        Set<UUID> owners = PermissionService.getInstance()
            .accessibleDirectoryOwners(player.getUUID());
        PlayerGroupStore store = PlayerGroupStore.get(player.server);
        Map<com.coobird.staticlogistics.api.group.GroupKey, GroupRef> directory = new LinkedHashMap<>();
        for (UUID owner : owners) {
            store.getGroupRefs(owner).forEach(group -> directory.put(group.key(), group));
        }

        List<GroupRef> groups = List.copyOf(directory.values());
        S2CAccessSnapshotPayload.pages(faces, links, groups)
            .forEach(payload -> PacketDistributor.sendToPlayer(player, payload));
    }
}
