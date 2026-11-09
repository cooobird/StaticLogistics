package com.coobird.staticlogistics.network.s2c;

import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.client.gui.screen.LinkConfiguratorScreen;
import com.coobird.staticlogistics.network.BoundedNetworkCodecs;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import org.mesdag.portlib.network.IPortPacket;
import org.mesdag.portlib.network.PortRegistryFriendlyByteBuf;
import org.mesdag.portlib.network.codec.PortByteBufCodecs;
import org.mesdag.portlib.network.codec.PortStreamCodec;

import java.util.UUID;

public record S2CBlueprintCopyResultPayload(UUID request, boolean success) implements IPortPacket.S2C {
    public static final ResourceLocation ID = StaticLogistics.asResource("blueprint_copy_result");
    public static final PortStreamCodec<PortRegistryFriendlyByteBuf, S2CBlueprintCopyResultPayload> STREAM_CODEC = PortStreamCodec.composite(
        BoundedNetworkCodecs.UUID, S2CBlueprintCopyResultPayload::request, PortByteBufCodecs.BOOL, S2CBlueprintCopyResultPayload::success, S2CBlueprintCopyResultPayload::new);

    @Override
    public ResourceLocation identifier() {
        return ID;
    }

    @Override
    public void work(Player packetPlayer) {
        S2CBlueprintCopyResultPayload payload = this;
        Player context = packetPlayer;
        if (Minecraft.getInstance().screen instanceof LinkConfiguratorScreen screen)
            screen.acceptBlueprintCopy(payload.request(), payload.success());
    }
}
