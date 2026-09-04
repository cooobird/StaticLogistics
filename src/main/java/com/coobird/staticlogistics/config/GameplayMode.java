package com.coobird.staticlogistics.config;

/**
 * 控制物流性能是否受容器升级限制。
 */
public enum GameplayMode {
    /**
     * 无需安装升级，直接采用简易模式的最大能力。
     */
    SIMPLE,
    /**
     * 由已安装的升级决定速度、范围、单次传输量和跨维度能力。
     */
    ADVANCED
}
