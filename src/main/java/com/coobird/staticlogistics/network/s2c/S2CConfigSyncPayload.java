package com.coobird.staticlogistics.network.s2c;

import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.config.SLConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import org.mesdag.portlib.network.IPortPacket;
import org.mesdag.portlib.network.PortRegistryFriendlyByteBuf;
import org.mesdag.portlib.network.codec.PortStreamCodec;

public record S2CConfigSyncPayload(CompoundTag configValues) implements IPortPacket.S2C {
    public static final ResourceLocation ID = StaticLogistics.asResource("config_sync");
    public static final PortStreamCodec<PortRegistryFriendlyByteBuf, S2CConfigSyncPayload> STREAM_CODEC = new PortStreamCodec<>() {
        @Override
        public S2CConfigSyncPayload decode(PortRegistryFriendlyByteBuf buffer) {
            FriendlyByteBuf fbuf = buffer;
            return new S2CConfigSyncPayload(fbuf.readNbt());
        }

        @Override
        public void encode(PortRegistryFriendlyByteBuf buffer, S2CConfigSyncPayload value) {
            FriendlyByteBuf fbuf = buffer;
            fbuf.writeNbt(value.configValues());
        }
    };

    @Override
    public ResourceLocation identifier() {
        return ID;
    }

    @Override
    public void work(Player player) {
        Minecraft.getInstance().execute(() -> SLConfig.applyServerConfig(configValues));
    }
}
