package com.coobird.staticlogistics.network.c2s;

import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.api.group.GroupKey;
import com.coobird.staticlogistics.logistics.group.PermissionService;
import com.coobird.staticlogistics.logistics.group.PlayerGroupStore;
import com.coobird.staticlogistics.logistics.node.NodeInteractionValidator;
import com.coobird.staticlogistics.logistics.redstone.RedstoneControlStore;
import com.coobird.staticlogistics.network.s2c.S2CRedstoneControlGroupPayload;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 请求当前物流分组的红石控制投影，用于网络预览范围框。
 */
public record C2SQueryRedstoneGroupPayload(GroupKey groupKey)
    implements CustomPacketPayload {
    public static final Type<C2SQueryRedstoneGroupPayload> TYPE =
        new Type<>(StaticLogistics.asResource("query_redstone_group"));
    public static final StreamCodec<RegistryFriendlyByteBuf, C2SQueryRedstoneGroupPayload>
        STREAM_CODEC = StreamCodec.composite(
        GroupKey.STREAM_CODEC, C2SQueryRedstoneGroupPayload::groupKey,
        C2SQueryRedstoneGroupPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(C2SQueryRedstoneGroupPayload payload,
                              IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                || !NodeInteractionValidator.holdsConfigurator(player)
                || !PermissionService.getInstance().canModify(
                payload.groupKey().ownerId(), player)
                || PlayerGroupStore.get(player.server).findGroup(payload.groupKey()) == null) {
                return;
            }
            sendGroup(player, payload.groupKey());
        });
    }

    public static void sendGroup(ServerPlayer player, GroupKey groupKey) {
        var bindings = RedstoneControlStore.get(player.server).getBindings(groupKey);
        var entries = bindings.entrySet().stream()
            .map(entry -> S2CRedstoneControlGroupPayload.Entry.from(
                player.server, entry.getKey(), entry.getValue()))
            .toList();
        int pageCount = Math.max(1, (entries.size()
            + S2CRedstoneControlGroupPayload.MAX_PAGE_ENTRIES - 1)
            / S2CRedstoneControlGroupPayload.MAX_PAGE_ENTRIES);
        for (int page = 0; page < pageCount; page++) {
            int from = page * S2CRedstoneControlGroupPayload.MAX_PAGE_ENTRIES;
            int to = Math.min(entries.size(),
                from + S2CRedstoneControlGroupPayload.MAX_PAGE_ENTRIES);
            PacketDistributor.sendToPlayer(player,
                new S2CRedstoneControlGroupPayload(groupKey, page == 0,
                    entries.subList(from, to)));
        }
    }

    /**
     * 绑定结构发生变化时，立即刷新所有有权查看该分组的在线玩家。
     */
    public static void sendGroupToAuthorized(ServerPlayer actor, GroupKey groupKey) {
        PermissionService permissions = PermissionService.getInstance();
        for (ServerPlayer player : actor.server.getPlayerList().getPlayers()) {
            if (permissions.canAccess(groupKey.ownerId(), player)) {
                sendGroup(player, groupKey);
            }
        }
    }
}
