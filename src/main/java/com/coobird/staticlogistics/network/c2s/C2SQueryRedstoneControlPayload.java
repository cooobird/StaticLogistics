package com.coobird.staticlogistics.network.c2s;

import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.logistics.group.ConnectionCommandService;
import com.coobird.staticlogistics.logistics.node.ConnectionKey;
import com.coobird.staticlogistics.logistics.redstone.RedstoneControlBinding;
import com.coobird.staticlogistics.logistics.redstone.RedstoneControlStore;
import com.coobird.staticlogistics.network.s2c.S2CRedstoneControlStatePayload;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record C2SQueryRedstoneControlPayload(ConnectionKey connection)
    implements CustomPacketPayload {
    public static final Type<C2SQueryRedstoneControlPayload> TYPE = new Type<>(StaticLogistics.asResource("query_redstone_control"));

    public static final StreamCodec<RegistryFriendlyByteBuf, C2SQueryRedstoneControlPayload> STREAM_CODEC =
        StreamCodec.composite(ConnectionKey.STREAM_CODEC,
            C2SQueryRedstoneControlPayload::connection,
            C2SQueryRedstoneControlPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(C2SQueryRedstoneControlPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                || !new ConnectionCommandService(player.server)
                .isSelectable(player, payload.connection())) return;
            sendState(player, payload.connection());
        });
    }

    public static void sendState(ServerPlayer player, ConnectionKey connection) {
        RedstoneControlStore store = RedstoneControlStore.get(player.server);
        RedstoneControlBinding binding = store.getBinding(connection);
        boolean powered = binding != null && store.isPowered(player.server, binding.controller());
        boolean allowed = binding == null || binding.mode().allows(powered);
        PacketDistributor.sendToPlayer(player,
            new S2CRedstoneControlStatePayload(connection, binding, powered, allowed));
    }
}
