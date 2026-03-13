package com.coobird.staticlogistics;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

@EventBusSubscriber(modid = Staticlogistics.MODID)
public final class SLConfig {

    private static ModConfigSpec CONFIG_SPEC;

    public static ModConfigSpec.IntValue IRON_MULTIPLIER;
    public static ModConfigSpec.IntValue GOLD_MULTIPLIER;
    public static ModConfigSpec.IntValue DIAMOND_MULTIPLIER;
    public static ModConfigSpec.IntValue NETHERITE_MULTIPLIER;

    private static volatile int ironMultCache = 4;
    private static volatile int goldMultCache = 8;
    private static volatile int diamondMultCache = 16;
    private static volatile int netheriteMultCache = 32;

    public static void register(ModContainer container) {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.push("upgrades");
        IRON_MULTIPLIER = builder.defineInRange("iron_multiplier", 4, 1, 1024);
        GOLD_MULTIPLIER = builder.defineInRange("gold_multiplier", 8, 1, 2048);
        DIAMOND_MULTIPLIER = builder.defineInRange("diamond_multiplier", 16, 1, 4096);
        NETHERITE_MULTIPLIER = builder.defineInRange("netherite_multiplier", 32, 1, 8192);
        builder.pop();

        CONFIG_SPEC = builder.build();
        container.registerConfig(ModConfig.Type.SERVER, CONFIG_SPEC, "staticlogistics-server.toml");
    }

    @SubscribeEvent
    public static void onConfigEvent(ModConfigEvent event) {
        if (event.getConfig().getSpec() == CONFIG_SPEC) {
            onLoad();
        }
    }

    public static void onLoad() {
        if (CONFIG_SPEC.isLoaded()) {
            ironMultCache = IRON_MULTIPLIER.get();
            goldMultCache = GOLD_MULTIPLIER.get();
            diamondMultCache = DIAMOND_MULTIPLIER.get();
            netheriteMultCache = NETHERITE_MULTIPLIER.get();
        }
    }

    public static int getIronMult() {
        return ironMultCache;
    }

    public static int getGoldMult() {
        return goldMultCache;
    }

    public static int getDiamondMult() {
        return diamondMultCache;
    }

    public static int getNetheriteMult() {
        return netheriteMultCache;
    }

    public static int getMultiplierForTier(String tier) {
        return switch (tier.toLowerCase()) {
            case "iron" -> ironMultCache;
            case "gold" -> goldMultCache;
            case "diamond" -> diamondMultCache;
            case "netherite" -> netheriteMultCache;
            case "creative" -> Integer.MAX_VALUE;
            default -> 2;
        };
    }
}