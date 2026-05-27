package com.coobird.staticlogistics.api.type;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * 定义物流节点可以拥有的特殊能力/升级类型
 */
public enum UpgradeType {
    SPEED("speed"),           // 速度升级：加快传输速度
    RANGE("range"),           // 范围升级：增加搜索范围
    STACK("stack"),           // 堆叠升级：增加每次传输数量
    DIMENSION("dimension"),   // 跨维度升级：允许跨维度传输
    BASIC_FILTER("basic_filter"), // 基础过滤：物品白名单/黑名单
    TAG_FILTER("tag_filter"),     // Tag 过滤：按物品 Tag 匹配
    NBT_FILTER("nbt_filter");     // NBT 过滤：按物品 NBT 数据匹配

    // 升级的名称标识
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