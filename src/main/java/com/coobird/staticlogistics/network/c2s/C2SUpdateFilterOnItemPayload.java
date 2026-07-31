package com.coobird.staticlogistics.network.c2s;

import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.content.menu.FilterConfiguratorMenu;
import com.coobird.staticlogistics.logistics.filter.FilterData;
import com.coobird.staticlogistics.logistics.node.NodeInteractionRules;
import com.coobird.staticlogistics.logistics.node.NodeMutationService;
import com.coobird.staticlogistics.network.ServerPacketRateLimiter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record C2SUpdateFilterOnItemPayload(BlockPos pos, Direction face, ResourceLocation typeId, boolean isInput, FilterData filter) implements CustomPacketPayload {
    public static final Type<C2SUpdateFilterOnItemPayload> TYPE = new Type<>(StaticLogistics.asResource("update_filter_on_item"));

    public static final StreamCodec<RegistryFriendlyByteBuf, C2SUpdateFilterOnItemPayload> STREAM_CODEC =
        StreamCodec.composite(
            BlockPos.STREAM_CODEC, C2SUpdateFilterOnItemPayload::pos,
            Direction.STREAM_CODEC, C2SUpdateFilterOnItemPayload::face,
            ResourceLocation.STREAM_CODEC, C2SUpdateFilterOnItemPayload::typeId,
            ByteBufCodecs.BOOL, C2SUpdateFilterOnItemPayload::isInput,
            FilterData.STREAM_CODEC, C2SUpdateFilterOnItemPayload::filter,
            C2SUpdateFilterOnItemPayload::new
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(final C2SUpdateFilterOnItemPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                || !(player.level() instanceof ServerLevel serverLevel)
                || !ServerPacketRateLimiter.allow(
                player, ServerPacketRateLimiter.Action.FILTER_UPDATE)) return;

            NodeMutationService mutations = new NodeMutationService();
            NodeMutationService.ValidatedNode node =
                player.containerMenu instanceof FilterConfiguratorMenu menu
                    ? menu.resolveValidatedNode(player) : null;
            if (node != null) {
                int slotIndex = payload.isInput() ? 0 : 1;
                var upgradeStack = node.config().filterConfig.getUpgrades().getStackInSlot(slotIndex);
                if (player.containerMenu instanceof FilterConfiguratorMenu menu
                    && NodeInteractionRules.matchesTarget(
                    menu.getPos(), menu.getFace(), payload.pos(), payload.face())
                    && menu.isInput() == payload.isInput()
                    && menu.matchesInstalledFilter(node.config())
                    && menu.getTransferType() != null
                    && menu.getTransferType().typeId().equals(payload.typeId())
                    && mutations.updateFilter(node, payload.typeId(), payload.isInput(), payload.filter())) {
                    menu.commitFilterData(payload.filter(), upgradeStack);
                }
            }
        });
    }
}
