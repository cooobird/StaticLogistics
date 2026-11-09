package com.coobird.staticlogistics.network.c2s;

import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.content.menu.LinkConfiguratorMenu;
import com.coobird.staticlogistics.network.s2c.S2CClearLinkEndpointPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.mesdag.portlib.network.IPortPacket;
import org.mesdag.portlib.network.PortPacketDistributor;
import org.mesdag.portlib.network.PortRegistryFriendlyByteBuf;
import org.mesdag.portlib.network.codec.PortByteBufCodecs;
import org.mesdag.portlib.network.codec.PortStreamCodec;

/// 清除连接配置器当前节点目标，但保持容器菜单继续打开。
public record C2SClearLinkEndpointPayload(int menuId) implements IPortPacket.C2S {
    public static final ResourceLocation ID = StaticLogistics.asResource("request_clear_link_endpoint");
    public static final PortStreamCodec<PortRegistryFriendlyByteBuf,
        C2SClearLinkEndpointPayload> STREAM_CODEC =
        PortStreamCodec.composite(PortByteBufCodecs.VAR_INT,
            C2SClearLinkEndpointPayload::menuId, C2SClearLinkEndpointPayload::new);

    @Override
    public ResourceLocation identifier() {
        return ID;
    }

    public static void handle(
        C2SClearLinkEndpointPayload payload,
        Player context
    ) {

        if (!(context instanceof ServerPlayer player)
            || !(player.containerMenu instanceof LinkConfiguratorMenu menu)) {
            return;
        }
        if (menu.containerId != payload.menuId()) return;
        menu.clearTarget();
        PortPacketDistributor.sendToPlayer(
            player, new S2CClearLinkEndpointPayload(menu.containerId));
        menu.broadcastFullState();

    }

    @Override
    public void work(ServerPlayer packetPlayer) {
        handle(this, packetPlayer);
    }
}
