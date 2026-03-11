package com.coobird.staticlogistics.network.c2s;

import com.coobird.staticlogistics.Staticlogistics;
import com.coobird.staticlogistics.storage.LinkManager;
import com.coobird.staticlogistics.transfer.TransferSettings;
import com.coobird.staticlogistics.core.TransferType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 客户端向服务器请求更新特定接口的传输设置
 */
public record C2SUpdateSettingsPayload(
    BlockPos pos,
    Direction face,
    TransferType transferType,
    TransferSettings settings
) implements CustomPacketPayload {

    public static final Type<C2SUpdateSettingsPayload> TYPE = new Type<>(Staticlogistics.asResource("update_settings"));

    public static final StreamCodec<RegistryFriendlyByteBuf, C2SUpdateSettingsPayload> STREAM_CODEC = StreamCodec.composite(
        BlockPos.STREAM_CODEC, C2SUpdateSettingsPayload::pos,
        Direction.STREAM_CODEC, C2SUpdateSettingsPayload::face,
        TransferType.STREAM_CODEC, C2SUpdateSettingsPayload::transferType,
        TransferSettings.STREAM_CODEC, C2SUpdateSettingsPayload::settings,
        C2SUpdateSettingsPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(final C2SUpdateSettingsPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            var player = context.player();
            if (player.level() instanceof ServerLevel serverLevel) {
                LinkManager manager = LinkManager.get(serverLevel);
                manager.setSettings(payload.pos(), payload.face(), payload.transferType(), payload.settings());
                manager.syncToAll(serverLevel);
            }
        });
    }
}