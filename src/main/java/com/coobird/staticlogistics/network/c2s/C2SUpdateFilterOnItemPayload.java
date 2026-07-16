package com.coobird.staticlogistics.network.c2s;

import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.content.menu.FilterConfiguratorMenu;
import com.coobird.staticlogistics.logistics.filter.FilterData;
import com.coobird.staticlogistics.logistics.node.NodeInteractionRules;
import com.coobird.staticlogistics.logistics.node.NodeMutationService;
import com.coobird.staticlogistics.network.ServerPacketRateLimiter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.mesdag.portlib.network.IPortPacket;
import org.mesdag.portlib.network.PortRegistryFriendlyByteBuf;
import org.mesdag.portlib.network.codec.PortStreamCodec;

public record C2SUpdateFilterOnItemPayload(BlockPos pos,
                                           Direction face,
                                           ResourceLocation typeId,
                                           boolean isInput,
                                           FilterData filter) implements IPortPacket.C2S {
    public static final ResourceLocation ID = StaticLogistics.asResource("update_filter_on_item");
    public static final PortStreamCodec<PortRegistryFriendlyByteBuf, C2SUpdateFilterOnItemPayload> STREAM_CODEC = new PortStreamCodec<>() {
        @Override
        public C2SUpdateFilterOnItemPayload decode(PortRegistryFriendlyByteBuf buffer) {
            FriendlyByteBuf fbuf = buffer;
            BlockPos pos = fbuf.readBlockPos();
            Direction face = fbuf.readEnum(Direction.class);
            ResourceLocation typeId = fbuf.readResourceLocation();
            boolean isInput = fbuf.readBoolean();
            FilterData filter = FilterData.STREAM_CODEC.decode(buffer);
            return new C2SUpdateFilterOnItemPayload(pos, face, typeId, isInput, filter);
        }

        @Override
        public void encode(PortRegistryFriendlyByteBuf buffer, C2SUpdateFilterOnItemPayload value) {
            FriendlyByteBuf fbuf = buffer;
            fbuf.writeBlockPos(value.pos());
            fbuf.writeEnum(value.face());
            fbuf.writeResourceLocation(value.typeId());
            fbuf.writeBoolean(value.isInput());
            FilterData.STREAM_CODEC.encode(buffer, value.filter());
        }
    };

    @Override
    public ResourceLocation identifier() {
        return ID;
    }

    @Override
    public void work(ServerPlayer player) {
        if (!ServerPacketRateLimiter.allow(
            player, ServerPacketRateLimiter.Action.FILTER_UPDATE)) return;
        if (!(player.containerMenu instanceof FilterConfiguratorMenu menu)
            || !NodeInteractionRules.matchesTarget(menu.getPos(), menu.getFace(), pos, face)
            || menu.isInput() != isInput
            || !menu.getTransferType().typeId().equals(typeId)) return;

        NodeMutationService mutations = new NodeMutationService();
        NodeMutationService.ValidatedNode node = mutations.resolve(player, pos, face);
        if (node == null) return;
        int slotIndex = isInput ? 0 : 1;
        var upgradeStack = node.config().filterConfig.getUpgrades().getStackInSlot(slotIndex);
        if (mutations.updateFilter(node, typeId, isInput, filter)) {
            menu.commitFilterData(filter, upgradeStack);
        }
    }
}
