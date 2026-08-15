package com.coobird.staticlogistics.network;

import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.api.group.GroupKey;
import com.coobird.staticlogistics.logistics.group.PermissionService;
import com.coobird.staticlogistics.logistics.group.PlayerGroupStore;
import com.coobird.staticlogistics.network.s2c.S2CTopologyUpdatePayload;
import net.minecraft.server.level.ServerPlayer;
import org.mesdag.portlib.network.IPortPacket;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 把已授权数据包发送给发起玩家所在团队的在线成员。
 */
public final class TeamPacketSync {
    private TeamPacketSync() {
    }

    public static void send(ServerPlayer player, UUID ownerId, IPortPacket.S2C payload) {
        boolean sentToPlayer = false;
        PermissionService permissions = PermissionService.getInstance();
        Set<UUID> recipients = PermissionService.getInstance().teamMembersOf(player.getUUID());
        for (UUID memberId : recipients) {
            ServerPlayer member = player.server.getPlayerList().getPlayer(memberId);
            if (!permissions.canAccess(ownerId, member)) continue;
            SLNetwork.HANDLER.sendToPlayer(member, payload);
            sentToPlayer |= member.getUUID().equals(player.getUUID());
        }
        if (!sentToPlayer && permissions.canAccess(ownerId, player)) {
            SLNetwork.HANDLER.sendToPlayer(player, payload);
        }
    }

    /**
     * 将同一次拓扑变更的全部页面发送给相同的授权成员集合。
     */
    public static void sendTopology(
        ServerPlayer player,
        UUID ownerId,
        Collection<S2CTopologyUpdatePayload.FaceUpdate> updates
    ) {
        PlayerGroupStore store = PlayerGroupStore.get(player.server);
        PermissionService permissions = PermissionService.getInstance();
        Set<UUID> recipients = permissions.teamMembersOf(player.getUUID());
        boolean sentToPlayer = false;
        for (UUID memberId : recipients) {
            ServerPlayer member = player.server.getPlayerList().getPlayer(memberId);
            if (!permissions.canAccess(ownerId, member)) continue;
            sendTopologyTo(member, sanitize(updates, member, permissions), store);
            sentToPlayer |= member.getUUID().equals(player.getUUID());
        }
        if (!sentToPlayer && permissions.canAccess(ownerId, player)) {
            sendTopologyTo(player, sanitize(updates, player, permissions), store);
        }
    }

    private static Collection<S2CTopologyUpdatePayload.FaceUpdate> sanitize(
        Collection<S2CTopologyUpdatePayload.FaceUpdate> updates,
        ServerPlayer recipient,
        PermissionService permissions
    ) {
        return updates.stream().map(update -> {
            Map<GroupKey, Set<LogisticsNode>> visible = new LinkedHashMap<>();
            update.linksByGroup().forEach((groupKey, targets) -> {
                if (permissions.canAccess(groupKey.ownerId(), recipient)) {
                    visible.put(groupKey, targets);
                }
            });
            return new S2CTopologyUpdatePayload.FaceUpdate(update.topology(), visible);
        }).collect(Collectors.toList());
    }

    private static void sendTopologyTo(
        ServerPlayer recipient,
        Collection<S2CTopologyUpdatePayload.FaceUpdate> updates,
        PlayerGroupStore store
    ) {
        S2CTopologyUpdatePayload.pages(updates, store::getConnectionName)
            .forEach(payload -> SLNetwork.HANDLER.sendToPlayer(recipient, payload));
    }
}
