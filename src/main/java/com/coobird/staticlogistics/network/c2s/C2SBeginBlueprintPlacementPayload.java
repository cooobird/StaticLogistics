package com.coobird.staticlogistics.network.c2s;

import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.content.item.BlueprintModeHandler;
import com.coobird.staticlogistics.content.menu.LinkConfiguratorMenu;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/// 蓝图页或世界快捷键进入选址，不自动使用玩家位置生成预览。
public record C2SBeginBlueprintPlacementPayload() implements CustomPacketPayload {
    public static final Type<C2SBeginBlueprintPlacementPayload> TYPE = new Type<>(StaticLogistics.asResource("begin_blueprint_placement"));
    public static final StreamCodec<RegistryFriendlyByteBuf, C2SBeginBlueprintPlacementPayload> STREAM_CODEC = StreamCodec.unit(new C2SBeginBlueprintPlacementPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(C2SBeginBlueprintPlacementPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                || player.containerMenu != player.inventoryMenu
                && !(player.containerMenu instanceof LinkConfiguratorMenu)) return;
            var stack = ToolSettingsTarget.findConfigurator(player);
            if (stack.isEmpty()) return;
            BlueprintModeHandler.start(player, stack);
            if (BlueprintModeHandler.isPlacing(stack)) {
                player.containerMenu.broadcastChanges();
                if (player.containerMenu instanceof LinkConfiguratorMenu) player.closeContainer();
                player.displayClientMessage(Component.translatable("msg.staticlogistics.blueprint.choose_target"), true);
            }
        });
    }
}
