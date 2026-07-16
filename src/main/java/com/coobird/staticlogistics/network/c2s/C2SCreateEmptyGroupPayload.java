package com.coobird.staticlogistics.network.c2s;

import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.logistics.group.GroupCommandService;
import com.coobird.staticlogistics.content.item.LinkConfiguratorItem;
import com.coobird.staticlogistics.content.item.LinkConfiguratorSelection;
import com.coobird.staticlogistics.api.group.GroupConstraints;
import com.coobird.staticlogistics.network.BoundedNetworkCodecs;
import com.coobird.staticlogistics.network.s2c.S2CGroupDirectoryPayload;
import com.coobird.staticlogistics.network.TeamPacketSync;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 客户端通知服务端创建了一个新的空分组
 */
public record C2SCreateEmptyGroupPayload(String groupId) implements CustomPacketPayload {
    public static final Type<C2SCreateEmptyGroupPayload> TYPE = new Type<>(StaticLogistics.asResource("create_empty_group"));

    public static final StreamCodec<RegistryFriendlyByteBuf, C2SCreateEmptyGroupPayload> STREAM_CODEC = StreamCodec.composite(
        BoundedNetworkCodecs.GROUP_NAME, C2SCreateEmptyGroupPayload::groupId,
        C2SCreateEmptyGroupPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(final C2SCreateEmptyGroupPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            var player = context.player();
            if (!(player instanceof net.minecraft.server.level.ServerPlayer serverPlayer)) return;
            boolean holdsTool = player.getMainHandItem().getItem() instanceof LinkConfiguratorItem
                || player.getOffhandItem().getItem() instanceof LinkConfiguratorItem;
            if (!holdsTool || player.getServer() == null) return;
            try {
                String groupId = GroupConstraints.normalizeName(payload.groupId());
                var created = new GroupCommandService(player.getServer()).create(serverPlayer, groupId);
                LinkConfiguratorSelection.select(player, created);
                var groups = com.coobird.staticlogistics.logistics.group.PlayerGroupStore.get(player.getServer())
                    .getGroupRefs(player.getUUID());
                TeamPacketSync.send(serverPlayer,
                    new S2CGroupDirectoryPayload(player.getUUID(), groups));
            } catch (IllegalArgumentException | IllegalStateException ignored) {
                // 无效请求不改变服务端状态。
            }
        });
    }
}
