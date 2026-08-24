package com.coobird.staticlogistics.network.s2c;

import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.api.group.GroupKey;
import com.coobird.staticlogistics.client.data.ClientRedstoneControlData;
import com.coobird.staticlogistics.logistics.node.ConnectionKey;
import com.coobird.staticlogistics.logistics.redstone.RedstoneControlBinding;
import com.coobird.staticlogistics.logistics.redstone.RedstoneControlStore;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.EncoderException;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.player.Player;
import org.mesdag.portlib.network.IPortPacket;
import org.mesdag.portlib.network.PortRegistryFriendlyByteBuf;
import org.mesdag.portlib.network.codec.PortStreamCodec;

import java.util.ArrayList;
import java.util.List;

/**
 * 分页同步一个物流分组内全部已绑定的红石控制。
 */
public record S2CRedstoneControlGroupPayload(GroupKey groupKey, boolean reset,
                                             List<Entry> entries)
    implements IPortPacket.S2C {
    public static final int MAX_PAGE_ENTRIES = 256;
    public static final ResourceLocation ID = StaticLogistics.asResource("redstone_control_group");
    public static final PortStreamCodec<PortRegistryFriendlyByteBuf,
        S2CRedstoneControlGroupPayload> STREAM_CODEC = new PortStreamCodec<>() {
        @Override
        public S2CRedstoneControlGroupPayload decode(PortRegistryFriendlyByteBuf buffer) {
            GroupKey groupKey = GroupKey.STREAM_CODEC.decode(buffer);
            boolean reset = buffer.readBoolean();
            int size = buffer.readVarInt();
            if (size < 0 || size > MAX_PAGE_ENTRIES) {
                throw new DecoderException("Invalid redstone group page size: " + size);
            }
            List<Entry> entries = new ArrayList<>(size);
            for (int index = 0; index < size; index++) {
                entries.add(new Entry(
                    ConnectionKey.STREAM_CODEC.decode(buffer),
                    RedstoneControlBinding.STREAM_CODEC.decode(buffer),
                    buffer.readBoolean(), buffer.readBoolean()));
            }
            return new S2CRedstoneControlGroupPayload(groupKey, reset, entries);
        }

        @Override
        public void encode(PortRegistryFriendlyByteBuf buffer,
                           S2CRedstoneControlGroupPayload payload) {
            if (payload.entries().size() > MAX_PAGE_ENTRIES) {
                throw new EncoderException("Redstone group page is too large");
            }
            GroupKey.STREAM_CODEC.encode(buffer, payload.groupKey());
            buffer.writeBoolean(payload.reset());
            buffer.writeVarInt(payload.entries().size());
            for (Entry entry : payload.entries()) {
                ConnectionKey.STREAM_CODEC.encode(buffer, entry.connection());
                RedstoneControlBinding.STREAM_CODEC.encode(buffer, entry.binding());
                buffer.writeBoolean(entry.powered());
                buffer.writeBoolean(entry.allowed());
            }
        }
    };

    public S2CRedstoneControlGroupPayload {
        entries = List.copyOf(entries);
    }

    @Override
    public ResourceLocation identifier() {
        return ID;
    }

    @Override
    public void work(Player player) {
        Minecraft.getInstance().execute(() -> ClientRedstoneControlData.INSTANCE.acceptGroupPage(
            groupKey(), reset(), entries().stream()
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
}
