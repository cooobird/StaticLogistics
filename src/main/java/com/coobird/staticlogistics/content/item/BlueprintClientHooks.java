package com.coobird.staticlogistics.content.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.ToIntFunction;

/** 在通用物品代码与仅客户端界面代码之间提供无客户端类型的桥接。 */
public final class BlueprintClientHooks {
    private static Consumer<ItemStack> screenOpener = ignored -> { };
    private static BiConsumer<ItemStack, List<Component>> tooltipAppender = (ignored, tooltip) -> { };
    private static ToIntFunction<String> inventoryCounter = ignored -> 0;

    private BlueprintClientHooks() {
    }

    public static void install(Consumer<ItemStack> opener,
                               BiConsumer<ItemStack, List<Component>> appender,
                               ToIntFunction<String> counter) {
        screenOpener = opener;
        tooltipAppender = appender;
        inventoryCounter = counter;
    }

    public static void openScreen(ItemStack stack) {
        screenOpener.accept(stack);
    }

    public static void appendKeyTooltips(ItemStack stack, List<Component> tooltip) {
        tooltipAppender.accept(stack, tooltip);
    }

    public static int countInventoryItem(String itemId) {
        return inventoryCounter.applyAsInt(itemId);
    }
}
