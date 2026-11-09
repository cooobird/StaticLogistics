package com.coobird.staticlogistics.integration.resource;

import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.api.transfer.TransactionCapabilities;
import com.coobird.staticlogistics.config.SLConfig;
import com.coobird.staticlogistics.logistics.node.EndpointConfig;
import com.coobird.staticlogistics.transfer.LogisticsResource;
import com.mojang.logging.LogUtils;
import mekanism.api.heat.IHeatHandler;
import mekanism.common.capabilities.Capabilities;
import mekanism.common.registries.MekanismBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.BlockCapability;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.function.LongSupplier;
import java.util.function.Supplier;

/// 接入 Mekanism 热量传输。
public class MekanismHeatResource implements LogisticsResource<IHeatHandler> {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ResourceLocation TYPE_ID = StaticLogistics.asResource("mek_heat");

    @Override
    public ResourceLocation typeId() {
        return TYPE_ID;
    }

    @Override
    public int color() {
        return 0xFFFF6600;
    }

    @Override
    public Component unit() {
        return Component.translatable("gui.staticlogistics.unit.heat");
    }

    @Override
    public String translationKey() {
        return "transfer_type.staticlogistics.mek_heat";
    }

    @Override
    public Supplier<ItemStack> iconSupplier() {
        return () -> new ItemStack(MekanismBlocks.RESISTIVE_HEATER.get());
    }

    @Override
    public LongSupplier baseStackSizeSupplier() {
        return SLConfig::getMekHeatStack;
    }

    @Override
    public TransactionCapabilities transactionCapabilities() {
        return TransactionCapabilities.exactCompensating();
    }

    @Override
    public @Nullable IHeatHandler resolve(ServerLevel level, BlockPos pos, Direction face) {
        return level.getCapability(Capabilities.HEAT, pos, face);
    }

    @Override
    public BlockCapability<IHeatHandler, Direction> blockCapability() {
        return Capabilities.HEAT;
    }

    @Override
    public long extract(IHeatHandler handle, long amount, boolean simulate) {
        if (!simulate) return transferHeatOut(handle, amount, false);
        try {
            return transferHeatOut(handle, amount, true);
        } catch (RuntimeException exception) {
            LOGGER.error("Mekanism heat extract simulation failed", exception);
            return 0;
        }
    }

    @Override
    public long insert(IHeatHandler handle, long amount, boolean simulate) {
        if (!simulate) {
            handle.handleHeat(amount);
        }
        return amount;
    }

    private static long transferHeatOut(IHeatHandler handle, long amount, boolean simulate) {
        int capacitorCount = handle.getHeatCapacitorCount();
        if (capacitorCount <= 0) return 0;

        double totalHeat = 0;
        for (int i = 0; i < capacitorCount; i++) {
            totalHeat += handle.getTemperature(i) * handle.getHeatCapacity(i);
        }

        long actualAmount = Math.min(amount, (long) totalHeat);
        if (actualAmount <= 0 || simulate) return actualAmount;

        double totalCapacity = handle.getTotalHeatCapacity();
        if (totalCapacity <= 0) return 0;
        for (int i = 0; i < capacitorCount; i++) {
            double ratio = handle.getHeatCapacity(i) / totalCapacity;
            handle.handleHeat(i, -(actualAmount * ratio));
        }
        return actualAmount;
    }

    @Override
    public long maxInsertToTarget(IHeatHandler handle, Object value, EndpointConfig config) {
        long remaining = config.linkConfig.getKeepStock();
        if (remaining <= 0) return Long.MAX_VALUE;
        for (int capacitor = 0; capacitor < handle.getHeatCapacitorCount(); capacitor++) {
            double stored = handle.getTemperature(capacitor) * handle.getHeatCapacity(capacitor);
            if (!Double.isFinite(stored) || stored < 0) return 0;
            remaining -= Math.min(remaining, (long) stored);
            if (remaining == 0) break;
        }
        return remaining;
    }

}
