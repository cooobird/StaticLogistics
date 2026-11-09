package com.coobird.staticlogistics.network.c2s;

import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.content.item.BlueprintModeHandler;
import com.coobird.staticlogistics.logistics.SLDataComponents;
import com.coobird.staticlogistics.logistics.blueprint.BlueprintData;
import com.coobird.staticlogistics.network.BoundedNetworkCodecs;
import com.coobird.staticlogistics.network.ServerPacketRateLimiter;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.mesdag.portlib.network.IPortPacket;
import org.mesdag.portlib.network.PortRegistryFriendlyByteBuf;
import org.mesdag.portlib.network.codec.PortByteBufCodecs;
import org.mesdag.portlib.network.codec.PortStreamCodec;

/// 客户端滚轮调整预览位置/旋转 → 服务端同步 BLUEPRINT_PREVIEW_ANCHOR / BLUEPRINT_PREVIEW_ROTATION。
public record C2SUpdateBlueprintPreviewPayload(BlockPos previewAnchor,
                                               int rotation) implements IPortPacket.C2S {

    public static final ResourceLocation ID = StaticLogistics.asResource("update_blueprint_preview");

    public static final PortStreamCodec<PortRegistryFriendlyByteBuf, C2SUpdateBlueprintPreviewPayload> STREAM_CODEC =
        PortStreamCodec.composite(
            BoundedNetworkCodecs.BLOCK_POS, C2SUpdateBlueprintPreviewPayload::previewAnchor,
            PortByteBufCodecs.VAR_INT, C2SUpdateBlueprintPreviewPayload::rotation,
            C2SUpdateBlueprintPreviewPayload::new
        );

    @Override
    public ResourceLocation identifier() {
        return ID;
    }

    public static void handle(final C2SUpdateBlueprintPreviewPayload payload, final Player context) {

        var player = context;
        if (!(player instanceof ServerPlayer serverPlayer)
            || !ServerPacketRateLimiter.allow(serverPlayer,
            ServerPacketRateLimiter.Action.BLUEPRINT_PREVIEW)) return;
        ItemStack stack = player.getItemInHand(InteractionHand.MAIN_HAND);
        if (!(BlueprintModeHandler.isPlacing(stack))) {
            stack = player.getItemInHand(InteractionHand.OFF_HAND);
            if (!(BlueprintModeHandler.isPlacing(stack))) return;
        }

        if (stack.getOrDefault(SLDataComponents.BLUEPRINT_DATA.get(), BlueprintData.EMPTY).isEmpty()
            || stack.getOrDefault(SLDataComponents.BLUEPRINT_PREVIEW_ANCHOR.get(), "").isEmpty()
            || !player.level().dimension().location().toString().equals(stack.getOrDefault(SLDataComponents.BLUEPRINT_PREVIEW_DIMENSION.get(), "")))
            return;
        stack.set(SLDataComponents.BLUEPRINT_PREVIEW_ANCHOR.get(), payload.previewAnchor().toShortString());
        stack.set(SLDataComponents.BLUEPRINT_PREVIEW_ROTATION.get(), payload.rotation() & 3);

    }

    @Override
    public void work(ServerPlayer packetPlayer) {
        handle(this, packetPlayer);
    }
}
