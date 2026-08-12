package com.coobird.staticlogistics.network.c2s;

import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.api.group.GroupKey;
import com.coobird.staticlogistics.content.menu.LinkConfiguratorMenu;
import com.coobird.staticlogistics.logistics.node.NodeMutationService;
import com.coobird.staticlogistics.network.SLNetwork;
import com.coobird.staticlogistics.network.s2c.S2CSelectLinkEndpointPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.mesdag.portlib.network.IPortPacket;
import org.mesdag.portlib.network.PortRegistryFriendlyByteBuf;
import org.mesdag.portlib.network.codec.PortStreamCodec;

import java.util.Objects;

/**
 * 在现有菜单中切换服务端已验证的节点，不重新创建菜单。
 */
public record C2SOpenLinkEndpointPayload(GroupKey groupKey, LogisticsNode node, boolean inputSide)
    implements IPortPacket.C2S {
    public static final ResourceLocation ID = StaticLogistics.asResource("open_link_endpoint");
    public static final PortStreamCodec<PortRegistryFriendlyByteBuf, C2SOpenLinkEndpointPayload> STREAM_CODEC =
        new PortStreamCodec<>() {
            @Override
            public C2SOpenLinkEndpointPayload decode(PortRegistryFriendlyByteBuf buffer) {
                return new C2SOpenLinkEndpointPayload(
                    GroupKey.STREAM_CODEC.decode(buffer),
                    LogisticsNode.STREAM_CODEC.decode(buffer),
                    buffer.readBoolean());
            }

            @Override
            public void encode(PortRegistryFriendlyByteBuf buffer, C2SOpenLinkEndpointPayload value) {
                GroupKey.STREAM_CODEC.encode(buffer, value.groupKey());
                LogisticsNode.STREAM_CODEC.encode(buffer, value.node());
                buffer.writeBoolean(value.inputSide());
            }
        };

    public C2SOpenLinkEndpointPayload {
        Objects.requireNonNull(groupKey, "Group key must not be null");
        Objects.requireNonNull(node, "Node must not be null");
    }

    @Override
    public ResourceLocation identifier() {
        return ID;
    }

    @Override
    public void work(ServerPlayer player) {
        NodeMutationService.ValidatedNode resolved = new NodeMutationService()
            .resolveRemote(player, node(), groupKey());
        if (resolved == null || !(player.containerMenu instanceof LinkConfiguratorMenu menu)) return;
        menu.selectTarget(resolved, groupKey(), inputSide());
        SLNetwork.HANDLER.sendToPlayer(player,
            new S2CSelectLinkEndpointPayload(
                groupKey(), node(), inputSide(), resolved.config().getSelectedTypeIds()));
        menu.broadcastFullState();
    }
}
