package com.coobird.staticlogistics.network.c2s;

import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.content.item.BlueprintModeHandler;
import com.coobird.staticlogistics.content.menu.LinkConfiguratorMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.mesdag.portlib.network.IPortPacket;
import org.mesdag.portlib.network.PortRegistryFriendlyByteBuf;
import org.mesdag.portlib.network.codec.PortStreamCodec;

/// 蓝图页或世界快捷键进入选址，不自动使用玩家位置生成预览。
public record C2SBeginBlueprintPlacementPayload() implements IPortPacket.C2S {
    public static final ResourceLocation ID = StaticLogistics.asResource("begin_blueprint_placement");
    public static final PortStreamCodec<PortRegistryFriendlyByteBuf, C2SBeginBlueprintPlacementPayload> STREAM_CODEC = PortStreamCodec.unit(new C2SBeginBlueprintPlacementPayload());

    @Override
    public ResourceLocation identifier() {
        return ID;
    }

    @Override
    public void work(ServerPlayer packetPlayer) {
        C2SBeginBlueprintPlacementPayload payload = this;
        Player context = packetPlayer;

        if (!(context instanceof ServerPlayer player)
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

    }
}
