package com.coobird.staticlogistics.network.c2s;

import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.content.item.ToolMode;
import com.coobird.staticlogistics.logistics.SLDataComponents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 仅更新连接配置器的操作模式。
 */
public record C2SUpdateToolModePayload(int mode) implements CustomPacketPayload {
    public static final Type<C2SUpdateToolModePayload> TYPE = new Type<>(StaticLogistics.asResource("update_tool_mode"));

    public static final StreamCodec<RegistryFriendlyByteBuf, C2SUpdateToolModePayload> STREAM_CODEC =
        StreamCodec.composite(ByteBufCodecs.VAR_INT, C2SUpdateToolModePayload::mode,
            C2SUpdateToolModePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(C2SUpdateToolModePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ItemStack stack = ToolSettingsTarget.findConfigurator(context.player());
            if (!stack.isEmpty()) {
                stack.set(SLDataComponents.TOOL_MODE.get(), ToolMode.fromId(payload.mode()).getId());
            }
        });
    }
}
