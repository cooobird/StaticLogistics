package com.coobird.staticlogistics.core;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;

import java.util.*;

public enum DistributionStrategy implements StringRepresentable {
    SEQUENTIAL("sequential") {
        @Override
        public List<StaticLink> sort(List<StaticLink> links) {
            return links.stream()
                .sorted(Comparator.comparingInt(StaticLink::priority).reversed())
                .toList();
        }
    },
    ROUND_ROBIN("round_robin") {
        @Override
        public List<StaticLink> sort(List<StaticLink> links) {
            return SEQUENTIAL.sort(links);
        }
    },
    NEAREST("nearest") {
        @Override
        public List<StaticLink> sort(List<StaticLink> links) {
            return links.stream()
                .sorted(Comparator.comparingInt(StaticLink::priority).reversed()
                    .thenComparingDouble(l -> l.sourcePos().distSqr(l.destPos())))
                .toList();
        }
    },
    FURTHEST("furthest") {
        @Override
        public List<StaticLink> sort(List<StaticLink> links) {
            return links.stream()
                .sorted(Comparator.comparingInt(StaticLink::priority).reversed()
                    .thenComparing((l1, l2) -> Double.compare(l2.sourcePos().distSqr(l2.destPos()), l1.sourcePos().distSqr(l1.destPos()))))
                .toList();
        }
    },
    RANDOM("random") {
        @Override
        public List<StaticLink> sort(List<StaticLink> links) {
            List<StaticLink> sorted = new ArrayList<>(links);
            Collections.shuffle(sorted);
            sorted.sort(Comparator.comparingInt(StaticLink::priority).reversed());
            return sorted;
        }
    };

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

    public Component getLabel() {
        return Component.translatable(getDescriptionId());
    }

    public Component getDisplayName() {
        return getLabel();
    }

    public abstract List<StaticLink> sort(List<StaticLink> links);

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