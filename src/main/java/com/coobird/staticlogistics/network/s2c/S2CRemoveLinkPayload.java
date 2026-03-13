package com.coobird.staticlogistics.network.s2c;

import com.coobird.staticlogistics.Staticlogistics;
import com.coobird.staticlogistics.core.ClientLinkCache;
import com.coobird.staticlogistics.core.StaticLink;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record S2CRemoveLinkPayload(StaticLink link) implements CustomPacketPayload {
    public static final Type<S2CRemoveLinkPayload> TYPE = new Type<>(Staticlogistics.asResource("remove_link_s2c"));

    public static final StreamCodec<RegistryFriendlyByteBuf, S2CRemoveLinkPayload> STREAM_CODEC = StreamCodec.composite(
        StaticLink.STREAM_CODEC, S2CRemoveLinkPayload::link,
        S2CRemoveLinkPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(final S2CRemoveLinkPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            ClientLinkCache.removeLink(payload.link());
        });
    }
}