package com.coobird.staticlogistics.network.s2c;

import com.coobird.staticlogistics.Staticlogistics;
import com.coobird.staticlogistics.config.SLConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record S2CConfigSyncPacket(CompoundTag configValues) implements CustomPacketPayload {

    public static final Type<S2CConfigSyncPacket> TYPE = new Type<>(Staticlogistics.asResource("sync_config"));

    public static final StreamCodec<RegistryFriendlyByteBuf, S2CConfigSyncPacket> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.COMPOUND_TAG,
            S2CConfigSyncPacket::configValues,
            S2CConfigSyncPacket::new
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(final S2CConfigSyncPacket p, final IPayloadContext c) {
        c.enqueueWork(() -> SLConfig.applyServerConfig(p.configValues));
    }
}
