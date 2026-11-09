package com.coobird.staticlogistics.network.c2s;

import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.api.group.GroupConstraints;
import com.coobird.staticlogistics.api.group.GroupKey;
import com.coobird.staticlogistics.content.item.LinkConfiguratorSelection;
import com.coobird.staticlogistics.logistics.group.GroupCommandService;
import com.coobird.staticlogistics.logistics.group.PlayerGroupStore;
import com.coobird.staticlogistics.logistics.node.NodeInteractionValidator;
import com.coobird.staticlogistics.network.BoundedNetworkCodecs;
import com.coobird.staticlogistics.network.TeamPacketSync;
import com.coobird.staticlogistics.network.s2c.S2CGroupDirectoryPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.mesdag.portlib.network.IPortPacket;
import org.mesdag.portlib.network.PortRegistryFriendlyByteBuf;
import org.mesdag.portlib.network.codec.PortStreamCodec;

public record C2SGroupRenamePayload(GroupKey groupKey, String newGroupId) implements IPortPacket.C2S {
    public static final ResourceLocation ID = StaticLogistics.asResource("group_rename");

    public static final PortStreamCodec<PortRegistryFriendlyByteBuf, C2SGroupRenamePayload> STREAM_CODEC =
        PortStreamCodec.ofMember((payload, buf) -> {
                GroupKey.STREAM_CODEC.encode(buf, payload.groupKey());
                BoundedNetworkCodecs.GROUP_NAME.encode(buf, payload.newGroupId());
            },
            buf -> new C2SGroupRenamePayload(
                GroupKey.STREAM_CODEC.decode(buf),
                BoundedNetworkCodecs.GROUP_NAME.decode(buf)));

    @Override
    public ResourceLocation identifier() {
        return ID;
    }

    public static void handle(final C2SGroupRenamePayload payload, final Player context) {

        var player = context;
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        if (!NodeInteractionValidator.holdsConfigurator(serverPlayer)) return;
        var server = player.getServer();
        if (server == null) return;
        String newGroupName;
        try {
            newGroupName = GroupConstraints.normalizeName(payload.newGroupId());
        } catch (IllegalArgumentException exception) {
            return;
        }
        var target = PlayerGroupStore.get(server).findGroup(payload.groupKey());
        if (target != null && new GroupCommandService(server).rename(
            serverPlayer, payload.groupKey(), newGroupName)) {
            var result = PlayerGroupStore.get(server)
                .findGroup(payload.groupKey().ownerId(), newGroupName);
            LinkConfiguratorSelection.replaceIfSelected(
                player, target.key(), target.displayName(), result);
            TeamPacketSync.send(serverPlayer, payload.groupKey().ownerId(),
                new S2CGroupDirectoryPayload(
                    payload.groupKey().ownerId(), PlayerGroupStore.get(server)
                    .getGroupRefs(payload.groupKey().ownerId())));
        }

    }

    @Override
    public void work(ServerPlayer packetPlayer) {
        handle(this, packetPlayer);
    }
}
