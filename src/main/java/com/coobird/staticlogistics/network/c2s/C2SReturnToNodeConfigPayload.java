package com.coobird.staticlogistics.network.c2s;

import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.content.menu.FilterConfiguratorMenu;
import com.coobird.staticlogistics.content.menu.NodeConfiguratorMenu;
import com.coobird.staticlogistics.logistics.node.*;
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
 * 从节点过滤器返回节点配置界面。
 */
public record C2SReturnToNodeConfigPayload(BlockPos pos, Direction face)
    implements IPortPacket.C2S {
    public static final ResourceLocation ID = StaticLogistics.asResource("return_to_node_config");
    public static final PortStreamCodec<PortRegistryFriendlyByteBuf, C2SReturnToNodeConfigPayload> STREAM_CODEC =
        new PortStreamCodec<>() {
            @Override
            public C2SReturnToNodeConfigPayload decode(PortRegistryFriendlyByteBuf buffer) {
                return new C2SReturnToNodeConfigPayload(
                    buffer.readBlockPos(), buffer.readEnum(Direction.class));
            }

            @Override
            public void encode(PortRegistryFriendlyByteBuf buffer, C2SReturnToNodeConfigPayload value) {
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
            menu.getPos(), menu.getFace(), pos, face)) return;
        FaceConfigComposite config = LinkManager.get(player.serverLevel())
            .getFaceConfig(FaceAddress.of(pos, face));
        if (!NodeInteractionValidator.canUseExisting(player, pos, face, config)) return;

        NetworkHooks.openScreen(player,
            new SimpleMenuProvider((id, inventory, ignored) ->
                new NodeConfiguratorMenu(id, inventory, pos, face),
                Component.translatable("gui.staticlogistics.face_config")),
            buffer -> {
                buffer.writeBlockPos(pos);
                buffer.writeEnum(face);
                NodeConfiguratorMenu.writeInitialTypeData(
                    buffer, StaticLogistics.asResource("item"), config);
            });
    }
}

