package com.coobird.staticlogistics.api.type;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

public enum DistributionStrategy implements StringRepresentable {
    SEQUENTIAL("sequential"),
    ROUND_ROBIN("round_robin"),
    NEAREST("nearest"),
    FURTHEST("furthest"),
    RANDOM("random");

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
    @NotNull
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