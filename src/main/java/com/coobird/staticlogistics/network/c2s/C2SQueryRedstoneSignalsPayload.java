package com.coobird.staticlogistics.network.c2s;

import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.api.group.GroupKey;
import com.coobird.staticlogistics.logistics.group.PermissionService;
import com.coobird.staticlogistics.logistics.group.PlayerGroupStore;
import com.coobird.staticlogistics.logistics.node.NodeInteractionValidator;
import com.coobird.staticlogistics.network.ServerPacketRateLimiter;
import com.coobird.staticlogistics.network.s2c.S2CRedstoneSignalsPayload;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 请求一个物流分组内所有红石控制方案的最新信号，不重复传输绑定结构。
 */
public record C2SQueryRedstoneSignalsPayload(GroupKey groupKey)
    implements CustomPacketPayload {
    public static final Type<C2SQueryRedstoneSignalsPayload> TYPE =
        new Type<>(StaticLogistics.asResource("query_redstone_signals"));
    public static final StreamCodec<RegistryFriendlyByteBuf, C2SQueryRedstoneSignalsPayload>
        STREAM_CODEC = StreamCodec.composite(
        GroupKey.STREAM_CODEC, C2SQueryRedstoneSignalsPayload::groupKey,
        C2SQueryRedstoneSignalsPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(C2SQueryRedstoneSignalsPayload payload,
                              IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                || !NodeInteractionValidator.holdsConfigurator(player)
                || !ServerPacketRateLimiter.allow(
                player, ServerPacketRateLimiter.Action.REDSTONE_SIGNAL_QUERY)
                || !PermissionService.getInstance().canModify(
                payload.groupKey().ownerId(), player)
                || PlayerGroupStore.get(player.server).findGroup(payload.groupKey()) == null) {
                return;
            }
            S2CRedstoneSignalsPayload.sendGroup(player, payload.groupKey());
        });
    }
}
