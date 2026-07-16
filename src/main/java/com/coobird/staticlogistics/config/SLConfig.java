package com.coobird.staticlogistics.config;

import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.network.s2c.S2CConfigSyncPayload;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.util.concurrent.atomic.AtomicLong;

@EventBusSubscriber(modid = StaticLogistics.MODID)
public final class SLConfig {

    public static final AtomicLong configGeneration = new AtomicLong(0);

    private static ModConfigSpec CONFIG_SPEC;

    // 通用设置
    // 物流节点的默认搜索半径（格）
    public static ModConfigSpec.IntValue DEFAULT_RADIUS;
    // 物流节点的默认工作间隔（tick）
    public static ModConfigSpec.IntValue DEFAULT_TICK_INTERVAL;

    // 物品每 tick 传输堆叠数
    public static ModConfigSpec.IntValue DEFAULT_ITEM_STACK;
    // 流体每 tick 传输量（mB）
    public static ModConfigSpec.IntValue DEFAULT_FLUID_STACK;
    // 能量每 tick 传输量（FE）
    public static ModConfigSpec.IntValue DEFAULT_ENERGY_STACK;

    // Mekanism 化学品每 tick 传输量
    public static ModConfigSpec.IntValue MEK_CHEMICAL_STACK;
    // Mekanism 热量每 tick 传输量
    public static ModConfigSpec.IntValue MEK_HEAT_STACK;
    // Ars Nouveau 魔源每 tick 传输量
    public static ModConfigSpec.IntValue ARS_SOURCE_STACK;
    // Botania 魔力每 tick 传输量
    public static ModConfigSpec.IntValue BOTANIA_MANA_STACK;

    // 铁升级的倍率
    public static ModConfigSpec.IntValue IRON_MULTIPLIER;
    // 金升级的倍率
    public static ModConfigSpec.IntValue GOLD_MULTIPLIER;
    // 钻石升级的倍率
    public static ModConfigSpec.IntValue DIAMOND_MULTIPLIER;
    // 下界合金升级的倍率
    public static ModConfigSpec.IntValue NETHERITE_MULTIPLIER;
    // 下界之星升级的倍率
    public static ModConfigSpec.IntValue NETHER_STAR_MULTIPLIER;

    // 是否在物流节点被拆除时自动清理玩家物品中存储的节点引用
    public static ModConfigSpec.BooleanValue AUTO_CLEAN_STORED_NODES;

    // 供应方缓存最大条目数
    public static ModConfigSpec.IntValue CACHE_PROVIDER_SIZE;
    // 缓存哈希表的负载因子
    public static ModConfigSpec.DoubleValue CACHE_LOAD_FACTOR;

    // 批量同步数据包每包最大条目数
    public static ModConfigSpec.IntValue NETWORK_MAX_BULK_ENTRIES;

    // 每 tick 扫描的节点批次基数
    public static ModConfigSpec.IntValue PERF_TICKER_BATCH_SIZE;
    // 单维度物流调度的软时间预算（微秒）
    public static ModConfigSpec.IntValue PERF_TICKER_TIME_BUDGET_US;
    // 冷却清理间隔（tick）
    public static ModConfigSpec.IntValue PERF_CLEAN_INTERVAL;
    // 传输失败后的默认冷却时间（tick）
    public static ModConfigSpec.IntValue PERF_DEFAULT_COOLDOWN;
    // 触发批量清理的冷却条目阈值
    public static ModConfigSpec.IntValue PERF_BATCH_CLEAN_THRESHOLD;
    // 每次批量清理的条目数
    public static ModConfigSpec.IntValue PERF_BATCH_CLEAN_SIZE;
    // 传输上下文对象池大小
    public static ModConfigSpec.IntValue PERF_CONTEXT_POOL_SIZE;

    // 通用设置缓存值
    private static volatile int DefaultRadius = 16;
    private static volatile int DefaultTickInterval = 20;
    // 核心资源传输量缓存值
    private static volatile int DefaultItemStack = 64;
    private static volatile int DefaultFluidStack = 250;
    private static volatile int DefaultEnergyStack = 1024;

