package com.coobird.staticlogistics.network.c2s;

import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.content.item.LinkConfiguratorSelection;
import com.coobird.staticlogistics.content.menu.LinkConfiguratorMenu;
import com.coobird.staticlogistics.logistics.group.ConnectionCommandService;
import com.coobird.staticlogistics.logistics.node.ConnectionKey;
import com.coobird.staticlogistics.network.s2c.S2CClearLinkEndpointPayload;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 请求删除一条连接及删除后真正孤立的端点。
 */
public record C2SDeleteConnectionPayload(ConnectionKey connection) implements CustomPacketPayload {
    public static final Type<C2SDeleteConnectionPayload> TYPE =
        new Type<>(StaticLogistics.asResource("delete_connection"));

    public static final StreamCodec<RegistryFriendlyByteBuf, C2SDeleteConnectionPayload> STREAM_CODEC =
        StreamCodec.composite(
            ConnectionKey.STREAM_CODEC, C2SDeleteConnectionPayload::connection,
            C2SDeleteConnectionPayload::new
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(C2SDeleteConnectionPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            if (!new ConnectionCommandService(player.server)
                .delete(player, payload.connection())) return;
            LinkConfiguratorSelection.clearConnectionIfSelected(
                player, payload.connection());
            if (player.containerMenu instanceof LinkConfiguratorMenu menu
                && menu.hasTarget()
                && payload.connection().groupKey().equals(menu.getRemoteGroupKey())
                && (menu.getTargetNode().equals(payload.connection().first())
                || menu.getTargetNode().equals(payload.connection().second()))) {
                menu.clearTarget();
                PacketDistributor.sendToPlayer(player, new S2CClearLinkEndpointPayload());
                menu.broadcastFullState();
            }
        });
    }
}
