package com.coobird.staticlogistics.integration.resource;

import com.coobird.staticlogistics.integration.ModCompat;
import com.coobird.staticlogistics.transfer.TransferRegistries;
import com.coobird.staticlogistics.transfer.TransferTypeBootstrap;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

/**
 * 可选模组资源适配器的唯一注册入口。
 */
public final class IntegrationResourceBootstrap {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static boolean initialized;

    private IntegrationResourceBootstrap() {
    }

    public static synchronized void init() {
        if (initialized) return;
        if (ModCompat.isMekanismLoaded()) {
            TransferRegistries.registerAdapter(
                new MekanismChemicalResource(), TransferTypeBootstrap.BIT_MEK_CHEMICALS);
            TransferRegistries.registerAdapter(
                new MekanismHeatResource(), TransferTypeBootstrap.BIT_MEK_HEAT);
            LOGGER.info("Registered Mekanism transfer support: chemicals, heat");
        }
        if (ModCompat.isArsNouveauLoaded()) {
            TransferRegistries.registerAdapter(
                new ArsSourceResource(), TransferTypeBootstrap.BIT_ARS_SOURCE);
            LOGGER.info("Registered Ars Nouveau source transfer support");
        }
        if (ModCompat.isBotaniaLoaded()) {
            TransferRegistries.registerAdapter(
                new BotaniaManaResource(), TransferTypeBootstrap.BIT_BOTANIA_MANA);
            LOGGER.info("Registered Botania mana transfer support");
        }
        initialized = true;
    }
}
