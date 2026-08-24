package com.coobird.staticlogistics.network.s2c;

import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.api.group.GroupKey;
import com.coobird.staticlogistics.client.data.ClientRedstoneControlData;
import com.coobird.staticlogistics.logistics.node.ConnectionKey;
import com.coobird.staticlogistics.logistics.redstone.RedstoneControlBinding;
import com.coobird.staticlogistics.logistics.redstone.RedstoneControlMode;
import com.coobird.staticlogistics.logistics.redstone.RedstoneControlStore;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.EncoderException;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

/**
 * 分页同步一个物流分组内全部已绑定的红石控制。
 */
public record S2CRedstoneControlGroupPayload(GroupKey groupKey, boolean reset,
                                             List<Entry> entries)
    implements CustomPacketPayload {
    public static final int MAX_PAGE_ENTRIES = 256;
    public static final Type<S2CRedstoneControlGroupPayload> TYPE =
        new Type<>(StaticLogistics.asResource("redstone_control_group"));
    public static final StreamCodec<RegistryFriendlyByteBuf, S2CRedstoneControlGroupPayload>
        STREAM_CODEC = new StreamCodec<>() {
        @Override
        public S2CRedstoneControlGroupPayload decode(RegistryFriendlyByteBuf buffer) {
            GroupKey groupKey = GroupKey.STREAM_CODEC.decode(buffer);
            boolean reset = buffer.readBoolean();
            int size = buffer.readVarInt();
            if (size < 0 || size > MAX_PAGE_ENTRIES) {
                throw new DecoderException("Invalid redstone group page size: " + size);
            }
            List<Entry> entries = new ArrayList<>(size);
            for (int index = 0; index < size; index++) entries.add(decodeEntry(buffer));
            return new S2CRedstoneControlGroupPayload(groupKey, reset, List.copyOf(entries));
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buffer,
                           S2CRedstoneControlGroupPayload payload) {
            if (payload.entries().size() > MAX_PAGE_ENTRIES) {
                throw new EncoderException("Redstone group page is too large");
            }
            GroupKey.STREAM_CODEC.encode(buffer, payload.groupKey());
            buffer.writeBoolean(payload.reset());
            buffer.writeVarInt(payload.entries().size());
            payload.entries().forEach(entry -> encodeEntry(buffer, entry));
        }
    };

    public S2CRedstoneControlGroupPayload {
        entries = List.copyOf(entries);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(S2CRedstoneControlGroupPayload payload,
                              IPayloadContext context) {
        context.enqueueWork(() -> ClientRedstoneControlData.INSTANCE.acceptGroupPage(
            payload.groupKey(), payload.reset(), payload.entries().stream()
                .map(entry -> new ClientRedstoneControlData.GroupEntry(
                    entry.connection(), new ClientRedstoneControlData.State(
                    entry.binding(), entry.powered(), entry.allowed())))
                .toList()));
    }

    public record Entry(ConnectionKey connection, RedstoneControlBinding binding,
                        boolean powered, boolean allowed) {
        public static Entry from(MinecraftServer server, ConnectionKey connection,
                                 RedstoneControlBinding binding) {
            RedstoneControlStore store = RedstoneControlStore.get(server);
            boolean powered = store.isPowered(server, binding.controller());
            return new Entry(connection, binding, powered,
                binding.mode().allows(powered));
        }
    }

    private static Entry decodeEntry(RegistryFriendlyByteBuf buffer) {
        ConnectionKey connection = ConnectionKey.STREAM_CODEC.decode(buffer);
        ResourceKey<Level> dimension = ResourceKey.create(
            Registries.DIMENSION, buffer.readResourceLocation());
        BlockPos position = buffer.readBlockPos();
        int modeIndex = buffer.readVarInt();
        RedstoneControlMode[] modes = RedstoneControlMode.values();
        RedstoneControlMode mode = modeIndex >= 0 && modeIndex < modes.length
            ? modes[modeIndex] : RedstoneControlMode.HIGH;
        return new Entry(connection,
            new RedstoneControlBinding(GlobalPos.of(dimension, position), mode),
            buffer.readBoolean(), buffer.readBoolean());
    }

    private static void encodeEntry(RegistryFriendlyByteBuf buffer, Entry entry) {
        ConnectionKey.STREAM_CODEC.encode(buffer, entry.connection());
        buffer.writeResourceLocation(entry.binding().controller().dimension().location());
        buffer.writeBlockPos(entry.binding().controller().pos());
        buffer.writeVarInt(entry.binding().mode().ordinal());
        buffer.writeBoolean(entry.powered());
        buffer.writeBoolean(entry.allowed());
    }
}
