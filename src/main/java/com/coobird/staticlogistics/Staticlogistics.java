package com.coobird.staticlogistics;

import com.coobird.staticlogistics.common.data.gen.ModLanguageProvider;
import com.coobird.staticlogistics.common.init.ModCreativeTabs;
import com.coobird.staticlogistics.common.init.ModDataComponents;
import com.coobird.staticlogistics.common.init.ModItems;
import com.mojang.logging.LogUtils;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

@Mod(Staticlogistics.MODID)
public class Staticlogistics {
    public static final String MODID = "staticlogistics";
    private static final Logger LOGGER = LogUtils.getLogger();

    public static List<Consumer<ModLanguageProvider>> chineseProviders = new ArrayList<>();

    public Staticlogistics(IEventBus modEventBus, ModContainer modContainer) {
        ModItems.ITEMS.register(modEventBus);
        ModDataComponents.DATA_COMPONENT_TYPES.register(modEventBus);
        ModCreativeTabs.CREATIVE_TABS.register(modEventBus);
    }

    public static ResourceLocation asResource(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }

    public static <T> ResourceKey<T> asResourceKey(ResourceKey<? extends Registry<T>> registryKey, String path) {
        return ResourceKey.create(registryKey, asResource(path));
    }
}
