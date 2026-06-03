package com.coobird.staticlogistics.api.type;

import com.coobird.staticlogistics.Staticlogistics;
import com.coobird.staticlogistics.transfer.strategy.distribute.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.StringRepresentable;

import java.util.*;

/**
 * 物品分发策略 —— 决定物品按什么顺序分给多个目标节点。
 * <p>
 * 基于 Registry 模式，第三方模组可调用 {@link #register(ResourceLocation, GroupSorter)} 注册自定义策略。
 * <p>
 * 使用示例（外部模组）：
 * <pre>{@code
 *   DistributionStrategy MY_STRATEGY = DistributionStrategy.register(
 *       ResourceLocation.fromNamespaceAndPath("mymod", "least_busy"),
 *       new MyLeastBusyGroupSorter()
 *   );
 * }</pre>
 */
public final class DistributionStrategy implements StringRepresentable {

    private static final Map<ResourceLocation, DistributionStrategy> REGISTRY = new LinkedHashMap<>();
    private static final List<DistributionStrategy> VALUES = new ArrayList<>();
    private static final Map<String, DistributionStrategy> LEGACY_MAP = new HashMap<>();


    public static final DistributionStrategy SEQUENTIAL = register(
        Staticlogistics.asResource("sequential"),
        SequentialGroupSorter.INSTANCE,
        "SEQUENTIAL"
    );

    public static final DistributionStrategy ROUND_ROBIN = register(
        Staticlogistics.asResource("round_robin"),
        RoundRobinGroupSorter.INSTANCE,
        "ROUND_ROBIN"
    );

    public static final DistributionStrategy NEAREST = register(
        Staticlogistics.asResource("nearest"),
        NearestGroupSorter.INSTANCE,
        "NEAREST"
    );

    public static final DistributionStrategy FURTHEST = register(
        Staticlogistics.asResource("furthest"),
        FurthestGroupSorter.INSTANCE,
        "FURTHEST"
    );

    public static final DistributionStrategy RANDOM = register(
        Staticlogistics.asResource("random"),
        RandomGroupSorter.INSTANCE,
        "RANDOM"
    );

    private final ResourceLocation id;
    private final GroupSorter sorter;

    private DistributionStrategy(ResourceLocation id, GroupSorter sorter) {
        this.id = id;
        this.sorter = sorter;
    }

    /**
     * 注册内置策略。
     */
    private static DistributionStrategy register(ResourceLocation id, GroupSorter sorter, String legacyName) {
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
     *
     * @param id     唯一标识（建议带 mod 命名空间，如 "mymod:least_busy"）
     * @param sorter 排序器实现
     * @return 注册后的策略实例
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
     * 按名称查找策略。
     * <p>支持三种格式：
     * <ol>
     *   <li>新格式 ResourceLocation: {@code "staticlogistics:sequential"} 或 {@code "mymod:custom"}</li>
     *   <li>短名（默认命名空间）: {@code "sequential"} → {@code "staticlogistics:sequential"}</li>
     *   <li>旧版 enum 全大写名: {@code "SEQUENTIAL"} → {@code "staticlogistics:sequential"}</li>
     * </ol>
     *
     * @param name 策略名称
     * @return 找到的策略，未找到返回 SEQUENTIAL
     */
    public static DistributionStrategy byName(String name) {
        if (name == null || name.isEmpty()) {
            return SEQUENTIAL;
        }
        ResourceLocation rl = ResourceLocation.tryParse(name);
        if (rl != null && REGISTRY.containsKey(rl)) {
            return REGISTRY.get(rl);
        }
        if (!name.contains(":")) {
            rl = Staticlogistics.asResource(name.toLowerCase(Locale.ROOT));
            if (REGISTRY.containsKey(rl)) {
                return REGISTRY.get(rl);
            }
        }
        DistributionStrategy legacy = LEGACY_MAP.get(name.toLowerCase(Locale.ROOT));
        if (legacy != null) {
            return legacy;
        }
        return SEQUENTIAL;
    }

    /**
     * 获取所有已注册策略（注册顺序，不可变）。
     */
    public static List<DistributionStrategy> getValues() {
        return Collections.unmodifiableList(VALUES);
    }


    public ResourceLocation getId() {
        return id;
    }

    public GroupSorter getSorter() {
        return sorter;
    }

    public String getDescriptionId() {
        return "strategy.staticlogistics." + id.getPath();
    }

    public Component getDisplayName() {
        return Component.translatable(getDescriptionId());
    }

    @Override
    public String getSerializedName() {
        return id.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DistributionStrategy that)) return false;
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return id.toString();
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, DistributionStrategy> STREAM_CODEC =
        ResourceLocation.STREAM_CODEC.<RegistryFriendlyByteBuf>cast()
            .map(DistributionStrategy::byNameRl, DistributionStrategy::getId);

    private static DistributionStrategy byNameRl(ResourceLocation rl) {
        DistributionStrategy s = REGISTRY.get(rl);
        return s != null ? s : SEQUENTIAL;
    }
}
