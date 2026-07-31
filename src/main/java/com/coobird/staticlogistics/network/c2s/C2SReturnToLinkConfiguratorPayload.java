package com.coobird.staticlogistics.network.c2s;

import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.content.menu.FilterConfiguratorMenu;
import com.coobird.staticlogistics.content.menu.LinkConfiguratorMenu;
import com.coobird.staticlogistics.logistics.node.NodeInteractionRules;
import com.coobird.staticlogistics.logistics.node.NodeMutationService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraftforge.network.NetworkHooks;
import org.mesdag.portlib.network.IPortPacket;
import org.mesdag.portlib.network.PortRegistryFriendlyByteBuf;
import org.mesdag.portlib.network.codec.PortStreamCodec;

/**
 * 从过滤器子界面返回连接配置器。
 */
public record C2SReturnToLinkConfiguratorPayload(BlockPos pos, Direction face)
    implements IPortPacket.C2S {
    public static final ResourceLocation ID = StaticLogistics.asResource("return_to_link_configurator");
    public static final PortStreamCodec<PortRegistryFriendlyByteBuf, C2SReturnToLinkConfiguratorPayload> STREAM_CODEC =
        new PortStreamCodec<>() {
            @Override
            public C2SReturnToLinkConfiguratorPayload decode(PortRegistryFriendlyByteBuf buffer) {
                return new C2SReturnToLinkConfiguratorPayload(
                    buffer.readBlockPos(), buffer.readEnum(Direction.class));
            }

            @Override
            public void encode(PortRegistryFriendlyByteBuf buffer, C2SReturnToLinkConfiguratorPayload value) {
                buffer.writeBlockPos(value.pos());
                buffer.writeEnum(value.face());
            }
        };

    @Override
    public ResourceLocation identifier() {
        return ID;
    }

    @Override
    public void work(ServerPlayer player) {
        if (!(player.containerMenu instanceof FilterConfiguratorMenu menu)
            || !NodeInteractionRules.matchesTarget(
            menu.getPos(), menu.getFace(), pos(), face())) return;
        NodeMutationService.ValidatedNode node = menu.resolveValidatedNode(player);
        if (node == null) return;
        int toolSlot = LinkConfiguratorMenu.findToolSlot(player.getInventory());
        if (toolSlot < 0) return;
        NetworkHooks.openScreen(player,
            new SimpleMenuProvider((id, inventory, ignored) ->
                new LinkConfiguratorMenu(
                    id, inventory, menu.getTargetNode(),
                    menu.getRemoteGroupKey(), menu.isInput()),
                Component.translatable("gui.staticlogistics.face_config")),
            buffer -> LinkConfiguratorMenu.writeTargetOpenData(
                buffer, menu.getTargetNode(), menu.getRemoteGroupKey(),
                menu.isInput(), node.config(), toolSlot));
    }
}
