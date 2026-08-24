package com.coobird.staticlogistics.network.c2s;

import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.content.menu.LinkConfiguratorMenu;
import com.coobird.staticlogistics.logistics.group.ConnectionCommandService;
import com.coobird.staticlogistics.logistics.node.ConnectionKey;
import com.coobird.staticlogistics.logistics.redstone.RedstoneControlStore;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 解除一条连接已有的红石控制；检测点绑定改由世界点选流程完成。
 */
public record C2SSetRedstoneControlPayload(ConnectionKey connection, boolean bind)
    implements CustomPacketPayload {
    public static final Type<C2SSetRedstoneControlPayload> TYPE = new Type<>(StaticLogistics.asResource("set_redstone_control"));

    public static final StreamCodec<RegistryFriendlyByteBuf, C2SSetRedstoneControlPayload> STREAM_CODEC =
        StreamCodec.composite(
            ConnectionKey.STREAM_CODEC, C2SSetRedstoneControlPayload::connection,
            net.minecraft.network.codec.ByteBufCodecs.BOOL, C2SSetRedstoneControlPayload::bind,
            C2SSetRedstoneControlPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(C2SSetRedstoneControlPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                || !(player.containerMenu instanceof LinkConfiguratorMenu)
                || !new ConnectionCommandService(player.server)
                .isSelectable(player, payload.connection())) return;

            RedstoneControlStore store = RedstoneControlStore.get(player.server);
            if (!payload.bind()) {
                store.unbind(payload.connection());
            }
            C2SQueryRedstoneGroupPayload.sendGroupToAuthorized(
                player, payload.connection().groupKey());
        });
    }
}
