package com.coobird.staticlogistics.config;

import com.coobird.staticlogistics.Staticlogistics;
import com.coobird.staticlogistics.api.filter.MatchStrategy;
import com.coobird.staticlogistics.filter.registry.ComponentMatchStrategyRegistry;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@EventBusSubscriber(modid = Staticlogistics.MODID)
public final class SLConfig {

    private static ModConfigSpec CONFIG_SPEC;

    public static ModConfigSpec.IntValue DEFAULT_RADIUS;
    public static ModConfigSpec.IntValue DEFAULT_TICK_INTERVAL;
    public static ModConfigSpec.IntValue MAX_TRANSFER_LIMIT;

    public static ModConfigSpec.IntValue DEFAULT_ITEM_STACK;
    public static ModConfigSpec.IntValue DEFAULT_FLUID_STACK;
    public static ModConfigSpec.IntValue DEFAULT_ENERGY_STACK;

    public static ModConfigSpec.IntValue MEK_CHEMICAL_STACK;
    public static ModConfigSpec.IntValue MEK_HEAT_STACK;
    public static ModConfigSpec.IntValue ARS_SOURCE_STACK;
    public static ModConfigSpec.IntValue PNEUMATIC_PRESSURE_STACK;
    public static ModConfigSpec.IntValue PNEUMATIC_HEAT_STACK;

    public static ModConfigSpec.IntValue IRON_MULTIPLIER;
    public static ModConfigSpec.IntValue GOLD_MULTIPLIER;
    public static ModConfigSpec.IntValue DIAMOND_MULTIPLIER;
    public static ModConfigSpec.IntValue NETHERITE_MULTIPLIER;
    public static ModConfigSpec.IntValue NETHER_STAR_MULTIPLIER;

    public static ModConfigSpec.ConfigValue<List<? extends String>> COMPONENT_STRATEGY_OVERRIDES;

    public static ModConfigSpec.BooleanValue AUTO_CLEAN_STORED_NODES;

    public static ModConfigSpec.IntValue CACHE_PROVIDER_SIZE;
    public static ModConfigSpec.DoubleValue CACHE_LOAD_FACTOR;
    public static ModConfigSpec.IntValue CACHE_TARGET_SIZE;
    public static ModConfigSpec.IntValue CACHE_GLOBAL_TARGET_SIZE;

    public static ModConfigSpec.IntValue NETWORK_MAX_BULK_ENTRIES;

    public static ModConfigSpec.IntValue PERF_TICKER_BATCH_SIZE;
    public static ModConfigSpec.IntValue PERF_CLEAN_INTERVAL;
    public static ModConfigSpec.IntValue PERF_DEFAULT_COOLDOWN;
    public static ModConfigSpec.IntValue PERF_BATCH_CLEAN_THRESHOLD;
    public static ModConfigSpec.IntValue PERF_BATCH_CLEAN_SIZE;
    public static ModConfigSpec.IntValue PERF_CONTEXT_POOL_SIZE;

    private static volatile int DefaultRadius = 16;
    private static volatile int DefaultTickInterval = 20;
    private static volatile int MaxTransferLimit = 10_000_000;
    private static volatile int DefaultItemStack = 8;
    private static volatile int DefaultFluidStack = 250;
    private static volatile int DefaultEnergyStack = 1024;

    private static volatile int MekChemicalStack = 250;
    private static volatile int MekHeatStack = 1000;
    private static volatile int ArsSourceStack = 100;
    private static volatile int PneumaticPressureStack = 1000;
    private static volatile int PneumaticHeatStack = 1000;

    private static volatile int ironMultCache = 2;
    private static volatile int goldMultCache = 3;
    private static volatile int diamondMultCache = 5;
    private static volatile int netheriteMultCache = 8;
    private static volatile int netherStarMultCache = 10_000;

    private static volatile boolean autoCleanStoredNodes = false;

    private static volatile int cacheProviderSize = 1000;
    private static volatile double cacheLoadFactor = 0.75;
    private static volatile int cacheTargetSize = 50;
    private static volatile int cacheGlobalTargetSize = 500;

