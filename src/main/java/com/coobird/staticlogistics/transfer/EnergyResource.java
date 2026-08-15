package com.coobird.staticlogistics.transfer;

import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.api.transfer.TransactionCapabilities;
import com.coobird.staticlogistics.config.SLConfig;
import com.coobird.staticlogistics.logistics.util.SaturatedMath;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * 能量资源适配器 —— 最简单的资源类型，无过滤器、无冷却、无链接要求。
 */
public class EnergyResource implements LogisticsResource<IEnergyStorage> {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ResourceLocation TYPE_ID = StaticLogistics.asResource("energy");

    @Override
    public ResourceLocation typeId() {
        return TYPE_ID;
    }

    @Override
    public int color() {
        return 0xFFFFFF00;
    }

    @Override
    public String translationKey() {
        return "transfer_type.staticlogistics.energy";
    }

    @Override
    public Supplier<ItemStack> iconSupplier() {
        return () -> new ItemStack(Items.REDSTONE);
    }

    @Override
    public IntSupplier baseStackSizeSupplier() {
        return SLConfig::getEnergyStack;
    }

    @Override
    public boolean requiresCooldown() {
        return false;
    }

    @Override
    public boolean requiresValidLinks() {
        return false;
    }

    @Override
    public TransactionCapabilities transactionCapabilities() {
        return TransactionCapabilities.exactCompensating();
    }

    @Override
    public @Nullable IEnergyStorage resolve(ServerLevel level, BlockPos pos, Direction face) {
        return level.getCapability(Capabilities.EnergyStorage.BLOCK, pos, face);
    }

    @Override
    public BlockCapability<IEnergyStorage, Direction> blockCapability() {
        return Capabilities.EnergyStorage.BLOCK;
    }

    @Override
    public long extract(IEnergyStorage handle, long amount, boolean simulate) {
        if (!simulate) return handle.extractEnergy(SaturatedMath.toNonNegativeInt(amount), false);
        try {
            return handle.extractEnergy(SaturatedMath.toNonNegativeInt(amount), true);
        } catch (RuntimeException exception) {
            LOGGER.error("Energy extract simulation failed", exception);
            return 0;
        }
    }

    @Override
    public long insert(IEnergyStorage handle, long amount, boolean simulate) {
        if (!simulate) return handle.receiveEnergy(SaturatedMath.toNonNegativeInt(amount), false);
        try {
            return handle.receiveEnergy(SaturatedMath.toNonNegativeInt(amount), true);
        } catch (RuntimeException exception) {
            LOGGER.error("Energy insert simulation failed", exception);
            return 0;
        }
    }
}
