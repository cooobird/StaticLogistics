package com.coobird.staticlogistics.network.c2s;

import com.coobird.staticlogistics.Staticlogistics;
import com.coobird.staticlogistics.api.type.ToolMode;
import com.coobird.staticlogistics.item.BlueprintItem;
import com.coobird.staticlogistics.item.LinkConfiguratorItem;
import com.coobird.staticlogistics.registry.SLDataComponents;
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

public record C2SUpdateToolSettingsPayload(String groupId, int mode,
                                           int typeMask) implements CustomPacketPayload {
    public static final Type<C2SUpdateToolSettingsPayload> TYPE = new Type<>(Staticlogistics.asResource("update_tool_settings"));

    public static final StreamCodec<RegistryFriendlyByteBuf, C2SUpdateToolSettingsPayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.STRING_UTF8, C2SUpdateToolSettingsPayload::groupId,
        ByteBufCodecs.VAR_INT, C2SUpdateToolSettingsPayload::mode,
        ByteBufCodecs.VAR_INT, C2SUpdateToolSettingsPayload::typeMask,
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
            if (!(stack.getItem() instanceof LinkConfiguratorItem) && !(stack.getItem() instanceof BlueprintItem)) {
                stack = player.getItemInHand(InteractionHand.OFF_HAND);
                if (!(stack.getItem() instanceof LinkConfiguratorItem) && !(stack.getItem() instanceof BlueprintItem))
                    return;
            }

            String rawId = payload.groupId().trim();
            // 去除控制字符，保留中英文、数字、空格、下划线、连字符
            String safeId = rawId.replaceAll("[^\\p{L}\\p{N}_\\- ]", "");
            String finalId = safeId.isEmpty() ? "" : safeId.substring(0, Math.min(safeId.length(), 32));
            stack.set(SLDataComponents.SELECTED_GROUP.get(), finalId);

            int finalMask = payload.typeMask();
            stack.set(SLDataComponents.SELECTED_TYPES_MASK.get(), finalMask);

            int vMode = Mth.clamp(payload.mode(), 0, ToolMode.values().length - 1);
            int currentMode = stack.getOrDefault(SLDataComponents.TOOL_MODE.get(), 0);

            if (currentMode != vMode) {
                stack.set(SLDataComponents.TOOL_MODE.get(), vMode);
                var nodes = stack.getOrDefault(SLDataComponents.STORED_NODES.get(), List.of());
                player.displayClientMessage(Component.translatable(
                    nodes.isEmpty() ? "msg.staticlogistics.mode_switched" : "msg.staticlogistics.mode_switched_with_nodes",
                    ToolMode.values()[vMode].getDisplayName(),
                    nodes.size()
                ), true);
            }
        });
    }
}