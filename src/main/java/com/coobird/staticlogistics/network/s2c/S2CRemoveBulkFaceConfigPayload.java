package com.coobird.staticlogistics.network.s2c;

import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.client.data.ClientLinkData;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import org.mesdag.portlib.network.IPortPacket;
import org.mesdag.portlib.network.PortRegistryFriendlyByteBuf;
import org.mesdag.portlib.network.codec.PortStreamCodec;

import java.util.ArrayList;
import java.util.List;

public record S2CRemoveBulkFaceConfigPayload(List<Entry> entries) implements IPortPacket.S2C {
    public record Entry(GlobalPos pos, Direction face) {
    }

    public static final ResourceLocation ID = StaticLogistics.asResource("remove_bulk_face_config");

    public static final PortStreamCodec<PortRegistryFriendlyByteBuf, S2CRemoveBulkFaceConfigPayload> STREAM_CODEC = new PortStreamCodec<>() {
        @Override
        public S2CRemoveBulkFaceConfigPayload decode(PortRegistryFriendlyByteBuf buf) {
            FriendlyByteBuf fbuf = buf;
            int size = fbuf.readVarInt();
            List<Entry> entries = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                ResourceLocation dimId = fbuf.readResourceLocation();
                var dim = net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, dimId);
                var pos = GlobalPos.of(dim, fbuf.readBlockPos());
                Direction face = Direction.from3DDataValue(fbuf.readVarInt());
                entries.add(new Entry(pos, face));
            }
            return new S2CRemoveBulkFaceConfigPayload(entries);
        }

        @Override
        public void encode(PortRegistryFriendlyByteBuf buf, S2CRemoveBulkFaceConfigPayload packet) {
            FriendlyByteBuf fbuf = buf;
            fbuf.writeVarInt(packet.entries().size());
            for (Entry entry : packet.entries()) {
                fbuf.writeResourceLocation(entry.pos().dimension().location());
                fbuf.writeBlockPos(entry.pos().pos());
                fbuf.writeVarInt(entry.face().get3DDataValue());
            }
        }
    };

    @Override
    public ResourceLocation identifier() {
        return ID;
    }

    @Override
    public void work(Player player) {
        net.minecraft.client.Minecraft.getInstance().execute(() -> {
            for (Entry entry : entries) {
                ClientLinkData.INSTANCE.removeFaceConfig(entry.pos(), entry.face());
            }
        });
    }
}
