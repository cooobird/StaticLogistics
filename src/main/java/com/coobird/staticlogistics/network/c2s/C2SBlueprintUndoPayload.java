package com.coobird.staticlogistics.network.c2s;

import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.content.item.BlueprintItem;
import com.coobird.staticlogistics.logistics.blueprint.BlueprintPasteService;
import com.coobird.staticlogistics.network.ServerPacketRateLimiter;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 客户端请求撤销蓝图粘贴
 */
public record C2SBlueprintUndoPayload() implements CustomPacketPayload {
    public static final Type<C2SBlueprintUndoPayload> TYPE = new Type<>(StaticLogistics.asResource("blueprint_undo"));
    public static final StreamCodec<RegistryFriendlyByteBuf, C2SBlueprintUndoPayload> STREAM_CODEC =
        StreamCodec.unit(new C2SBlueprintUndoPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(C2SBlueprintUndoPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player
                && player.level() instanceof ServerLevel level
                && ServerPacketRateLimiter.allow(
                player, ServerPacketRateLimiter.Action.BLUEPRINT_UNDO)) {
                boolean holdsBlueprint = player.getItemInHand(InteractionHand.MAIN_HAND).getItem() instanceof BlueprintItem
                    || player.getItemInHand(InteractionHand.OFF_HAND).getItem() instanceof BlueprintItem;
                if (!holdsBlueprint) return;
                BlueprintPasteService.undoPaste(level, player);
            }
        });
    }
}
