package com.coobird.staticlogistics.transfer;

import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.api.type.DistributionStrategy;
import com.coobird.staticlogistics.api.type.GroupSorter;
import com.coobird.staticlogistics.transfer.strategy.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * 分发策略注册表 —— 管理所有已注册的 DistributionStrategy。
 * 第三方模组通过 {@link #register(ResourceLocation, GroupSorter)} 注册自定义策略。
 */
public final class DistributionStrategyRegistry {
    private static final Map<ResourceLocation, DistributionStrategy> REGISTRY = new LinkedHashMap<>();
    private static final List<DistributionStrategy> VALUES = new ArrayList<>();
    private static final Map<String, DistributionStrategy> LEGACY_MAP = new HashMap<>();

    public static final DistributionStrategy SEQUENTIAL = registerInternal(
        StaticLogistics.asResource("sequential"), SequentialGroupSorter.INSTANCE, "SEQUENTIAL");
    public static final DistributionStrategy ROUND_ROBIN = registerInternal(
        StaticLogistics.asResource("round_robin"), RoundRobinGroupSorter.INSTANCE, "ROUND_ROBIN");
    public static final DistributionStrategy NEAREST = registerInternal(
        StaticLogistics.asResource("nearest"), NearestGroupSorter.INSTANCE, "NEAREST");
    public static final DistributionStrategy FURTHEST = registerInternal(
        StaticLogistics.asResource("furthest"), FurthestGroupSorter.INSTANCE, "FURTHEST");
    public static final DistributionStrategy RANDOM = registerInternal(
        StaticLogistics.asResource("random"), RandomGroupSorter.INSTANCE, "RANDOM");

    private DistributionStrategyRegistry() {
    }

    private static DistributionStrategy registerInternal(ResourceLocation id, GroupSorter sorter, String legacyName) {
        DistributionStrategy strategy = new DistributionStrategy(id, sorter);
        REGISTRY.put(id, strategy);
        VALUES.add(strategy);
        if (legacyName != null && !legacyName.isEmpty()) {
            LEGACY_MAP.put(legacyName.toLowerCase(Locale.ROOT), strategy);
        }
        return strategy;
    }

    /**
     * 外部模组注册自定义分发策略。
     */
    public static DistributionStrategy register(ResourceLocation id, GroupSorter sorter) {
        if (REGISTRY.containsKey(id)) {
            return REGISTRY.get(id);
        }
        DistributionStrategy strategy = new DistributionStrategy(id, sorter);
        REGISTRY.put(id, strategy);
        VALUES.add(strategy);
        return strategy;
    }

    /**
     * 按名称查找策略。支持 ResourceLocation、短名、旧版 enum 名。
     */
    public static DistributionStrategy byName(String name) {
        if (name == null || name.isEmpty()) return SEQUENTIAL;
        ResourceLocation rl = ResourceLocation.tryParse(name);
        if (rl != null && REGISTRY.containsKey(rl)) return REGISTRY.get(rl);
        if (!name.contains(":")) {
            rl = StaticLogistics.asResource(name.toLowerCase(Locale.ROOT));
            if (REGISTRY.containsKey(rl)) return REGISTRY.get(rl);
        }
        DistributionStrategy legacy = LEGACY_MAP.get(name.toLowerCase(Locale.ROOT));
        return legacy != null ? legacy : SEQUENTIAL;
    }

    /**
     * 返回精确匹配的已注册策略；不存在时不进行兼容回退。
     */
    @Nullable
    public static DistributionStrategy get(ResourceLocation id) {
        return REGISTRY.get(id);
    }

    static DistributionStrategy byNameRl(ResourceLocation rl) {
        DistributionStrategy s = REGISTRY.get(rl);
        return s != null ? s : SEQUENTIAL;
    }

    /**
     * 获取所有已注册策略（注册顺序，不可变）。
     */
    public static List<DistributionStrategy> getValues() {
        return Collections.unmodifiableList(VALUES);
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, DistributionStrategy> STREAM_CODEC =
        ResourceLocation.STREAM_CODEC.<RegistryFriendlyByteBuf>cast()
            .map(DistributionStrategyRegistry::byNameRl, DistributionStrategy::id);
}
