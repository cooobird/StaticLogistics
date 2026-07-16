package com.coobird.staticlogistics.api.type;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.StringRepresentable;

/**
 * 物品分发策略 —— 决定物品按什么顺序分给多个目标节点。
 * <p>
 * 注册和查找由 Static Logistics 的策略注册中心管理。
 */
public record DistributionStrategy(ResourceLocation id, GroupSorter sorter) implements StringRepresentable {

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
}
