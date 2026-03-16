package com.coobird.staticlogistics.network.s2c;

import com.coobird.staticlogistics.Staticlogistics;
import com.coobird.staticlogistics.client.ClientLinkCache;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;
import java.util.UUID;

public record S2CRemoveLinksBulkPayload(List<UUID> linkIds) implements CustomPacketPayload {
    public static final Type<S2CRemoveLinksBulkPayload> TYPE = new Type<>(Staticlogistics.asResource("remove_links_bulk_s2c"));
    public static final StreamCodec<RegistryFriendlyByteBuf, S2CRemoveLinksBulkPayload> STREAM_CODEC = StreamCodec.composite(
        UUIDUtil.STREAM_CODEC.apply(ByteBufCodecs.list()), S2CRemoveLinksBulkPayload::linkIds, S2CRemoveLinksBulkPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(final S2CRemoveLinksBulkPayload p, final IPayloadContext c) {
        c.enqueueWork(() -> p.linkIds().forEach(ClientLinkCache::removeLinkById));
    }
}