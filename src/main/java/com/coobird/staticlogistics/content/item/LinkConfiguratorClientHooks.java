package com.coobird.staticlogistics.content.item;

import java.util.function.BooleanSupplier;

/**
 * 隔离通用物品逻辑与客户端键位系统，避免服务端加载客户端类。
 */
public final class LinkConfiguratorClientHooks {
    private static BooleanSupplier bulkSelectionActive = () -> false;

    private LinkConfiguratorClientHooks() {
    }

    public static void installBulkSelectionKey(BooleanSupplier supplier) {
        bulkSelectionActive = supplier == null ? () -> false : supplier;
    }

    public static boolean isBulkSelectionActive() {
        return bulkSelectionActive.getAsBoolean();
    }
}
