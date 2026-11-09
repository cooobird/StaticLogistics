package com.coobird.staticlogistics.network.s2c;

import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.client.data.ToolModeSession;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/// 返回模式请求的权威结果，包含被拒绝的请求。
public record S2CToolModeResultPayload(int slot, long requestId, int mode,
                                       boolean accepted) implements CustomPacketPayload {
    public static final Type<S2CToolModeResultPayload> TYPE = new Type<>(StaticLogistics.asResource("tool_mode_result"));
    public static final StreamCodec<RegistryFriendlyByteBuf, S2CToolModeResultPayload> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.VAR_INT, S2CToolModeResultPayload::slot,
            ByteBufCodecs.VAR_LONG, S2CToolModeResultPayload::requestId,
            ByteBufCodecs.VAR_INT, S2CToolModeResultPayload::mode,
            ByteBufCodecs.BOOL, S2CToolModeResultPayload::accepted,
            S2CToolModeResultPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(S2CToolModeResultPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ToolModeSession.acknowledge(payload.slot(), payload.requestId(), payload.mode(), payload.accepted()));
    }
}
