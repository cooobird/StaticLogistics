package com.coobird.staticlogistics.network.c2s;

import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.logistics.node.NodeMutationService;
import com.coobird.staticlogistics.api.LogisticsNode;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record C2SRemoveLinkPayload(LogisticsNode node) implements CustomPacketPayload {
    public static final Type<C2SRemoveLinkPayload> TYPE = new Type<>(StaticLogistics.asResource("remove_link"));

    public static final StreamCodec<RegistryFriendlyByteBuf, C2SRemoveLinkPayload> STREAM_CODEC = StreamCodec.composite(
        LogisticsNode.STREAM_CODEC, C2SRemoveLinkPayload::node,
        C2SRemoveLinkPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(final C2SRemoveLinkPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            var server = player.getServer();
            if (server == null) return;

            ServerLevel targetLevel = server.getLevel(payload.node().gPos().dimension());
            if (targetLevel == null) return;

            if (targetLevel != player.level()) return;
            NodeMutationService mutations = new NodeMutationService();
            NodeMutationService.ValidatedNode node = mutations.resolve(
                player, payload.node().gPos().pos(), payload.node().face());
            if (node != null) mutations.remove(node);
        });
    }
}
