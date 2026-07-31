package com.coobird.staticlogistics.network.c2s;

import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.content.menu.LinkConfiguratorMenu;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.mesdag.portlib.network.IPortPacket;
import org.mesdag.portlib.network.PortRegistryFriendlyByteBuf;
import org.mesdag.portlib.network.codec.PortStreamCodec;

/**
 * 切换当前节点显示的输入或输出侧。
 */
public record C2SSelectLinkEndpointSidePayload(boolean inputSide) implements IPortPacket.C2S {
    public static final ResourceLocation ID = StaticLogistics.asResource("select_link_endpoint_side");
    public static final PortStreamCodec<PortRegistryFriendlyByteBuf, C2SSelectLinkEndpointSidePayload> STREAM_CODEC =
        new PortStreamCodec<>() {
            @Override
            public C2SSelectLinkEndpointSidePayload decode(PortRegistryFriendlyByteBuf buffer) {
                return new C2SSelectLinkEndpointSidePayload(buffer.readBoolean());
            }

            @Override
            public void encode(PortRegistryFriendlyByteBuf buffer, C2SSelectLinkEndpointSidePayload value) {
                buffer.writeBoolean(value.inputSide());
            }
        };

    @Override
    public ResourceLocation identifier() {
        return ID;
    }

    @Override
    public void work(ServerPlayer player) {
        if (!(player.containerMenu instanceof LinkConfiguratorMenu menu)
            || !menu.hasTarget() || !menu.stillValid(player)) return;
        menu.selectVisibleSide(inputSide());
        menu.broadcastChanges();
    }
}
