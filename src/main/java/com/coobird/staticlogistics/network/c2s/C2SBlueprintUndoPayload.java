package com.coobird.staticlogistics.network.c2s;

import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.content.item.BlueprintItem;
import com.coobird.staticlogistics.logistics.blueprint.BlueprintPasteService;
import com.coobird.staticlogistics.network.ServerPacketRateLimiter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import org.mesdag.portlib.network.IPortPacket;
import org.mesdag.portlib.network.PortRegistryFriendlyByteBuf;
import org.mesdag.portlib.network.codec.PortStreamCodec;

public record C2SBlueprintUndoPayload() implements IPortPacket.C2S {
    public static final ResourceLocation ID = StaticLogistics.asResource("blueprint_undo");
    public static final PortStreamCodec<PortRegistryFriendlyByteBuf, C2SBlueprintUndoPayload> STREAM_CODEC = new PortStreamCodec<>() {
        @Override
        public C2SBlueprintUndoPayload decode(PortRegistryFriendlyByteBuf buffer) {
            return new C2SBlueprintUndoPayload();
        }

        @Override
        public void encode(PortRegistryFriendlyByteBuf buffer, C2SBlueprintUndoPayload value) {
        }
    };

    @Override
    public ResourceLocation identifier() {
        return ID;
    }

    @Override
    public void work(ServerPlayer player) {
        if (ServerPacketRateLimiter.allow(
            player, ServerPacketRateLimiter.Action.BLUEPRINT_UNDO)
            && player.level() instanceof ServerLevel level) {
            boolean holdsBlueprint = player.getItemInHand(InteractionHand.MAIN_HAND).getItem() instanceof BlueprintItem
                || player.getItemInHand(InteractionHand.OFF_HAND).getItem() instanceof BlueprintItem;
            if (!holdsBlueprint) return;
            BlueprintPasteService.undoPaste(level, player);
        }
    }
}
