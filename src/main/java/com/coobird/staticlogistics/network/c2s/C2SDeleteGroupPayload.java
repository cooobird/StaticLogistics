package com.coobird.staticlogistics.network.c2s;

import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.api.group.GroupKey;
import com.coobird.staticlogistics.content.item.LinkConfiguratorSelection;
import com.coobird.staticlogistics.content.menu.LinkConfiguratorMenu;
import com.coobird.staticlogistics.logistics.group.GroupCommandService;
import com.coobird.staticlogistics.logistics.group.PlayerGroupStore;
import com.coobird.staticlogistics.logistics.node.NodeInteractionValidator;
import com.coobird.staticlogistics.network.TeamPacketSync;
import com.coobird.staticlogistics.network.s2c.S2CClearLinkEndpointPayload;
import com.coobird.staticlogistics.network.s2c.S2CGroupDirectoryPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.mesdag.portlib.network.IPortPacket;
import org.mesdag.portlib.network.PortPacketDistributor;
import org.mesdag.portlib.network.PortRegistryFriendlyByteBuf;
import org.mesdag.portlib.network.codec.PortStreamCodec;

/// 客户端请求删除指定分组及其所有链接。
public record C2SDeleteGroupPayload(GroupKey groupKey) implements IPortPacket.C2S {

    public static final ResourceLocation ID = StaticLogistics.asResource("delete_group");

    public static final PortStreamCodec<PortRegistryFriendlyByteBuf, C2SDeleteGroupPayload> STREAM_CODEC =
        PortStreamCodec.composite(GroupKey.STREAM_CODEC, C2SDeleteGroupPayload::groupKey, C2SDeleteGroupPayload::new);

    @Override
    public ResourceLocation identifier() {
        return ID;
    }

    public static void handle(final C2SDeleteGroupPayload payload, final Player context) {

        var player = context;
        if (!(player instanceof ServerPlayer sp)) return;
        if (!NodeInteractionValidator.holdsConfigurator(sp)) return;
        var server = sp.getServer();
        if (server == null) return;

        var target = PlayerGroupStore.get(server).findGroup(payload.groupKey());
        if (target != null && new GroupCommandService(server).delete(sp, payload.groupKey())) {
            LinkConfiguratorSelection.clearIfSelected(sp, target.key(), target.displayName());
            if (sp.containerMenu instanceof LinkConfiguratorMenu menu
                && menu.hasTarget()
                && payload.groupKey().equals(menu.getRemoteGroupKey())) {
                menu.clearTarget();
                PortPacketDistributor.sendToPlayer(
                    sp, new S2CClearLinkEndpointPayload(menu.containerId));
                menu.broadcastFullState();
            }
            TeamPacketSync.send(sp, payload.groupKey().ownerId(),
                new S2CGroupDirectoryPayload(
                    payload.groupKey().ownerId(),
                    PlayerGroupStore.get(server).getGroupRefs(payload.groupKey().ownerId())));
        }

    }

    @Override
    public void work(ServerPlayer packetPlayer) {
        handle(this, packetPlayer);
    }
}
