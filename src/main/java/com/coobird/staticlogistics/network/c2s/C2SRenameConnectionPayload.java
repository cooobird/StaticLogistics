package com.coobird.staticlogistics.network.c2s;

import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.api.group.GroupConstraints;
import com.coobird.staticlogistics.logistics.group.ConnectionCommandService;
import com.coobird.staticlogistics.logistics.node.ConnectionKey;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 请求重命名一条稳定连接。
 */
public record C2SRenameConnectionPayload(ConnectionKey connection, String displayName) implements CustomPacketPayload {
    public static final Type<C2SRenameConnectionPayload> TYPE = new Type<>(StaticLogistics.asResource("rename_connection"));

    public static final StreamCodec<RegistryFriendlyByteBuf, C2SRenameConnectionPayload> STREAM_CODEC = StreamCodec.of(
        (buffer, payload) -> {
            ConnectionKey.STREAM_CODEC.encode(buffer, payload.connection());
            buffer.writeUtf(payload.displayName(), GroupConstraints.MAX_CONNECTION_NAME_LENGTH);
        },
        buffer -> new C2SRenameConnectionPayload(
            ConnectionKey.STREAM_CODEC.decode(buffer),
            buffer.readUtf(GroupConstraints.MAX_CONNECTION_NAME_LENGTH))
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(C2SRenameConnectionPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                new ConnectionCommandService(player.server)
                    .rename(player, payload.connection(), payload.displayName());
            }
        });
    }
}
