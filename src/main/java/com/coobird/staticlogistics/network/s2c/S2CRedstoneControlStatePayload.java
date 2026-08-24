package com.coobird.staticlogistics.network.s2c;

import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.client.data.ClientRedstoneControlData;
import com.coobird.staticlogistics.logistics.node.ConnectionKey;
import com.coobird.staticlogistics.logistics.redstone.RedstoneControlBinding;
import com.coobird.staticlogistics.logistics.redstone.RedstoneControlMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.Nullable;

public record S2CRedstoneControlStatePayload(ConnectionKey connection, @Nullable RedstoneControlBinding binding,
                                             boolean powered, boolean allowed) implements CustomPacketPayload {
    public static final Type<S2CRedstoneControlStatePayload> TYPE =
        new Type<>(StaticLogistics.asResource("redstone_control_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf, S2CRedstoneControlStatePayload> STREAM_CODEC =
        new StreamCodec<>() {
            @Override
            public S2CRedstoneControlStatePayload decode(RegistryFriendlyByteBuf buffer) {
                ConnectionKey connection = ConnectionKey.STREAM_CODEC.decode(buffer);
                RedstoneControlBinding binding = null;
                if (buffer.readBoolean()) {
                    ResourceKey<Level> dimension = ResourceKey.create(
                        Registries.DIMENSION, buffer.readResourceLocation());
                    BlockPos position = buffer.readBlockPos();
                    int modeIndex = buffer.readVarInt();
                    RedstoneControlMode[] modes = RedstoneControlMode.values();
                    RedstoneControlMode mode = modeIndex >= 0 && modeIndex < modes.length
                        ? modes[modeIndex] : RedstoneControlMode.HIGH;
                    binding = new RedstoneControlBinding(GlobalPos.of(dimension, position), mode);
                }
                return new S2CRedstoneControlStatePayload(
                    connection, binding, buffer.readBoolean(), buffer.readBoolean());
            }

            @Override
            public void encode(RegistryFriendlyByteBuf buffer,
                               S2CRedstoneControlStatePayload payload) {
                ConnectionKey.STREAM_CODEC.encode(buffer, payload.connection());
                buffer.writeBoolean(payload.binding() != null);
                if (payload.binding() != null) {
                    buffer.writeResourceLocation(payload.binding().controller().dimension().location());
                    buffer.writeBlockPos(payload.binding().controller().pos());
                    buffer.writeVarInt(payload.binding().mode().ordinal());
                }
                buffer.writeBoolean(payload.powered());
                buffer.writeBoolean(payload.allowed());
            }
        };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(S2CRedstoneControlStatePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientRedstoneControlData.INSTANCE.accept(
            payload.connection(), payload.binding(), payload.powered(), payload.allowed()));
    }
}
