package com.coobird.staticlogistics.network.c2s;

import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.content.menu.LinkConfiguratorMenu;
import com.coobird.staticlogistics.network.s2c.S2CClearLinkEndpointPayload;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 清除连接配置器当前节点目标，但保持容器菜单继续打开。
 */
public record C2SClearLinkEndpointPayload() implements CustomPacketPayload {
    public static final Type<C2SClearLinkEndpointPayload> TYPE =
        new Type<>(StaticLogistics.asResource("request_clear_link_endpoint"));
    public static final StreamCodec<RegistryFriendlyByteBuf,
        C2SClearLinkEndpointPayload> STREAM_CODEC =
        StreamCodec.unit(new C2SClearLinkEndpointPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(
        C2SClearLinkEndpointPayload payload,
        IPayloadContext context
    ) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                || !(player.containerMenu instanceof LinkConfiguratorMenu menu)) {
                return;
            }
            menu.clearTarget();
            PacketDistributor.sendToPlayer(
                player, new S2CClearLinkEndpointPayload());
            menu.broadcastChanges();
        });
    }
}
