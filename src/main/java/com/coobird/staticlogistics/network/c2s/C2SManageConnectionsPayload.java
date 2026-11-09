package com.coobird.staticlogistics.network.c2s;

import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.api.group.GroupConstraints;
import com.coobird.staticlogistics.content.item.LinkConfiguratorSelection;
import com.coobird.staticlogistics.content.menu.LinkConfiguratorMenu;
import com.coobird.staticlogistics.logistics.group.ConnectionCommandService;
import com.coobird.staticlogistics.logistics.node.ConnectionKey;
import com.coobird.staticlogistics.network.ServerPacketRateLimiter;
import com.coobird.staticlogistics.network.s2c.S2CClearLinkEndpointPayload;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

/// 单条和多条连接共用管理命令，整批提交，绑定发起请求的配置窗口。
public record C2SManageConnectionsPayload(int menuId, List<ConnectionKey> connections, String operation,
                                          String value) implements CustomPacketPayload {
    public static final Type<C2SManageConnectionsPayload> TYPE = new Type<>(StaticLogistics.asResource("manage_connections"));

    public C2SManageConnectionsPayload {
        connections = List.copyOf(connections);
        if (connections.isEmpty() || connections.size() > GroupConstraints.MAX_HIERARCHY_LINKS)
            throw new IllegalArgumentException("Invalid connection count");
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, C2SManageConnectionsPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public C2SManageConnectionsPayload decode(RegistryFriendlyByteBuf buffer) {
            int menuId = buffer.readVarInt(), size = buffer.readVarInt();
            if (size < 1 || size > GroupConstraints.MAX_HIERARCHY_LINKS)
                throw new DecoderException("Invalid connection count");
            List<ConnectionKey> keys = new ArrayList<>();
            for (int i = 0; i < size; i++) keys.add(ConnectionKey.STREAM_CODEC.decode(buffer));
            return new C2SManageConnectionsPayload(menuId, keys, buffer.readUtf(16), buffer.readUtf(48));
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buffer, C2SManageConnectionsPayload payload) {
            buffer.writeVarInt(payload.menuId());
            buffer.writeVarInt(payload.connections().size());
            payload.connections().forEach(key -> ConnectionKey.STREAM_CODEC.encode(buffer, key));
            buffer.writeUtf(payload.operation(), 16);
            buffer.writeUtf(payload.value(), 48);
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(C2SManageConnectionsPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player) || !(player.containerMenu instanceof LinkConfiguratorMenu menu)
                || menu.containerId != payload.menuId()
                || !ServerPacketRateLimiter.allow(player,
                ServerPacketRateLimiter.Action.CONNECTION_MANAGEMENT,
                payload.connections().size())) return;
            var service = new ConnectionCommandService(player.server);
            boolean changed = switch (payload.operation()) {
                case "rename" -> service.renameBatch(player, payload.connections(), payload.value());
                case "move" -> service.moveBatch(player, payload.connections(), payload.value());
                case "delete" -> service.deleteBatch(player, payload.connections());
                default -> false;
            };
            if (!changed) {
                player.displayClientMessage(Component.translatable("gui.staticlogistics.directory.manage_failed"), false);
                return;
            }
            if (payload.operation().equals("delete")) {
                payload.connections().forEach(key -> LinkConfiguratorSelection.clearConnectionIfSelected(player, key));
                if (menu.hasTarget() && payload.connections().stream().anyMatch(key -> key.groupKey().equals(menu.getRemoteGroupKey()) && key.contains(menu.getTargetNode().gPos()))) {
                    menu.clearTarget();
                    PacketDistributor.sendToPlayer(player, new S2CClearLinkEndpointPayload(menu.containerId));
                    menu.broadcastFullState();
                }
            }
        });
    }
}
