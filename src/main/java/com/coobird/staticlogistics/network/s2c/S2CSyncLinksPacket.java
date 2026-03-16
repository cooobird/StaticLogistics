package com.coobird.staticlogistics.network.s2c;

import com.coobird.staticlogistics.Staticlogistics;
import com.coobird.staticlogistics.client.ClientLinkCache;
import com.coobird.staticlogistics.client.gui.LinkConfiguratorScreen;
import com.coobird.staticlogistics.core.StaticLink;
import net.minecraft.client.Minecraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;

public record S2CSyncLinksPacket(List<StaticLink> links, boolean isFullSync) implements CustomPacketPayload {
    public static final Type<S2CSyncLinksPacket> TYPE = new Type<>(Staticlogistics.asResource("sync_links"));

    public static final StreamCodec<RegistryFriendlyByteBuf, S2CSyncLinksPacket> STREAM_CODEC = StreamCodec.composite(
        StaticLink.STREAM_CODEC.apply(ByteBufCodecs.list()), S2CSyncLinksPacket::links,
        ByteBufCodecs.BOOL, S2CSyncLinksPacket::isFullSync,
        S2CSyncLinksPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(final S2CSyncLinksPacket payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (payload.isFullSync()) ClientLinkCache.invalidate();
            payload.links().forEach(ClientLinkCache::addOrUpdateLink);

            Minecraft mc = Minecraft.getInstance();
            if (mc.screen instanceof LinkConfiguratorScreen screen) {
                screen.onDataSynced();
            }
        });
    }
}