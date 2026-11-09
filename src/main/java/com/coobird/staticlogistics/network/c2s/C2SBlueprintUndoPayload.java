package com.coobird.staticlogistics.network.c2s;

import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.content.item.LinkConfiguratorItem;
import com.coobird.staticlogistics.logistics.blueprint.BlueprintPasteService;
import com.coobird.staticlogistics.network.ServerPacketRateLimiter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import org.mesdag.portlib.network.IPortPacket;
import org.mesdag.portlib.network.PortRegistryFriendlyByteBuf;
import org.mesdag.portlib.network.codec.PortStreamCodec;

/// 客户端请求撤销蓝图粘贴
public record C2SBlueprintUndoPayload() implements IPortPacket.C2S {
    public static final ResourceLocation ID = StaticLogistics.asResource("blueprint_undo");
    public static final PortStreamCodec<PortRegistryFriendlyByteBuf, C2SBlueprintUndoPayload> STREAM_CODEC =
        PortStreamCodec.unit(new C2SBlueprintUndoPayload());

    @Override
    public ResourceLocation identifier() {
        return ID;
    }

    @Override
    public void work(ServerPlayer packetPlayer) {
        C2SBlueprintUndoPayload payload = this;
        Player context = packetPlayer;

        if (context instanceof ServerPlayer player
            && player.level() instanceof ServerLevel level
            && ServerPacketRateLimiter.allow(
            player, ServerPacketRateLimiter.Action.BLUEPRINT_UNDO)) {
            boolean holdsBlueprint = player.getItemInHand(InteractionHand.MAIN_HAND).getItem() instanceof LinkConfiguratorItem
                || player.getItemInHand(InteractionHand.OFF_HAND).getItem() instanceof LinkConfiguratorItem;
            if (!holdsBlueprint) return;
            BlueprintPasteService.undoPaste(level, player);
        }

    }
}
