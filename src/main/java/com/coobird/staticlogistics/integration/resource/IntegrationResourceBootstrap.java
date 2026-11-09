package com.coobird.staticlogistics.integration.resource;

import com.coobird.staticlogistics.integration.ModCompat;
import com.coobird.staticlogistics.transfer.TransferRegistries;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

/// 可选模组资源适配器的唯一注册入口。
public final class IntegrationResourceBootstrap {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static boolean initialized;

    private IntegrationResourceBootstrap() {
    }

    public static synchronized void init() {
        if (initialized) return;
        if (ModCompat.isMekanismLoaded()) {
            TransferRegistries.registerAdapter(
                new MekanismChemicalResource());
            TransferRegistries.registerAdapter(
                new MekanismHeatResource());
            LOGGER.info("Registered Mekanism transfer support: chemicals, heat");
        }
        if (ModCompat.isArsNouveauLoaded()) {
            TransferRegistries.registerAdapter(
                new ArsSourceResource());
            LOGGER.info("Registered Ars Nouveau source transfer support");
        }
        if (ModCompat.isBotaniaLoaded()) {
            TransferRegistries.registerAdapter(
                new BotaniaManaResource());
            LOGGER.info("Registered Botania mana transfer support");
        }
        initialized = true;
    }
}
