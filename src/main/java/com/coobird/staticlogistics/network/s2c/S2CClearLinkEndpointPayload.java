package com.coobird.staticlogistics.network.s2c;

import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.content.menu.LinkConfiguratorMenu;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 服务端确认当前连接配置器不再绑定任何节点。
 */
public record S2CClearLinkEndpointPayload() implements CustomPacketPayload {
    public static final Type<S2CClearLinkEndpointPayload> TYPE =
        new Type<>(StaticLogistics.asResource("clear_link_endpoint"));
    public static final StreamCodec<RegistryFriendlyByteBuf,
        S2CClearLinkEndpointPayload> STREAM_CODEC =
        StreamCodec.unit(new S2CClearLinkEndpointPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(
        S2CClearLinkEndpointPayload payload,
        IPayloadContext context
    ) {
        context.enqueueWork(() -> {
            if (context.player().containerMenu
                instanceof LinkConfiguratorMenu menu) {
                menu.clearTarget();
            }
        });
    }
}
