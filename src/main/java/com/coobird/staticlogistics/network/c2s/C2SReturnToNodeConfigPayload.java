package com.coobird.staticlogistics.network.c2s;

import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.content.menu.FilterConfiguratorMenu;
import com.coobird.staticlogistics.content.menu.NodeConfiguratorMenu;
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

/** 从节点过滤器返回节点配置界面。 */
public record C2SReturnToNodeConfigPayload(BlockPos pos, Direction face)
    implements CustomPacketPayload {
    public static final Type<C2SReturnToNodeConfigPayload> TYPE =
        new Type<>(StaticLogistics.asResource("return_to_node_config"));
    public static final StreamCodec<RegistryFriendlyByteBuf, C2SReturnToNodeConfigPayload> STREAM_CODEC =
        StreamCodec.composite(
            BlockPos.STREAM_CODEC, C2SReturnToNodeConfigPayload::pos,
            Direction.STREAM_CODEC, C2SReturnToNodeConfigPayload::face,
            C2SReturnToNodeConfigPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(C2SReturnToNodeConfigPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                || !(player.containerMenu instanceof FilterConfiguratorMenu menu)
                || !NodeInteractionRules.matchesTarget(
                    menu.getPos(), menu.getFace(), payload.pos(), payload.face())) return;

            NodeMutationService.ValidatedNode node = new NodeMutationService().resolve(
                player, payload.pos(), payload.face());
            if (node == null) return;
            player.openMenu(
                new SimpleMenuProvider((id, inventory, ignored) -> new NodeConfiguratorMenu(
                    id, inventory, payload.pos(), payload.face()),
                    Component.translatable("gui.staticlogistics.face_config")),
                buffer -> {
                    buffer.writeBlockPos(payload.pos());
                    buffer.writeEnum(payload.face());
                    NodeConfiguratorMenu.writeInitialTypeData(
                        buffer, StaticLogistics.asResource("item"), node.config());
                });
        });
    }
}
