package com.coobird.staticlogistics;

import com.coobird.staticlogistics.config.SLConfig;
import com.coobird.staticlogistics.core.registration.TransferRegistries;
import com.coobird.staticlogistics.datagen.SlLanguageProvider;
import com.coobird.staticlogistics.integration.ExtendedTypeRegisterHandler;
import com.coobird.staticlogistics.integration.ModCompat;
import com.coobird.staticlogistics.integration.ftb.FTBEventHandlers;
import com.coobird.staticlogistics.registry.SLCreativeTabs;
import com.coobird.staticlogistics.registry.SLDataComponents;
import com.coobird.staticlogistics.registry.SLItems;
import com.coobird.staticlogistics.registry.SLMenuTypes;
import com.mojang.logging.LogUtils;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

@Mod(Staticlogistics.MODID)
public class Staticlogistics {
    public static final String MODID = "staticlogistics";
    private static final Logger LOGGER = LogUtils.getLogger();

    public static List<Consumer<SlLanguageProvider>> chineseProviders = new ArrayList<>();

    public Staticlogistics(IEventBus modEventBus, ModContainer modContainer) {
        SLConfig.register(modContainer);
        if (FMLEnvironment.dist.isClient())
            modContainer.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        SLItems.register(modEventBus);
        SLMenuTypes.TYPES.register(modEventBus);
        SLDataComponents.DATA_COMPONENT_TYPES.register(modEventBus);
        SLCreativeTabs.CREATIVE_TABS.register(modEventBus);
        modEventBus.addListener(this::commonSetup);
        if (ModCompat.isFtbTeamsLoaded()) {
            FTBEventHandlers.init();
            LOGGER.info("Static Logistics: FTB Teams integration movement detected and initialized.");
        }
    }

    private void commonSetup(final net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent event) {
        LOGGER.info("Static Logistics: Starting common setup...");
        event.enqueueWork(() -> {
            TransferRegistries.init();
            ExtendedTypeRegisterHandler.init();
            LOGGER.info("Static Logistics: Logistics system successfully initialized with {} active transfer types.", TransferRegistries.getAllActive().size());
        });
    }

    public static ResourceLocation asResource(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }

    public static <T> ResourceKey<T> asResourceKey(ResourceKey<? extends Registry<T>> registryKey, String path) {
        return ResourceKey.create(registryKey, asResource(path));
    }
}
