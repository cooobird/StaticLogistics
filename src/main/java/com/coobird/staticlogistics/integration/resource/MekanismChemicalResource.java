package com.coobird.staticlogistics.integration.resource;

import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.api.transfer.TransactionCapabilities;
import com.coobird.staticlogistics.config.SLConfig;
import com.coobird.staticlogistics.logistics.node.FaceConfigComposite;
import com.coobird.staticlogistics.transfer.ExtractionResult;
import com.coobird.staticlogistics.transfer.LogisticsResource;
import com.mojang.logging.LogUtils;
import mekanism.api.Action;
import mekanism.api.chemical.ChemicalStack;
import mekanism.api.chemical.IChemicalHandler;
import mekanism.common.registries.MekanismBlocks;
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
 * Mekanism 化学品资源适配器。
 *
 * <p>通过 {@link LogisticsResource} 接口接入物流管线。
 * 化学品使用 {@link ChemicalStack}，需要覆写 {@link #extractTyped} 和 {@link #insertTyped} 以正确传递化学类型。
 */
public class MekanismChemicalResource implements LogisticsResource<IChemicalHandler> {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ResourceLocation TYPE_ID = StaticLogistics.asResource("mek_chemicals");

    @Override
    public TransactionCapabilities transactionCapabilities() {
        return TransactionCapabilities.exactCompensating();
    }

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
        return "transfer_type.staticlogistics.mek_chemicals";
    }

    @Override
    public Supplier<ItemStack> iconSupplier() {
        return () -> new ItemStack(MekanismBlocks.BASIC_CHEMICAL_TANK.get());
    }

    @Override
    public IntSupplier baseStackSizeSupplier() {
        return SLConfig::getMekChemicalStack;
    }

    @Override
    public @Nullable IChemicalHandler resolve(ServerLevel level, BlockPos pos, Direction face) {
        return level.getCapability(mekanism.common.capabilities.Capabilities.CHEMICAL.block(), pos, face);
    }

    @Override
    public BlockCapability<IChemicalHandler, Direction> blockCapability() {
        return mekanism.common.capabilities.Capabilities.CHEMICAL.block();
    }

    @Override
    public ExtractionResult<ChemicalStack> extractTyped(IChemicalHandler handle, long amount, boolean simulate) {
        if (!simulate) return ExtractionResult.of(handle.extractChemical(amount, Action.EXECUTE));
        try {
            return ExtractionResult.of(handle.extractChemical(amount, Action.SIMULATE));
        } catch (RuntimeException exception) {
            LOGGER.error("Mekanism chemical extract simulation failed", exception);
            return ExtractionResult.of(ChemicalStack.EMPTY);
        }
    }

    @Override
    public long insertTyped(IChemicalHandler handle, Object value, boolean simulate) {
        if (!(value instanceof ChemicalStack stack) || stack.isEmpty()) return 0;
        if (!simulate) {
            ChemicalStack remainder = handle.insertChemical(stack, Action.EXECUTE);
            return stack.getAmount() - remainder.getAmount();
        }
        try {
            ChemicalStack remainder = handle.insertChemical(stack, Action.SIMULATE);
            return stack.getAmount() - remainder.getAmount();
        } catch (RuntimeException exception) {
            LOGGER.error("Mekanism chemical insert simulation failed", exception);
            return 0;
        }
    }

    @Override
    public boolean isEmptyResult(@Nullable Object value) {
        if (value == null) return true;
        if (value instanceof ChemicalStack chem) return chem.isEmpty();
        return false;
    }

    @Override
    public long amountOf(Object value) {
        return value instanceof ChemicalStack stack ? stack.getAmount() : -1L;
    }

    @Override
    public Object withAmount(Object value, long amount) {
        return value instanceof ChemicalStack stack ? stack.copyWithAmount(Math.max(0L, amount)) : null;
    }

    @Override
    public boolean canInsertToTarget(IChemicalHandler handle, Object value, FaceConfigComposite targetCfg) {
        if (!(value instanceof ChemicalStack stack) || stack.isEmpty()) return false;
        ChemicalStack simulated = handle.insertChemical(stack.copy(), Action.SIMULATE);
        return simulated.isEmpty() || simulated.getAmount() < stack.getAmount();
    }
}
