package com.coobird.staticlogistics.network.c2s;

import com.coobird.staticlogistics.Staticlogistics;
import com.coobird.staticlogistics.core.StaticLink;
import com.coobird.staticlogistics.storage.LinkManager;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.Collections;

public record C2SRemoveLinkPayload(StaticLink link) implements CustomPacketPayload {
    public static final Type<C2SRemoveLinkPayload> TYPE = new Type<>(Staticlogistics.asResource("remove_link"));

    public static final StreamCodec<RegistryFriendlyByteBuf, C2SRemoveLinkPayload> STREAM_CODEC = StreamCodec.composite(
        StaticLink.STREAM_CODEC, C2SRemoveLinkPayload::link,
        C2SRemoveLinkPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(final C2SRemoveLinkPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            var player = context.player();
            var server = player.getServer();
            if (server == null) return;
            ServerLevel sourceLevel = server.getLevel(payload.link().sourceDimension());
            if (sourceLevel != null) {
                LinkManager manager = LinkManager.get(sourceLevel);
                if (manager == null) return;
                manager.removeLinksBulk(
                    Collections.singletonList(payload.link()),
                    sourceLevel,
                    player
                );
            }
        });
    }
}