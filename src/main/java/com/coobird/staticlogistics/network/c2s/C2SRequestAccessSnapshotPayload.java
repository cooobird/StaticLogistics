package com.coobird.staticlogistics.network.c2s;

import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.content.event.PlayerEvents;
import com.coobird.staticlogistics.network.ServerPacketRateLimiter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.mesdag.portlib.network.IPortPacket;
import org.mesdag.portlib.network.PortRegistryFriendlyByteBuf;
import org.mesdag.portlib.network.codec.PortStreamCodec;

/// 客户端分页投影超时后，请求服务端重新发送完整权威快照。
public record C2SRequestAccessSnapshotPayload() implements IPortPacket.C2S {
    public static final ResourceLocation ID = StaticLogistics.asResource("request_access_snapshot");
    public static final PortStreamCodec<PortRegistryFriendlyByteBuf, C2SRequestAccessSnapshotPayload> STREAM_CODEC =
        new PortStreamCodec<>() {
            @Override
            public C2SRequestAccessSnapshotPayload decode(PortRegistryFriendlyByteBuf buffer) {
                return new C2SRequestAccessSnapshotPayload();
            }

            @Override
            public void encode(PortRegistryFriendlyByteBuf buffer,
                               C2SRequestAccessSnapshotPayload payload) {
            }
        };

    @Override
    public ResourceLocation identifier() {
        return ID;
    }

    @Override
    public void work(ServerPlayer packetPlayer) {
        C2SRequestAccessSnapshotPayload payload = this;
        Player context = packetPlayer;

        if (!(context instanceof ServerPlayer player)
            || !ServerPacketRateLimiter.allow(
            player, ServerPacketRateLimiter.Action.ACCESS_SNAPSHOT_RESYNC)) return;
        PlayerEvents.requestClientStateRefresh(player);

    }
}
