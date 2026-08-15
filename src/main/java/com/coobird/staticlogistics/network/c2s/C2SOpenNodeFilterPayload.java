package com.coobird.staticlogistics.network.c2s;

import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.api.group.GroupKey;
import com.coobird.staticlogistics.content.item.UpgradeItem;
import com.coobird.staticlogistics.content.menu.FilterConfiguratorMenu;
import com.coobird.staticlogistics.content.menu.LinkConfiguratorMenu;
import com.coobird.staticlogistics.logistics.node.*;
import com.coobird.staticlogistics.transfer.TransferRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkHooks;
import org.mesdag.portlib.network.IPortPacket;
import org.mesdag.portlib.network.PortRegistryFriendlyByteBuf;
import org.mesdag.portlib.network.codec.PortStreamCodec;

/**
 * 从节点配置界面打开对应方向的物品过滤器。
 */
public record C2SOpenNodeFilterPayload(BlockPos pos, Direction face, boolean input)
    implements IPortPacket.C2S {
    public static final ResourceLocation ID = StaticLogistics.asResource("open_node_filter");
    public static final PortStreamCodec<PortRegistryFriendlyByteBuf, C2SOpenNodeFilterPayload> STREAM_CODEC =
        new PortStreamCodec<>() {
            @Override
            public C2SOpenNodeFilterPayload decode(PortRegistryFriendlyByteBuf buffer) {
                return new C2SOpenNodeFilterPayload(
                    buffer.readBlockPos(), buffer.readEnum(Direction.class), buffer.readBoolean());
            }

            @Override
            public void encode(PortRegistryFriendlyByteBuf buffer, C2SOpenNodeFilterPayload value) {
                buffer.writeBlockPos(value.pos());
                buffer.writeEnum(value.face());
                buffer.writeBoolean(value.input());
            }
        };

    @Override
    public ResourceLocation identifier() {
        return ID;
    }

    @Override
    public void work(ServerPlayer player) {
        if (!(player.containerMenu instanceof LinkConfiguratorMenu menu)
            || !NodeInteractionRules.matchesTarget(
            menu.getPos(), menu.getFace(), pos, face)) return;
        FaceConfigComposite config = LinkManager.get(player.serverLevel())
            .getFaceConfig(FaceAddress.of(pos, face));
        if (!NodeInteractionValidator.canUseExisting(player, pos, face, config)) return;
        if (input ? !config.isGlobalInputEnabled() : !config.isGlobalOutputEnabled()) return;

        int slotIndex = input ? 0 : 1;
        if (!menu.getSlot(slotIndex).isActive()) return;
        ItemStack upgradeStack = menu.getSlot(slotIndex).getItem();
        if (!(upgradeStack.getItem() instanceof UpgradeItem upgrade)
            || !upgrade.isFilterUpgrade()) return;

        var transferType = TransferRegistries.get(StaticLogistics.asResource("item"));
        if (transferType == null) return;
        NetworkHooks.openScreen(player,
            new SimpleMenuProvider((id, inventory, ignored) -> new FilterConfiguratorMenu(
                id, inventory, menu.getTargetNode(), menu.getRemoteGroupKey(),
                transferType, input, upgradeStack),
                Component.translatable("gui.staticlogistics.filter.title")),
            buffer -> {
                buffer.writeBlockPos(pos);
                buffer.writeEnum(face);
                buffer.writeResourceLocation(transferType.typeId());
                buffer.writeBoolean(input);
                buffer.writeItem(upgradeStack);
                buffer.writeResourceLocation(menu.getTargetNode().gPos().dimension().location());
                GroupKey.STREAM_CODEC.encode(
                    (PortRegistryFriendlyByteBuf) buffer, menu.getRemoteGroupKey());
            });
    }
}
