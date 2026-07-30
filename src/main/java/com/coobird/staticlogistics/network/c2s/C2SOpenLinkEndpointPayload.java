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

import java.util.Objects;

/**
 * 在现有连接配置菜单内切换服务端权威节点，不重新创建菜单。
 */
public record C2SOpenLinkEndpointPayload(GroupKey groupKey, LogisticsNode node, boolean inputSide)
    implements CustomPacketPayload {
    public static final Type<C2SOpenLinkEndpointPayload> TYPE =
        new Type<>(StaticLogistics.asResource("open_link_endpoint"));
    public static final StreamCodec<RegistryFriendlyByteBuf, C2SOpenLinkEndpointPayload> STREAM_CODEC =
        StreamCodec.composite(
            GroupKey.STREAM_CODEC, C2SOpenLinkEndpointPayload::groupKey,
            LogisticsNode.STREAM_CODEC, C2SOpenLinkEndpointPayload::node,
            ByteBufCodecs.BOOL, C2SOpenLinkEndpointPayload::inputSide,
            C2SOpenLinkEndpointPayload::new);

    public C2SOpenLinkEndpointPayload {
        Objects.requireNonNull(groupKey, "Group key must not be null");
        Objects.requireNonNull(node, "Node must not be null");
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(C2SOpenLinkEndpointPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            NodeMutationService.ValidatedNode resolved = new NodeMutationService()
                .resolveRemote(player, payload.node(), payload.groupKey());
            if (resolved == null) return;
            if (!(player.containerMenu instanceof LinkConfiguratorMenu menu)) return;
            menu.selectTarget(resolved, payload.groupKey(), payload.inputSide());
            PacketDistributor.sendToPlayer(player,
                new S2CSelectLinkEndpointPayload(
                    payload.groupKey(), payload.node(), payload.inputSide(),
                    resolved.config().getSelectedTypeIds()));
            menu.broadcastChanges();
        });
    }
}
