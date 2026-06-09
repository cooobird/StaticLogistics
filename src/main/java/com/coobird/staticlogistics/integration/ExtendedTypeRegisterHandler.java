package com.coobird.staticlogistics.integration;

import com.coobird.staticlogistics.integration.resource.*;

/**
 * 外部模组传输类型注册入口 —— 只做模组加载检测，实际逻辑委托给各 Resource 实现。
 */
public class ExtendedTypeRegisterHandler {

    public static void init() {
        if (ModCompat.isMekanismLoaded()) {
            MekanismGasResource.register();
            MekanismInfusionResource.register();
            MekanismPigmentResource.register();
            MekanismSlurryResource.register();
            MekanismHeatResource.register();
        }
        if (ModCompat.isArsNouveauLoaded()) {
            ArsSourceResource.register();
        }
        if (ModCompat.isBotaniaLoaded()) {
            BotaniaManaResource.register();
        }
        if (ModCompat.isGregTechCeuLoaded()) {
            GregTechEnergyResource.register();
        }
    }
}
