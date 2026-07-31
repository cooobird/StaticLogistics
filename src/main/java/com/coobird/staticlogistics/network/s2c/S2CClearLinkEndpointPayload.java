package com.coobird.staticlogistics.network.s2c;

import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.content.menu.LinkConfiguratorMenu;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import org.mesdag.portlib.network.IPortPacket;
import org.mesdag.portlib.network.PortRegistryFriendlyByteBuf;
import org.mesdag.portlib.network.codec.PortStreamCodec;

/**
 * 确认客户端菜单不再绑定任何节点。
 */
public record S2CClearLinkEndpointPayload() implements IPortPacket.S2C {
    public static final ResourceLocation ID = StaticLogistics.asResource("clear_link_endpoint");
    public static final PortStreamCodec<PortRegistryFriendlyByteBuf, S2CClearLinkEndpointPayload> STREAM_CODEC =
        PortStreamCodec.unit(new S2CClearLinkEndpointPayload());

    @Override
    public ResourceLocation identifier() {
        return ID;
    }

    @Override
    public void work(Player player) {
        if (player.containerMenu instanceof LinkConfiguratorMenu menu) menu.clearTarget();
    }
}
