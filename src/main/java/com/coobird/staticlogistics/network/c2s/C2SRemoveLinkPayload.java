package com.coobird.staticlogistics.network.c2s;

import com.coobird.staticlogistics.Staticlogistics;
import com.coobird.staticlogistics.core.StaticLink;
import com.coobird.staticlogistics.network.s2c.S2CRemoveLinksBulkPayload;
import com.coobird.staticlogistics.storage.LinkManager;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

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
            if (player.level() instanceof ServerLevel serverLevel) {
                UUID targetId = payload.link().linkId();
                LinkManager.get(serverLevel).removeLinksBulk(
                    Collections.singletonList(payload.link()),
                    serverLevel,
                    player
                );

                PacketDistributor.sendToPlayersTrackingChunk(
                    serverLevel,
                    new ChunkPos(payload.link().sourcePos()),
                    new S2CRemoveLinksBulkPayload(List.of(targetId))
                );
            }
        });
    }
}