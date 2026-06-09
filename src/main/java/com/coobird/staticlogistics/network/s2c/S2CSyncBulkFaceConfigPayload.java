package com.coobird.staticlogistics.network.s2c;

import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.client.data.ClientLinkData;
import com.coobird.staticlogistics.storage.model.FaceConfigComposite;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import org.mesdag.portlib.network.IPortPacket;
import org.mesdag.portlib.network.PortRegistryFriendlyByteBuf;
import org.mesdag.portlib.network.codec.PortStreamCodec;

import java.util.ArrayList;
import java.util.List;

public record S2CSyncBulkFaceConfigPayload(List<Entry> entries) implements IPortPacket.S2C {
    public record Entry(GlobalPos pos, Direction face, FaceConfigComposite config, long version) {
    }

    public static final ResourceLocation ID = StaticLogistics.asResource("sync_bulk_face_config");

    public static final PortStreamCodec<PortRegistryFriendlyByteBuf, S2CSyncBulkFaceConfigPayload> STREAM_CODEC = new PortStreamCodec<>() {
        @Override
        public S2CSyncBulkFaceConfigPayload decode(PortRegistryFriendlyByteBuf buf) {
            FriendlyByteBuf fbuf = buf;
            int size = fbuf.readVarInt();
            List<Entry> entries = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                ResourceLocation dimId = fbuf.readResourceLocation();
                var dim = net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, dimId);
                var pos = GlobalPos.of(dim, fbuf.readBlockPos());
                Direction face = Direction.from3DDataValue(fbuf.readVarInt());
                CompoundTag nbt = fbuf.readNbt();
                FaceConfigComposite config = new FaceConfigComposite();
                if (nbt != null) config.deserializeNBT(null, nbt); // TODO: Provider not available in network context
                long version = fbuf.readLong();
                entries.add(new Entry(pos, face, config, version));
            }
            return new S2CSyncBulkFaceConfigPayload(entries);
        }

        @Override
        public void encode(PortRegistryFriendlyByteBuf buf, S2CSyncBulkFaceConfigPayload packet) {
            FriendlyByteBuf fbuf = buf;
            fbuf.writeVarInt(packet.entries().size());
            for (Entry entry : packet.entries()) {
                fbuf.writeResourceLocation(entry.pos().dimension().location());
                fbuf.writeBlockPos(entry.pos().pos());
                fbuf.writeVarInt(entry.face().get3DDataValue());
                fbuf.writeNbt(entry.config().serializeNBT(null)); // TODO: Provider not available in network context
                fbuf.writeLong(entry.version());
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
                ClientLinkData.INSTANCE.setFaceConfig(entry.pos(), entry.face(), entry.config(), entry.version());
            }
        });
    }
}
