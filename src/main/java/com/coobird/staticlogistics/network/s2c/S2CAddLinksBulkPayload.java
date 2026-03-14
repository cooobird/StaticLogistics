package com.coobird.staticlogistics.network.s2c;

import com.coobird.staticlogistics.Staticlogistics;
import com.coobird.staticlogistics.client.ClientLinkCache;
import com.coobird.staticlogistics.core.StaticLink;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;

public record S2CAddLinksBulkPayload(List<StaticLink> links) implements CustomPacketPayload {
    public static final Type<S2CAddLinksBulkPayload> TYPE = new Type<>(Staticlogistics.asResource("add_links_bulk_s2c"));

    public static final StreamCodec<RegistryFriendlyByteBuf, S2CAddLinksBulkPayload> STREAM_CODEC = StreamCodec.composite(
        StaticLink.STREAM_CODEC.apply(ByteBufCodecs.list()), S2CAddLinksBulkPayload::links,
        S2CAddLinksBulkPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(final S2CAddLinksBulkPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            payload.links().forEach(ClientLinkCache::addOrUpdateLink);
        });
    }
}