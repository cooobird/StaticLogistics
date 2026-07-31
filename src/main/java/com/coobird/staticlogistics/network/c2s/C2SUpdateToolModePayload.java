package com.coobird.staticlogistics.network.c2s;

import PortLib.extensions.net.minecraft.world.item.ItemStack.PortItemStackExtension;
import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.content.item.ToolMode;
import com.coobird.staticlogistics.logistics.SLDataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.mesdag.portlib.network.IPortPacket;
import org.mesdag.portlib.network.PortRegistryFriendlyByteBuf;
import org.mesdag.portlib.network.codec.PortStreamCodec;

/**
 * 仅更新连接配置器的操作模式。
 */
public record C2SUpdateToolModePayload(int mode) implements IPortPacket.C2S {
    public static final ResourceLocation ID =
        StaticLogistics.asResource("update_tool_mode");
    public static final PortStreamCodec<PortRegistryFriendlyByteBuf,
        C2SUpdateToolModePayload> STREAM_CODEC = new PortStreamCodec<>() {
        @Override
        public C2SUpdateToolModePayload decode(
            PortRegistryFriendlyByteBuf buffer
        ) {
            return new C2SUpdateToolModePayload(buffer.readVarInt());
        }

        @Override
        public void encode(
            PortRegistryFriendlyByteBuf buffer,
            C2SUpdateToolModePayload payload
        ) {
            buffer.writeVarInt(payload.mode());
        }
    };

    @Override
    public ResourceLocation identifier() {
        return ID;
    }

    @Override
    public void work(ServerPlayer player) {
        ItemStack stack = ToolSettingsTarget.findConfigurator(player);
        if (!stack.isEmpty()) {
            PortItemStackExtension.setData(
                stack, SLDataComponents.TOOL_MODE.get(),
                ToolMode.fromId(mode).getId());
        }
    }
}
