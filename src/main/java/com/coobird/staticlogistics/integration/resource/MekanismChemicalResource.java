package com.coobird.staticlogistics.integration.resource;

import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.api.LogisticsResource;
import com.coobird.staticlogistics.config.SLConfig;
import com.coobird.staticlogistics.storage.model.FaceConfigComposite;
import com.coobird.staticlogistics.transfer.handler.ExtractionResult;
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
    public ExtractionResult<ChemicalStack> extractTyped(IChemicalHandler handle, long amount, boolean simulate) {
        try {
            ChemicalStack extracted = handle.extractChemical(amount, simulate ? Action.SIMULATE : Action.EXECUTE);
            return ExtractionResult.of(extracted);
        } catch (Exception e) {
            LOGGER.error("Mekanism chemical extract failed", e);
            return ExtractionResult.of(ChemicalStack.EMPTY);
        }
    }

    @Override
    public long insertTyped(IChemicalHandler handle, Object value, boolean simulate) {
        if (!(value instanceof ChemicalStack stack) || stack.isEmpty()) return 0;
        try {
            ChemicalStack remainder = handle.insertChemical(stack, simulate ? Action.SIMULATE : Action.EXECUTE);
            return stack.getAmount() - remainder.getAmount();
        } catch (Exception e) {
            LOGGER.error("Mekanism chemical insert failed", e);
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
    public boolean canInsertToTarget(IChemicalHandler handle, Object value, FaceConfigComposite targetCfg) {
        if (!(value instanceof ChemicalStack stack) || stack.isEmpty()) return false;
        ChemicalStack simulated = handle.insertChemical(stack.copy(), Action.SIMULATE);
        return simulated.isEmpty() || simulated.getAmount() < stack.getAmount();
    }
}
