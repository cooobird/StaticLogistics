package com.coobird.staticlogistics.network.c2s;

import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.content.event.PlayerEvents;
import com.coobird.staticlogistics.network.ServerPacketRateLimiter;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/// 客户端分页投影超时后，请求服务端重新发送完整权威快照。
public record C2SRequestAccessSnapshotPayload() implements CustomPacketPayload {
    public static final Type<C2SRequestAccessSnapshotPayload> TYPE =
        new Type<>(StaticLogistics.asResource("request_access_snapshot"));
    public static final StreamCodec<RegistryFriendlyByteBuf, C2SRequestAccessSnapshotPayload> STREAM_CODEC =
        new StreamCodec<>() {
            @Override
            public C2SRequestAccessSnapshotPayload decode(RegistryFriendlyByteBuf buffer) {
                return new C2SRequestAccessSnapshotPayload();
            }

            @Override
            public void encode(RegistryFriendlyByteBuf buffer,
                               C2SRequestAccessSnapshotPayload payload) {
            }
        };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(C2SRequestAccessSnapshotPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                || !ServerPacketRateLimiter.allow(
                player, ServerPacketRateLimiter.Action.ACCESS_SNAPSHOT_RESYNC)) return;
            PlayerEvents.requestClientStateRefresh(player);
        });
    }
}
