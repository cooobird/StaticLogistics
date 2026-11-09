package com.coobird.staticlogistics.network.s2c;

import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.client.gui.screen.LinkConfiguratorScreen;
import com.coobird.staticlogistics.logistics.node.ConnectionKey;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import org.mesdag.portlib.network.IPortPacket;
import org.mesdag.portlib.network.PortRegistryFriendlyByteBuf;
import org.mesdag.portlib.network.codec.PortByteBufCodecs;
import org.mesdag.portlib.network.codec.PortStreamCodec;

public record S2CChannelCreatedPayload(int menuId, long requestId, ConnectionKey connection,
                                       LogisticsNode node) implements IPortPacket.S2C {
    public static final ResourceLocation ID = StaticLogistics.asResource("channel_created");
    public static final PortStreamCodec<PortRegistryFriendlyByteBuf, S2CChannelCreatedPayload> STREAM_CODEC = PortStreamCodec.composite(
        PortByteBufCodecs.VAR_INT, S2CChannelCreatedPayload::menuId,
        PortByteBufCodecs.VAR_LONG, S2CChannelCreatedPayload::requestId,
        ConnectionKey.STREAM_CODEC, S2CChannelCreatedPayload::connection, LogisticsNode.STREAM_CODEC, S2CChannelCreatedPayload::node, S2CChannelCreatedPayload::new);

    @Override
    public ResourceLocation identifier() {
        return ID;
    }

    @Override
    public void work(Player packetPlayer) {
        S2CChannelCreatedPayload payload = this;
        Player context = packetPlayer;

        if (Minecraft.getInstance().screen instanceof LinkConfiguratorScreen screen && screen.getMenu().containerId == payload.menuId)
            screen.acceptCreatedChannel(payload.requestId, payload.connection, payload.node);

    }
}
