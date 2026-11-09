package com.coobird.staticlogistics.network.c2s;

import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.api.group.GroupKey;
import com.coobird.staticlogistics.content.menu.LinkConfiguratorMenu;
import com.coobird.staticlogistics.logistics.node.NodeMutationService;
import com.coobird.staticlogistics.network.s2c.S2CSelectLinkEndpointPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.mesdag.portlib.network.IPortPacket;
import org.mesdag.portlib.network.PortPacketDistributor;
import org.mesdag.portlib.network.PortRegistryFriendlyByteBuf;
import org.mesdag.portlib.network.codec.PortByteBufCodecs;
import org.mesdag.portlib.network.codec.PortStreamCodec;

/// 在现有连接配置菜单内切换服务端权威节点，不重新创建菜单。
public record C2SOpenLinkEndpointPayload(GroupKey groupKey, LogisticsNode node,
                                         boolean inputSide, int menuId, long requestId) implements IPortPacket.C2S {
    public static final ResourceLocation ID = StaticLogistics.asResource("open_link_endpoint");

    public static final PortStreamCodec<PortRegistryFriendlyByteBuf, C2SOpenLinkEndpointPayload> STREAM_CODEC =
        PortStreamCodec.composite(
            GroupKey.STREAM_CODEC, C2SOpenLinkEndpointPayload::groupKey,
            LogisticsNode.STREAM_CODEC, C2SOpenLinkEndpointPayload::node,
            PortByteBufCodecs.BOOL, C2SOpenLinkEndpointPayload::inputSide,
            PortByteBufCodecs.VAR_INT, C2SOpenLinkEndpointPayload::menuId,
            PortByteBufCodecs.VAR_LONG, C2SOpenLinkEndpointPayload::requestId,
            C2SOpenLinkEndpointPayload::new);

    @Override
    public ResourceLocation identifier() {
        return ID;
    }

    @Override
    public void work(ServerPlayer packetPlayer) {
        C2SOpenLinkEndpointPayload payload = this;
        Player context = packetPlayer;

        if (!(context instanceof ServerPlayer player) || !(player.containerMenu instanceof LinkConfiguratorMenu menu))
            return;
        if (menu.containerId != payload.menuId()) return;
        boolean accepted = false;
        try {
            NodeMutationService.ValidatedNode resolved = new NodeMutationService()
                .resolveRemoteView(player, payload.node(), payload.groupKey());
            if (resolved == null) return;
            menu.selectTarget(resolved, payload.groupKey(), payload.inputSide());
            accepted = true;
            PortPacketDistributor.sendToPlayer(player,
                new S2CSelectLinkEndpointPayload(
                    menu.containerId, payload.groupKey(), payload.node(), payload.inputSide(),
                    resolved.config().getSelectedTypeIds(), payload.requestId()));
        } finally {
            menu.acknowledgeConfiguration(payload.requestId(), accepted);
        }

    }
}
