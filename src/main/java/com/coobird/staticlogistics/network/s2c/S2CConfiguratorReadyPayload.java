package com.coobird.staticlogistics.network.s2c;

import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.client.gui.screen.LinkConfiguratorScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import org.mesdag.portlib.network.IPortPacket;
import org.mesdag.portlib.network.PortRegistryFriendlyByteBuf;
import org.mesdag.portlib.network.codec.PortByteBufCodecs;
import org.mesdag.portlib.network.codec.PortStreamCodec;

import java.util.List;

/// 菜单完整状态发出后的确认，防止客户端在切换途中编辑旧目标。
public record S2CConfiguratorReadyPayload(int menuId, long requestId, boolean accepted,
                                          List<ResourceLocation> selectedTypes) implements IPortPacket.S2C {
    public static final ResourceLocation ID = StaticLogistics.asResource("configurator_ready");
    public static final PortStreamCodec<PortRegistryFriendlyByteBuf, S2CConfiguratorReadyPayload> STREAM_CODEC = PortStreamCodec.composite(
        PortByteBufCodecs.VAR_INT, S2CConfiguratorReadyPayload::menuId, PortByteBufCodecs.VAR_LONG, S2CConfiguratorReadyPayload::requestId, PortByteBufCodecs.BOOL, S2CConfiguratorReadyPayload::accepted, ResourceLocation.STREAM_CODEC.apply(PortByteBufCodecs.list()), S2CConfiguratorReadyPayload::selectedTypes, S2CConfiguratorReadyPayload::new);

    @Override
    public ResourceLocation identifier() {
        return ID;
    }

    @Override
    public void work(Player packetPlayer) {
        S2CConfiguratorReadyPayload payload = this;
        Player context = packetPlayer;

        if (!payload.accepted() && Minecraft.getInstance().player != null)
            Minecraft.getInstance().player.displayClientMessage(Component.translatable("gui.staticlogistics.configuration_rejected"), false);
        if (Minecraft.getInstance().screen instanceof LinkConfiguratorScreen screen && screen.getMenu().containerId == payload.menuId) {
            screen.confirmConfiguration(payload.requestId(), payload.selectedTypes());
        }

    }
}
