package com.coobird.staticlogistics.network.c2s;

import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.content.item.UpgradeItem;
import com.coobird.staticlogistics.content.menu.FilterConfiguratorMenu;
import com.coobird.staticlogistics.logistics.filter.FilterData;
import com.coobird.staticlogistics.logistics.node.NodeInteractionRules;
import com.coobird.staticlogistics.logistics.node.NodeMutationService;
import com.coobird.staticlogistics.network.BoundedNetworkCodecs;
import com.coobird.staticlogistics.network.ServerPacketRateLimiter;
import com.coobird.staticlogistics.transfer.UpgradeType;
import io.netty.handler.codec.DecoderException;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.mesdag.portlib.network.IPortPacket;
import org.mesdag.portlib.network.PortRegistryFriendlyByteBuf;
import org.mesdag.portlib.network.codec.PortStreamCodec;

public record C2SUpdateFilterOnItemPayload(BlockPos pos, Direction face, ResourceLocation typeId, boolean isInput,
                                           UpgradeType filterType, FilterData filter) implements IPortPacket.C2S {
    public static final ResourceLocation ID = StaticLogistics.asResource("update_filter_on_item");

    public static final PortStreamCodec<PortRegistryFriendlyByteBuf, C2SUpdateFilterOnItemPayload> STREAM_CODEC = new PortStreamCodec<>() {
        @Override
        public C2SUpdateFilterOnItemPayload decode(PortRegistryFriendlyByteBuf buf) {
            BlockPos pos = BoundedNetworkCodecs.BLOCK_POS.decode(buf);
            Direction face = BoundedNetworkCodecs.DIRECTION.decode(buf);
            ResourceLocation typeId = ResourceLocation.STREAM_CODEC.decode(buf);
            boolean isInput = buf.readBoolean();
            UpgradeType filterType = buf.readEnum(UpgradeType.class);
            if (!isFilterType(filterType)) throw new DecoderException("Invalid filter upgrade type: " + filterType);
            return new C2SUpdateFilterOnItemPayload(pos, face, typeId, isInput, filterType,
                FilterData.STREAM_CODEC.decode(buf));
        }

        @Override
        public void encode(PortRegistryFriendlyByteBuf buf, C2SUpdateFilterOnItemPayload payload) {
            if (!isFilterType(payload.filterType())) {
                throw new IllegalArgumentException("Invalid filter upgrade type: " + payload.filterType());
            }
            BoundedNetworkCodecs.BLOCK_POS.encode(buf, payload.pos());
            BoundedNetworkCodecs.DIRECTION.encode(buf, payload.face());
            ResourceLocation.STREAM_CODEC.encode(buf, payload.typeId());
            buf.writeBoolean(payload.isInput());
            buf.writeEnum(payload.filterType());
            /// 类型只决定客户端写包时的数据表示；服务端仍以已安装过滤器为准。
            FilterData.STREAM_CODEC.encode(buf, payload.filter().normalizedFor(payload.filterType()));
        }
    };

    @Override
    public ResourceLocation identifier() {
        return ID;
    }

    private static boolean isFilterType(UpgradeType type) {
        return type == UpgradeType.FILTER;
    }

    public static void handle(final C2SUpdateFilterOnItemPayload payload, final Player context) {

        if (!(context instanceof ServerPlayer player)
            || !(player.level() instanceof ServerLevel serverLevel)
            || !ServerPacketRateLimiter.allow(
            player, ServerPacketRateLimiter.Action.FILTER_UPDATE)) return;

        NodeMutationService mutations = new NodeMutationService();
        NodeMutationService.ValidatedNode node =
            player.containerMenu instanceof FilterConfiguratorMenu menu
                ? menu.resolveValidatedNode(player) : null;
        if (node != null) {
            int slotIndex = 0;
            var upgradeStack = node.config().filterConfig.getUpgrades().getStackInSlot(slotIndex);
            if (upgradeStack.getItem() instanceof UpgradeItem upgrade
                && player.containerMenu instanceof FilterConfiguratorMenu menu
                && NodeInteractionRules.matchesTarget(
                menu.getPos(), menu.getFace(), payload.pos(), payload.face())
                && menu.isInput() == payload.isInput()
                && menu.matchesInstalledFilter(node.config())
                && menu.getTransferType() != null
                && menu.getTransferType().typeId().equals(payload.typeId())
                && mutations.updateFilter(node, payload.typeId(), payload.isInput(),
                payload.filter().normalizedFor(upgrade.getType()))) {
                menu.commitFilterData(payload.filter().normalizedFor(upgrade.getType()), upgradeStack);
            }
        }

    }

    @Override
    public void work(ServerPlayer packetPlayer) {
        handle(this, packetPlayer);
    }
}
