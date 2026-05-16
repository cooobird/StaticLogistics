package com.coobird.staticlogistics.network.c2s;

import com.coobird.staticlogistics.Staticlogistics;
import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.storage.LinkManager;
import com.coobird.staticlogistics.storage.config.FaceConfigComposite;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record C2SRemoveLinkPayload(LogisticsNode node) implements CustomPacketPayload {
    public static final Type<C2SRemoveLinkPayload> TYPE = new Type<>(Staticlogistics.asResource("remove_link"));

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
            var player = context.player();
            var server = player.getServer();
            if (server == null) return;

            ServerLevel targetLevel = server.getLevel(payload.node().gPos().dimension());
            if (targetLevel == null) return;

            LinkManager manager = LinkManager.get(targetLevel);
            long key = payload.node().toKey();

            FaceConfigComposite config = manager.getFaceConfig(key);
            if (config != null) {
                if (!config.canPlayerModify(player)) return;
                manager.removeFaceConfig(key);
            }
        });
    }
}