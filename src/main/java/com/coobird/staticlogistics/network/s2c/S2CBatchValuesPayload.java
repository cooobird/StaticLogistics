package com.coobird.staticlogistics.network.s2c;

import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.client.gui.screen.LinkConfiguratorScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

public record S2CBatchValuesPayload(UUID request, long priority, long stock, boolean mixedPriority,
                                    boolean mixedStock) implements CustomPacketPayload {
    public static final Type<S2CBatchValuesPayload> TYPE = new Type<>(StaticLogistics.asResource("batch_values"));
    public static final StreamCodec<RegistryFriendlyByteBuf, S2CBatchValuesPayload> STREAM_CODEC = new StreamCodec<>() {
        public S2CBatchValuesPayload decode(RegistryFriendlyByteBuf buf) {
            return new S2CBatchValuesPayload(buf.readUUID(), buf.readLong(), buf.readLong(), buf.readBoolean(), buf.readBoolean());
        }

        public void encode(RegistryFriendlyByteBuf buf, S2CBatchValuesPayload value) {
            buf.writeUUID(value.request()).writeLong(value.priority()).writeLong(value.stock()).writeBoolean(value.mixedPriority()).writeBoolean(value.mixedStock());
        }
    };

    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(S2CBatchValuesPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (Minecraft.getInstance().screen instanceof LinkConfiguratorScreen screen)
                screen.acceptBatchValues(payload);
        });
    }
}
