package com.coobird.staticlogistics.network.c2s;

import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.api.group.GroupKey;
import com.coobird.staticlogistics.content.menu.FilterConfiguratorMenu;
import com.coobird.staticlogistics.content.menu.LinkConfiguratorMenu;
import com.coobird.staticlogistics.logistics.node.NodeInteractionRules;
import com.coobird.staticlogistics.logistics.node.NodeMutationService;
import com.coobird.staticlogistics.transfer.TransferRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 从连接配置器打开所选连接面的输入或输出过滤器。
 */
public record C2SOpenNodeFilterPayload(
    BlockPos pos,
    Direction face,
    boolean input
) implements CustomPacketPayload {
    public static final Type<C2SOpenNodeFilterPayload> TYPE =
        new Type<>(StaticLogistics.asResource("open_node_filter"));
    public static final StreamCodec<RegistryFriendlyByteBuf, C2SOpenNodeFilterPayload> STREAM_CODEC =
        StreamCodec.composite(
            BlockPos.STREAM_CODEC, C2SOpenNodeFilterPayload::pos,
            Direction.STREAM_CODEC, C2SOpenNodeFilterPayload::face,
            ByteBufCodecs.BOOL, C2SOpenNodeFilterPayload::input,
            C2SOpenNodeFilterPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(C2SOpenNodeFilterPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                || !(player.containerMenu instanceof LinkConfiguratorMenu menu)
                || !NodeInteractionRules.matchesTarget(
                menu.getPos(), menu.getFace(), payload.pos(), payload.face())) return;

            NodeMutationService.ValidatedNode node = menu.resolveValidatedNode(player);
            if (node == null) return;

            int slotIndex = payload.input() ? 0 : 1;
            if (!menu.getSlot(slotIndex).isActive()) return;
            ItemStack upgradeStack = menu.getSlot(slotIndex).getItem();
            if (upgradeStack.isEmpty()) return;

            var transferType = TransferRegistries.get(StaticLogistics.asResource("item"));
            if (transferType == null) return;
            player.openMenu(
                new SimpleMenuProvider((id, inventory, ignored) -> new FilterConfiguratorMenu(
                    id, inventory, menu.getTargetNode(), menu.getRemoteGroupKey(),
                    transferType, payload.input(), upgradeStack),
                    Component.translatable("gui.staticlogistics.filter.title")),
                buffer -> {
                    buffer.writeBlockPos(payload.pos());
                    buffer.writeEnum(payload.face());
                    buffer.writeResourceLocation(transferType.typeId());
                    buffer.writeBoolean(payload.input());
                    ItemStack.STREAM_CODEC.encode(buffer, upgradeStack);
                    buffer.writeResourceLocation(menu.getTargetDimension().location());
                    GroupKey.STREAM_CODEC.encode(
                        buffer, menu.getRemoteGroupKey());
                });
        });
    }
}
