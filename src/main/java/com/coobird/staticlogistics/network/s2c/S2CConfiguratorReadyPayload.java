package com.coobird.staticlogistics.network.s2c;

import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.client.gui.screen.LinkConfiguratorScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;

/// 菜单完整状态发出后的确认，防止客户端在切换途中编辑旧目标。
public record S2CConfiguratorReadyPayload(int menuId, long requestId, boolean accepted,
                                          List<ResourceLocation> selectedTypes) implements CustomPacketPayload {
    public static final Type<S2CConfiguratorReadyPayload> TYPE = new Type<>(StaticLogistics.asResource("configurator_ready"));
    public static final StreamCodec<RegistryFriendlyByteBuf, S2CConfiguratorReadyPayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT, S2CConfiguratorReadyPayload::menuId, ByteBufCodecs.VAR_LONG, S2CConfiguratorReadyPayload::requestId, ByteBufCodecs.BOOL, S2CConfiguratorReadyPayload::accepted, ResourceLocation.STREAM_CODEC.apply(ByteBufCodecs.list()), S2CConfiguratorReadyPayload::selectedTypes, S2CConfiguratorReadyPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(S2CConfiguratorReadyPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!payload.accepted() && Minecraft.getInstance().player != null)
                Minecraft.getInstance().player.displayClientMessage(Component.translatable("gui.staticlogistics.configuration_rejected"), false);
            if (Minecraft.getInstance().screen instanceof LinkConfiguratorScreen screen && screen.getMenu().containerId == payload.menuId) {
                screen.confirmConfiguration(payload.requestId(), payload.selectedTypes());
            }
        });
    }
}
