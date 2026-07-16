package com.coobird.staticlogistics.network;

import com.coobird.staticlogistics.logistics.group.PermissionService;
import com.coobird.staticlogistics.network.s2c.S2CTopologyUpdatePayload;
import net.minecraft.server.level.ServerPlayer;
import org.mesdag.portlib.network.IPortPacket;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;

/**
 * 把已授权数据包发送给发起玩家所在团队的在线成员。
 */
public final class TeamPacketSync {
    private TeamPacketSync() {
    }

    public static void send(ServerPlayer player, IPortPacket.S2C payload) {
        boolean sentToPlayer = false;
        Set<UUID> recipients = PermissionService.getInstance().teamMembersOf(player.getUUID());
        for (UUID memberId : recipients) {
            ServerPlayer member = player.server.getPlayerList().getPlayer(memberId);
            if (member == null) continue;
            SLNetwork.HANDLER.sendToPlayer(member, payload);
            sentToPlayer |= member.getUUID().equals(player.getUUID());
        }
        if (!sentToPlayer) SLNetwork.HANDLER.sendToPlayer(player, payload);
    }

    /**
     * 将同一次拓扑变更的全部页面发送给相同的授权成员集合。
     */
    public static void sendTopology(
        ServerPlayer player,
        Collection<S2CTopologyUpdatePayload.FaceUpdate> updates
    ) {
        S2CTopologyUpdatePayload.pages(updates).forEach(payload -> send(player, payload));
    }
}
