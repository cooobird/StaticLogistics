package com.coobird.staticlogistics.network.c2s;

import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.api.group.GroupKey;
import com.coobird.staticlogistics.content.item.LinkConfiguratorSelection;
import com.coobird.staticlogistics.content.menu.LinkConfiguratorMenu;
import com.coobird.staticlogistics.logistics.group.GroupCommandService;
import com.coobird.staticlogistics.logistics.group.PlayerGroupStore;
import com.coobird.staticlogistics.logistics.node.NodeInteractionValidator;
import com.coobird.staticlogistics.network.SLNetwork;
import com.coobird.staticlogistics.network.TeamPacketSync;
import com.coobird.staticlogistics.network.s2c.S2CClearLinkEndpointPayload;
import com.coobird.staticlogistics.network.s2c.S2CGroupDirectoryPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.mesdag.portlib.network.IPortPacket;
import org.mesdag.portlib.network.PortRegistryFriendlyByteBuf;
import org.mesdag.portlib.network.codec.PortStreamCodec;

/**
 * 客户端请求删除指定分组及其全部链接。
 */
public record C2SDeleteGroupPayload(GroupKey groupKey) implements IPortPacket.C2S {
    public static final ResourceLocation ID = StaticLogistics.asResource("delete_group");
    public static final PortStreamCodec<PortRegistryFriendlyByteBuf, C2SDeleteGroupPayload> STREAM_CODEC =
        new PortStreamCodec<>() {
            @Override
            public C2SDeleteGroupPayload decode(PortRegistryFriendlyByteBuf buffer) {
                return new C2SDeleteGroupPayload(GroupKey.STREAM_CODEC.decode(buffer));
            }

            @Override
            public void encode(PortRegistryFriendlyByteBuf buffer, C2SDeleteGroupPayload value) {
                GroupKey.STREAM_CODEC.encode(buffer, value.groupKey());
            }
        };

    @Override
    public ResourceLocation identifier() {
        return ID;
    }

    @Override
    public void work(ServerPlayer player) {
        if (!NodeInteractionValidator.holdsConfigurator(player)
            || player.getServer() == null) return;
        var store = PlayerGroupStore.get(player.getServer());
        var target = store.findGroup(groupKey());
        if (target != null && new GroupCommandService(player.getServer()).delete(player, groupKey())) {
            LinkConfiguratorSelection.clearIfSelected(player, target.key(), target.displayName());
            if (player.containerMenu instanceof LinkConfiguratorMenu menu
                && menu.hasTarget()
                && groupKey().equals(menu.getRemoteGroupKey())) {
                menu.clearTarget();
                SLNetwork.HANDLER.sendToPlayer(player, new S2CClearLinkEndpointPayload());
                menu.broadcastFullState();
            }
            TeamPacketSync.send(player, groupKey().ownerId(), new S2CGroupDirectoryPayload(
                groupKey().ownerId(), store.getGroupRefs(groupKey().ownerId())));
        }
    }
}
