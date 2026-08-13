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
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record C2SGroupRenamePayload(GroupKey groupKey, String newGroupId) implements CustomPacketPayload {
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
        });
    }

}
