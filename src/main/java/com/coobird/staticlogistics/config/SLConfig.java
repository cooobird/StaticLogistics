package com.coobird.staticlogistics.config;

import com.coobird.staticlogistics.Staticlogistics;
import com.coobird.staticlogistics.api.filter.MatchStrategy;
import com.coobird.staticlogistics.filter.registry.ComponentMatchStrategyRegistry;
import com.coobird.staticlogistics.network.s2c.S2CConfigSyncPacket;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// 模组主配置文件：定义所有可配置项，注册到 NeoForge 配置系统，并在加载/重载时把值缓存到 volatile 字段中
@EventBusSubscriber(modid = Staticlogistics.MODID)
public final class SLConfig {

    /**
     * 配置代数计数器，每次重载递增。ContainerConfig 用它判断缓存是否失效。
     */
    public static volatile long configGeneration = 0;

    // 构造出的 ModConfigSpec 对象，注册和事件匹配时用
    private static ModConfigSpec CONFIG_SPEC;

    // ===== 通用设置 =====
    // 物流节点的默认搜索半径（格）
    public static ModConfigSpec.IntValue DEFAULT_RADIUS;
    // 物流节点的默认工作间隔（tick）
    public static ModConfigSpec.IntValue DEFAULT_TICK_INTERVAL;
    // 每 tick 最大传输量上限
    public static ModConfigSpec.LongValue MAX_TRANSFER_LIMIT;

    // ===== 核心资源每 tick 传输量 =====
    // 物品每 tick 传输堆叠数
    public static ModConfigSpec.IntValue DEFAULT_ITEM_STACK;
    // 流体每 tick 传输量（mB）
    public static ModConfigSpec.IntValue DEFAULT_FLUID_STACK;
    // 能量每 tick 传输量（FE）
    public static ModConfigSpec.IntValue DEFAULT_ENERGY_STACK;

    // ===== 联动模组资源传输量 =====
    // Mekanism 化学品每 tick 传输量
    public static ModConfigSpec.IntValue MEK_CHEMICAL_STACK;
    // Mekanism 热量每 tick 传输量
    public static ModConfigSpec.IntValue MEK_HEAT_STACK;
    // Ars Nouveau 魔源每 tick 传输量
    public static ModConfigSpec.IntValue ARS_SOURCE_STACK;

    // ===== 升级倍率（按材料等级） =====
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

    // ===== 过滤器相关 =====
    // 数据组件匹配策略覆盖列表（格式："命名空间:组件ID=策略"）
    public static ModConfigSpec.ConfigValue<List<? extends String>> COMPONENT_STRATEGY_OVERRIDES;

    // ===== 杂项 =====
    // 是否在物流节点被拆除时自动清理玩家物品中存储的节点引用
    public static ModConfigSpec.BooleanValue AUTO_CLEAN_STORED_NODES;

    // 供应方缓存最大条目数
    public static ModConfigSpec.IntValue CACHE_PROVIDER_SIZE;
    // 缓存哈希表的负载因子
    public static ModConfigSpec.DoubleValue CACHE_LOAD_FACTOR;
    // 每个面缓存的目标最大数量
    public static ModConfigSpec.IntValue CACHE_TARGET_SIZE;

    // ===== 网络设置 =====
    // 批量同步数据包每包最大条目数
    public static ModConfigSpec.IntValue NETWORK_MAX_BULK_ENTRIES;

    // ===== 性能设置 =====
    // 每 tick 处理的节点数量
    public static ModConfigSpec.IntValue PERF_TICKER_BATCH_SIZE;
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

    // ----- 运行时缓存值（volatile 保证多线程可见性） -----
    // 通用设置缓存值
    private static volatile int DefaultRadius = 16;
    private static volatile int DefaultTickInterval = 20;
    private static volatile long MaxTransferLimit = 10_000_000L;
    // 核心资源传输量缓存值
    private static volatile int DefaultItemStack = 8;
    private static volatile int DefaultFluidStack = 250;
    private static volatile int DefaultEnergyStack = 1024;

    // 联动模组资源传输量缓存值
    private static volatile int MekChemicalStack = 250;
    private static volatile int MekHeatStack = 1000;
    private static volatile int ArsSourceStack = 100;

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
    private static volatile int cacheTargetSize = 50;
    // 网络设置缓存值
    private static volatile int networkMaxBulkEntries = 100;

    // 性能设置缓存值
    private static volatile int perfTickerBatchSize = 50;
    private static volatile int perfCleanInterval = 200;
    private static volatile int perfDefaultCooldown = 10;
    private static volatile int perfBatchCleanThreshold = 500;
    private static volatile int perfBatchCleanSize = 200;
    private static volatile int perfContextPoolSize = 100;

