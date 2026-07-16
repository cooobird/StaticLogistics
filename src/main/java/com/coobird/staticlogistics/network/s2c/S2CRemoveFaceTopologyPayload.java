package com.coobird.staticlogistics.network.s2c;

import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.client.data.ClientLinkData;
import com.coobird.staticlogistics.logistics.util.LogisticsConstants;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.EncoderException;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** 批量应用面拓扑删除墓碑。 */
public record S2CRemoveFaceTopologyPayload(List<Entry> entries) implements CustomPacketPayload {
    public S2CRemoveFaceTopologyPayload {
        entries = List.copyOf(Objects.requireNonNull(entries, "Topology removals must not be null"));
    }

    public record Entry(GlobalPos pos, Direction face, long version) {
        public Entry {
            Objects.requireNonNull(pos, "Topology removal position must not be null");
            Objects.requireNonNull(face, "Topology removal face must not be null");
        }
    }

    public static final Type<S2CRemoveFaceTopologyPayload> TYPE =
        new Type<>(StaticLogistics.asResource("remove_face_topology"));

    public static final StreamCodec<RegistryFriendlyByteBuf, S2CRemoveFaceTopologyPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public S2CRemoveFaceTopologyPayload decode(RegistryFriendlyByteBuf buf) {
            int size = buf.readVarInt();
            if (size < 0 || size > LogisticsConstants.Network.getMaxBulkEntries()) {
                throw new DecoderException("Invalid removal entry count: " + size);
            }
            List<Entry> entries = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                GlobalPos pos = GlobalPos.STREAM_CODEC.decode(buf);
                Direction face = Direction.STREAM_CODEC.decode(buf);
                long version = buf.readLong();
                entries.add(new Entry(pos, face, version));
            }
            return new S2CRemoveFaceTopologyPayload(entries);
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buf, S2CRemoveFaceTopologyPayload packet) {
            if (packet.entries().size() > LogisticsConstants.Network.getMaxBulkEntries()) {
                throw new EncoderException("Invalid removal entry count: " + packet.entries().size());
            }
            buf.writeVarInt(packet.entries().size());
            for (Entry entry : packet.entries()) {
                GlobalPos.STREAM_CODEC.encode(buf, entry.pos());
                Direction.STREAM_CODEC.encode(buf, entry.face());
                buf.writeLong(entry.version());
            }
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(S2CRemoveFaceTopologyPayload packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            for (Entry entry : packet.entries()) {
                ClientLinkData.INSTANCE.removeFaceTopology(entry.pos(), entry.face(), entry.version());
            }
        });
    }
}
