package com.coobird.staticlogistics.network.c2s;

import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.api.group.GroupKey;
import com.coobird.staticlogistics.content.menu.LinkConfiguratorMenu;
import com.coobird.staticlogistics.logistics.node.NodeMutationService;
import com.coobird.staticlogistics.network.s2c.S2CSelectLinkEndpointPayload;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/// 在现有连接配置菜单内切换服务端权威节点，不重新创建菜单。
public record C2SOpenLinkEndpointPayload(GroupKey groupKey, LogisticsNode node,
                                         boolean inputSide, int menuId, long requestId) implements CustomPacketPayload {
    public static final Type<C2SOpenLinkEndpointPayload> TYPE = new Type<>(StaticLogistics.asResource("open_link_endpoint"));

    public static final StreamCodec<RegistryFriendlyByteBuf, C2SOpenLinkEndpointPayload> STREAM_CODEC =
        StreamCodec.composite(
            GroupKey.STREAM_CODEC, C2SOpenLinkEndpointPayload::groupKey,
            LogisticsNode.STREAM_CODEC, C2SOpenLinkEndpointPayload::node,
            ByteBufCodecs.BOOL, C2SOpenLinkEndpointPayload::inputSide,
            ByteBufCodecs.VAR_INT, C2SOpenLinkEndpointPayload::menuId,
            ByteBufCodecs.VAR_LONG, C2SOpenLinkEndpointPayload::requestId,
            C2SOpenLinkEndpointPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(C2SOpenLinkEndpointPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player) || !(player.containerMenu instanceof LinkConfiguratorMenu menu))
                return;
            if (menu.containerId != payload.menuId()) return;
            boolean accepted = false;
            try {
                NodeMutationService.ValidatedNode resolved = new NodeMutationService()
                    .resolveRemoteView(player, payload.node(), payload.groupKey());
                if (resolved == null) return;
                menu.selectTarget(resolved, payload.groupKey(), payload.inputSide());
                accepted = true;
                PacketDistributor.sendToPlayer(player,
                    new S2CSelectLinkEndpointPayload(
                        menu.containerId, payload.groupKey(), payload.node(), payload.inputSide(),
                        resolved.config().getSelectedTypeIds(), payload.requestId()));
            } finally {
                menu.acknowledgeConfiguration(payload.requestId(), accepted);
            }
        });
    }
}
