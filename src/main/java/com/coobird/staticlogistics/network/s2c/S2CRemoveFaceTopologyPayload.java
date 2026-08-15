package com.coobird.staticlogistics.network.s2c;

import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.client.data.ClientLinkData;
import com.coobird.staticlogistics.logistics.util.LogisticsConstants;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.EncoderException;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import org.mesdag.portlib.network.IPortPacket;
import org.mesdag.portlib.network.PortRegistryFriendlyByteBuf;
import org.mesdag.portlib.network.codec.PortStreamCodec;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 批量应用面拓扑删除墓碑。
 */
public record S2CRemoveFaceTopologyPayload(List<Entry> entries) implements IPortPacket.S2C {
    public S2CRemoveFaceTopologyPayload {
        entries = List.copyOf(Objects.requireNonNull(entries, "Topology removals must not be null"));
    }

    public record Entry(GlobalPos pos, Direction face, long version) {
        public Entry {
            Objects.requireNonNull(pos, "Topology removal position must not be null");
            Objects.requireNonNull(face, "Topology removal face must not be null");
        }
    }

    public static final ResourceLocation ID = StaticLogistics.asResource("remove_face_topology");

    public static final PortStreamCodec<PortRegistryFriendlyByteBuf, S2CRemoveFaceTopologyPayload> STREAM_CODEC = new PortStreamCodec<>() {
        @Override
        public S2CRemoveFaceTopologyPayload decode(PortRegistryFriendlyByteBuf buf) {
            int size = buf.readVarInt();
            if (size < 0 || size > LogisticsConstants.Network.getMaxBulkEntries()) {
                throw new DecoderException("Invalid removal entry count: " + size);
            }
            List<Entry> entries = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                GlobalPos pos = GlobalPos.of(
                    buf.readResourceKey(Registries.DIMENSION), buf.readBlockPos());
                Direction face = buf.readEnum(Direction.class);
                long version = buf.readLong();
                entries.add(new Entry(pos, face, version));
            }
            return new S2CRemoveFaceTopologyPayload(entries);
        }

        @Override
        public void encode(PortRegistryFriendlyByteBuf buf, S2CRemoveFaceTopologyPayload packet) {
            if (packet.entries().size() > LogisticsConstants.Network.getMaxBulkEntries()) {
                throw new EncoderException("Invalid removal entry count: " + packet.entries().size());
            }
            buf.writeVarInt(packet.entries().size());
            for (Entry entry : packet.entries()) {
                buf.writeResourceKey(entry.pos().dimension());
                buf.writeBlockPos(entry.pos().pos());
                buf.writeEnum(entry.face());
                buf.writeLong(entry.version());
            }
        }
    };

    @Override
    public ResourceLocation identifier() {
        return ID;
    }

    @Override
    public void work(Player player) {
        Minecraft.getInstance().execute(() -> {
            for (Entry entry : entries()) {
                ClientLinkData.INSTANCE.removeFaceTopology(entry.pos(), entry.face(), entry.version());
            }
        });
    }
}
