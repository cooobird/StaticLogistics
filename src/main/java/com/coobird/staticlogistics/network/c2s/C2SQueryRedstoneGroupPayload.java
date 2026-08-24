package com.coobird.staticlogistics.network.c2s;

import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.api.group.GroupKey;
import com.coobird.staticlogistics.logistics.group.PermissionService;
import com.coobird.staticlogistics.logistics.group.PlayerGroupStore;
import com.coobird.staticlogistics.logistics.node.NodeInteractionValidator;
import com.coobird.staticlogistics.logistics.redstone.RedstoneControlStore;
import com.coobird.staticlogistics.network.SLNetwork;
import com.coobird.staticlogistics.network.s2c.S2CRedstoneControlGroupPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.mesdag.portlib.network.IPortPacket;
import org.mesdag.portlib.network.PortRegistryFriendlyByteBuf;
import org.mesdag.portlib.network.codec.PortStreamCodec;

/**
 * 请求当前物流分组的红石控制结构投影。
 */
public record C2SQueryRedstoneGroupPayload(GroupKey groupKey)
    implements IPortPacket.C2S {
    public static final ResourceLocation ID = StaticLogistics.asResource("query_redstone_group");
    public static final PortStreamCodec<PortRegistryFriendlyByteBuf,
        C2SQueryRedstoneGroupPayload> STREAM_CODEC = PortStreamCodec.composite(
        GroupKey.STREAM_CODEC, C2SQueryRedstoneGroupPayload::groupKey,
        C2SQueryRedstoneGroupPayload::new);

    @Override
    public ResourceLocation identifier() {
        return ID;
    }

    @Override
    public void work(ServerPlayer player) {
        if (!NodeInteractionValidator.holdsConfigurator(player)
            || !PermissionService.getInstance().canModify(groupKey().ownerId(), player)
            || PlayerGroupStore.get(player.server).findGroup(groupKey()) == null) return;
        sendGroup(player, groupKey());
    }

    public static void sendGroup(ServerPlayer player, GroupKey groupKey) {
        var entries = RedstoneControlStore.get(player.server).getBindings(groupKey)
            .entrySet().stream()
            .map(entry -> S2CRedstoneControlGroupPayload.Entry.from(
                player.server, entry.getKey(), entry.getValue()))
            .toList();
        int pageCount = Math.max(1, (entries.size()
            + S2CRedstoneControlGroupPayload.MAX_PAGE_ENTRIES - 1)
            / S2CRedstoneControlGroupPayload.MAX_PAGE_ENTRIES);
        for (int page = 0; page < pageCount; page++) {
            int from = page * S2CRedstoneControlGroupPayload.MAX_PAGE_ENTRIES;
            int to = Math.min(entries.size(), from
                + S2CRedstoneControlGroupPayload.MAX_PAGE_ENTRIES);
            SLNetwork.HANDLER.sendToPlayer(player,
                new S2CRedstoneControlGroupPayload(groupKey, page == 0,
                    entries.subList(from, to)));
        }
    }

    public static void sendGroupToAuthorized(ServerPlayer actor, GroupKey groupKey) {
        PermissionService permissions = PermissionService.getInstance();
        for (ServerPlayer player : actor.server.getPlayerList().getPlayers()) {
            if (permissions.canAccess(groupKey.ownerId(), player)) sendGroup(player, groupKey);
        }
    }
}
