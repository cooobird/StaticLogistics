package com.coobird.staticlogistics.network.c2s;

import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.api.group.GroupKey;
import com.coobird.staticlogistics.logistics.group.PermissionService;
import com.coobird.staticlogistics.logistics.group.PlayerGroupStore;
import com.coobird.staticlogistics.logistics.node.NodeInteractionValidator;
import com.coobird.staticlogistics.logistics.redstone.RedstoneControlBinding;
import com.coobird.staticlogistics.logistics.redstone.RedstoneControlStore;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.mesdag.portlib.network.IPortPacket;
import org.mesdag.portlib.network.PortRegistryFriendlyByteBuf;
import org.mesdag.portlib.network.codec.PortStreamCodec;

/**
 * 从网络预览解除共享同一检测点的整组红石控制。
 */
public record C2SRemoveRedstoneControlGroupPayload(
    GroupKey groupKey, RedstoneControlBinding binding
) implements IPortPacket.C2S {
    public static final ResourceLocation ID = StaticLogistics.asResource("remove_redstone_control_group");
    public static final PortStreamCodec<PortRegistryFriendlyByteBuf,
        C2SRemoveRedstoneControlGroupPayload> STREAM_CODEC = PortStreamCodec.composite(
        GroupKey.STREAM_CODEC, C2SRemoveRedstoneControlGroupPayload::groupKey,
        RedstoneControlBinding.STREAM_CODEC,
        C2SRemoveRedstoneControlGroupPayload::binding,
        C2SRemoveRedstoneControlGroupPayload::new);

    @Override
    public ResourceLocation identifier() {
        return ID;
    }

    @Override
    public void work(ServerPlayer player) {
        if (!NodeInteractionValidator.holdsConfigurator(player)
            || !PermissionService.getInstance().canModify(groupKey().ownerId(), player)
            || PlayerGroupStore.get(player.server).findGroup(groupKey()) == null) return;
        RedstoneControlStore.get(player.server).unbindGroup(groupKey(), binding());
        C2SQueryRedstoneGroupPayload.sendGroupToAuthorized(player, groupKey());
    }
}
