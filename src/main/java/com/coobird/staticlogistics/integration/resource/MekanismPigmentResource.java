package com.coobird.staticlogistics.integration.resource;

import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.api.LogisticsResource;
import com.coobird.staticlogistics.config.SLConfig;
import com.coobird.staticlogistics.storage.model.FaceConfigComposite;
import com.coobird.staticlogistics.transfer.handler.ExtractionResult;
import com.mojang.logging.LogUtils;
import mekanism.api.Action;
import mekanism.api.chemical.pigment.IPigmentHandler;
import mekanism.api.chemical.pigment.PigmentStack;
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
 * Mekanism 颜料资源适配�?(Forge 1.20.1)�? * 通过 {@link mekanism.common.capabilities.Capabilities#PIGMENT_HANDLER} 访问�?
 */
public class MekanismPigmentResource implements LogisticsResource<IPigmentHandler> {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ResourceLocation TYPE_ID = StaticLogistics.asResource("mek_pigment");

    @Override
    public ResourceLocation typeId() {
        return TYPE_ID;
    }

    @Override
    public int color() {
        return 0xFFFF55FF;
    }

    @Override
    public String translationKey() {
        return "transfer_type.staticlogistics.mek_pigment";
    }

    @Override
    public Supplier<ItemStack> iconSupplier() {
        return () -> new ItemStack(MekanismBlocks.PIGMENT_EXTRACTOR);
    }

    @Override
    public IntSupplier baseStackSizeSupplier() {
        return SLConfig::getMekPigmentStack;
    }

    @Override
    public @Nullable IPigmentHandler resolve(ServerLevel level, BlockPos pos, Direction face) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null) return null;
        return be.getCapability(mekanism.common.capabilities.Capabilities.PIGMENT_HANDLER, face).orElse(null);
    }

    @Override
    public ExtractionResult<?> extractTyped(IPigmentHandler handle, long amount, boolean simulate) {
        try {
            PigmentStack extracted = handle.extractChemical(amount, simulate ? Action.SIMULATE : Action.EXECUTE);
            return ExtractionResult.of(extracted);
        } catch (Exception e) {
            LOGGER.error("Mekanism pigment extract failed", e);
            return ExtractionResult.of(PigmentStack.EMPTY);
        }
    }

    @Override
    public long insertTyped(IPigmentHandler handle, Object value, boolean simulate) {
        if (!(value instanceof PigmentStack stack) || stack.isEmpty()) return 0;
        try {
            PigmentStack remainder = handle.insertChemical(stack, simulate ? Action.SIMULATE : Action.EXECUTE);
            return stack.getAmount() - remainder.getAmount();
        } catch (Exception e) {
            LOGGER.error("Mekanism pigment insert failed", e);
            return 0;
        }
    }

    @Override
    public boolean isEmptyResult(@Nullable Object value) {
        if (value == null) return true;
        if (value instanceof PigmentStack s) return s.isEmpty();
        return false;
    }

    @Override
    public boolean canInsertToTarget(IPigmentHandler handle, Object value, FaceConfigComposite targetCfg) {
        if (!(value instanceof PigmentStack stack) || stack.isEmpty()) return false;
        PigmentStack simulated = handle.insertChemical(stack.copy(), Action.SIMULATE);
        return simulated.isEmpty() || simulated.getAmount() < stack.getAmount();
    }
}
