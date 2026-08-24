package com.coobird.staticlogistics.network.s2c;

import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.api.group.GroupKey;
import com.coobird.staticlogistics.client.data.ClientRedstoneControlData;
import com.coobird.staticlogistics.logistics.redstone.RedstoneControlBinding;
import com.coobird.staticlogistics.logistics.redstone.RedstoneControlStore;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.EncoderException;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

/**
 * 一个分组内红石控制方案的轻量运行态分页。
 */
public record S2CRedstoneSignalsPayload(GroupKey groupKey, List<Entry> entries)
    implements CustomPacketPayload {
    public static final int MAX_PAGE_ENTRIES = 256;
    public static final Type<S2CRedstoneSignalsPayload> TYPE =
        new Type<>(StaticLogistics.asResource("redstone_signals"));
    public static final StreamCodec<RegistryFriendlyByteBuf, S2CRedstoneSignalsPayload>
        STREAM_CODEC = new StreamCodec<>() {
        @Override
        public S2CRedstoneSignalsPayload decode(RegistryFriendlyByteBuf buffer) {
            GroupKey groupKey = GroupKey.STREAM_CODEC.decode(buffer);
            int size = buffer.readVarInt();
            if (size < 0 || size > MAX_PAGE_ENTRIES) {
                throw new DecoderException("Invalid redstone signal page size: " + size);
            }
            List<Entry> entries = new ArrayList<>(size);
            for (int index = 0; index < size; index++) {
                entries.add(new Entry(
                    RedstoneControlBinding.STREAM_CODEC.decode(buffer),
                    buffer.readBoolean(), buffer.readBoolean()));
            }
            return new S2CRedstoneSignalsPayload(groupKey, entries);
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buffer,
                           S2CRedstoneSignalsPayload payload) {
            if (payload.entries().size() > MAX_PAGE_ENTRIES) {
                throw new EncoderException("Redstone signal page is too large");
            }
            GroupKey.STREAM_CODEC.encode(buffer, payload.groupKey());
            buffer.writeVarInt(payload.entries().size());
            for (Entry entry : payload.entries()) {
                RedstoneControlBinding.STREAM_CODEC.encode(buffer, entry.binding());
                buffer.writeBoolean(entry.powered());
                buffer.writeBoolean(entry.allowed());
            }
        }
    };

    public S2CRedstoneSignalsPayload {
        entries = List.copyOf(entries);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(S2CRedstoneSignalsPayload payload,
                              IPayloadContext context) {
        context.enqueueWork(() -> ClientRedstoneControlData.INSTANCE.acceptSignals(
            payload.groupKey(), payload.entries().stream()
                .map(entry -> new ClientRedstoneControlData.SignalUpdate(
                    entry.binding(), entry.powered(), entry.allowed()))
                .toList()));
    }

    public static void sendGroup(ServerPlayer player, GroupKey groupKey) {
        RedstoneControlStore store = RedstoneControlStore.get(player.server);
        List<Entry> entries = store.getBindings(groupKey).values().stream()
            .distinct()
            .map(binding -> {
                boolean powered = store.isPowered(player.server, binding.controller());
                return new Entry(binding, powered, binding.mode().allows(powered));
            })
            .toList();
        for (int from = 0; from < entries.size(); from += MAX_PAGE_ENTRIES) {
            int to = Math.min(entries.size(), from + MAX_PAGE_ENTRIES);
            PacketDistributor.sendToPlayer(player,
                new S2CRedstoneSignalsPayload(groupKey, entries.subList(from, to)));
        }
    }

    public record Entry(RedstoneControlBinding binding,
                        boolean powered, boolean allowed) {
    }
}
