package com.coobird.staticlogistics.network.c2s;

import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.api.group.GroupKey;
import com.coobird.staticlogistics.logistics.group.PermissionService;
import com.coobird.staticlogistics.logistics.group.PlayerGroupStore;
import com.coobird.staticlogistics.logistics.node.NodeInteractionValidator;
import com.coobird.staticlogistics.logistics.redstone.RedstoneControlBinding;
import com.coobird.staticlogistics.logistics.redstone.RedstoneControlStore;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 从网络预览解除共享同一检测点的整组红石控制。
 */
public record C2SRemoveRedstoneControlGroupPayload(
    GroupKey groupKey, RedstoneControlBinding binding
) implements CustomPacketPayload {
    public static final Type<C2SRemoveRedstoneControlGroupPayload> TYPE =
        new Type<>(StaticLogistics.asResource("remove_redstone_control_group"));
    public static final StreamCodec<RegistryFriendlyByteBuf,
        C2SRemoveRedstoneControlGroupPayload> STREAM_CODEC = StreamCodec.composite(
        GroupKey.STREAM_CODEC, C2SRemoveRedstoneControlGroupPayload::groupKey,
        RedstoneControlBinding.STREAM_CODEC,
        C2SRemoveRedstoneControlGroupPayload::binding,
        C2SRemoveRedstoneControlGroupPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(C2SRemoveRedstoneControlGroupPayload payload,
                              IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                || !NodeInteractionValidator.holdsConfigurator(player)
                || !PermissionService.getInstance().canModify(
                payload.groupKey().ownerId(), player)
                || PlayerGroupStore.get(player.server).findGroup(payload.groupKey()) == null) {
                return;
            }
            RedstoneControlStore.get(player.server)
                .unbindGroup(payload.groupKey(), payload.binding());
            C2SQueryRedstoneGroupPayload.sendGroupToAuthorized(
                player, payload.groupKey());
        });
    }
}
