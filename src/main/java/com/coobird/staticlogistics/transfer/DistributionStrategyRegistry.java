package com.coobird.staticlogistics.transfer;

import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.api.type.DistributionStrategy;
import com.coobird.staticlogistics.api.type.GroupSorter;
import com.coobird.staticlogistics.transfer.strategy.DistanceGroupSorter;
import com.coobird.staticlogistics.transfer.strategy.RandomGroupSorter;
import com.coobird.staticlogistics.transfer.strategy.RoundRobinGroupSorter;
import com.coobird.staticlogistics.transfer.strategy.SequentialGroupSorter;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/// 分发策略注册表 —— 管理所有已注册的 DistributionStrategy。
/// 第三方模组通过 #register(ResourceLocation, GroupSorter) 注册自定义策略。
public final class DistributionStrategyRegistry {
    private record RegistryState(Map<ResourceLocation, DistributionStrategy> strategies,
                                 Map<String, DistributionStrategy> legacyNames,
                                 List<DistributionStrategy> values) {
    }

    private static volatile RegistryState state = new RegistryState(Map.of(), Map.of(), List.of());
    private static volatile boolean frozen;

    public static final DistributionStrategy SEQUENTIAL = registerInternal(
        StaticLogistics.asResource("sequential"), SequentialGroupSorter.INSTANCE, "SEQUENTIAL");
    public static final DistributionStrategy ROUND_ROBIN = registerInternal(
        StaticLogistics.asResource("round_robin"), RoundRobinGroupSorter.INSTANCE, "ROUND_ROBIN");
    public static final DistributionStrategy NEAREST = registerInternal(
        StaticLogistics.asResource("nearest"), DistanceGroupSorter.NEAREST, "NEAREST");
    public static final DistributionStrategy FURTHEST = registerInternal(
        StaticLogistics.asResource("furthest"), DistanceGroupSorter.FURTHEST, "FURTHEST");
    public static final DistributionStrategy RANDOM = registerInternal(
        StaticLogistics.asResource("random"), RandomGroupSorter.INSTANCE, "RANDOM");

    private DistributionStrategyRegistry() {
    }

    private static synchronized DistributionStrategy registerInternal(ResourceLocation id, GroupSorter sorter,
                                                                      String legacyName) {
        DistributionStrategy strategy = new DistributionStrategy(id, sorter);
        publish(state, strategy, legacyName);
        return strategy;
    }

    /// 外部模组注册自定义分发策略。
    public static synchronized DistributionStrategy register(ResourceLocation id, GroupSorter sorter) {
        if (frozen) throw new IllegalStateException("Distribution strategy registration is already frozen");
        RegistryState current = state;
        if (current.strategies().containsKey(id)) return current.strategies().get(id);
        DistributionStrategy strategy = new DistributionStrategy(id, sorter);
        publish(current, strategy, null);
        return strategy;
    }

    public static synchronized void freeze() {
        frozen = true;
    }

    private static void publish(RegistryState current, DistributionStrategy strategy, @Nullable String legacyName) {
        Map<ResourceLocation, DistributionStrategy> strategies = new LinkedHashMap<>(current.strategies());
        Map<String, DistributionStrategy> legacyNames = new LinkedHashMap<>(current.legacyNames());
        strategies.put(strategy.id(), strategy);
        if (legacyName != null && !legacyName.isEmpty()) {
            legacyNames.put(legacyName.toLowerCase(Locale.ROOT), strategy);
        }
        state = new RegistryState(Collections.unmodifiableMap(strategies),
            Collections.unmodifiableMap(legacyNames), List.copyOf(strategies.values()));
    }

    /// 按名称查找策略。支持 ResourceLocation、短名、旧版 enum 名。
    public static DistributionStrategy byName(String name) {
        if (name == null || name.isEmpty()) return SEQUENTIAL;
        RegistryState current = state;
        ResourceLocation rl = ResourceLocation.tryParse(name);
        if (rl != null && current.strategies().containsKey(rl)) return current.strategies().get(rl);
        if (!name.contains(":")) {
            rl = StaticLogistics.asResource(name.toLowerCase(Locale.ROOT));
            if (current.strategies().containsKey(rl)) return current.strategies().get(rl);
        }
        DistributionStrategy legacy = current.legacyNames().get(name.toLowerCase(Locale.ROOT));
        return legacy != null ? legacy : SEQUENTIAL;
    }

    /// 返回精确匹配的已注册策略；不存在时不进行兼容回退。
    @Nullable
    public static DistributionStrategy get(ResourceLocation id) {
        return state.strategies().get(id);
    }

    static DistributionStrategy byNameRl(ResourceLocation rl) {
        DistributionStrategy s = state.strategies().get(rl);
        return s != null ? s : SEQUENTIAL;
    }

    /// 获取所有已注册策略（注册顺序，不可变）。
    public static List<DistributionStrategy> getValues() {
        return state.values();
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, DistributionStrategy> STREAM_CODEC =
        ResourceLocation.STREAM_CODEC.<RegistryFriendlyByteBuf>cast()
            .map(DistributionStrategyRegistry::byNameRl, DistributionStrategy::id);
}
