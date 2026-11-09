package com.coobird.staticlogistics.network.s2c;

import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.client.gui.screen.LinkConfiguratorScreen;
import com.coobird.staticlogistics.logistics.node.ConnectionKey;
import net.minecraft.client.Minecraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record S2CChannelCreatedPayload(int menuId, long requestId, ConnectionKey connection,
                                       LogisticsNode node) implements CustomPacketPayload {
    public static final Type<S2CChannelCreatedPayload> TYPE = new Type<>(StaticLogistics.asResource("channel_created"));
    public static final StreamCodec<RegistryFriendlyByteBuf, S2CChannelCreatedPayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT, S2CChannelCreatedPayload::menuId,
        ByteBufCodecs.VAR_LONG, S2CChannelCreatedPayload::requestId,
        ConnectionKey.STREAM_CODEC, S2CChannelCreatedPayload::connection, LogisticsNode.STREAM_CODEC, S2CChannelCreatedPayload::node, S2CChannelCreatedPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(S2CChannelCreatedPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (Minecraft.getInstance().screen instanceof LinkConfiguratorScreen screen && screen.getMenu().containerId == payload.menuId)
                screen.acceptCreatedChannel(payload.requestId, payload.connection, payload.node);
        });
    }
}
