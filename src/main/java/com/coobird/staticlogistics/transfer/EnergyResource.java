package com.coobird.staticlogistics.transfer;

import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.api.transfer.TransactionCapabilities;
import com.coobird.staticlogistics.config.SLConfig;
import com.coobird.staticlogistics.logistics.node.EndpointConfig;
import com.coobird.staticlogistics.logistics.util.SaturatedMath;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.energy.IEnergyStorage;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.function.LongSupplier;
import java.util.function.Supplier;

/// 接入能量传输。
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
    public Component unit() {
        return Component.translatable("gui.staticlogistics.unit.energy");
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
    public LongSupplier baseStackSizeSupplier() {
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
        return CapabilityCache.get(level, pos, face, ForgeCapabilities.ENERGY);
    }

    @Override
    public Capability<IEnergyStorage> blockCapability() {
        return ForgeCapabilities.ENERGY;
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

    @Override
    public long maxInsertToTarget(IEnergyStorage handle, Object value, EndpointConfig config) {
        long stock = config.linkConfig.getKeepStock();
        return stock <= 0 ? Long.MAX_VALUE : Math.max(0L, stock - handle.getEnergyStored());
    }

}
