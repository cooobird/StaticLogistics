package com.coobird.staticlogistics.network.s2c;

import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.client.gui.screen.LinkConfiguratorScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

public record S2CBlueprintCopyResultPayload(UUID request, boolean success) implements CustomPacketPayload {
    public static final Type<S2CBlueprintCopyResultPayload> TYPE = new Type<>(StaticLogistics.asResource("blueprint_copy_result"));
    public static final StreamCodec<RegistryFriendlyByteBuf, S2CBlueprintCopyResultPayload> STREAM_CODEC = StreamCodec.composite(
        UUIDUtil.STREAM_CODEC, S2CBlueprintCopyResultPayload::request, ByteBufCodecs.BOOL, S2CBlueprintCopyResultPayload::success, S2CBlueprintCopyResultPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(S2CBlueprintCopyResultPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (Minecraft.getInstance().screen instanceof LinkConfiguratorScreen screen)
                screen.acceptBlueprintCopy(payload.request(), payload.success());
        });
    }
}
