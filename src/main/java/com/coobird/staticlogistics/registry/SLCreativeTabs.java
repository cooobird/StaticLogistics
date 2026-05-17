package com.coobird.staticlogistics.registry;

import com.coobird.staticlogistics.Staticlogistics;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 注册模组的创造模式物品栏标签页。
 */
public class SLCreativeTabs {
    /**
     * 延迟注册器，用于在模组初始化时注册创造标签页
     */
    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Staticlogistics.MODID);

    /**
     * 静态物流模组的创造标签页，图标使用连接配置器
     */
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB_STATIC_LOGISTICS = CREATIVE_TABS.register(Staticlogistics.MODID,
        () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.staticlogistics"))
            .icon(() -> new ItemStack(SLItems.LINK_CONFIGURATOR.get()))
            .displayItems((parameters, output) -> {
                SLItems.ITEMS.getEntries().forEach(entry -> output.accept(entry.get()));
            })
            .build());
}
