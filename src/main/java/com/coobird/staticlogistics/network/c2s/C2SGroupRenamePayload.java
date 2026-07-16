package com.coobird.staticlogistics.network.c2s;

import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.content.item.LinkConfiguratorItem;
import com.coobird.staticlogistics.content.item.LinkConfiguratorSelection;
import com.coobird.staticlogistics.logistics.group.GroupCommandService;
import com.coobird.staticlogistics.api.group.GroupKey;
import com.coobird.staticlogistics.logistics.group.PlayerGroupStore;
import com.coobird.staticlogistics.network.TeamPacketSync;
import com.coobird.staticlogistics.network.s2c.S2CGroupDirectoryPayload;
import com.coobird.staticlogistics.network.BoundedNetworkCodecs;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record C2SGroupRenamePayload(
    GroupKey groupKey,
    String newGroupId
) implements CustomPacketPayload {

    public static final Type<C2SGroupRenamePayload> TYPE = new Type<>(StaticLogistics.asResource("group_rename"));

    public static final StreamCodec<RegistryFriendlyByteBuf, C2SGroupRenamePayload> STREAM_CODEC =
        StreamCodec.of(
            (buf, payload) -> {
                GroupKey.STREAM_CODEC.encode(buf, payload.groupKey());
                BoundedNetworkCodecs.GROUP_NAME.encode(buf, payload.newGroupId());
            },
            buf -> new C2SGroupRenamePayload(
                GroupKey.STREAM_CODEC.decode(buf),
                BoundedNetworkCodecs.GROUP_NAME.decode(buf)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(final C2SGroupRenamePayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            var player = context.player();
            if (!(player instanceof net.minecraft.server.level.ServerPlayer serverPlayer)) return;
            boolean holdsTool = player.getMainHandItem().getItem() instanceof LinkConfiguratorItem
                || player.getOffhandItem().getItem() instanceof LinkConfiguratorItem;
            if (!holdsTool) return;
            var server = player.getServer();
            if (server == null) return;
            var target = PlayerGroupStore.get(server).findGroup(payload.groupKey());
            if (target != null && new GroupCommandService(server).rename(
                serverPlayer, payload.groupKey(), payload.newGroupId())) {
                var result = PlayerGroupStore.get(server)
                    .findGroup(payload.groupKey().ownerId(), payload.newGroupId());
                LinkConfiguratorSelection.replaceIfSelected(
                    player, target.key(), target.displayName(), result);
                TeamPacketSync.send(serverPlayer, new S2CGroupDirectoryPayload(
                    payload.groupKey().ownerId(), PlayerGroupStore.get(server)
                        .getGroupRefs(payload.groupKey().ownerId())));
            }
        });
    }

}
