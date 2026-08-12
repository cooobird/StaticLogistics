package com.coobird.staticlogistics.network.c2s;

import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.content.menu.LinkConfiguratorMenu;
import com.coobird.staticlogistics.network.SLNetwork;
import com.coobird.staticlogistics.network.s2c.S2CClearLinkEndpointPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.mesdag.portlib.network.IPortPacket;
import org.mesdag.portlib.network.PortRegistryFriendlyByteBuf;
import org.mesdag.portlib.network.codec.PortStreamCodec;

/**
 * 清除连接配置器当前选中的节点，但保持菜单打开。
 */
public record C2SClearLinkEndpointPayload() implements IPortPacket.C2S {
    public static final ResourceLocation ID = StaticLogistics.asResource("request_clear_link_endpoint");
    public static final PortStreamCodec<PortRegistryFriendlyByteBuf, C2SClearLinkEndpointPayload> STREAM_CODEC =
        PortStreamCodec.unit(new C2SClearLinkEndpointPayload());

    @Override
    public ResourceLocation identifier() {
        return ID;
    }

    @Override
    public void work(ServerPlayer player) {
        if (!(player.containerMenu instanceof LinkConfiguratorMenu menu)) return;
        menu.clearTarget();
        SLNetwork.HANDLER.sendToPlayer(player, new S2CClearLinkEndpointPayload());
        menu.broadcastFullState();
    }
}
