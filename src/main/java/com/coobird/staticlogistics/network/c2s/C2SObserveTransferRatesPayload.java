package com.coobird.staticlogistics.network.c2s;

import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.content.menu.LinkConfiguratorMenu;
import com.coobird.staticlogistics.logistics.node.ConnectionKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.mesdag.portlib.network.IPortPacket;
import org.mesdag.portlib.network.PortRegistryFriendlyByteBuf;
import org.mesdag.portlib.network.codec.PortByteBufCodecs;
import org.mesdag.portlib.network.codec.PortStreamCodec;

public record C2SObserveTransferRatesPayload(int menuId, LogisticsNode node, ConnectionKey connection,
                                             boolean enabled) implements IPortPacket.C2S {
    public static final ResourceLocation ID = StaticLogistics.asResource("observe_transfer_rates");
    public static final PortStreamCodec<PortRegistryFriendlyByteBuf, C2SObserveTransferRatesPayload> STREAM_CODEC = PortStreamCodec.composite(
        PortByteBufCodecs.VAR_INT, C2SObserveTransferRatesPayload::menuId,
        LogisticsNode.STREAM_CODEC, C2SObserveTransferRatesPayload::node,
        ConnectionKey.STREAM_CODEC, C2SObserveTransferRatesPayload::connection,
        PortByteBufCodecs.BOOL, C2SObserveTransferRatesPayload::enabled, C2SObserveTransferRatesPayload::new);

    @Override
    public ResourceLocation identifier() {
        return ID;
    }

    @Override
    public void work(ServerPlayer packetPlayer) {
        C2SObserveTransferRatesPayload payload = this;
        Player context = packetPlayer;

        if (context instanceof ServerPlayer player
            && player.containerMenu instanceof LinkConfiguratorMenu menu
            && menu.containerId == payload.menuId() && menu.hasTarget()
            && payload.node().equals(menu.getTargetNode())) {
            menu.observeTransferRates(payload.connection(), payload.enabled());
        }

    }
}
