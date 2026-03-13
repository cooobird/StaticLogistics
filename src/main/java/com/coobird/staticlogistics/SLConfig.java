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

    // 配置项
    public static ModConfigSpec.IntValue DEFAULT_RADIUS;
    public static ModConfigSpec.IntValue DEFAULT_TICK_INTERVAL;
    public static ModConfigSpec.IntValue DEFAULT_ITEM_STACK;
    public static ModConfigSpec.IntValue DEFAULT_FLUID_STACK;
    public static ModConfigSpec.IntValue DEFAULT_ENERGY_STACK;

    public static ModConfigSpec.IntValue IRON_MULTIPLIER;
    public static ModConfigSpec.IntValue GOLD_MULTIPLIER;
    public static ModConfigSpec.IntValue DIAMOND_MULTIPLIER;
    public static ModConfigSpec.IntValue NETHERITE_MULTIPLIER;

    private static volatile int radiusCache = 8;
    private static volatile int tickIntervalCache = 20;
    private static volatile int itemStackCache = 64;
    private static volatile int fluidStackCache = 1000;
    private static volatile int energyStackCache = 4000;

    private static volatile int ironMultCache = 4;
    private static volatile int goldMultCache = 8;
    private static volatile int diamondMultCache = 16;
    private static volatile int netheriteMultCache = 32;

    public static void register(ModContainer container) {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.push("general");

        DEFAULT_RADIUS = builder
            .comment("config.staticlogistics.default_radius.comment")
            .translation("config.staticlogistics.default_radius")
            .defineInRange("default_radius", 8, 1, 1024);

        DEFAULT_TICK_INTERVAL = builder
            .comment("config.staticlogistics.default_tick_interval.comment")
            .translation("config.staticlogistics.default_tick_interval")
            .defineInRange("default_tick_interval", 20, 1, 1200);

        DEFAULT_ITEM_STACK = builder
            .comment("config.staticlogistics.item_stack_size.comment")
            .translation("config.staticlogistics.item_stack_size")
            .defineInRange("item_stack_size", 64, 1, 64);

        DEFAULT_FLUID_STACK = builder
            .comment("config.staticlogistics.fluid_stack_size.comment")
            .translation("config.staticlogistics.fluid_stack_size")
            .defineInRange("fluid_stack_size", 1000, 1, Integer.MAX_VALUE);

        DEFAULT_ENERGY_STACK = builder
            .comment("config.staticlogistics.energy_stack_size.comment")
            .translation("config.staticlogistics.energy_stack_size")
            .defineInRange("energy_stack_size", 10000, 1, Integer.MAX_VALUE);

        builder.pop();

        builder.push("upgrades");

        IRON_MULTIPLIER = builder
            .comment("config.staticlogistics.iron_multiplier.comment")
            .translation("config.staticlogistics.iron_multiplier")
            .defineInRange("iron_multiplier", 4, 1, 1024);

        GOLD_MULTIPLIER = builder
            .comment("config.staticlogistics.gold_multiplier.comment")
            .translation("config.staticlogistics.gold_multiplier")
            .defineInRange("gold_multiplier", 8, 1, 2048);

        DIAMOND_MULTIPLIER = builder
            .comment("config.staticlogistics.diamond_multiplier.comment")
            .translation("config.staticlogistics.diamond_multiplier")
            .defineInRange("diamond_multiplier", 16, 1, 4096);

        NETHERITE_MULTIPLIER = builder
            .comment("config.staticlogistics.netherite_multiplier.comment")
            .translation("config.staticlogistics.netherite_multiplier")
            .defineInRange("netherite_multiplier", 32, 1, 8192);

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
            radiusCache = DEFAULT_RADIUS.get();
            tickIntervalCache = DEFAULT_TICK_INTERVAL.get();
            itemStackCache = DEFAULT_ITEM_STACK.get();
            fluidStackCache = DEFAULT_FLUID_STACK.get();
            energyStackCache = DEFAULT_ENERGY_STACK.get();

            ironMultCache = IRON_MULTIPLIER.get();
            goldMultCache = GOLD_MULTIPLIER.get();
            diamondMultCache = DIAMOND_MULTIPLIER.get();
            netheriteMultCache = NETHERITE_MULTIPLIER.get();
        }
    }

    public static int getDefaultRadius() {
        return radiusCache;
    }

    public static int getDefaultTickInterval() {
        return tickIntervalCache;
    }

    public static int getItemStack() {
        return itemStackCache;
    }

    public static int getFluidStack() {
        return fluidStackCache;
    }

    public static int getEnergyStack() {
        return energyStackCache;
    }

    public static int getMultiplierForTier(String tier) {
        if (tier == null) return 1;
        return switch (tier.toLowerCase()) {
            case "iron" -> ironMultCache;
            case "gold" -> goldMultCache;
            case "diamond" -> diamondMultCache;
            case "netherite" -> netheriteMultCache;
            case "creative" -> Integer.MAX_VALUE;
            default -> 1;
        };
    }
}