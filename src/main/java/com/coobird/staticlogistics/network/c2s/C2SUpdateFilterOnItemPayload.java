package com.coobird.staticlogistics.network.c2s;

import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.content.item.UpgradeItem;
import com.coobird.staticlogistics.content.menu.FilterConfiguratorMenu;
import com.coobird.staticlogistics.logistics.filter.FilterData;
import com.coobird.staticlogistics.logistics.node.NodeInteractionRules;
import com.coobird.staticlogistics.logistics.node.NodeMutationService;
import com.coobird.staticlogistics.network.ServerPacketRateLimiter;
import com.coobird.staticlogistics.transfer.UpgradeType;
import io.netty.handler.codec.DecoderException;
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
                                           UpgradeType filterType,
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
            UpgradeType filterType = fbuf.readEnum(UpgradeType.class);
            if (!isFilterType(filterType)) throw new DecoderException("Invalid filter upgrade type: " + filterType);
            FilterData filter = FilterData.STREAM_CODEC.decode(buffer);
            return new C2SUpdateFilterOnItemPayload(pos, face, typeId, isInput, filterType, filter);
        }

        @Override
        public void encode(PortRegistryFriendlyByteBuf buffer, C2SUpdateFilterOnItemPayload value) {
            FriendlyByteBuf fbuf = buffer;
            fbuf.writeBlockPos(value.pos());
            fbuf.writeEnum(value.face());
            fbuf.writeResourceLocation(value.typeId());
            fbuf.writeBoolean(value.isInput());
            if (!isFilterType(value.filterType())) {
                throw new IllegalArgumentException("Invalid filter upgrade type: " + value.filterType());
            }
            fbuf.writeEnum(value.filterType());
            // 类型只决定客户端写包时的数据表示；服务端仍以已安装过滤器为准。
            FilterData.STREAM_CODEC.encode(buffer, value.filter().normalizedFor(value.filterType()));
        }
    };

    private static boolean isFilterType(UpgradeType type) {
        return type == UpgradeType.BASIC_FILTER || type == UpgradeType.TAG_FILTER || type == UpgradeType.NBT_FILTER;
    }

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
            || menu.getTransferType() == null
            || !menu.getTransferType().typeId().equals(typeId)) return;

        NodeMutationService mutations = new NodeMutationService();
        NodeMutationService.ValidatedNode node = menu.resolveValidatedNode(player);
        if (node == null) return;
        int slotIndex = isInput ? 0 : 1;
        var upgradeStack = node.config().filterConfig.getUpgrades().getStackInSlot(slotIndex);
        if (upgradeStack.getItem() instanceof UpgradeItem upgrade
            && menu.matchesInstalledFilter(node.config())) {
            FilterData normalized = filter.normalizedFor(upgrade.getType());
            if (mutations.updateFilter(node, typeId, isInput, normalized)) {
                menu.commitFilterData(normalized, upgradeStack);
            }
        }
    }
}
