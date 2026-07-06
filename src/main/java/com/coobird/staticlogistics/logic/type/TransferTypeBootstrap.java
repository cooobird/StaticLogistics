package com.coobird.staticlogistics.logic.type;

import com.coobird.staticlogistics.integration.ModCompat;
import com.coobird.staticlogistics.integration.resource.*;
import com.coobird.staticlogistics.transfer.resource.EnergyResource;
import com.coobird.staticlogistics.transfer.resource.FluidResource;
import com.coobird.staticlogistics.transfer.resource.ItemResource;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

/**
 * 传输类型启动注册入口。
 *
 * <p>稳定 bitOffset 在这里集中显式分配；{@link TransferRegistries} 只负责维护注册表本身。
 */
public final class TransferTypeBootstrap {
    private static final Logger LOGGER = LogUtils.getLogger();

    public static final int BIT_ITEM = 0;
    public static final int BIT_FLUID = 1;
    public static final int BIT_ENERGY = 2;
    public static final int BIT_MEK_GAS = 3;
    public static final int BIT_MEK_INFUSION = 4;
    public static final int BIT_MEK_PIGMENT = 5;
    public static final int BIT_MEK_SLURRY = 6;
    public static final int BIT_MEK_HEAT = 7;
    public static final int BIT_ARS_SOURCE = 8;
    public static final int BIT_BOTANIA_MANA = 9;
    public static final int BIT_GTCEU_ENERGY = 10;

    private static boolean initialized = false;

    private TransferTypeBootstrap() {
    }

    public static synchronized void init() {
        if (initialized) return;

        TransferRegistries.registerAdapter(new ItemResource(), BIT_ITEM);
        TransferRegistries.registerAdapter(new FluidResource(), BIT_FLUID);
        TransferRegistries.registerAdapter(new EnergyResource(), BIT_ENERGY);

        if (ModCompat.isMekanismLoaded()) {
            TransferRegistries.registerAdapter(new MekanismGasResource(), BIT_MEK_GAS);
            TransferRegistries.registerAdapter(new MekanismInfusionResource(), BIT_MEK_INFUSION);
            TransferRegistries.registerAdapter(new MekanismPigmentResource(), BIT_MEK_PIGMENT);
            TransferRegistries.registerAdapter(new MekanismSlurryResource(), BIT_MEK_SLURRY);
            TransferRegistries.registerAdapter(new MekanismHeatResource(), BIT_MEK_HEAT);
            LOGGER.info("Static Logistics: Mekanism transfer types registered.");
        }
        if (ModCompat.isArsNouveauLoaded()) {
            TransferRegistries.registerAdapter(new ArsSourceResource(), BIT_ARS_SOURCE);
            LOGGER.info("Static Logistics: Ars Nouveau source transfer type registered.");
        }
        if (ModCompat.isBotaniaLoaded()) {
            TransferRegistries.registerAdapter(new BotaniaManaResource(), BIT_BOTANIA_MANA);
            LOGGER.info("Static Logistics: Botania mana transfer type registered.");
        }
        if (ModCompat.isGregTechCeuLoaded()) {
            TransferRegistries.registerAdapter(new GregTechEnergyResource(), BIT_GTCEU_ENERGY);
            LOGGER.info("Static Logistics: GregTech CEu energy transfer type registered.");
        }

        initialized = true;
    }
}
