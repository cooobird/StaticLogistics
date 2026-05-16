package com.coobird.staticlogistics.network.s2c;

import com.coobird.staticlogistics.Staticlogistics;
import com.coobird.staticlogistics.client.data.ClientLinkData;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

public record S2CRemoveBulkFaceConfigPacket(List<Entry> entries) implements CustomPacketPayload {
    public record Entry(GlobalPos pos, Direction face) {
    }

    public static final Type<S2CRemoveBulkFaceConfigPacket> TYPE = new Type<>(Staticlogistics.asResource("remove_bulk_face_config"));

    public static final StreamCodec<RegistryFriendlyByteBuf, S2CRemoveBulkFaceConfigPacket> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public S2CRemoveBulkFaceConfigPacket decode(RegistryFriendlyByteBuf buf) {
            int size = buf.readVarInt();
            List<Entry> entries = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                GlobalPos pos = GlobalPos.STREAM_CODEC.decode(buf);
                Direction face = Direction.STREAM_CODEC.decode(buf);
                entries.add(new Entry(pos, face));
            }
            return new S2CRemoveBulkFaceConfigPacket(entries);
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buf, S2CRemoveBulkFaceConfigPacket packet) {
            buf.writeVarInt(packet.entries().size());
            for (Entry entry : packet.entries()) {
                GlobalPos.STREAM_CODEC.encode(buf, entry.pos());
                Direction.STREAM_CODEC.encode(buf, entry.face());
            }
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(S2CRemoveBulkFaceConfigPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            for (Entry entry : packet.entries()) {
                ClientLinkData.INSTANCE.removeFaceConfig(entry.pos(), entry.face());
            }
        });
    }
}