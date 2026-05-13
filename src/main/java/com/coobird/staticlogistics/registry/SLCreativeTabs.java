package com.coobird.staticlogistics.registry;

import com.coobird.staticlogistics.Staticlogistics;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class SLCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Staticlogistics.MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB_STATIC_LOGISTICS = CREATIVE_TABS.register(Staticlogistics.MODID,
        () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.staticlogistics"))
            .icon(() -> new ItemStack(SLItems.LINK_CONFIGURATOR.get()))
            .displayItems((parameters, output) -> {
                SLItems.ITEMS.getEntries().forEach(entry -> output.accept(entry.get()));
            })
            .build());
}
