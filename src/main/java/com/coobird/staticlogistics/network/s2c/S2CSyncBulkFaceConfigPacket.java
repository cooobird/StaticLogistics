package com.coobird.staticlogistics.network.s2c;

import com.coobird.staticlogistics.Staticlogistics;
import com.coobird.staticlogistics.client.data.ClientLinkData;
import com.coobird.staticlogistics.storage.config.FaceConfigComposite;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

public record S2CSyncBulkFaceConfigPacket(List<Entry> entries) implements CustomPacketPayload {
    public record Entry(GlobalPos pos, Direction face, FaceConfigComposite config, int version) {
    }

    public static final Type<S2CSyncBulkFaceConfigPacket> TYPE = new Type<>(Staticlogistics.asResource("sync_bulk_face_config"));

    public static final StreamCodec<RegistryFriendlyByteBuf, S2CSyncBulkFaceConfigPacket> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public S2CSyncBulkFaceConfigPacket decode(RegistryFriendlyByteBuf buf) {
            int size = buf.readVarInt();
            List<Entry> entries = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                GlobalPos pos = GlobalPos.STREAM_CODEC.decode(buf);
                Direction face = Direction.STREAM_CODEC.decode(buf);
                FaceConfigComposite config = new FaceConfigComposite();
                config.deserializeNBT(buf.registryAccess(), ByteBufCodecs.COMPOUND_TAG.decode(buf));
                int version = buf.readInt();
                entries.add(new Entry(pos, face, config, version));
            }
            return new S2CSyncBulkFaceConfigPacket(entries);
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buf, S2CSyncBulkFaceConfigPacket packet) {
            buf.writeVarInt(packet.entries().size());
            for (Entry entry : packet.entries()) {
                GlobalPos.STREAM_CODEC.encode(buf, entry.pos());
                Direction.STREAM_CODEC.encode(buf, entry.face());
                ByteBufCodecs.COMPOUND_TAG.encode(buf, entry.config().serializeNBT(buf.registryAccess()));
                buf.writeInt(entry.version());
            }
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(S2CSyncBulkFaceConfigPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            for (Entry entry : packet.entries()) {
                ClientLinkData.INSTANCE.setFaceConfig(entry.pos(), entry.face(), entry.config(), entry.version());
            }
        });
    }
}