    private static volatile int networkMaxBulkEntries = 100;

    private static volatile int perfTickerBatchSize = 50;
    private static volatile int perfCleanInterval = 200;
    private static volatile int perfDefaultCooldown = 10;
    private static volatile int perfBatchCleanThreshold = 500;
    private static volatile int perfBatchCleanSize = 200;
    private static volatile int perfContextPoolSize = 100;

    public static void register(ModContainer container) {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.push("general");
        DEFAULT_RADIUS = builder
            .translation("config.staticlogistics.default_radius")
            .defineInRange("default_radius", DefaultRadius, 1, 1024);
        DEFAULT_TICK_INTERVAL = builder
            .translation("config.staticlogistics.default_tick_interval")
            .defineInRange("default_tick_interval", DefaultTickInterval, 1, 1200);
        MAX_TRANSFER_LIMIT = builder
            .translation("config.staticlogistics.max_transfer_limit")
            .comment("Maximum amount of items/fluids/energy transferred per tick. Large values may cause performance issues.")
            .defineInRange("max_transfer_limit", MaxTransferLimit, 1, Integer.MAX_VALUE);
        AUTO_CLEAN_STORED_NODES = builder
            .translation("config.staticlogistics.auto_clean_stored_nodes")
            .comment("If true, when a logistics node is removed, the stored node references in players' Link Configurator items will be automatically cleaned up.")
            .define("auto_clean_stored_nodes", false);
        builder.pop();

        builder.push("cache");
        CACHE_PROVIDER_SIZE = builder
            .translation("config.staticlogistics.cache.provider_size")
            .comment("Maximum number of provider cache entries. Larger values use more memory but improve cache hit rate.")
            .defineInRange("provider_size", 1000, 100, 10000);
        CACHE_LOAD_FACTOR = builder
            .translation("config.staticlogistics.cache.load_factor")
            .comment("Cache load factor. Controls when hash tables resize. 0.75 is standard.")
            .defineInRange("load_factor", 0.75, 0.1, 1.0);
        CACHE_TARGET_SIZE = builder
            .translation("config.staticlogistics.cache.target_size")
            .comment("Maximum number of targets cached per face.")
            .defineInRange("target_size", 50, 10, 200);
        CACHE_GLOBAL_TARGET_SIZE = builder
            .translation("config.staticlogistics.cache.global_target_size")
            .comment("Maximum number of global target cache entries.")
            .defineInRange("global_target_size", 500, 100, 5000);
        builder.pop();

        builder.push("network");
        NETWORK_MAX_BULK_ENTRIES = builder
            .translation("config.staticlogistics.network.max_bulk_entries")
            .comment("Maximum entries per bulk sync packet. Larger values may cause network issues.")
            .defineInRange("max_bulk_entries", 100, 10, 1000);
        builder.pop();

        builder.push("performance");
        PERF_TICKER_BATCH_SIZE = builder
            .translation("config.staticlogistics.performance.ticker_batch_size")
            .comment("Number of nodes processed per tick. Smaller values reduce lag but increase delay.")
            .defineInRange("ticker_batch_size", 50, 10, 200);
        PERF_CLEAN_INTERVAL = builder
            .translation("config.staticlogistics.performance.clean_interval")
            .comment("Cooldown cleanup interval in ticks.")
            .defineInRange("clean_interval", 200, 20, 1200);
        PERF_DEFAULT_COOLDOWN = builder
            .translation("config.staticlogistics.performance.default_cooldown")
            .comment("Default cooldown ticks after failed transfer.")
            .defineInRange("default_cooldown", 10, 1, 100);
        PERF_BATCH_CLEAN_THRESHOLD = builder
            .translation("config.staticlogistics.performance.batch_clean_threshold")
            .comment("Cooldown entries threshold for batch cleanup.")
            .defineInRange("batch_clean_threshold", 500, 100, 2000);
        PERF_BATCH_CLEAN_SIZE = builder
            .translation("config.staticlogistics.performance.batch_clean_size")
            .comment("Number of entries to clean per batch.")
            .defineInRange("batch_clean_size", 200, 50, 1000);
        PERF_CONTEXT_POOL_SIZE = builder
            .translation("config.staticlogistics.performance.context_pool_size")
            .comment("Transfer context object pool size.")
            .defineInRange("context_pool_size", 100, 20, 500);
        builder.pop();

        builder.push("core");
        DEFAULT_ITEM_STACK = builder
            .translation("config.staticlogistics.item_stack_size")
            .defineInRange("item_stack_size", DefaultItemStack, 1, 64);
        DEFAULT_FLUID_STACK = builder
            .translation("config.staticlogistics.fluid_stack_size")
            .defineInRange("fluid_stack_size", DefaultFluidStack, 1, Integer.MAX_VALUE);
        DEFAULT_ENERGY_STACK = builder
            .translation("config.staticlogistics.energy_stack_size")
            .defineInRange("energy_stack_size", DefaultEnergyStack, 1, Integer.MAX_VALUE);
        builder.pop();

        builder.push("integration");
        MEK_CHEMICAL_STACK = builder
            .translation("config.staticlogistics.mek_chemical_stack_size")
            .defineInRange("mek_chemical_stack_size", MekChemicalStack, 1, Integer.MAX_VALUE);
        MEK_HEAT_STACK = builder
            .translation("config.staticlogistics.mek_heat_stack_size")
            .defineInRange("mek_heat_stack_size", MekHeatStack, 1, Integer.MAX_VALUE);
        ARS_SOURCE_STACK = builder
            .translation("config.staticlogistics.ars_source_stack_size")
            .defineInRange("ars_source_stack_size", ArsSourceStack, 1, Integer.MAX_VALUE);
        PNEUMATIC_PRESSURE_STACK = builder
            .translation("config.staticlogistics.pneumatic_pressure_stack_size")
            .defineInRange("pneumatic_pressure_stack_size", PneumaticPressureStack, 1, Integer.MAX_VALUE);
        PNEUMATIC_HEAT_STACK = builder
            .translation("config.staticlogistics.pneumatic_heat_stack_size")
            .defineInRange("pneumatic_heat_stack_size", PneumaticHeatStack, 1, Integer.MAX_VALUE);
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
            .defineInRange("nether_star_multiplier", netherStarMultCache, 1, 100_000);
        builder.pop();

        builder.push("filter");
        COMPONENT_STRATEGY_OVERRIDES = builder
            .comment(
                "Default: Empty",
                "Override partial match strategy for specific data components.",
                "Format: \"namespace:component_id=STRATEGY\" (e.g., \"minecraft:damage=IGNORE\").",
                "Valid strategies: EXACT, CONTAINS, SMART_CONTAINS, IGNORE.",
                "These entries override the built-in defaults.",
                "Note: minecraft:damage is IGNORE by default."
            )
            .translation("config.staticlogistics.filter.component_strategy_overrides")
            .defineListAllowEmpty(
                "component_strategy_overrides",
                ArrayList::new,
                () -> "",
                entry -> {
                    if (entry instanceof String s) {
                        String[] parts = s.split("=", 2);
                        if (parts.length == 2) {
                            try {
                                MatchStrategy.valueOf(parts[1].toUpperCase());
                                return true;
                            } catch (IllegalArgumentException ignored) {
                            }
                        }
                    }
                    return false;
                }
            );
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
            MaxTransferLimit = MAX_TRANSFER_LIMIT.get();
            DefaultItemStack = DEFAULT_ITEM_STACK.get();
            DefaultFluidStack = DEFAULT_FLUID_STACK.get();
            DefaultEnergyStack = DEFAULT_ENERGY_STACK.get();

            MekChemicalStack = MEK_CHEMICAL_STACK.get();
            MekHeatStack = MEK_HEAT_STACK.get();
            ArsSourceStack = ARS_SOURCE_STACK.get();
            PneumaticPressureStack = PNEUMATIC_PRESSURE_STACK.get();
            PneumaticHeatStack = PNEUMATIC_HEAT_STACK.get();

            ironMultCache = IRON_MULTIPLIER.get();
            goldMultCache = GOLD_MULTIPLIER.get();
            diamondMultCache = DIAMOND_MULTIPLIER.get();
            netheriteMultCache = NETHERITE_MULTIPLIER.get();
            netherStarMultCache = NETHER_STAR_MULTIPLIER.get();

            autoCleanStoredNodes = AUTO_CLEAN_STORED_NODES.get();

            loadComponentStrategyOverrides();
            loadPerformanceConfig();
        }
    }

    private static void loadComponentStrategyOverrides() {
        List<? extends String> list = COMPONENT_STRATEGY_OVERRIDES.get();
        if (list == null || list.isEmpty()) return;

        Map<String, String> map = new HashMap<>();
        for (String entry : list) {
            String[] parts = entry.split("=", 2);
            if (parts.length == 2) {
                map.put(parts[0], parts[1].toUpperCase());
            }
        }
        ComponentMatchStrategyRegistry.loadConfigOverrides(map);
    }

    private static void loadPerformanceConfig() {
        cacheProviderSize = CACHE_PROVIDER_SIZE.get();
        cacheLoadFactor = CACHE_LOAD_FACTOR.get();
        cacheTargetSize = CACHE_TARGET_SIZE.get();
        cacheGlobalTargetSize = CACHE_GLOBAL_TARGET_SIZE.get();

        networkMaxBulkEntries = NETWORK_MAX_BULK_ENTRIES.get();

        perfTickerBatchSize = PERF_TICKER_BATCH_SIZE.get();
        perfCleanInterval = PERF_CLEAN_INTERVAL.get();
        perfDefaultCooldown = PERF_DEFAULT_COOLDOWN.get();
        perfBatchCleanThreshold = PERF_BATCH_CLEAN_THRESHOLD.get();
        perfBatchCleanSize = PERF_BATCH_CLEAN_SIZE.get();
        perfContextPoolSize = PERF_CONTEXT_POOL_SIZE.get();
    }

    public static int getDefaultRadius() {
        return DefaultRadius;
    }

    public static int getDefaultTickInterval() {
        return DefaultTickInterval;
    }

    public static int getMaxTransferLimit() {
        return MaxTransferLimit;
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
        return MekChemicalStack;
    }

    public static int getMekHeatStack() {
        return MekHeatStack;
    }

    public static int getArsSourceStack() {
        return ArsSourceStack;
    }

    public static int getPneumaticPressureStack() {
        return PneumaticPressureStack;
    }

    public static int getPneumaticHeatStack() {
        return PneumaticHeatStack;
    }

    public static int getMultiplierForTier(String tier) {
        return switch (tier.toLowerCase()) {
            case "iron" -> ironMultCache;
            case "gold" -> goldMultCache;
            case "diamond" -> diamondMultCache;
            case "netherite" -> netheriteMultCache;
            case "nether_star" -> netherStarMultCache;
            default -> 1;
        };
    }

    public static boolean shouldAutoCleanStoredNodes() {
        return autoCleanStoredNodes;
    }

    public static int getCacheProviderSize() {
        return cacheProviderSize;
    }

    public static float getCacheLoadFactor() {
        return (float) cacheLoadFactor;
    }

    public static int getCacheTargetSize() {
        return cacheTargetSize;
    }

    public static int getCacheGlobalTargetSize() {
        return cacheGlobalTargetSize;
    }

    public static int getNetworkMaxBulkEntries() {
        return networkMaxBulkEntries;
    }

    public static int getPerfTickerBatchSize() {
        return perfTickerBatchSize;
    }

    public static int getPerfCleanInterval() {
        return perfCleanInterval;
    }

    public static int getPerfDefaultCooldown() {
        return perfDefaultCooldown;
    }

    public static int getPerfBatchCleanThreshold() {
        return perfBatchCleanThreshold;
    }

    public static int getPerfBatchCleanSize() {
        return perfBatchCleanSize;
    }

    public static int getPerfContextPoolSize() {
        return perfContextPoolSize;
    }
}