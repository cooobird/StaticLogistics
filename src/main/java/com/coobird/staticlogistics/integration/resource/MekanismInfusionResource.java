package com.coobird.staticlogistics.integration.resource;

import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.api.transfer.TransactionCapabilities;
import com.coobird.staticlogistics.config.SLConfig;
import com.coobird.staticlogistics.logistics.node.FaceConfigComposite;
import com.coobird.staticlogistics.transfer.ExtractionResult;
import com.coobird.staticlogistics.transfer.LogisticsResource;
import com.mojang.logging.LogUtils;
import mekanism.api.Action;
import mekanism.api.chemical.infuse.IInfusionHandler;
import mekanism.api.chemical.infuse.InfusionStack;
import mekanism.common.registries.MekanismItems;
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
 * 通过 {@link mekanism.common.capabilities.Capabilities#INFUSION_HANDLER} 访问 Mekanism 灌注类型。
 */
public class MekanismInfusionResource implements LogisticsResource<IInfusionHandler> {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ResourceLocation TYPE_ID = StaticLogistics.asResource("mek_infusion");

    @Override
    public ResourceLocation typeId() {
        return TYPE_ID;
    }

    @Override
    public int color() {
        return 0xFFFFAA00;
    }

    @Override
    public String translationKey() {
        return "transfer_type.staticlogistics.mek_infusion";
    }

    @Override
    public Supplier<ItemStack> iconSupplier() {
        return () -> new ItemStack(MekanismItems.ENRICHED_REDSTONE);
    }

    @Override
    public IntSupplier baseStackSizeSupplier() {
        return SLConfig::getMekInfusionStack;
    }

    @Override
    public TransactionCapabilities transactionCapabilities() {
        return TransactionCapabilities.exactSimulationOnly();
    }

    @Override
    public @Nullable IInfusionHandler resolve(ServerLevel level, BlockPos pos, Direction face) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null) return null;
        return com.coobird.staticlogistics.transfer.CapabilityCache.get(
            level, pos, face, mekanism.common.capabilities.Capabilities.INFUSION_HANDLER);
    }

    @Override
    public ExtractionResult<?> extractTyped(IInfusionHandler handle, long amount, boolean simulate) {
        try {
            InfusionStack extracted = handle.extractChemical(amount, simulate ? Action.SIMULATE : Action.EXECUTE);
            return ExtractionResult.of(extracted);
        } catch (Exception e) {
            LOGGER.error("Mekanism infusion extract failed", e);
            return ExtractionResult.of(InfusionStack.EMPTY);
        }
    }

    @Override
    public long insertTyped(IInfusionHandler handle, Object value, boolean simulate) {
        if (!(value instanceof InfusionStack stack) || stack.isEmpty()) return 0;
        try {
            InfusionStack remainder = handle.insertChemical(stack, simulate ? Action.SIMULATE : Action.EXECUTE);
            return stack.getAmount() - remainder.getAmount();
        } catch (Exception e) {
            LOGGER.error("Mekanism infusion insert failed", e);
            return 0;
        }
    }

    @Override
    public boolean isEmptyResult(@Nullable Object value) {
        if (value == null) return true;
        if (value instanceof InfusionStack s) return s.isEmpty();
        return false;
    }

    @Override
    public boolean canInsertToTarget(IInfusionHandler handle, Object value, FaceConfigComposite targetCfg) {
        if (!(value instanceof InfusionStack stack) || stack.isEmpty()) return false;
        InfusionStack simulated = handle.insertChemical(stack.copy(), Action.SIMULATE);
        return simulated.isEmpty() || simulated.getAmount() < stack.getAmount();
    }

    @Override
    public long amountOf(Object value) {
        return value instanceof InfusionStack stack ? stack.getAmount() : -1L;
    }

    @Override
    public Object withAmount(Object value, long amount) {
        if (!(value instanceof InfusionStack stack)) return null;
        InfusionStack copy = stack.copy();
        copy.setAmount(Math.max(0L, amount));
        return copy;
    }
}
