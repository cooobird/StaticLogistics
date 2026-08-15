package com.coobird.staticlogistics.integration.resource;

import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.api.transfer.TransactionCapabilities;
import com.coobird.staticlogistics.config.SLConfig;
import com.coobird.staticlogistics.logistics.util.SaturatedMath;
import com.coobird.staticlogistics.transfer.LogisticsResource;
import com.hollingsworth.arsnouveau.api.source.ISourceCap;
import com.hollingsworth.arsnouveau.setup.registry.CapabilityRegistry;
import com.hollingsworth.arsnouveau.setup.registry.ItemsRegistry;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.BlockCapability;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * Ars Nouveau 魔源资源适配器。
 *
 * <p>通过 {@link LogisticsResource} 接口接入物流管线。
 * 魔源使用 int 量，extractSource/receiveSource 均为幂等安全操作。
 */
public class ArsSourceResource implements LogisticsResource<ISourceCap> {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ResourceLocation TYPE_ID = StaticLogistics.asResource("ars_source");

    @Override
    public ResourceLocation typeId() {
        return TYPE_ID;
    }

    @Override
    public int color() {
        return 0xFF8000FF;
    }

    @Override
    public String translationKey() {
        return "transfer_type.staticlogistics.ars_source";
    }

    @Override
    public Supplier<ItemStack> iconSupplier() {
        return () -> new ItemStack(ItemsRegistry.SOURCE_GEM);
    }

    @Override
    public IntSupplier baseStackSizeSupplier() {
        return SLConfig::getArsSourceStack;
    }

    @Override
    public TransactionCapabilities transactionCapabilities() {
        return TransactionCapabilities.exactCompensating();
    }

    @Override
    public @Nullable ISourceCap resolve(ServerLevel level, BlockPos pos, Direction face) {
        return level.getCapability(CapabilityRegistry.SOURCE_CAPABILITY, pos, face);
    }

    @Override
    public BlockCapability<ISourceCap, Direction> blockCapability() {
        return CapabilityRegistry.SOURCE_CAPABILITY;
    }

    @Override
    public long extract(ISourceCap handle, long amount, boolean simulate) {
        if (!simulate) return handle.extractSource(SaturatedMath.toNonNegativeInt(amount), false);
        try {
            return handle.extractSource(SaturatedMath.toNonNegativeInt(amount), true);
        } catch (RuntimeException exception) {
            LOGGER.error("Ars source extract simulation failed", exception);
            return 0;
        }
    }

    @Override
    public long insert(ISourceCap handle, long amount, boolean simulate) {
        if (!simulate) return handle.receiveSource(SaturatedMath.toNonNegativeInt(amount), false);
        try {
            return handle.receiveSource(SaturatedMath.toNonNegativeInt(amount), true);
        } catch (RuntimeException exception) {
            LOGGER.error("Ars source insert simulation failed", exception);
            return 0;
        }
    }
}
