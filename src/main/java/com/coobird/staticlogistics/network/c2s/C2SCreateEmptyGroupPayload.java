package com.coobird.staticlogistics.network.c2s;

import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.logic.GlobalLogisticsManager;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 客户端通知服务端创建了一个新的空分组
 */
public record C2SCreateEmptyGroupPayload(String groupId) implements CustomPacketPayload {
    public static final Type<C2SCreateEmptyGroupPayload> TYPE = new Type<>(StaticLogistics.asResource("create_empty_group"));

    public static final StreamCodec<RegistryFriendlyByteBuf, C2SCreateEmptyGroupPayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.STRING_UTF8, C2SCreateEmptyGroupPayload::groupId,
        C2SCreateEmptyGroupPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(final C2SCreateEmptyGroupPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            var player = context.player();
            String groupId = payload.groupId().trim();
            if (!groupId.isEmpty() && player.getServer() != null) {
                GlobalLogisticsManager.get(player.getServer()).addGroup(player.getUUID(), groupId);
            }
        });
    }
}
