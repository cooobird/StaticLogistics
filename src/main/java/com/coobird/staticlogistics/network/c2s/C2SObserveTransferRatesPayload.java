package com.coobird.staticlogistics.network.c2s;

import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.content.menu.LinkConfiguratorMenu;
import com.coobird.staticlogistics.logistics.node.ConnectionKey;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record C2SObserveTransferRatesPayload(int menuId, LogisticsNode node, ConnectionKey connection,
                                             boolean enabled) implements CustomPacketPayload {
    public static final Type<C2SObserveTransferRatesPayload> TYPE = new Type<>(StaticLogistics.asResource("observe_transfer_rates"));
    public static final StreamCodec<RegistryFriendlyByteBuf, C2SObserveTransferRatesPayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT, C2SObserveTransferRatesPayload::menuId,
        LogisticsNode.STREAM_CODEC, C2SObserveTransferRatesPayload::node,
        ConnectionKey.STREAM_CODEC, C2SObserveTransferRatesPayload::connection,
        ByteBufCodecs.BOOL, C2SObserveTransferRatesPayload::enabled, C2SObserveTransferRatesPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(C2SObserveTransferRatesPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player
                && player.containerMenu instanceof LinkConfiguratorMenu menu
                && menu.containerId == payload.menuId() && menu.hasTarget()
                && payload.node().equals(menu.getTargetNode())) {
                menu.observeTransferRates(payload.connection(), payload.enabled());
            }
        });
    }
}
