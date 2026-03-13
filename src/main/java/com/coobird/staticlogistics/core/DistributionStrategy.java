package com.coobird.staticlogistics.core;

import com.mojang.serialization.Codec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public enum DistributionStrategy implements StringRepresentable {
    /**
     * 顺序优先：填满一个再填下一个
     */
    SEQUENTIAL("sequential") {
        @Override
        public List<StaticLink> sort(List<StaticLink> links) {
            // 保持原始顺序
            return links;
        }
    },
    /**
     * 轮询分发：每个目标轮流发一次
     * 注意：逻辑由 TransferEngine 的提前退出机制 (return true) 配合实现
     */
    ROUND_ROBIN("round_robin") {
        @Override
        public List<StaticLink> sort(List<StaticLink> links) {
            return links;
        }
    },
    /**
     * 最近优先：物理距离最近的优先
     */
    NEAREST("nearest") {
        @Override
        public List<StaticLink> sort(List<StaticLink> links) {
            List<StaticLink> sorted = new ArrayList<>(links);
            sorted.sort(Comparator.comparingDouble(l -> l.sourcePos().distSqr(l.destPos())));
            return sorted;
        }
    },
    /**
     * 最远优先：物理距离最远的优先
     */
    FURTHEST("furthest") {
        @Override
        public List<StaticLink> sort(List<StaticLink> links) {
            List<StaticLink> sorted = new ArrayList<>(links);
            sorted.sort((l1, l2) -> Double.compare(l2.destPos().distSqr(l2.sourcePos()), l1.destPos().distSqr(l1.sourcePos())));
            return sorted;
        }
    },
    /**
     * 随机分发：完全随机选择目标
     */
    RANDOM("random") {
        @Override
        public List<StaticLink> sort(List<StaticLink> links) {
            List<StaticLink> sorted = new ArrayList<>(links);
            Collections.shuffle(sorted);
            return sorted;
        }
    };

    public static final Codec<DistributionStrategy> CODEC = StringRepresentable.fromEnum(DistributionStrategy::values);
    public static final StreamCodec<RegistryFriendlyByteBuf, DistributionStrategy> STREAM_CODEC =
        ByteBufCodecs.VAR_INT.<RegistryFriendlyByteBuf>cast().map(
            index -> DistributionStrategy.values()[index],
            DistributionStrategy::ordinal
        );

    private final String name;

    DistributionStrategy(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    public Component getDisplayName() {
        return Component.translatable("strategy.staticlogistics." + name);
    }

    /**
     * 根据当前策略对链路进行排序
     */
    public abstract List<StaticLink> sort(List<StaticLink> links);
}