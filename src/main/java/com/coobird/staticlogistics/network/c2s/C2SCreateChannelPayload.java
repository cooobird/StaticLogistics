package com.coobird.staticlogistics.network.c2s;

import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.content.event.PlayerEvents;
import com.coobird.staticlogistics.content.menu.LinkConfiguratorMenu;
import com.coobird.staticlogistics.logistics.group.PermissionService;
import com.coobird.staticlogistics.logistics.node.ConnectionKey;
import com.coobird.staticlogistics.logistics.node.ContainerChannelService;
import com.coobird.staticlogistics.network.ServerPacketRateLimiter;
import com.coobird.staticlogistics.network.s2c.S2CChannelCreatedPayload;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/// 只为当前容器新增通道，使用已存在的容器连接；不复制远端通道和目录项。
public record C2SCreateChannelPayload(ConnectionKey connection, int menuId,
                                      long requestId) implements CustomPacketPayload {
    public static final Type<C2SCreateChannelPayload> TYPE = new Type<>(StaticLogistics.asResource("create_channel"));
    public static final StreamCodec<RegistryFriendlyByteBuf, C2SCreateChannelPayload> STREAM_CODEC =
        StreamCodec.composite(ConnectionKey.STREAM_CODEC, C2SCreateChannelPayload::connection,
            ByteBufCodecs.VAR_INT, C2SCreateChannelPayload::menuId,
            ByteBufCodecs.VAR_LONG, C2SCreateChannelPayload::requestId, C2SCreateChannelPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(C2SCreateChannelPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player) || !(player.containerMenu instanceof LinkConfiguratorMenu menu))
                return;
            if (menu.containerId != payload.menuId()) return;
            boolean accepted = false;
            try {
                if (!menu.hasTarget() || !payload.connection.groupKey().equals(menu.getRemoteGroupKey())
                    || !ServerPacketRateLimiter.allow(player, ServerPacketRateLimiter.Action.FACE_CONFIGURATION))
                    return;
                var key = payload.connection;
                var created = ContainerChannelService.create(player, key, menu.getTargetNode());
                if (created == null) return;
                accepted = true;
                for (ServerPlayer recipient : player.server.getPlayerList().getPlayers())
                    if (PermissionService.getInstance().canAccess(key.groupKey().ownerId(), recipient)) {
                        PlayerEvents.requestClientStateRefresh(recipient);
                    }
                PacketDistributor.sendToPlayer(player, new S2CChannelCreatedPayload(menu.containerId, payload.requestId(), key, created));
            } finally {
                menu.acknowledgeConfiguration(payload.requestId(), accepted);
            }
        });
    }

}
