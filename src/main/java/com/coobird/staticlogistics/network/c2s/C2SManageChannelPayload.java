package com.coobird.staticlogistics.network.c2s;

import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.api.group.GroupConstraints;
import com.coobird.staticlogistics.content.event.PlayerEvents;
import com.coobird.staticlogistics.content.menu.LinkConfiguratorMenu;
import com.coobird.staticlogistics.logistics.group.PermissionService;
import com.coobird.staticlogistics.logistics.node.ConnectionKey;
import com.coobird.staticlogistics.logistics.node.ContainerChannelService;
import com.coobird.staticlogistics.logistics.node.NodeMutationService;
import com.coobird.staticlogistics.network.ServerPacketRateLimiter;
import com.coobird.staticlogistics.network.s2c.S2CChannelCreatedPayload;
import com.coobird.staticlogistics.network.s2c.S2CSelectLinkEndpointPayload;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/// 通道重命名、删除共用入口；请求绑定窗口和连接面，不能操作其他容器。
public record C2SManageChannelPayload(int menuId, ConnectionKey connection, LogisticsNode channel, String operation,
                                      String name, long requestId) implements CustomPacketPayload {
    public static final Type<C2SManageChannelPayload> TYPE = new Type<>(StaticLogistics.asResource("manage_channel"));
    public static final StreamCodec<RegistryFriendlyByteBuf, C2SManageChannelPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public C2SManageChannelPayload decode(RegistryFriendlyByteBuf buffer) {
            return new C2SManageChannelPayload(buffer.readVarInt(), ConnectionKey.STREAM_CODEC.decode(buffer),
                LogisticsNode.STREAM_CODEC.decode(buffer), buffer.readUtf(16), buffer.readUtf(GroupConstraints.MAX_CONNECTION_NAME_LENGTH), buffer.readVarLong());
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buffer, C2SManageChannelPayload value) {
            buffer.writeVarInt(value.menuId());
            ConnectionKey.STREAM_CODEC.encode(buffer, value.connection());
            LogisticsNode.STREAM_CODEC.encode(buffer, value.channel());
            buffer.writeUtf(value.operation(), 16);
            buffer.writeUtf(value.name(), GroupConstraints.MAX_CONNECTION_NAME_LENGTH);
            buffer.writeVarLong(value.requestId());
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(C2SManageChannelPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player) || !(player.containerMenu instanceof LinkConfiguratorMenu menu)
                || menu.containerId != payload.menuId()) return;
            boolean accepted = false;
            try {
                if (!menu.hasTarget() || !payload.connection().groupKey().equals(menu.getRemoteGroupKey())
                    || !menu.getTargetNode().gPos().equals(payload.channel().gPos()) || menu.getFace() != payload.channel().face()
                    || !ServerPacketRateLimiter.allow(player, ServerPacketRateLimiter.Action.FACE_CONFIGURATION))
                    return;
                if (payload.operation().equals("rename")) {
                    if (!ContainerChannelService.rename(player, payload.connection(), payload.channel(), payload.name()))
                        return;
                } else if (payload.operation().equals("delete")) {
                    var replacement = ContainerChannelService.delete(player, payload.connection(), payload.channel());
                    if (replacement == null) return;
                    if (menu.getTargetNode().equals(payload.channel())) {
                        var resolved = new NodeMutationService().resolveRemote(player, replacement, payload.connection().groupKey());
                        if (resolved == null) throw new IllegalStateException("Channel replacement is not available");
                        boolean input = resolved.config().isGlobalInputEnabled() && !resolved.config().isGlobalOutputEnabled();
                        menu.selectTarget(resolved, payload.connection().groupKey(), input);
                        PacketDistributor.sendToPlayer(player, new S2CSelectLinkEndpointPayload(menu.containerId,
                            payload.connection().groupKey(), replacement, input, resolved.config().getSelectedTypeIds(), payload.requestId()));
                        PacketDistributor.sendToPlayer(player, new S2CChannelCreatedPayload(menu.containerId, payload.requestId(), payload.connection(), replacement));
                    }
                } else return;
                accepted = true;
                for (ServerPlayer recipient : player.server.getPlayerList().getPlayers())
                    if (PermissionService.getInstance().canAccess(
                        payload.connection().groupKey().ownerId(), recipient)) {
                        PlayerEvents.requestClientStateRefresh(recipient);
                    }
            } finally {
                menu.acknowledgeConfiguration(payload.requestId(), accepted);
            }
        });
    }
}
