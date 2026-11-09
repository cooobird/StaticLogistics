package com.coobird.staticlogistics.network.s2c;

import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.client.data.ToolModeSession;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import org.mesdag.portlib.network.IPortPacket;
import org.mesdag.portlib.network.PortRegistryFriendlyByteBuf;
import org.mesdag.portlib.network.codec.PortByteBufCodecs;
import org.mesdag.portlib.network.codec.PortStreamCodec;

/// 返回模式请求的权威结果，包含被拒绝的请求。
public record S2CToolModeResultPayload(int slot, long requestId, int mode,
                                       boolean accepted) implements IPortPacket.S2C {
    public static final ResourceLocation ID = StaticLogistics.asResource("tool_mode_result");
    public static final PortStreamCodec<PortRegistryFriendlyByteBuf, S2CToolModeResultPayload> STREAM_CODEC =
        PortStreamCodec.composite(
            PortByteBufCodecs.VAR_INT, S2CToolModeResultPayload::slot,
            PortByteBufCodecs.VAR_LONG, S2CToolModeResultPayload::requestId,
            PortByteBufCodecs.VAR_INT, S2CToolModeResultPayload::mode,
            PortByteBufCodecs.BOOL, S2CToolModeResultPayload::accepted,
            S2CToolModeResultPayload::new);

    @Override
    public ResourceLocation identifier() {
        return ID;
    }

    @Override
    public void work(Player packetPlayer) {
        S2CToolModeResultPayload payload = this;
        Player context = packetPlayer;
        ToolModeSession.acknowledge(payload.slot(), payload.requestId(), payload.mode(), payload.accepted());
    }
}
