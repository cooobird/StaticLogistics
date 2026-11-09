package com.coobird.staticlogistics.network.s2c;

import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.client.data.ClientLinkData;
import com.coobird.staticlogistics.logistics.util.LogisticsConstants;
import com.coobird.staticlogistics.network.BoundedNetworkCodecs;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.EncoderException;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import org.mesdag.portlib.network.IPortPacket;
import org.mesdag.portlib.network.PortRegistryFriendlyByteBuf;
import org.mesdag.portlib.network.codec.PortStreamCodec;

import java.util.ArrayList;
import java.util.List;

/// 批量应用面拓扑删除墓碑。
public record S2CRemoveFaceTopologyPayload(List<Entry> entries) implements IPortPacket.S2C {
    public S2CRemoveFaceTopologyPayload {
        entries = List.copyOf(entries);
    }

    public record Entry(GlobalPos pos, Direction face, int channel, long version) {
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
                GlobalPos pos = BoundedNetworkCodecs.GLOBAL_POS.decode(buf);
                Direction face = BoundedNetworkCodecs.DIRECTION.decode(buf);
                int channel = buf.readVarInt();
                long version = buf.readLong();
                entries.add(new Entry(pos, face, channel, version));
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
                BoundedNetworkCodecs.GLOBAL_POS.encode(buf, entry.pos());
                BoundedNetworkCodecs.DIRECTION.encode(buf, entry.face());
                buf.writeVarInt(entry.channel());
                buf.writeLong(entry.version());
            }
        }
    };

    @Override
    public ResourceLocation identifier() {
        return ID;
    }

    @Override
    public void work(Player packetPlayer) {
        S2CRemoveFaceTopologyPayload packet = this;
        Player context = packetPlayer;

        for (Entry entry : packet.entries()) {
            ClientLinkData.INSTANCE.removeFaceTopology(new LogisticsNode(entry.pos(), entry.face(), entry.channel()), entry.version());
            }

    }
}
