package com.coobird.staticlogistics.logic.type;

import com.coobird.staticlogistics.integration.ModCompat;
import com.coobird.staticlogistics.integration.resource.ArsSourceResource;
import com.coobird.staticlogistics.integration.resource.BotaniaManaResource;
import com.coobird.staticlogistics.integration.resource.MekanismChemicalResource;
import com.coobird.staticlogistics.integration.resource.MekanismHeatResource;
import com.coobird.staticlogistics.transfer.resource.EnergyResource;
import com.coobird.staticlogistics.transfer.resource.FluidResource;
import com.coobird.staticlogistics.transfer.resource.ItemResource;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

/**
 * 传输类型启动注册清单。
 *
 * <p>这里只列出内置和联动资源类型及其稳定 bitOffset，
 * {@link TransferRegistries} 只负责维护注册表本身。
 */
public final class TransferTypeBootstrap {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static boolean initialized = false;

    public static final int BIT_ITEM = 0;
    public static final int BIT_FLUID = 1;
    public static final int BIT_ENERGY = 2;
    public static final int BIT_MEK_CHEMICALS = 3;
    public static final int BIT_MEK_HEAT = 4;
    public static final int BIT_ARS_SOURCE = 5;
    public static final int BIT_BOTANIA_MANA = 6;

    private TransferTypeBootstrap() {
    }

    public static synchronized void init() {
        if (initialized) return;
        registerBuiltinAdapters();
        registerIntegrationAdapters();
        initialized = true;
    }

    private static void registerBuiltinAdapters() {
        TransferRegistries.registerAdapter(new ItemResource(), BIT_ITEM);
        TransferRegistries.registerAdapter(new FluidResource(), BIT_FLUID);
        TransferRegistries.registerAdapter(new EnergyResource(), BIT_ENERGY);
    }

    private static void registerIntegrationAdapters() {
        if (ModCompat.isMekanismLoaded()) {
            TransferRegistries.registerAdapter(new MekanismChemicalResource(), BIT_MEK_CHEMICALS);
            TransferRegistries.registerAdapter(new MekanismHeatResource(), BIT_MEK_HEAT);
            LOGGER.info("Registered Mekanism transfer support: chemicals, heat");
        }
        if (ModCompat.isArsNouveauLoaded()) {
            TransferRegistries.registerAdapter(new ArsSourceResource(), BIT_ARS_SOURCE);
            LOGGER.info("Registered Ars Nouveau source transfer support");
        }
        if (ModCompat.isBotaniaLoaded()) {
            TransferRegistries.registerAdapter(new BotaniaManaResource(), BIT_BOTANIA_MANA);
            LOGGER.info("Registered Botania mana transfer support");
        }
    }
}
