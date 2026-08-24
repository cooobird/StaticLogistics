package com.coobird.staticlogistics.network.c2s;

import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.logistics.group.ConnectionCommandService;
import com.coobird.staticlogistics.logistics.node.ConnectionKey;
import com.coobird.staticlogistics.logistics.redstone.RedstoneControlBinding;
import com.coobird.staticlogistics.logistics.redstone.RedstoneControlStore;
import com.coobird.staticlogistics.network.SLNetwork;
import com.coobird.staticlogistics.network.s2c.S2CRedstoneControlStatePayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.mesdag.portlib.network.IPortPacket;
import org.mesdag.portlib.network.PortRegistryFriendlyByteBuf;
import org.mesdag.portlib.network.codec.PortStreamCodec;

/**
 * 查询一条连接当前的红石控制状态。
 */
public record C2SQueryRedstoneControlPayload(ConnectionKey connection)
    implements IPortPacket.C2S {
    public static final ResourceLocation ID = StaticLogistics.asResource("query_redstone_control");
    public static final PortStreamCodec<PortRegistryFriendlyByteBuf,
        C2SQueryRedstoneControlPayload> STREAM_CODEC = PortStreamCodec.composite(
        ConnectionKey.STREAM_CODEC, C2SQueryRedstoneControlPayload::connection,
        C2SQueryRedstoneControlPayload::new);

    @Override
    public ResourceLocation identifier() {
        return ID;
    }

    @Override
    public void work(ServerPlayer player) {
        if (!new ConnectionCommandService(player.server).isSelectable(player, connection())) return;
        sendState(player, connection());
    }

    public static void sendState(ServerPlayer player, ConnectionKey connection) {
        RedstoneControlStore store = RedstoneControlStore.get(player.server);
        RedstoneControlBinding binding = store.getBinding(connection);
        boolean powered = binding != null && store.isPowered(player.server, binding.controller());
        boolean allowed = binding == null || binding.mode().allows(powered);
        SLNetwork.HANDLER.sendToPlayer(player,
            new S2CRedstoneControlStatePayload(connection, binding, powered, allowed));
    }
}
