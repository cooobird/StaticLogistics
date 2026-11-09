package com.coobird.staticlogistics.network.c2s;

import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.api.group.GroupKey;
import com.coobird.staticlogistics.content.item.UpgradeItem;
import com.coobird.staticlogistics.content.menu.FilterConfiguratorMenu;
import com.coobird.staticlogistics.content.menu.LinkConfiguratorMenu;
import com.coobird.staticlogistics.logistics.SLDataComponents;
import com.coobird.staticlogistics.logistics.filter.FilterData;
import com.coobird.staticlogistics.logistics.node.NodeInteractionRules;
import com.coobird.staticlogistics.logistics.node.NodeMutationService;
import com.coobird.staticlogistics.network.BoundedNetworkCodecs;
import com.coobird.staticlogistics.transfer.TransferRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkHooks;
import org.mesdag.portlib.network.IPortPacket;
import org.mesdag.portlib.network.PortRegistryFriendlyByteBuf;
import org.mesdag.portlib.network.codec.PortByteBufCodecs;
import org.mesdag.portlib.network.codec.PortStreamCodec;
import org.mesdag.portlib.wrapper.common.extensions.IPortFriendlyByteBufExtension;

/// 从连接配置器打开所选连接面的输入或输出过滤器。
public record C2SOpenNodeFilterPayload(BlockPos pos, Direction face, boolean input) implements IPortPacket.C2S {
    public static final ResourceLocation ID = StaticLogistics.asResource("open_node_filter");

    public static final PortStreamCodec<PortRegistryFriendlyByteBuf, C2SOpenNodeFilterPayload> STREAM_CODEC = PortStreamCodec.composite(
        BoundedNetworkCodecs.BLOCK_POS, C2SOpenNodeFilterPayload::pos,
        BoundedNetworkCodecs.DIRECTION, C2SOpenNodeFilterPayload::face,
        PortByteBufCodecs.BOOL, C2SOpenNodeFilterPayload::input,
        C2SOpenNodeFilterPayload::new);

    @Override
    public ResourceLocation identifier() {
        return ID;
    }

    @Override
    public void work(ServerPlayer packetPlayer) {
        C2SOpenNodeFilterPayload payload = this;
        Player context = packetPlayer;

        if (!(context instanceof ServerPlayer player)
            || !(player.containerMenu instanceof LinkConfiguratorMenu menu)
            || !NodeInteractionRules.matchesTarget(
            menu.getPos(), menu.getFace(), payload.pos(), payload.face())) return;

        NodeMutationService.ValidatedNode node = menu.resolveValidatedNode(player);
        if (node == null) return;

        int slotIndex = 0;
        if (!menu.getSlot(slotIndex).isActive()) return;
        ItemStack upgradeStack = menu.getSlot(slotIndex).getItem();
        if (!(upgradeStack.getItem() instanceof UpgradeItem upgrade) || !upgrade.isFilterUpgrade()) return;
        FilterData currentFilter = upgradeStack.getOrDefault(SLDataComponents.FILTER_DATA.get(), FilterData.EMPTY);
        FilterData filter = currentFilter.normalizedFor(upgrade.getType());
        if (filter != currentFilter) upgradeStack.set(SLDataComponents.FILTER_DATA.get(), filter);

        var transferType = TransferRegistries.get(StaticLogistics.asResource("item"));
        if (transferType == null) return;
        NetworkHooks.openScreen(player,
            new SimpleMenuProvider((id, inventory, ignored) -> new FilterConfiguratorMenu(
                id, inventory, menu.getTargetNode(), menu.getRemoteGroupKey(),
                transferType, payload.input(), upgradeStack),
                Component.translatable("gui.staticlogistics.filter.title")),
            buffer -> {
                buffer.writeBlockPos(payload.pos());
                buffer.writeEnum(payload.face());
                buffer.writeVarInt(menu.getTargetNode().channel());
                buffer.writeResourceLocation(transferType.typeId());
                buffer.writeBoolean(payload.input());
                ItemStack.STREAM_CODEC.encode(IPortFriendlyByteBufExtension.of(buffer).wrap(), upgradeStack);
                FilterData.STREAM_CODEC.encode(IPortFriendlyByteBufExtension.of(buffer).wrap(), filter);
                buffer.writeResourceLocation(menu.getTargetDimension().location());
                GroupKey.STREAM_CODEC.encode(IPortFriendlyByteBufExtension.of(buffer).wrap(), menu.getRemoteGroupKey());
            });

    }
}
