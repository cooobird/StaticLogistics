package com.coobird.staticlogistics.network.c2s;

import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.api.group.GroupConstraints;
import com.coobird.staticlogistics.logistics.group.ConnectionCommandService;
import com.coobird.staticlogistics.logistics.node.ConnectionKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.mesdag.portlib.network.IPortPacket;
import org.mesdag.portlib.network.PortRegistryFriendlyByteBuf;
import org.mesdag.portlib.network.codec.PortStreamCodec;

/**
 * 重命名一条稳定连接。
 */
public record C2SRenameConnectionPayload(ConnectionKey connection, String displayName)
    implements IPortPacket.C2S {
    public static final ResourceLocation ID = StaticLogistics.asResource("rename_connection");
    public static final PortStreamCodec<PortRegistryFriendlyByteBuf, C2SRenameConnectionPayload> STREAM_CODEC =
        new PortStreamCodec<>() {
            @Override
            public C2SRenameConnectionPayload decode(PortRegistryFriendlyByteBuf buffer) {
                return new C2SRenameConnectionPayload(
                    ConnectionKey.STREAM_CODEC.decode(buffer),
                    buffer.readUtf(GroupConstraints.MAX_CONNECTION_NAME_LENGTH));
            }

            @Override
            public void encode(PortRegistryFriendlyByteBuf buffer, C2SRenameConnectionPayload value) {
                ConnectionKey.STREAM_CODEC.encode(buffer, value.connection());
                buffer.writeUtf(value.displayName(), GroupConstraints.MAX_CONNECTION_NAME_LENGTH);
            }
        };

    @Override
    public ResourceLocation identifier() {
        return ID;
    }

    @Override
    public void work(ServerPlayer player) {
        new ConnectionCommandService(player.server).rename(player, connection(), displayName());
    }
}
