package com.coobird.staticlogistics.network.c2s;

import com.coobird.staticlogistics.Staticlogistics;
import com.coobird.staticlogistics.common.init.SLDataComponents;
import com.coobird.staticlogistics.common.item.LinkConfiguratorItem;
import com.coobird.staticlogistics.transfer.TransferType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;

public record C2SUpdateToolSettingsPayload(int priority, String groupId, int mode,
                                           TransferType transferType) implements CustomPacketPayload {
    public static final Type<C2SUpdateToolSettingsPayload> TYPE = new Type<>(Staticlogistics.asResource("update_tool_settings"));

    public static final StreamCodec<RegistryFriendlyByteBuf, C2SUpdateToolSettingsPayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT, C2SUpdateToolSettingsPayload::priority,
        ByteBufCodecs.STRING_UTF8, C2SUpdateToolSettingsPayload::groupId,
        ByteBufCodecs.VAR_INT, C2SUpdateToolSettingsPayload::mode,
        TransferType.STREAM_CODEC, C2SUpdateToolSettingsPayload::transferType,
        C2SUpdateToolSettingsPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(final C2SUpdateToolSettingsPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            var player = context.player();
            ItemStack stack = player.getItemInHand(InteractionHand.MAIN_HAND);
            if (!(stack.getItem() instanceof LinkConfiguratorItem)) {
                stack = player.getItemInHand(InteractionHand.OFF_HAND);
                if (!(stack.getItem() instanceof LinkConfiguratorItem)) return;
            }

            stack.set(SLDataComponents.PRIORITY.get(), Mth.clamp(payload.priority(), -128, 127));
            String safeId = payload.groupId().trim().replaceAll("[^a-zA-Z0-9_\\-]", "");
            stack.set(SLDataComponents.SELECTED_GROUP.get(), safeId.isEmpty() ? "1" : safeId.substring(0, Math.min(safeId.length(), 32)));
            stack.set(SLDataComponents.SELECTED_TYPE.get(), payload.transferType());

            int vMode = Mth.clamp(payload.mode(), 0, LinkConfiguratorItem.ToolMode.values().length - 1);
            if (stack.getOrDefault(SLDataComponents.TOOL_MODE.get(), 0) != vMode) {
                stack.set(SLDataComponents.TOOL_MODE.get(), vMode);
                var nodes = stack.getOrDefault(SLDataComponents.STORED_NODES.get(), List.of());
                player.displayClientMessage(Component.translatable(nodes.isEmpty() ? "msg.staticlogistics.mode_switched" : "msg.staticlogistics.mode_switched_with_nodes", LinkConfiguratorItem.ToolMode.values()[vMode].getDisplayName(), nodes.size()), true);
            }
        });
    }
}