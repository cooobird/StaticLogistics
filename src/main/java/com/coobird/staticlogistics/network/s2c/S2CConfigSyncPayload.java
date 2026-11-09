package com.coobird.staticlogistics.network.s2c;

import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.config.SLConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import org.mesdag.portlib.network.IPortPacket;
import org.mesdag.portlib.network.PortRegistryFriendlyByteBuf;
import org.mesdag.portlib.network.codec.PortByteBufCodecs;
import org.mesdag.portlib.network.codec.PortStreamCodec;

public record S2CConfigSyncPayload(CompoundTag configValues) implements IPortPacket.S2C {

    public static final ResourceLocation ID = StaticLogistics.asResource("sync_config");

    public static final PortStreamCodec<PortRegistryFriendlyByteBuf, S2CConfigSyncPayload> STREAM_CODEC =
        PortStreamCodec.composite(
            PortByteBufCodecs.COMPOUND_TAG,
            S2CConfigSyncPayload::configValues,
            S2CConfigSyncPayload::new
        );

    @Override
    public ResourceLocation identifier() {
        return ID;
    }

    @Override
    public void work(Player player) {
        SLConfig.applyServerConfig(configValues);
    }
}
