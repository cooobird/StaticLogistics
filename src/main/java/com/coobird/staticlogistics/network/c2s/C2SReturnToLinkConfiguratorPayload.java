package com.coobird.staticlogistics.network.c2s;

import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.content.menu.FilterConfiguratorMenu;
import com.coobird.staticlogistics.content.menu.LinkConfiguratorMenu;
import com.coobird.staticlogistics.logistics.node.NodeInteractionRules;
import com.coobird.staticlogistics.logistics.node.NodeMutationService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 从连接面的过滤器子界面返回连接配置器。
 */
public record C2SReturnToLinkConfiguratorPayload(BlockPos pos, Direction face)
    implements CustomPacketPayload {
    public static final Type<C2SReturnToLinkConfiguratorPayload> TYPE =
        new Type<>(StaticLogistics.asResource("return_to_link_configurator"));
    public static final StreamCodec<RegistryFriendlyByteBuf, C2SReturnToLinkConfiguratorPayload> STREAM_CODEC =
        StreamCodec.composite(
            BlockPos.STREAM_CODEC, C2SReturnToLinkConfiguratorPayload::pos,
            Direction.STREAM_CODEC, C2SReturnToLinkConfiguratorPayload::face,
            C2SReturnToLinkConfiguratorPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(C2SReturnToLinkConfiguratorPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                || !(player.containerMenu instanceof FilterConfiguratorMenu menu)
                || !NodeInteractionRules.matchesTarget(
                menu.getPos(), menu.getFace(), payload.pos(), payload.face())) return;

            NodeMutationService.ValidatedNode node = menu.resolveValidatedNode(player);
            if (node == null) return;
            int toolSlot = LinkConfiguratorMenu.findToolSlot(player.getInventory());
            if (toolSlot < 0) return;
            player.openMenu(
                new SimpleMenuProvider((id, inventory, ignored) ->
                    new LinkConfiguratorMenu(
                        id, inventory, menu.getTargetNode(),
                        menu.getRemoteGroupKey(), menu.isInput()),
                    Component.translatable("gui.staticlogistics.face_config")),
                buffer -> LinkConfiguratorMenu.writeTargetOpenData(
                    buffer, menu.getTargetNode(), menu.getRemoteGroupKey(),
                    menu.isInput(), node.config(), toolSlot));
        });
    }
}
