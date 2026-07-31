package com.coobird.staticlogistics.client.gui.component;

import PortLib.extensions.net.minecraft.world.item.ItemStack.PortItemStackExtension;
import com.coobird.staticlogistics.content.item.ToolMode;
import com.coobird.staticlogistics.logistics.SLDataComponents;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * 统一显示配置器模式切换的客户端反馈。
 *
 * <p>模式校验与物品数据持久化由服务端负责；快捷栏上方的即时提示属于客户端交互反馈，
 * 不应依赖数据包到达顺序或服务端物品同步时机。</p>
 */
public final class ToolModeFeedback {
    private ToolModeFeedback() {
    }

    public static void show(LocalPlayer player, ItemStack stack, ToolMode mode) {
        int storedNodeCount = PortItemStackExtension.getDataOrDefault(
            stack, SLDataComponents.STORED_NODES.get(), List.of()).size();
        String translationKey = storedNodeCount == 0
            ? "msg.staticlogistics.mode_switched"
            : "msg.staticlogistics.mode_switched_with_nodes";
        Component message = storedNodeCount == 0
            ? Component.translatable(translationKey, mode.getDisplayName())
            : Component.translatable(
            translationKey, mode.getDisplayName(), storedNodeCount);
        player.displayClientMessage(message, true);
    }
}
