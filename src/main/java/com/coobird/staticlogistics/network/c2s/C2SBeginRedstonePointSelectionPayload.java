package com.coobird.staticlogistics.network.c2s;

import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.logistics.node.ConnectionKey;
import com.coobird.staticlogistics.logistics.redstone.RedstonePointSelectionSession;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.EncoderException;
import net.minecraft.ChatFormatting;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

/**
 * 请求关闭配置界面并进入红石检测点选择状态。
 */
public record C2SBeginRedstonePointSelectionPayload(List<ConnectionKey> connections)
    implements CustomPacketPayload {
    public static final Type<C2SBeginRedstonePointSelectionPayload> TYPE =
        new Type<>(StaticLogistics.asResource("begin_redstone_point_selection"));

    public static final StreamCodec<RegistryFriendlyByteBuf, C2SBeginRedstonePointSelectionPayload>
        STREAM_CODEC = new StreamCodec<>() {
        @Override
        public C2SBeginRedstonePointSelectionPayload decode(RegistryFriendlyByteBuf buffer) {
            int size = buffer.readVarInt();
            if (size < 1 || size > RedstonePointSelectionSession.MAX_CONNECTIONS) {
                throw new DecoderException("Invalid redstone connection selection size: " + size);
            }
            List<ConnectionKey> connections = new ArrayList<>(size);
            for (int index = 0; index < size; index++) {
                connections.add(ConnectionKey.STREAM_CODEC.decode(buffer));
            }
            return new C2SBeginRedstonePointSelectionPayload(List.copyOf(connections));
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buffer,
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
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(C2SBeginRedstonePointSelectionPayload payload,
                              IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            if (RedstonePointSelectionSession.begin(player, payload.connections())) {
                player.displayClientMessage(Component.translatable(
                    "message.staticlogistics.redstone.select_point",
                    payload.connections().stream().distinct().count()
                ).withStyle(ChatFormatting.AQUA), true);
            } else {
                player.displayClientMessage(Component.translatable(
                        "message.staticlogistics.redstone.selection_failed")
                    .withStyle(ChatFormatting.RED), true);
            }
        });
    }
}
