package com.coobird.staticlogistics.network.s2c;

import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.client.gui.screen.LinkConfiguratorScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import org.mesdag.portlib.network.IPortPacket;
import org.mesdag.portlib.network.PortRegistryFriendlyByteBuf;
import org.mesdag.portlib.network.codec.PortStreamCodec;

import java.util.UUID;

public record S2CBatchValuesPayload(UUID request, long priority, long stock, boolean mixedPriority,
                                    boolean mixedStock) implements IPortPacket.S2C {
    public static final ResourceLocation ID = StaticLogistics.asResource("batch_values");
    public static final PortStreamCodec<PortRegistryFriendlyByteBuf, S2CBatchValuesPayload> STREAM_CODEC = new PortStreamCodec<>() {
        public S2CBatchValuesPayload decode(PortRegistryFriendlyByteBuf buf) {
            return new S2CBatchValuesPayload(buf.readUUID(), buf.readLong(), buf.readLong(), buf.readBoolean(), buf.readBoolean());
        }

        public void encode(PortRegistryFriendlyByteBuf buf, S2CBatchValuesPayload value) {
            buf.writeUUID(value.request()).writeLong(value.priority()).writeLong(value.stock()).writeBoolean(value.mixedPriority()).writeBoolean(value.mixedStock());
        }
    };

    @Override
    public ResourceLocation identifier() {
        return ID;
    }

    @Override
    public void work(Player player) {
        if (Minecraft.getInstance().screen instanceof LinkConfiguratorScreen screen) screen.acceptBatchValues(this);
    }
}
