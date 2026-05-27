package com.coobird.staticlogistics.integration;

import net.neoforged.fml.ModList;

public class ModCompat {
    public static boolean isFtbTeamsLoaded() {
        return ModList.get().isLoaded("ftbteams");
    }

    public static boolean isMekanismLoaded() {
        return ModList.get().isLoaded("mekanism");
    }

    public static boolean isMekanismAdditionsLoaded() {
        return ModList.get().isLoaded("mekanismadditions");
    }

    public static boolean isArsNouveauLoaded() {
        return ModList.get().isLoaded("ars_nouveau");
    }

    public static boolean isPneumaticcraftLoaded() {
        return ModList.get().isLoaded("pneumaticcraft");
    }

    public static boolean isCreateLoaded() {
        return ModList.get().isLoaded("create");
    }

}