package com.coobird.staticlogistics.integration;

import com.coobird.staticlogistics.integration.resource.ArsSourceResource;
import com.coobird.staticlogistics.integration.resource.BotaniaManaResource;
import com.coobird.staticlogistics.integration.resource.MekanismChemicalResource;
import com.coobird.staticlogistics.integration.resource.MekanismHeatResource;

/**
 * 外部模组传输类型注册入口 —— 只做模组加载检测，实际逻辑委托给各 {@code *Handler}。
 */
public class ExtendedTypeRegisterHandler {

    public static void init() {
        if (ModCompat.isMekanismLoaded()) {
            MekanismChemicalResource.register();
            MekanismHeatResource.register();
        }
        if (ModCompat.isArsNouveauLoaded()) {
            ArsSourceResource.register();
        }
        if (ModCompat.isBotaniaLoaded()) {
            BotaniaManaResource.register();
        }
    }
}
