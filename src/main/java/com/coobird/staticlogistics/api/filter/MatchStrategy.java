package com.coobird.staticlogistics.api.filter;

/**
 * 匹配策略——决定做比较时采用什么规则来比较两个物品/流体
 */
public enum MatchStrategy {
    /**
     * 目标必须与模板完全相等
     */
    EXACT,

    /**
     * 目标必须包含模板的所有内容（用于 Map 类组件）
     */
    CONTAINS,

    /**
     * 使用智能递归比较（自动判断 Map/Collection/等值）
     */
    SMART_CONTAINS,

    /**
     * 跳过该组件，不参与比较
     */
    IGNORE
}