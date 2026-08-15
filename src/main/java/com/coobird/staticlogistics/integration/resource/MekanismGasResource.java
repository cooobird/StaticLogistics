package com.coobird.staticlogistics.integration.resource;

import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.api.transfer.TransactionCapabilities;
import com.coobird.staticlogistics.config.SLConfig;
import com.coobird.staticlogistics.logistics.node.FaceConfigComposite;
import com.coobird.staticlogistics.transfer.CapabilityCache;
import com.coobird.staticlogistics.transfer.ExtractionResult;
import com.coobird.staticlogistics.transfer.LogisticsResource;
import com.mojang.logging.LogUtils;
import mekanism.api.Action;
import mekanism.api.chemical.gas.GasStack;
import mekanism.api.chemical.gas.IGasHandler;
import mekanism.common.registries.MekanismBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * 通过 {@link mekanism.common.capabilities.Capabilities#GAS_HANDLER} 访问 Mekanism 气体。
 */
public class MekanismGasResource implements LogisticsResource<IGasHandler> {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ResourceLocation TYPE_ID = StaticLogistics.asResource("mek_gas");

    @Override
    public ResourceLocation typeId() {
        return TYPE_ID;
    }

    @Override
    public int color() {
        return 0xFF66FF66;
    }

    @Override
    public String translationKey() {
        return "transfer_type.staticlogistics.mek_gas";
    }

    @Override
    public Supplier<ItemStack> iconSupplier() {
        return () -> new ItemStack(MekanismBlocks.BASIC_CHEMICAL_TANK);
    }

    @Override
    public IntSupplier baseStackSizeSupplier() {
        return SLConfig::getMekGasStack;
    }

    @Override
    public TransactionCapabilities transactionCapabilities() {
        return TransactionCapabilities.exactCompensating();
    }

    @Override
    public @Nullable IGasHandler resolve(ServerLevel level, BlockPos pos, Direction face) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null) return null;
        return CapabilityCache.get(
            level, pos, face, mekanism.common.capabilities.Capabilities.GAS_HANDLER);
    }

    @Override
    public ExtractionResult<?> extractTyped(IGasHandler handle, long amount, boolean simulate) {
        if (!simulate) return ExtractionResult.of(handle.extractChemical(amount, Action.EXECUTE));
        try {
            return ExtractionResult.of(handle.extractChemical(amount, Action.SIMULATE));
        } catch (RuntimeException exception) {
            LOGGER.error("Mekanism gas extract simulation failed", exception);
            return ExtractionResult.of(GasStack.EMPTY);
        }
    }

    @Override
    public long insertTyped(IGasHandler handle, Object value, boolean simulate) {
        if (!(value instanceof GasStack stack) || stack.isEmpty()) return 0;
        if (!simulate) {
            GasStack remainder = handle.insertChemical(stack, Action.EXECUTE);
            return stack.getAmount() - remainder.getAmount();
        }
        try {
            GasStack remainder = handle.insertChemical(stack, Action.SIMULATE);
            return stack.getAmount() - remainder.getAmount();
        } catch (RuntimeException exception) {
            LOGGER.error("Mekanism gas insert simulation failed", exception);
            return 0;
        }
    }

    @Override
    public boolean isEmptyResult(@Nullable Object value) {
        if (value == null) return true;
        if (value instanceof GasStack gas) return gas.isEmpty();
        return false;
    }

    @Override
    public boolean canInsertToTarget(IGasHandler handle, Object value, FaceConfigComposite targetCfg) {
        if (!(value instanceof GasStack stack) || stack.isEmpty()) return false;
        GasStack simulated = handle.insertChemical(stack.copy(), Action.SIMULATE);
        return simulated.isEmpty() || simulated.getAmount() < stack.getAmount();
    }

    @Override
    public long amountOf(Object value) {
        return value instanceof GasStack stack ? stack.getAmount() : -1L;
    }

    @Override
    public Object withAmount(Object value, long amount) {
        if (!(value instanceof GasStack stack)) return null;
        GasStack copy = stack.copy();
        copy.setAmount(Math.max(0L, amount));
        return copy;
    }
}
