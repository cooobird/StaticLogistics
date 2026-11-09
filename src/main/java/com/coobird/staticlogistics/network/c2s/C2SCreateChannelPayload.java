package com.coobird.staticlogistics.network.c2s;

import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.content.event.PlayerEvents;
import com.coobird.staticlogistics.content.menu.LinkConfiguratorMenu;
import com.coobird.staticlogistics.logistics.group.PermissionService;
import com.coobird.staticlogistics.logistics.node.ConnectionKey;
import com.coobird.staticlogistics.logistics.node.ContainerChannelService;
import com.coobird.staticlogistics.network.ServerPacketRateLimiter;
import com.coobird.staticlogistics.network.s2c.S2CChannelCreatedPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.mesdag.portlib.network.IPortPacket;
import org.mesdag.portlib.network.PortPacketDistributor;
import org.mesdag.portlib.network.PortRegistryFriendlyByteBuf;
import org.mesdag.portlib.network.codec.PortByteBufCodecs;
import org.mesdag.portlib.network.codec.PortStreamCodec;

/// 只为当前容器新增通道，使用已存在的容器连接；不复制远端通道和目录项。
public record C2SCreateChannelPayload(ConnectionKey connection, int menuId, long requestId) implements IPortPacket.C2S {
    public static final ResourceLocation ID = StaticLogistics.asResource("create_channel");
    public static final PortStreamCodec<PortRegistryFriendlyByteBuf, C2SCreateChannelPayload> STREAM_CODEC =
        PortStreamCodec.composite(ConnectionKey.STREAM_CODEC, C2SCreateChannelPayload::connection,
            PortByteBufCodecs.VAR_INT, C2SCreateChannelPayload::menuId,
            PortByteBufCodecs.VAR_LONG, C2SCreateChannelPayload::requestId, C2SCreateChannelPayload::new);

    @Override
    public ResourceLocation identifier() {
        return ID;
    }

    @Override
    public void work(ServerPlayer packetPlayer) {
        C2SCreateChannelPayload payload = this;
        Player context = packetPlayer;

        if (!(context instanceof ServerPlayer player) || !(player.containerMenu instanceof LinkConfiguratorMenu menu))
            return;
        if (menu.containerId != payload.menuId()) return;
        boolean accepted = false;
        try {
            if (!menu.hasTarget() || !payload.connection.groupKey().equals(menu.getRemoteGroupKey())
                || !ServerPacketRateLimiter.allow(player, ServerPacketRateLimiter.Action.FACE_CONFIGURATION)) return;
            var key = payload.connection;
            var created = ContainerChannelService.create(player, key, menu.getTargetNode());
            if (created == null) return;
            accepted = true;
            for (ServerPlayer recipient : player.server.getPlayerList().getPlayers())
                if (PermissionService.getInstance().canAccess(key.groupKey().ownerId(), recipient)) {
                    PlayerEvents.requestClientStateRefresh(recipient);
                }
            PortPacketDistributor.sendToPlayer(player, new S2CChannelCreatedPayload(menu.containerId, payload.requestId(), key, created));
        } finally {
            menu.acknowledgeConfiguration(payload.requestId(), accepted);
        }

    }

}
