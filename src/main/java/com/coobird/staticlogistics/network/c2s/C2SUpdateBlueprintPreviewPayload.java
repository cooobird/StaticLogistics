package com.coobird.staticlogistics.network.c2s;

import com.coobird.staticlogistics.Staticlogistics;
import com.coobird.staticlogistics.item.BlueprintItem;
import com.coobird.staticlogistics.registry.SLDataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 客户端滚轮调整预览位置/旋转 → 服务端同步 BLUEPRINT_PREVIEW_ANCHOR / BLUEPRINT_PREVIEW_ROTATION。
 */
public record C2SUpdateBlueprintPreviewPayload(BlockPos previewAnchor,
                                               int rotation) implements CustomPacketPayload {

    public static final Type<C2SUpdateBlueprintPreviewPayload> TYPE =
        new Type<>(Staticlogistics.asResource("update_blueprint_preview"));

    public static final StreamCodec<RegistryFriendlyByteBuf, C2SUpdateBlueprintPreviewPayload> STREAM_CODEC =
        StreamCodec.composite(
            BlockPos.STREAM_CODEC, C2SUpdateBlueprintPreviewPayload::previewAnchor,
            ByteBufCodecs.VAR_INT, C2SUpdateBlueprintPreviewPayload::rotation,
            C2SUpdateBlueprintPreviewPayload::new
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(final C2SUpdateBlueprintPreviewPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            var player = context.player();
            ItemStack stack = player.getItemInHand(InteractionHand.MAIN_HAND);
            if (!(stack.getItem() instanceof BlueprintItem)) {
                stack = player.getItemInHand(InteractionHand.OFF_HAND);
                if (!(stack.getItem() instanceof BlueprintItem)) return;
            }

            stack.set(SLDataComponents.BLUEPRINT_PREVIEW_ANCHOR.get(), payload.previewAnchor().toShortString());
            stack.set(SLDataComponents.BLUEPRINT_PREVIEW_ROTATION.get(), payload.rotation() & 3);
        });
    }
}
