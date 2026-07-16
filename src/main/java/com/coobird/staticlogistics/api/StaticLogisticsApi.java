package com.coobird.staticlogistics.api;

import com.coobird.staticlogistics.api.transfer.ResourceAdapterRegistrar;
import com.coobird.staticlogistics.transfer.TransferRegistries;

/**
 * StaticLogistics 对第三方模组公开的稳定入口。
 */
public final class StaticLogisticsApi {
    private StaticLogisticsApi() {
    }

    public static ResourceAdapterRegistrar resourceAdapters() {
        return TransferRegistries.registrar();
    }
}
