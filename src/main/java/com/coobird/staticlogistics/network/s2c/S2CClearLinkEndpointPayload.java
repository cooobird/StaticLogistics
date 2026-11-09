package com.coobird.staticlogistics.network.s2c;

import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.content.menu.LinkConfiguratorMenu;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import org.mesdag.portlib.network.IPortPacket;
import org.mesdag.portlib.network.PortRegistryFriendlyByteBuf;
import org.mesdag.portlib.network.codec.PortByteBufCodecs;
import org.mesdag.portlib.network.codec.PortStreamCodec;

/// 服务端确认当前连接配置器不再绑定任何节点。
public record S2CClearLinkEndpointPayload(int menuId) implements IPortPacket.S2C {
    public static final ResourceLocation ID = StaticLogistics.asResource("clear_link_endpoint");
    public static final PortStreamCodec<PortRegistryFriendlyByteBuf,
        S2CClearLinkEndpointPayload> STREAM_CODEC =
        PortStreamCodec.composite(PortByteBufCodecs.VAR_INT, S2CClearLinkEndpointPayload::menuId, S2CClearLinkEndpointPayload::new);

    @Override
    public ResourceLocation identifier() {
        return ID;
    }

    public static void handle(
        S2CClearLinkEndpointPayload payload,
        Player context
    ) {

        if (context.containerMenu
            instanceof LinkConfiguratorMenu menu && menu.containerId == payload.menuId()) {
            menu.clearTarget();
        }

    }

    @Override
    public void work(Player packetPlayer) {
        handle(this, packetPlayer);
    }
}