    // 注册配置文件：构建 ModConfigSpec 并注册到 NeoForge 配置系统（SERVER 类型）
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
            .defineInRange("max_transfer_limit", MaxTransferLimit, 1L, Long.MAX_VALUE);
        AUTO_CLEAN_STORED_NODES = builder
            .translation("config.staticlogistics.auto_clean_stored_nodes")
            .comment("If true, stored node references in Link Configurator items will be automatically cleaned after batch linking or when a node is removed.")
            .define("auto_clean_stored_nodes", autoCleanStoredNodes);
        DEFAULT_ITEM_STACK = builder
            .translation("config.staticlogistics.item_stack_size")
            .defineInRange("item_stack_size", DefaultItemStack, 1, 64);
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
        builder.pop();

        builder.push("performance");
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
        NETWORK_MAX_BULK_ENTRIES = builder
            .translation("config.staticlogistics.network.max_bulk_entries")
            .comment("Maximum entries per bulk sync packet. Larger values may cause network issues.")
            .defineInRange("max_bulk_entries", 100, 10, 1000);
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
        container.registerConfig(ModConfig.Type.COMMON, CONFIG_SPEC, "staticlogistics.toml");
    }

    @SubscribeEvent
    public static void onConfigEvent(ModConfigEvent event) {
        if (event.getConfig().getSpec() == CONFIG_SPEC) {
            configGeneration++;
            onLoad();
            syncConfigToPlayers();
        }
    }

    // 配置加载/重载回调：把 ModConfigSpec 的当前值同步到 volatile 缓存字段
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

    // 解析数据组件匹配策略覆盖列表，写入 ComponentMatchStrategyRegistry
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

    // 把缓存、网络、性能相关配置项同步到 volatile 缓存字段
    private static void loadPerformanceConfig() {
        cacheProviderSize = CACHE_PROVIDER_SIZE.get();
        cacheLoadFactor = CACHE_LOAD_FACTOR.get();
        cacheTargetSize = CACHE_TARGET_SIZE.get();

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

    public static long getMaxTransferLimit() {
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

    /**
     * 将当前服务端配置广播给所有在线玩家。
     * 仅在逻辑服务端调用有效（客户端调用无操作）。
     */
    private static void syncConfigToPlayers() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        S2CConfigSyncPacket payload = buildSyncPayload();
        PacketDistributor.sendToAllPlayers(payload);
    }

    /**
     * 从当前 volatile 缓存值构建同步包。
     */
    private static S2CConfigSyncPacket buildSyncPayload() {
        return new S2CConfigSyncPacket(
            DefaultRadius, DefaultTickInterval, MaxTransferLimit,
            DefaultItemStack, DefaultFluidStack, DefaultEnergyStack,
            MekChemicalStack, MekHeatStack, ArsSourceStack,
            ironMultCache, goldMultCache, diamondMultCache, netheriteMultCache, netherStarMultCache,
            autoCleanStoredNodes,
            cacheProviderSize, cacheLoadFactor, cacheTargetSize,
            networkMaxBulkEntries,
            perfTickerBatchSize, perfCleanInterval, perfDefaultCooldown,
            perfBatchCleanThreshold, perfBatchCleanSize, perfContextPoolSize,
            new ArrayList<>(COMPONENT_STRATEGY_OVERRIDES.get())
        );
    }

    /**
     * 客户端收到服务端同步的配置后，写入 volatile 缓存。
     */
    public static void applyServerConfig(S2CConfigSyncPacket p) {
        if (p == null) {
            onLoad();
            return;
        }
        DefaultRadius = p.defaultRadius();
        DefaultTickInterval = p.defaultTickInterval();
        MaxTransferLimit = p.maxTransferLimit();
        DefaultItemStack = p.itemStack();
        DefaultFluidStack = p.fluidStack();
        DefaultEnergyStack = p.energyStack();
        MekChemicalStack = p.mekChemicalStack();
        MekHeatStack = p.mekHeatStack();
        ArsSourceStack = p.arsSourceStack();
        ironMultCache = p.ironMult();
        goldMultCache = p.goldMult();
        diamondMultCache = p.diamondMult();
        netheriteMultCache = p.netheriteMult();
        netherStarMultCache = p.netherStarMult();
        autoCleanStoredNodes = p.autoCleanStoredNodes();
        cacheProviderSize = p.cacheProviderSize();
        cacheLoadFactor = p.cacheLoadFactor();
        cacheTargetSize = p.cacheTargetSize();
        networkMaxBulkEntries = p.networkMaxBulkEntries();
        perfTickerBatchSize = p.tickerBatchSize();
        perfCleanInterval = p.cleanInterval();
        perfDefaultCooldown = p.defaultCooldown();
        perfBatchCleanThreshold = p.batchCleanThreshold();
        perfBatchCleanSize = p.batchCleanSize();
        perfContextPoolSize = p.contextPoolSize();
        Map<String, String> map = new HashMap<>();
        for (String entry : p.componentStrategyOverrides()) {
            String[] parts = entry.split("=", 2);
            if (parts.length == 2) map.put(parts[0], parts[1].toUpperCase());
        }
        ComponentMatchStrategyRegistry.loadConfigOverrides(map);
    }
}