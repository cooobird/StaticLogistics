package com.coobird.staticlogistics.integration.handler;

import com.coobird.staticlogistics.integration.ModCompat;

/**
 * 外部模组传输类型注册入口 —— 只做模组加载检测，实际逻辑委托给各 {@code *Handler}。
 */
public class ExtendedTypeRegisterHandler {

    public static void init() {
        if (ModCompat.isMekanismLoaded()) {
            MekanismChemicalHandler.register();
            MekanismHeatHandler.register();
        }
        if (ModCompat.isArsNouveauLoaded()) {
            ArsSourceHandler.register();
        }
        if (ModCompat.isBotaniaLoaded()) {
            BotaniaManaHandler.register();
        }
    }
}
