package com.coobird.staticlogistics.integration.resource;

import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.api.transfer.TransactionCapabilities;
import com.coobird.staticlogistics.config.SLConfig;
import com.coobird.staticlogistics.logistics.node.FaceConfigComposite;
import com.coobird.staticlogistics.transfer.ExtractionResult;
import com.coobird.staticlogistics.transfer.LogisticsResource;
import com.mojang.logging.LogUtils;
import mekanism.api.Action;
import mekanism.api.chemical.slurry.ISlurryHandler;
import mekanism.api.chemical.slurry.SlurryStack;
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
 * 通过 {@link mekanism.common.capabilities.Capabilities#SLURRY_HANDLER} 访问 Mekanism 浆液。
 */
public class MekanismSlurryResource implements LogisticsResource<ISlurryHandler> {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ResourceLocation TYPE_ID = StaticLogistics.asResource("mek_slurry");

    @Override
    public ResourceLocation typeId() {
        return TYPE_ID;
    }

    @Override
    public int color() {
        return 0xFF888888;
    }

    @Override
    public String translationKey() {
        return "transfer_type.staticlogistics.mek_slurry";
    }

    @Override
    public Supplier<ItemStack> iconSupplier() {
        return () -> new ItemStack(MekanismBlocks.CHEMICAL_CRYSTALLIZER);
    }


    @Override
    public IntSupplier baseStackSizeSupplier() {
        return SLConfig::getMekSlurryStack;
    }

    @Override
    public TransactionCapabilities transactionCapabilities() {
        return TransactionCapabilities.exactSimulationOnly();
    }

    @Override
    public @Nullable ISlurryHandler resolve(ServerLevel level, BlockPos pos, Direction face) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null) return null;
        return com.coobird.staticlogistics.transfer.CapabilityCache.get(
            level, pos, face, mekanism.common.capabilities.Capabilities.SLURRY_HANDLER);
    }

    @Override
    public ExtractionResult<?> extractTyped(ISlurryHandler handle, long amount, boolean simulate) {
        try {
            SlurryStack extracted = handle.extractChemical(amount, simulate ? Action.SIMULATE : Action.EXECUTE);
            return ExtractionResult.of(extracted);
        } catch (Exception e) {
            LOGGER.error("Mekanism slurry extract failed", e);
            return ExtractionResult.of(SlurryStack.EMPTY);
        }
    }

    @Override
    public long insertTyped(ISlurryHandler handle, Object value, boolean simulate) {
        if (!(value instanceof SlurryStack stack) || stack.isEmpty()) return 0;
        try {
            SlurryStack remainder = handle.insertChemical(stack, simulate ? Action.SIMULATE : Action.EXECUTE);
            return stack.getAmount() - remainder.getAmount();
        } catch (Exception e) {
            LOGGER.error("Mekanism slurry insert failed", e);
            return 0;
        }
    }

    @Override
    public boolean isEmptyResult(@Nullable Object value) {
        if (value == null) return true;
        if (value instanceof SlurryStack s) return s.isEmpty();
        return false;
    }

    @Override
    public boolean canInsertToTarget(ISlurryHandler handle, Object value, FaceConfigComposite targetCfg) {
        if (!(value instanceof SlurryStack stack) || stack.isEmpty()) return false;
        SlurryStack simulated = handle.insertChemical(stack.copy(), Action.SIMULATE);
        return simulated.isEmpty() || simulated.getAmount() < stack.getAmount();
    }

    @Override
    public long amountOf(Object value) {
        return value instanceof mekanism.api.chemical.slurry.SlurryStack stack
            ? stack.getAmount() : -1L;
    }

    @Override
    public Object withAmount(Object value, long amount) {
        if (!(value instanceof mekanism.api.chemical.slurry.SlurryStack stack)) return null;
        mekanism.api.chemical.slurry.SlurryStack copy = stack.copy();
        copy.setAmount(Math.max(0L, amount));
        return copy;
    }
}
