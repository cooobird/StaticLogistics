package com.coobird.staticlogistics.network.c2s;

import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.content.menu.LinkConfiguratorMenu;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 在现有连接配置器菜单中切换输入或输出侧。
 */
public record C2SSelectLinkEndpointSidePayload(boolean inputSide)
    implements CustomPacketPayload {
    public static final Type<C2SSelectLinkEndpointSidePayload> TYPE =
        new Type<>(StaticLogistics.asResource("select_link_endpoint_side"));
    public static final StreamCodec<
        RegistryFriendlyByteBuf,
        C2SSelectLinkEndpointSidePayload
        > STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.BOOL,
        C2SSelectLinkEndpointSidePayload::inputSide,
        C2SSelectLinkEndpointSidePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(
        C2SSelectLinkEndpointSidePayload payload,
        IPayloadContext context
    ) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                || !(player.containerMenu instanceof LinkConfiguratorMenu menu)
                || !menu.hasTarget()
                || !menu.stillValid(player)) {
                return;
            }
            menu.selectVisibleSide(payload.inputSide());
            menu.broadcastChanges();
        });
    }
}
