package com.coobird.staticlogistics.transfer;

/// 传输类型启动注册入口。
///
/// <p>位号由注册中心自动分配。
public final class TransferTypeBootstrap {
    private static boolean initialized = false;

    private TransferTypeBootstrap() {
    }

    public static synchronized void init() {
        if (initialized) return;

        TransferRegistries.registerAdapter(new ItemResource());
        TransferRegistries.registerAdapter(new FluidResource());
        TransferRegistries.registerAdapter(new EnergyResource());

        initialized = true;
    }
}
