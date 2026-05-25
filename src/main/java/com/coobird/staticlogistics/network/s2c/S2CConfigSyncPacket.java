package com.coobird.staticlogistics.network.s2c;

import com.coobird.staticlogistics.Staticlogistics;
import com.coobird.staticlogistics.config.SLConfig;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;

/**
 * 从服务端同步所有配置值到客户端。
 * 在配置热重载时由服务端广播给所有在线玩家。
 */
public record S2CConfigSyncPacket(
    int defaultRadius, int defaultTickInterval, long maxTransferLimit,
    int itemStack, int fluidStack, int energyStack,
    int mekChemicalStack, int mekHeatStack, int arsSourceStack,
    int ironMult, int goldMult, int diamondMult, int netheriteMult, int netherStarMult,
    boolean autoCleanStoredNodes,
    int cacheProviderSize, double cacheLoadFactor, int cacheTargetSize,
    int networkMaxBulkEntries,
    int tickerBatchSize, int cleanInterval, int defaultCooldown,
    int batchCleanThreshold, int batchCleanSize, int contextPoolSize,
    ArrayList<String> componentStrategyOverrides
) implements CustomPacketPayload {

    public static final Type<S2CConfigSyncPacket> TYPE = new Type<>(Staticlogistics.asResource("sync_config"));

    public static final StreamCodec<RegistryFriendlyByteBuf, S2CConfigSyncPacket> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public S2CConfigSyncPacket decode(RegistryFriendlyByteBuf buf) {
            return new S2CConfigSyncPacket(
                buf.readInt(), buf.readInt(), buf.readLong(),
                buf.readInt(), buf.readInt(), buf.readInt(),
                buf.readInt(), buf.readInt(), buf.readInt(),
                buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt(),
                buf.readBoolean(),
                buf.readInt(), buf.readDouble(), buf.readInt(),
                buf.readInt(),
                buf.readInt(), buf.readInt(), buf.readInt(),
                buf.readInt(), buf.readInt(), buf.readInt(),
                ByteBufCodecs.collection(ArrayList::new, ByteBufCodecs.STRING_UTF8).decode(buf)
            );
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buf, S2CConfigSyncPacket p) {
            buf.writeInt(p.defaultRadius);
            buf.writeInt(p.defaultTickInterval);
            buf.writeLong(p.maxTransferLimit);
            buf.writeInt(p.itemStack);
            buf.writeInt(p.fluidStack);
            buf.writeInt(p.energyStack);
            buf.writeInt(p.mekChemicalStack);
            buf.writeInt(p.mekHeatStack);
            buf.writeInt(p.arsSourceStack);
            buf.writeInt(p.ironMult);
            buf.writeInt(p.goldMult);
            buf.writeInt(p.diamondMult);
            buf.writeInt(p.netheriteMult);
            buf.writeInt(p.netherStarMult);
            buf.writeBoolean(p.autoCleanStoredNodes);
            buf.writeInt(p.cacheProviderSize);
            buf.writeDouble(p.cacheLoadFactor);
            buf.writeInt(p.cacheTargetSize);
            buf.writeInt(p.networkMaxBulkEntries);
            buf.writeInt(p.tickerBatchSize);
            buf.writeInt(p.cleanInterval);
            buf.writeInt(p.defaultCooldown);
            buf.writeInt(p.batchCleanThreshold);
            buf.writeInt(p.batchCleanSize);
            buf.writeInt(p.contextPoolSize);
            ByteBufCodecs.collection(ArrayList::new, ByteBufCodecs.STRING_UTF8).encode(buf, p.componentStrategyOverrides);
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(final S2CConfigSyncPacket p, final IPayloadContext c) {
        c.enqueueWork(() -> SLConfig.applyServerConfig(p));
    }
}
