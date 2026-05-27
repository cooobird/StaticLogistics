package com.coobird.staticlogistics.api.type;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;

import java.util.HashMap;
import java.util.Map;

/**
 * 物品分发策略——决定物品按什么顺序分给多个目标节点
 */
public enum DistributionStrategy implements StringRepresentable {
    SEQUENTIAL("sequential"),       // 顺序分发：按固定顺序一个个来
    ROUND_ROBIN("round_robin"),     // 轮询分发：轮流分给每个目标
    NEAREST("nearest"),             // 优先发给最近的
    FURTHEST("furthest"),           // 优先发给最远的
    RANDOM("random");               // 随机挑一个发

    private static final Map<String, DistributionStrategy> NAME_CACHE = new HashMap<>();

    static {
        for (DistributionStrategy strategy : values()) {
            NAME_CACHE.put(strategy.getSerializedName(), strategy);
        }
    }

    private final String name;

    DistributionStrategy(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    public String getDescriptionId() {
        return "strategy.staticlogistics." + name;
    }

    public Component getDisplayName() {
        return Component.translatable(getDescriptionId());
    }

    public static DistributionStrategy byName(String name, DistributionStrategy fallback) {
        DistributionStrategy strategy = NAME_CACHE.get(name);
        return strategy != null ? strategy : fallback;
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, DistributionStrategy> STREAM_CODEC =
        ByteBufCodecs.VAR_INT.<RegistryFriendlyByteBuf>cast().map(
            index -> DistributionStrategy.values()[index],
            DistributionStrategy::ordinal
        );
}