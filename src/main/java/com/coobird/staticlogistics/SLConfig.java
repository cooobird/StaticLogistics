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

    public static ModConfigSpec.IntValue DEFAULT_RADIUS;
    public static ModConfigSpec.IntValue DEFAULT_TICK_INTERVAL;
    public static ModConfigSpec.IntValue DEFAULT_ITEM_STACK;
    public static ModConfigSpec.IntValue DEFAULT_FLUID_STACK;
    public static ModConfigSpec.IntValue DEFAULT_ENERGY_STACK;
    public static ModConfigSpec.IntValue DEFAULT_MEK_CHEMICAL_STACK;
    public static ModConfigSpec.IntValue DEFAULT_ARS_SOURCE_STACK;

    public static ModConfigSpec.IntValue IRON_MULTIPLIER;
    public static ModConfigSpec.IntValue GOLD_MULTIPLIER;
    public static ModConfigSpec.IntValue DIAMOND_MULTIPLIER;
    public static ModConfigSpec.IntValue NETHERITE_MULTIPLIER;
    public static ModConfigSpec.IntValue NETHER_STAR_MULTIPLIER;

    private static volatile int DefaultRadius = 16;
    private static volatile int DefaultTickInterval = 20;
    private static volatile int DefaultItemStack = 8;
    private static volatile int DefaultFluidStack = 250;
    private static volatile int DefaultMekChemicalStack = 250;
    private static volatile int DefaultEnergyStack = 1024;
    private static volatile int DefaultArsSourceStack = 100;

    private static volatile int ironMultCache = 4;
    private static volatile int goldMultCache = 8;
    private static volatile int diamondMultCache = 16;
    private static volatile int netheriteMultCache = 32;
    private static volatile int netherStarMultCache = Integer.MAX_VALUE;

    public static void register(ModContainer container) {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.push("general");

        DEFAULT_RADIUS = builder
            .translation("config.staticlogistics.default_radius")
            .defineInRange("default_radius", DefaultRadius, 1, 1024);

        DEFAULT_TICK_INTERVAL = builder
            .translation("config.staticlogistics.default_tick_interval")
            .defineInRange("default_tick_interval", DefaultTickInterval, 1, 1200);

        DEFAULT_ITEM_STACK = builder
            .translation("config.staticlogistics.item_stack_size")
            .defineInRange("item_stack_size", DefaultItemStack, 1, 64);

        DEFAULT_FLUID_STACK = builder
            .translation("config.staticlogistics.fluid_stack_size")
            .defineInRange("fluid_stack_size", DefaultFluidStack, 1, Integer.MAX_VALUE);

        DEFAULT_ENERGY_STACK = builder
            .translation("config.staticlogistics.energy_stack_size")
            .defineInRange("energy_stack_size", DefaultEnergyStack, 1, Integer.MAX_VALUE);

        DEFAULT_MEK_CHEMICAL_STACK = builder
            .translation("config.staticlogistics.mek_chemical_stack_size")
            .defineInRange("mek_chemical_stack_size", DefaultMekChemicalStack, 1, Integer.MAX_VALUE);

        DEFAULT_ARS_SOURCE_STACK = builder
            .translation("config.staticlogistics.ars_source_stack_size")
            .defineInRange("ars_source_stack_size", DefaultArsSourceStack, 1, Integer.MAX_VALUE);

        builder.pop();

        builder.push("upgrades");

        IRON_MULTIPLIER = builder
            .translation("config.staticlogistics.iron_multiplier")
            .defineInRange("iron_multiplier", ironMultCache, 1, 1024);

        GOLD_MULTIPLIER = builder
            .translation("config.staticlogistics.gold_multiplier")
            .defineInRange("gold_multiplier", goldMultCache, 1, 2048);

        DIAMOND_MULTIPLIER = builder
            .translation("config.staticlogistics.diamond_multiplier")
            .defineInRange("diamond_multiplier", diamondMultCache, 1, 4096);

        NETHERITE_MULTIPLIER = builder
            .translation("config.staticlogistics.netherite_multiplier")
            .defineInRange("netherite_multiplier", netheriteMultCache, 1, 8192);

        NETHER_STAR_MULTIPLIER = builder
            .translation("config.staticlogistics.nether_star_multiplier")
            .defineInRange("nether_star_multiplier", netherStarMultCache, 1, Integer.MAX_VALUE);

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
            DefaultRadius = DEFAULT_RADIUS.get();
            DefaultTickInterval = DEFAULT_TICK_INTERVAL.get();
            DefaultItemStack = DEFAULT_ITEM_STACK.get();
            DefaultFluidStack = DEFAULT_FLUID_STACK.get();
            DefaultEnergyStack = DEFAULT_ENERGY_STACK.get();
            DefaultMekChemicalStack = DEFAULT_MEK_CHEMICAL_STACK.get();
            DefaultArsSourceStack = DEFAULT_ARS_SOURCE_STACK.get();

            ironMultCache = IRON_MULTIPLIER.get();
            goldMultCache = GOLD_MULTIPLIER.get();
            diamondMultCache = DIAMOND_MULTIPLIER.get();
            netheriteMultCache = NETHERITE_MULTIPLIER.get();
            netherStarMultCache = NETHER_STAR_MULTIPLIER.get();
        }
    }

    public static int getDefaultRadius() {
        return DefaultRadius;
    }

    public static int getDefaultTickInterval() {
        return DefaultTickInterval;
    }

    public static int getItemStack() {
        return DefaultItemStack;
    }

    public static int getFluidStack() {
        return DefaultFluidStack;
    }

    public static int getEnergyStack() {
        return DefaultEnergyStack;
    }

    public static int getMekChemicalStack() {
        return DefaultMekChemicalStack;
    }

    public static int getArsSourceStack() {
        return DefaultArsSourceStack;
    }

    public static int getMultiplierForTier(String tier) {
        if (tier == null) return 1;
        return switch (tier.toLowerCase()) {
            case "iron" -> ironMultCache;
            case "gold" -> goldMultCache;
            case "diamond" -> diamondMultCache;
            case "netherite" -> netheriteMultCache;
            case "nether_star" -> Integer.MAX_VALUE;
            default -> 1;
        };
    }
}