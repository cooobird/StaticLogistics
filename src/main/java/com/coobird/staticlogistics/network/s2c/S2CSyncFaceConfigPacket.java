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

public record S2CSyncFaceConfigPacket(GlobalPos pos, Direction face,
                                      FaceConfigComposite config, int version) implements CustomPacketPayload {
    public S2CSyncFaceConfigPacket(GlobalPos pos, Direction face, FaceConfigComposite config) {
        this(pos, face, config, config.getVersion());
    }

    public static final Type<S2CSyncFaceConfigPacket> TYPE = new Type<>(Staticlogistics.asResource("sync_face_config"));

    public static final StreamCodec<RegistryFriendlyByteBuf, S2CSyncFaceConfigPacket> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public S2CSyncFaceConfigPacket decode(RegistryFriendlyByteBuf buf) {
            GlobalPos p = GlobalPos.STREAM_CODEC.decode(buf);
            Direction f = Direction.STREAM_CODEC.decode(buf);
            FaceConfigComposite cfg = new FaceConfigComposite();
            cfg.deserializeNBT(buf.registryAccess(), ByteBufCodecs.COMPOUND_TAG.decode(buf));
            int version = buf.readInt();
            return new S2CSyncFaceConfigPacket(p, f, cfg, version);
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buf, S2CSyncFaceConfigPacket p) {
            GlobalPos.STREAM_CODEC.encode(buf, p.pos());
            Direction.STREAM_CODEC.encode(buf, p.face());
            ByteBufCodecs.COMPOUND_TAG.encode(buf, p.config().serializeNBT(buf.registryAccess()));
            buf.writeInt(p.version());
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(final S2CSyncFaceConfigPacket p, final IPayloadContext c) {
        c.enqueueWork(() -> ClientLinkData.INSTANCE.setFaceConfig(p.pos(), p.face(), p.config(), p.version()));
    }
}