    // 联动模组资源传输量缓存值
    private static volatile int MekChemicalStack = 250;
    private static volatile int MekHeatStack = 1000;
    private static volatile int ArsSourceStack = 100;
    private static volatile int BotaniaManaStack = 1000;

    // 升级倍率缓存值
    private static volatile int ironMultCache = 2;
    private static volatile int goldMultCache = 4;
    private static volatile int diamondMultCache = 8;
    private static volatile int netheriteMultCache = 16;
    private static volatile int netherStarMultCache = 64;

    // 杂项缓存值
    private static volatile boolean autoCleanStoredNodes = true;

    // 缓存设置缓存值
    private static volatile int cacheProviderSize = 1000;
    private static volatile double cacheLoadFactor = 0.75;
    // 网络设置缓存值
    private static volatile int networkMaxBulkEntries = 100;

    // 性能设置缓存值
    private static volatile int perfTickerBatchSize = 50;
    private static volatile int perfTickerTimeBudgetUs = 1_500;
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
        AUTO_CLEAN_STORED_NODES = builder
            .translation("config.staticlogistics.auto_clean_stored_nodes")
            .comment("If true, stored node references in Link Configurator items will be automatically cleaned after batch linking or when a node is removed.")
            .define("auto_clean_stored_nodes", autoCleanStoredNodes);
        DEFAULT_ITEM_STACK = builder
            .translation("config.staticlogistics.item_stack_size")
            .defineInRange("item_stack_size", DefaultItemStack, 1, Integer.MAX_VALUE);
        DEFAULT_FLUID_STACK = builder
            .translation("config.staticlogistics.fluid_stack_size")
            .defineInRange("fluid_stack_size", DefaultFluidStack, 1, Integer.MAX_VALUE);
        DEFAULT_ENERGY_STACK = builder
            .translation("config.staticlogistics.energy_stack_size")
            .defineInRange("energy_stack_size", DefaultEnergyStack, 1, Integer.MAX_VALUE);
        MEK_CHEMICAL_STACK = builder
            .translation("config.staticlogistics.mek_chemical_stack_size")
            .defineInRange("mek_chemical_stack_size", MekChemicalStack, 1, Integer.MAX_VALUE);
        MEK_HEAT_STACK = builder
            .translation("config.staticlogistics.mek_heat_stack_size")
            .defineInRange("mek_heat_stack_size", MekHeatStack, 1, Integer.MAX_VALUE);
        ARS_SOURCE_STACK = builder
            .translation("config.staticlogistics.ars_source_stack_size")
            .defineInRange("ars_source_stack_size", ArsSourceStack, 1, Integer.MAX_VALUE);
        BOTANIA_MANA_STACK = builder
            .translation("config.staticlogistics.botania_mana_stack_size")
            .defineInRange("botania_mana_stack_size", BotaniaManaStack, 1, Integer.MAX_VALUE);
        builder.pop();

        builder.push("performance");
        CACHE_PROVIDER_SIZE = builder
            .translation("config.staticlogistics.cache.provider_size")
            .comment("Expected active provider count used to size the scheduler index. This does not limit active nodes.")
            .defineInRange("provider_size", 1000, 100, 10000);
        CACHE_LOAD_FACTOR = builder
            .translation("config.staticlogistics.cache.load_factor")
            .comment("Cache load factor. Controls when hash tables resize. 0.75 is standard.")
            .defineInRange("load_factor", 0.75, 0.1, 1.0);
        NETWORK_MAX_BULK_ENTRIES = builder
            .translation("config.staticlogistics.network.max_bulk_entries")
            .comment("Maximum entries per bulk sync packet. Larger values may cause network issues.")
            .defineInRange("max_bulk_entries", 100, 10, 1000);
        PERF_TICKER_BATCH_SIZE = builder
            .translation("config.staticlogistics.performance.ticker_batch_size")
            .comment("Base number of node/type candidates scanned per tick.")
            .defineInRange("ticker_batch_size", 50, 10, 200);
        PERF_TICKER_TIME_BUDGET_US = builder
            .translation("config.staticlogistics.performance.ticker_time_budget_us")
            .comment("Soft scheduler time budget per dimension tick in microseconds.")
            .defineInRange("ticker_time_budget_us", 1_500, 100, 10_000);
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

