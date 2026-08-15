package com.coobird.staticlogistics.network.c2s;

import PortLib.extensions.net.minecraft.world.item.ItemStack.PortItemStackExtension;
import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.content.item.BlueprintItem;
import com.coobird.staticlogistics.logistics.SLDataComponents;
import com.coobird.staticlogistics.network.ServerPacketRateLimiter;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.mesdag.portlib.network.IPortPacket;
import org.mesdag.portlib.network.PortRegistryFriendlyByteBuf;
import org.mesdag.portlib.network.codec.PortStreamCodec;

public record C2SUpdateBlueprintPreviewPayload(BlockPos previewAnchor,
                                               int rotation) implements IPortPacket.C2S {
    public static final ResourceLocation ID = StaticLogistics.asResource("update_blueprint_preview");
    public static final PortStreamCodec<PortRegistryFriendlyByteBuf, C2SUpdateBlueprintPreviewPayload> STREAM_CODEC = new PortStreamCodec<>() {
        @Override
        public C2SUpdateBlueprintPreviewPayload decode(PortRegistryFriendlyByteBuf buffer) {
            FriendlyByteBuf fbuf = buffer;
            return new C2SUpdateBlueprintPreviewPayload(fbuf.readBlockPos(), fbuf.readVarInt());
        }

        @Override
        public void encode(PortRegistryFriendlyByteBuf buffer, C2SUpdateBlueprintPreviewPayload value) {
            FriendlyByteBuf fbuf = buffer;
            fbuf.writeBlockPos(value.previewAnchor());
            fbuf.writeVarInt(value.rotation());
        }
    };

    @Override
    public ResourceLocation identifier() {
        return ID;
    }

    @Override
    public void work(ServerPlayer player) {
        if (!ServerPacketRateLimiter.allow(
            player, ServerPacketRateLimiter.Action.BLUEPRINT_PREVIEW)) return;
        ItemStack stack = player.getItemInHand(InteractionHand.MAIN_HAND);
        if (!(stack.getItem() instanceof BlueprintItem)) {
            stack = player.getItemInHand(InteractionHand.OFF_HAND);
            if (!(stack.getItem() instanceof BlueprintItem)) return;
        }
        PortItemStackExtension.setData(stack, SLDataComponents.BLUEPRINT_PREVIEW_ANCHOR.get(), previewAnchor.toShortString());
        PortItemStackExtension.setData(stack, SLDataComponents.BLUEPRINT_PREVIEW_ROTATION.get(), rotation & 3);
    }
}
