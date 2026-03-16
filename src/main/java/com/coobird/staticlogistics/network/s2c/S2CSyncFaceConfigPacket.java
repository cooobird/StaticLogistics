package com.coobird.staticlogistics.network.s2c;

import com.coobird.staticlogistics.Staticlogistics;
import com.coobird.staticlogistics.client.ClientLinkCache;
import com.coobird.staticlogistics.core.FaceConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record S2CSyncFaceConfigPacket(BlockPos pos, Direction face, FaceConfig config) implements CustomPacketPayload {
    public static final Type<S2CSyncFaceConfigPacket> TYPE = new Type<>(Staticlogistics.asResource("sync_face_config"));

    public static final StreamCodec<RegistryFriendlyByteBuf, S2CSyncFaceConfigPacket> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public S2CSyncFaceConfigPacket decode(RegistryFriendlyByteBuf buf) {
            BlockPos p = BlockPos.STREAM_CODEC.decode(buf);
            Direction f = Direction.STREAM_CODEC.decode(buf);
            FaceConfig cfg = new FaceConfig();
            cfg.deserializeNBT(buf.registryAccess(), ByteBufCodecs.COMPOUND_TAG.decode(buf));
            return new S2CSyncFaceConfigPacket(p, f, cfg);
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buf, S2CSyncFaceConfigPacket p) {
            BlockPos.STREAM_CODEC.encode(buf, p.pos());
            Direction.STREAM_CODEC.encode(buf, p.face());
            ByteBufCodecs.COMPOUND_TAG.encode(buf, p.config().serializeNBT(buf.registryAccess()));
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(final S2CSyncFaceConfigPacket p, final IPayloadContext c) {
        c.enqueueWork(() -> ClientLinkCache.updateFaceConfig(p.pos(), p.face(), p.config()));
    }
}