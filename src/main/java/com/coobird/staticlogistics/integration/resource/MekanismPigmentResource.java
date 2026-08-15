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
 * 通过 {@link mekanism.common.capabilities.Capabilities#PIGMENT_HANDLER} 访问 Mekanism 颜料。
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
    public TransactionCapabilities transactionCapabilities() {
        return TransactionCapabilities.exactCompensating();
    }

    @Override
    public @Nullable IPigmentHandler resolve(ServerLevel level, BlockPos pos, Direction face) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null) return null;
        return CapabilityCache.get(
            level, pos, face, mekanism.common.capabilities.Capabilities.PIGMENT_HANDLER);
    }

    @Override
    public ExtractionResult<?> extractTyped(IPigmentHandler handle, long amount, boolean simulate) {
        if (!simulate) return ExtractionResult.of(handle.extractChemical(amount, Action.EXECUTE));
        try {
            return ExtractionResult.of(handle.extractChemical(amount, Action.SIMULATE));
        } catch (RuntimeException exception) {
            LOGGER.error("Mekanism pigment extract simulation failed", exception);
            return ExtractionResult.of(PigmentStack.EMPTY);
        }
    }

    @Override
    public long insertTyped(IPigmentHandler handle, Object value, boolean simulate) {
        if (!(value instanceof PigmentStack stack) || stack.isEmpty()) return 0;
        if (!simulate) {
            PigmentStack remainder = handle.insertChemical(stack, Action.EXECUTE);
            return stack.getAmount() - remainder.getAmount();
        }
        try {
            PigmentStack remainder = handle.insertChemical(stack, Action.SIMULATE);
            return stack.getAmount() - remainder.getAmount();
        } catch (RuntimeException exception) {
            LOGGER.error("Mekanism pigment insert simulation failed", exception);
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

    @Override
    public long amountOf(Object value) {
        return value instanceof mekanism.api.chemical.pigment.PigmentStack stack
            ? stack.getAmount() : -1L;
    }

    @Override
    public Object withAmount(Object value, long amount) {
        if (!(value instanceof mekanism.api.chemical.pigment.PigmentStack stack)) return null;
        mekanism.api.chemical.pigment.PigmentStack copy = stack.copy();
        copy.setAmount(Math.max(0L, amount));
        return copy;
    }
}
