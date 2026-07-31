package com.coobird.staticlogistics.network.c2s;

import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.content.item.LinkConfiguratorSelection;
import com.coobird.staticlogistics.content.menu.LinkConfiguratorMenu;
import com.coobird.staticlogistics.logistics.group.ConnectionCommandService;
import com.coobird.staticlogistics.logistics.node.ConnectionKey;
import com.coobird.staticlogistics.network.SLNetwork;
import com.coobird.staticlogistics.network.s2c.S2CClearLinkEndpointPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.mesdag.portlib.network.IPortPacket;
import org.mesdag.portlib.network.PortRegistryFriendlyByteBuf;
import org.mesdag.portlib.network.codec.PortStreamCodec;

/**
 * 删除一条连接，并回收删除后真正孤立的端点。
 */
public record C2SDeleteConnectionPayload(ConnectionKey connection) implements IPortPacket.C2S {
    public static final ResourceLocation ID = StaticLogistics.asResource("delete_connection");
    public static final PortStreamCodec<PortRegistryFriendlyByteBuf, C2SDeleteConnectionPayload> STREAM_CODEC =
        PortStreamCodec.composite(
            ConnectionKey.STREAM_CODEC, C2SDeleteConnectionPayload::connection,
            C2SDeleteConnectionPayload::new);

    @Override
    public ResourceLocation identifier() {
        return ID;
    }

    @Override
    public void work(ServerPlayer player) {
        if (!new ConnectionCommandService(player.server).delete(player, connection())) return;
        LinkConfiguratorSelection.clearConnectionIfSelected(player, connection());
        if (player.containerMenu instanceof LinkConfiguratorMenu menu
            && menu.hasTarget()
            && (menu.getTargetNode().equals(connection().first())
            || menu.getTargetNode().equals(connection().second()))) {
            menu.clearTarget();
            SLNetwork.HANDLER.sendToPlayer(player, new S2CClearLinkEndpointPayload());
            menu.broadcastChanges();
        }
    }
}
