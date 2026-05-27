package com.coobird.staticlogistics.network.c2s;

import com.coobird.staticlogistics.Staticlogistics;
import com.coobird.staticlogistics.core.manager.GlobalLogisticsManager;
import com.coobird.staticlogistics.core.service.GroupService;
import com.coobird.staticlogistics.item.LinkConfiguratorItem;
import com.coobird.staticlogistics.registry.SLDataComponents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record C2SGroupRenamePayload(
    String oldGroupId,
    String newGroupId
) implements CustomPacketPayload {

    public static final Type<C2SGroupRenamePayload> TYPE = new Type<>(Staticlogistics.asResource("group_rename"));

    public static final StreamCodec<RegistryFriendlyByteBuf, C2SGroupRenamePayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.STRING_UTF8, C2SGroupRenamePayload::oldGroupId,
        ByteBufCodecs.STRING_UTF8, C2SGroupRenamePayload::newGroupId,
        C2SGroupRenamePayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(final C2SGroupRenamePayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            var player = context.player();
            var server = player.getServer();
            if (server == null) return;
            GlobalLogisticsManager globalManager = GlobalLogisticsManager.get(server);
            GroupService.renameGroup(player.level(), player, payload.oldGroupId(), payload.newGroupId(), globalManager);
            updateConfiguratorGroup(player, payload.oldGroupId(), payload.newGroupId());
        });
    }

    private static void updateConfiguratorGroup(net.minecraft.world.entity.player.Player player, String oldId, String newId) {
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = player.getItemInHand(hand);
            if (stack.getItem() instanceof LinkConfiguratorItem) {
                String currentGroup = stack.getOrDefault(SLDataComponents.SELECTED_GROUP.get(), "");
                if (currentGroup.equals(oldId)) {
                    stack.set(SLDataComponents.SELECTED_GROUP.get(), newId);
                }
            }
        }
    }
}