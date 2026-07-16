package com.coobird.staticlogistics.content.item;

import net.minecraft.world.item.ItemStack;

import java.util.function.Consumer;

/**
 * 在通用配置器物品与仅客户端界面代码之间提供无客户端类型的桥接。
 */
public final class LinkConfiguratorClientHooks {
    private static Consumer<ItemStack> screenOpener = ignored -> {
    };

    private LinkConfiguratorClientHooks() {
    }

    public static void install(Consumer<ItemStack> opener) {
        screenOpener = opener;
    }

    public static void openScreen(ItemStack stack) {
        screenOpener.accept(stack);
    }
}
