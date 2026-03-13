package com.coobird.staticlogistics.network.c2s;

import com.coobird.staticlogistics.Staticlogistics;
import com.coobird.staticlogistics.core.ConnectionMode;
import com.coobird.staticlogistics.core.FaceConfig;
import com.coobird.staticlogistics.core.TransferType;
import com.coobird.staticlogistics.storage.LinkManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record C2SConfigureFacePayload(
    BlockPos pos,
    Direction face,
    TransferType transferType,
    CompoundTag data
) implements CustomPacketPayload {

    public static final Type<C2SConfigureFacePayload> TYPE = new Type<>(Staticlogistics.asResource("configure_face"));

    public static final StreamCodec<RegistryFriendlyByteBuf, C2SConfigureFacePayload> STREAM_CODEC = StreamCodec.composite(
        BlockPos.STREAM_CODEC, C2SConfigureFacePayload::pos,
        Direction.STREAM_CODEC, C2SConfigureFacePayload::face,
        TransferType.STREAM_CODEC, C2SConfigureFacePayload::transferType,
        ByteBufCodecs.COMPOUND_TAG, C2SConfigureFacePayload::data,
        C2SConfigureFacePayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(final C2SConfigureFacePayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player().level() instanceof ServerLevel serverLevel) {
                LinkManager manager = LinkManager.get(serverLevel);
                FaceConfig config = manager.getOrCreateFaceConfig(payload.pos(), payload.face());
                FaceConfig.SideData sideData = config.getSettings(payload.transferType());

                if (payload.data().contains("mode")) {
                    try {
                        sideData.mode = ConnectionMode.valueOf(payload.data().getString("mode"));
                    } catch (Exception ignored) {
                    }
                }
                if (payload.data().contains("channel")) {
                    sideData.channelColor = payload.data().getInt("channel");
                }

                manager.setDirty();
                manager.syncToAll(serverLevel);
            }
        });
    }
}