        builder.push("upgrades");
        IRON_MULTIPLIER = builder
            .translation("config.staticlogistics.iron_multiplier")
            .defineInRange("iron_multiplier", ironMultCache, 1, 128);
        GOLD_MULTIPLIER = builder
            .translation("config.staticlogistics.gold_multiplier")
            .defineInRange("gold_multiplier", goldMultCache, 1, 256);
        DIAMOND_MULTIPLIER = builder
            .translation("config.staticlogistics.diamond_multiplier")
            .defineInRange("diamond_multiplier", diamondMultCache, 1, 512);
        NETHERITE_MULTIPLIER = builder
            .translation("config.staticlogistics.netherite_multiplier")
            .defineInRange("netherite_multiplier", netheriteMultCache, 1, 1024);
        NETHER_STAR_MULTIPLIER = builder
            .translation("config.staticlogistics.nether_star_multiplier")
            .defineInRange("nether_star_multiplier", netherStarMultCache, 1, 10_000);
        builder.pop();

        CONFIG_SPEC = builder.build();
        container.registerConfig(ModConfig.Type.COMMON, CONFIG_SPEC, "staticlogistics.toml");
    }

    @SubscribeEvent
    public static void onConfigEvent(ModConfigEvent event) {
        if (event.getConfig().getSpec() == CONFIG_SPEC) {
            configGeneration.incrementAndGet();
            onLoad();
            syncConfigToPlayers();
        }
    }

    public static void onLoad() {
        if (CONFIG_SPEC.isLoaded()) {
            DefaultRadius = DEFAULT_RADIUS.get();
            DefaultTickInterval = DEFAULT_TICK_INTERVAL.get();
            DefaultItemStack = DEFAULT_ITEM_STACK.get();
            DefaultFluidStack = DEFAULT_FLUID_STACK.get();
            DefaultEnergyStack = DEFAULT_ENERGY_STACK.get();

            MekChemicalStack = MEK_CHEMICAL_STACK.get();
            MekHeatStack = MEK_HEAT_STACK.get();
            ArsSourceStack = ARS_SOURCE_STACK.get();
            BotaniaManaStack = BOTANIA_MANA_STACK.get();
            ironMultCache = IRON_MULTIPLIER.get();
            goldMultCache = GOLD_MULTIPLIER.get();
            diamondMultCache = DIAMOND_MULTIPLIER.get();
            netheriteMultCache = NETHERITE_MULTIPLIER.get();
            netherStarMultCache = NETHER_STAR_MULTIPLIER.get();

            autoCleanStoredNodes = AUTO_CLEAN_STORED_NODES.get();
            loadPerformanceConfig();
        }
    }

    private static void loadPerformanceConfig() {
        cacheProviderSize = CACHE_PROVIDER_SIZE.get();
        cacheLoadFactor = CACHE_LOAD_FACTOR.get();

        networkMaxBulkEntries = NETWORK_MAX_BULK_ENTRIES.get();

        perfTickerBatchSize = PERF_TICKER_BATCH_SIZE.get();
        perfTickerTimeBudgetUs = PERF_TICKER_TIME_BUDGET_US.get();
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

    public static int getBotaniaManaStack() {
        return BotaniaManaStack;
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

    public static int getNetworkMaxBulkEntries() {
        return networkMaxBulkEntries;
    }

    public static int getPerfTickerBatchSize() {
        return perfTickerBatchSize;
    }

    public static long getPerfTickerTimeBudgetNanos() {
        return perfTickerTimeBudgetUs * 1_000L;
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

    private static void syncConfigToPlayers() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        S2CConfigSyncPayload payload = new S2CConfigSyncPayload(buildConfigTag());
        PacketDistributor.sendToAllPlayers(payload);
    }

    private static CompoundTag buildConfigTag() {
        CompoundTag tag = new CompoundTag();
        // 通用设置。
        tag.putInt("defaultRadius", DefaultRadius);
        tag.putInt("defaultTickInterval", DefaultTickInterval);
        tag.putInt("itemStack", DefaultItemStack);
        tag.putInt("fluidStack", DefaultFluidStack);
        tag.putInt("energyStack", DefaultEnergyStack);
        tag.putInt("mekChemicalStack", MekChemicalStack);
        tag.putInt("mekHeatStack", MekHeatStack);
        tag.putInt("arsSourceStack", ArsSourceStack);
        tag.putInt("botaniaManaStack", BotaniaManaStack);
        tag.putBoolean("autoCleanStoredNodes", autoCleanStoredNodes);
        // 升级设置。
        tag.putInt("ironMult", ironMultCache);
        tag.putInt("goldMult", goldMultCache);
        tag.putInt("diamondMult", diamondMultCache);
        tag.putInt("netheriteMult", netheriteMultCache);
        tag.putInt("netherStarMult", netherStarMultCache);
        // 性能设置。
        tag.putInt("cacheProviderSize", cacheProviderSize);
        tag.putDouble("cacheLoadFactor", cacheLoadFactor);
        tag.putInt("networkMaxBulkEntries", networkMaxBulkEntries);
        tag.putInt("tickerBatchSize", perfTickerBatchSize);
        tag.putInt("tickerTimeBudgetUs", perfTickerTimeBudgetUs);
        tag.putInt("cleanInterval", perfCleanInterval);
        tag.putInt("defaultCooldown", perfDefaultCooldown);
        tag.putInt("batchCleanThreshold", perfBatchCleanThreshold);
        tag.putInt("batchCleanSize", perfBatchCleanSize);
        tag.putInt("contextPoolSize", perfContextPoolSize);
        return tag;
    }

    public static void applyServerConfig(CompoundTag tag) {
        if (tag == null || tag.isEmpty()) {
            onLoad();
            return;
        }
        DefaultRadius = tag.getInt("defaultRadius");
        DefaultTickInterval = tag.getInt("defaultTickInterval");
        DefaultItemStack = tag.getInt("itemStack");
        DefaultFluidStack = tag.getInt("fluidStack");
        DefaultEnergyStack = tag.getInt("energyStack");
        MekChemicalStack = tag.getInt("mekChemicalStack");
        MekHeatStack = tag.getInt("mekHeatStack");
        ArsSourceStack = tag.getInt("arsSourceStack");
        BotaniaManaStack = tag.getInt("botaniaManaStack");
        autoCleanStoredNodes = tag.getBoolean("autoCleanStoredNodes");
        ironMultCache = tag.getInt("ironMult");
        goldMultCache = tag.getInt("goldMult");
        diamondMultCache = tag.getInt("diamondMult");
        netheriteMultCache = tag.getInt("netheriteMult");
        netherStarMultCache = tag.getInt("netherStarMult");
        cacheProviderSize = tag.getInt("cacheProviderSize");
        cacheLoadFactor = tag.getDouble("cacheLoadFactor");
        networkMaxBulkEntries = tag.getInt("networkMaxBulkEntries");
        perfTickerBatchSize = tag.getInt("tickerBatchSize");
        perfTickerTimeBudgetUs = tag.contains("tickerTimeBudgetUs")
            ? tag.getInt("tickerTimeBudgetUs") : 1_500;
        perfCleanInterval = tag.getInt("cleanInterval");
        perfDefaultCooldown = tag.getInt("defaultCooldown");
        perfBatchCleanThreshold = tag.getInt("batchCleanThreshold");
        perfBatchCleanSize = tag.getInt("batchCleanSize");
        perfContextPoolSize = tag.getInt("contextPoolSize");
    }
}
