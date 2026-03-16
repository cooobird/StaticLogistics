package com.coobird.staticlogistics.network.c2s;

import com.coobird.staticlogistics.Staticlogistics;
import com.coobird.staticlogistics.core.ConnectionMode;
import com.coobird.staticlogistics.core.FaceConfig;
import com.coobird.staticlogistics.storage.LinkManager;
import com.coobird.staticlogistics.transfer.TransferType;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.slf4j.Logger;

public record C2SConfigureFacePayload(BlockPos pos, Direction face, TransferType transferType,
                                      CompoundTag data) implements CustomPacketPayload {
    private static final Logger LOGGER = LogUtils.getLogger();
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
            if (!(context.player().level() instanceof ServerLevel serverLevel)) return;
            if (context.player().blockPosition().distSqr(payload.pos()) > 128) return;

            LinkManager manager = LinkManager.get(serverLevel);
            FaceConfig config = manager.getOrCreateFaceConfig(payload.pos(), payload.face(), serverLevel);
            FaceConfig.SideData sideData = config.getSettings(payload.transferType());
            boolean changed = false;

            if (payload.data().contains("mode")) {
                try {
                    ConnectionMode m = ConnectionMode.valueOf(payload.data().getString("mode"));
                    if (sideData.mode != m) {
                        sideData.mode = m;
                        changed = true;
                    }
                } catch (Exception e) {
                    LOGGER.warn("Invalid mode: {}", payload.data().getString("mode"));
                }
            }
            if (payload.data().contains("strategy")) {
                try {
                    var s = com.coobird.staticlogistics.core.DistributionStrategy.valueOf(payload.data().getString("strategy"));
                    if (sideData.strategy != s) {
                        sideData.strategy = s;
                        changed = true;
                    }
                } catch (Exception e) {
                    LOGGER.warn("Invalid strategy");
                }
            }
            if (payload.data().contains("priority")) {
                int p = payload.data().getInt("priority");
                if (sideData.priority != p) {
                    sideData.priority = p;
                    changed = true;
                }
            }
            if (payload.data().contains("isBlacklist")) {
                boolean b = payload.data().getBoolean("isBlacklist");
                if (sideData.isBlacklist != b) {
                    sideData.isBlacklist = b;
                    changed = true;
                }
            }
            if (payload.data().contains("channel")) {
                int c = payload.data().getInt("channel");
                if (sideData.channelColor != c) {
                    sideData.channelColor = c;
                    changed = true;
                }
            }

            if (changed) {
                config.markDirty(); // 触发 LinkManager 缓存刷新
                manager.syncFaceConfigToAll(serverLevel, payload.pos(), payload.face(), config);
            }
        });
    }
}