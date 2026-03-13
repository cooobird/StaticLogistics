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
            BlockPos pos = BlockPos.STREAM_CODEC.decode(buf);
            Direction face = Direction.STREAM_CODEC.decode(buf);
            FaceConfig config = new FaceConfig();
            config.deserializeNBT(buf.registryAccess(), ByteBufCodecs.COMPOUND_TAG.decode(buf));
            return new S2CSyncFaceConfigPacket(pos, face, config);
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buf, S2CSyncFaceConfigPacket packet) {
            BlockPos.STREAM_CODEC.encode(buf, packet.pos());
            Direction.STREAM_CODEC.encode(buf, packet.face());
            ByteBufCodecs.COMPOUND_TAG.encode(buf, packet.config().serializeNBT(buf.registryAccess()));
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            ClientLinkCache.updateFaceConfig(this.pos, this.face, this.config);
        });
    }
}