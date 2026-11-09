package com.coobird.staticlogistics.transfer;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/// 定义物流节点可以拥有的特殊能力/升级类型
public enum UpgradeType {
    COMBINED("combined"),
    /// 容器共享的速度、范围和单次传输量升级
    FILTER("filter"); /// 通道过滤器

    /// 升级的名称标识
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
