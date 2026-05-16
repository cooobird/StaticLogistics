package com.coobird.staticlogistics.api.type;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * 定义物流节点可以拥有的特殊能力/升级类型
 */
public enum UpgradeType {
    SPEED("speed"),
    RANGE("range"),
    STACK("stack"),
    DIMENSION("dimension"),
    BASIC_FILTER("basic_filter"),
    TAG_FILTER("tag_filter"),
    NBT_FILTER("nbt_filter");

    private final String name;

    UpgradeType(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public MutableComponent getDescription() {
        return Component.translatable("tooltip.staticlogistics.upgrade." + name + "_desc");
    }
}