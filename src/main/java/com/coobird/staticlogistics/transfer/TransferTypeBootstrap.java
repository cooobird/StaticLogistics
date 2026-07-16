package com.coobird.staticlogistics.transfer;

/**
 * 传输类型启动注册清单。
 *
 * <p>这里只列出内置和联动资源类型及其稳定 bitOffset，
 * {@link TransferRegistries} 只负责维护注册表本身。
 */
public final class TransferTypeBootstrap {
    private static boolean initialized = false;

    public static final int BIT_ITEM = 0;
    public static final int BIT_FLUID = 1;
    public static final int BIT_ENERGY = 2;
    public static final int BIT_MEK_CHEMICALS = 3;
    public static final int BIT_MEK_HEAT = 4;
    public static final int BIT_ARS_SOURCE = 5;
    public static final int BIT_BOTANIA_MANA = 6;

    private TransferTypeBootstrap() {
    }

    public static synchronized void init() {
        if (initialized) return;
        registerBuiltinAdapters();
        initialized = true;
    }

    private static void registerBuiltinAdapters() {
        TransferRegistries.registerAdapter(new ItemResource(), BIT_ITEM);
        TransferRegistries.registerAdapter(new FluidResource(), BIT_FLUID);
        TransferRegistries.registerAdapter(new EnergyResource(), BIT_ENERGY);
    }

}
