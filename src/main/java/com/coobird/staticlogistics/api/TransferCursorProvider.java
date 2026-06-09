package com.coobird.staticlogistics.api;

/**
 * 传输游标提供者 —— 供 GroupSorter 获取 round-robin 游标，避免 API 层直接依赖 GlobalLogisticsManager。
 */
@FunctionalInterface
public interface TransferCursorProvider {
    int[] getCursor(long nodeKey, LogisticsResource<?> type);
}
