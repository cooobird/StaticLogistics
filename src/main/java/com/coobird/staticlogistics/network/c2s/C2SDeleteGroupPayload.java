package com.coobird.staticlogistics.network.c2s;

import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.logistics.group.GroupCommandService;
import com.coobird.staticlogistics.content.item.LinkConfiguratorItem;
import com.coobird.staticlogistics.content.item.LinkConfiguratorSelection;
import com.coobird.staticlogistics.network.TeamPacketSync;
import com.coobird.staticlogistics.network.s2c.S2CGroupDirectoryPayload;
import com.coobird.staticlogistics.api.group.GroupKey;
import com.coobird.staticlogistics.logistics.group.PlayerGroupStore;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 客户端请求删除指定分组及其所有链接。
 */
public record C2SDeleteGroupPayload(GroupKey groupKey) implements CustomPacketPayload {

    public static final Type<C2SDeleteGroupPayload> TYPE = new Type<>(StaticLogistics.asResource("delete_group"));

    public static final StreamCodec<RegistryFriendlyByteBuf, C2SDeleteGroupPayload> STREAM_CODEC =
        StreamCodec.composite(GroupKey.STREAM_CODEC, C2SDeleteGroupPayload::groupKey,
            C2SDeleteGroupPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(final C2SDeleteGroupPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            var player = context.player();
            if (!(player instanceof ServerPlayer sp)) return;
            boolean holdsTool = sp.getMainHandItem().getItem() instanceof LinkConfiguratorItem
                || sp.getOffhandItem().getItem() instanceof LinkConfiguratorItem;
            if (!holdsTool) return;
            var server = sp.getServer();
            if (server == null) return;

            var target = PlayerGroupStore.get(server).findGroup(payload.groupKey());
            if (target != null && new GroupCommandService(server).delete(sp, payload.groupKey())) {
                LinkConfiguratorSelection.clearIfSelected(sp, target.key(), target.displayName());
                TeamPacketSync.send(sp, new S2CGroupDirectoryPayload(
                    payload.groupKey().ownerId(),
                    PlayerGroupStore.get(server).getGroupRefs(payload.groupKey().ownerId())));
            }
        });
    }
}
