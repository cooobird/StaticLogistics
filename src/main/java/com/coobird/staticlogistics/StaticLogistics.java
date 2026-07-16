package com.coobird.staticlogistics;

import com.coobird.staticlogistics.client.event.ClientEvents;
import com.coobird.staticlogistics.config.SLConfig;
import com.coobird.staticlogistics.content.registry.SLCreativeTabs;
import com.coobird.staticlogistics.content.registry.SLItems;
import com.coobird.staticlogistics.content.registry.SLMenuTypes;
import com.coobird.staticlogistics.content.registry.SLTags;
import com.coobird.staticlogistics.datagen.SLLanguageProvider;
import com.coobird.staticlogistics.integration.ModCompat;
import com.coobird.staticlogistics.integration.ftb.FTBEventHandlers;
import com.coobird.staticlogistics.logistics.SLDataComponents;
import com.coobird.staticlogistics.network.SLNetwork;
import com.coobird.staticlogistics.transfer.TransferRegistries;
import com.coobird.staticlogistics.transfer.TransferTypeBootstrap;
import com.mojang.logging.LogUtils;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

@Mod(StaticLogistics.MODID)
public class StaticLogistics {
    public static final String MODID = "staticlogistics";
    private static final Logger LOGGER = LogUtils.getLogger();

    public static final List<Consumer<SLLanguageProvider>> chineseProviders = new CopyOnWriteArrayList<>();

    @SuppressWarnings("all")
    public StaticLogistics(FMLJavaModLoadingContext context) {
        SLDataComponents.init();
        IEventBus modEventBus = context.getModEventBus();
        SLConfig.register(context);
        SLItems.register(modEventBus);
        SLMenuTypes.TYPES.register(modEventBus);
        SLCreativeTabs.CREATIVE_TABS.register(modEventBus);
        SLNetwork.init();
        modEventBus.addListener(this::commonSetup);
        DistExecutor.unsafeRunWhenOn(
            Dist.CLIENT,
            () -> () -> ClientEvents.registerModBus(modEventBus)
        );
        if (ModCompat.isFtbTeamsLoaded()) {
            FTBEventHandlers.init();
            LOGGER.info("Static Logistics: FTB Teams integration movement detected and initialized.");
        }
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info("Static Logistics: Starting common setup...");
        event.enqueueWork(() -> {
            SLTags.init();
            TransferTypeBootstrap.init();
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
