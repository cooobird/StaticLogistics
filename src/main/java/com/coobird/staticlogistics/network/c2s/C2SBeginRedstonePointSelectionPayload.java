package com.coobird.staticlogistics.network.c2s;

import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.logistics.node.ConnectionKey;
import com.coobird.staticlogistics.logistics.redstone.RedstonePointSelectionSession;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.EncoderException;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.mesdag.portlib.network.IPortPacket;
import org.mesdag.portlib.network.PortRegistryFriendlyByteBuf;
import org.mesdag.portlib.network.codec.PortStreamCodec;

import java.util.ArrayList;
import java.util.List;

/**
 * 关闭配置界面后进入红石检测点选择状态。
 */
public record C2SBeginRedstonePointSelectionPayload(List<ConnectionKey> connections)
    implements IPortPacket.C2S {
    public static final ResourceLocation ID = StaticLogistics.asResource("begin_redstone_point_selection");
    public static final PortStreamCodec<PortRegistryFriendlyByteBuf,
        C2SBeginRedstonePointSelectionPayload> STREAM_CODEC = new PortStreamCodec<>() {
        @Override
        public C2SBeginRedstonePointSelectionPayload decode(PortRegistryFriendlyByteBuf buffer) {
            int size = buffer.readVarInt();
            if (size < 1 || size > RedstonePointSelectionSession.MAX_CONNECTIONS) {
                throw new DecoderException("Invalid redstone connection selection size: " + size);
            }
            List<ConnectionKey> connections = new ArrayList<>(size);
            for (int index = 0; index < size; index++) {
                connections.add(ConnectionKey.STREAM_CODEC.decode(buffer));
            }
            return new C2SBeginRedstonePointSelectionPayload(connections);
        }

        @Override
        public void encode(PortRegistryFriendlyByteBuf buffer,
                           C2SBeginRedstonePointSelectionPayload payload) {
            int size = payload.connections().size();
            if (size < 1 || size > RedstonePointSelectionSession.MAX_CONNECTIONS) {
                throw new EncoderException("Invalid redstone connection selection size: " + size);
            }
            buffer.writeVarInt(size);
            payload.connections().forEach(connection ->
                ConnectionKey.STREAM_CODEC.encode(buffer, connection));
        }
    };

    public C2SBeginRedstonePointSelectionPayload {
        connections = List.copyOf(connections);
    }

    @Override
    public ResourceLocation identifier() {
        return ID;
    }

    @Override
    public void work(ServerPlayer player) {
        if (RedstonePointSelectionSession.begin(player, connections())) {
            player.displayClientMessage(Component.translatable(
                "message.staticlogistics.redstone.select_point",
                connections().stream().distinct().count()).withStyle(ChatFormatting.AQUA), true);
        } else {
            player.displayClientMessage(Component.translatable(
                    "message.staticlogistics.redstone.selection_failed")
                .withStyle(ChatFormatting.RED), true);
        }
    }
}
