package com.coobird.staticlogistics.integration.resource;

import com.coobird.staticlogistics.integration.ModCompat;
import com.coobird.staticlogistics.transfer.TransferRegistries;

/// 注册已加载模组提供的资源类型。
public final class IntegrationResourceBootstrap {
    private static boolean initialized;

    private IntegrationResourceBootstrap() {
    }

    public static synchronized void init() {
        if (initialized) return;
        if (ModCompat.isMekanismLoaded()) {
            TransferRegistries.registerAdapter(new MekanismGasResource());
            TransferRegistries.registerAdapter(new MekanismInfusionResource());
            TransferRegistries.registerAdapter(new MekanismPigmentResource());
            TransferRegistries.registerAdapter(new MekanismSlurryResource());
            TransferRegistries.registerAdapter(new MekanismHeatResource());
        }
        if (ModCompat.isArsNouveauLoaded()) TransferRegistries.registerAdapter(new ArsSourceResource());
        if (ModCompat.isBotaniaLoaded()) TransferRegistries.registerAdapter(new BotaniaManaResource());
        if (ModCompat.isGregTechCeuLoaded()) TransferRegistries.registerAdapter(new GregTechEnergyResource());
        initialized = true;
    }
}
