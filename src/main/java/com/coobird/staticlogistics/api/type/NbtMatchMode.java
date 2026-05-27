package com.coobird.staticlogistics.api.type;

/**
 * NBT 匹配模式——比较物品时 NBT 数据怎么比
 */
public enum NbtMatchMode {
    PARTIAL, // 部分匹配：只要指定的 NBT 字段对得上就算过
    FULL     // 完全匹配：NBT 必须一模一样才行
}