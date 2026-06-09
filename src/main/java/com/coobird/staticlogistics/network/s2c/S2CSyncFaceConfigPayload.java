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

public record S2CSyncFaceConfigPayload(GlobalPos pos, Direction face,
                                       FaceConfigComposite config, long version) implements IPortPacket.S2C {
    public S2CSyncFaceConfigPayload(GlobalPos pos, Direction face, FaceConfigComposite config) {
        this(pos, face, config, config.getVersion());
    }

    public static final ResourceLocation ID = StaticLogistics.asResource("sync_face_config");
    public static final PortStreamCodec<PortRegistryFriendlyByteBuf, S2CSyncFaceConfigPayload> STREAM_CODEC = new PortStreamCodec<>() {
        @Override
        public S2CSyncFaceConfigPayload decode(PortRegistryFriendlyByteBuf buffer) {
            FriendlyByteBuf fbuf = buffer;
            GlobalPos pos = GlobalPos.of(
                net.minecraft.resources.ResourceKey.create(
                    net.minecraft.core.registries.Registries.DIMENSION,
                    fbuf.readResourceLocation()
                ),
                fbuf.readBlockPos()
            );
            Direction face = Direction.from3DDataValue(fbuf.readVarInt());
            CompoundTag nbt = fbuf.readNbt();
            FaceConfigComposite config = new FaceConfigComposite();
            if (nbt != null) config.deserializeNBT(null, nbt); // TODO: Provider not available in network context
            long version = fbuf.readLong();
            return new S2CSyncFaceConfigPayload(pos, face, config, version);
        }

        @Override
        public void encode(PortRegistryFriendlyByteBuf buffer, S2CSyncFaceConfigPayload value) {
            FriendlyByteBuf fbuf = buffer;
            fbuf.writeResourceLocation(value.pos().dimension().location());
            fbuf.writeBlockPos(value.pos().pos());
            fbuf.writeVarInt(value.face().get3DDataValue());
            fbuf.writeNbt(value.config().serializeNBT(null)); // TODO: Provider not available in network context
            fbuf.writeLong(value.version());
        }
    };

    @Override
    public ResourceLocation identifier() {
        return ID;
    }

    @Override
    public void work(Player player) {
        net.minecraft.client.Minecraft.getInstance().execute(() -> ClientLinkData.INSTANCE.setFaceConfig(pos, face, config, version));
    }
}
