package com.coobird.staticlogistics.transfer;

import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.api.transfer.TransactionCapabilities;
import com.coobird.staticlogistics.config.SLConfig;
import com.coobird.staticlogistics.logistics.filter.FilterEvaluator;
import com.coobird.staticlogistics.logistics.node.FaceConfigComposite;
import com.coobird.staticlogistics.logistics.util.SaturatedMath;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * 流体资源适配器 —— 带过滤器检查。
 */
public class FluidResource implements LogisticsResource<IFluidHandler> {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ResourceLocation TYPE_ID = StaticLogistics.asResource("fluid");

    @Override
    public ResourceLocation typeId() {
        return TYPE_ID;
    }

    @Override
    public int color() {
        return 0xFF3366FF;
    }

    @Override
    public String translationKey() {
        return "transfer_type.staticlogistics.fluid";
    }

    @Override
    public Supplier<ItemStack> iconSupplier() {
        return () -> new ItemStack(Items.WATER_BUCKET);
    }

    @Override
    public IntSupplier baseStackSizeSupplier() {
        return SLConfig::getFluidStack;
    }

    @Override
    public TransactionCapabilities transactionCapabilities() {
        return TransactionCapabilities.exactCompensating();
    }

    @Override
    public @Nullable IFluidHandler resolve(ServerLevel level, BlockPos pos, Direction face) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null) return null;
        return CapabilityCache.get(
            level, pos, face, ForgeCapabilities.FLUID_HANDLER);
    }

    @Override
    public ExtractionResult<?> extractTyped(IFluidHandler handle, long amount, boolean simulate,
                                            @Nullable FaceConfigComposite sourceCfg, boolean isPullMode,
                                            @Nullable TransferContext context) {
        FluidStack drained;
        try {
            drained = handle.drain(SaturatedMath.toNonNegativeInt(amount), IFluidHandler.FluidAction.SIMULATE);
        } catch (RuntimeException exception) {
            LOGGER.error("Fluid extract simulation failed", exception);
            return ExtractionResult.of(FluidStack.EMPTY);
        }
        if (drained.isEmpty()) return ExtractionResult.of(FluidStack.EMPTY);
        // 在真实提取前完成输出过滤，避免先修改外部容器再拒绝事务。
        if (sourceCfg != null && !isFluidAllowed(sourceCfg, drained, isPullMode)) {
            return ExtractionResult.of(FluidStack.EMPTY);
        }
        if (!simulate) {
            return ExtractionResult.of(handle.drain(drained.getAmount(), IFluidHandler.FluidAction.EXECUTE));
        }
        return ExtractionResult.of(drained);
    }

    @Override
    public long insertTyped(IFluidHandler handle, Object value, boolean simulate,
                            @Nullable FaceConfigComposite sourceCfg, boolean isPullMode,
                            @Nullable TransferContext context) {
        if (!(value instanceof FluidStack stack) || stack.isEmpty()) return 0;
        if (!simulate) return handle.fill(stack, IFluidHandler.FluidAction.EXECUTE);
        try {
            return handle.fill(stack, IFluidHandler.FluidAction.SIMULATE);
        } catch (RuntimeException exception) {
            LOGGER.error("Fluid insert simulation failed", exception);
            return 0;
        }
    }

    @Override
    public boolean isEmptyResult(@Nullable Object value) {
        if (value == null) return true;
        if (value instanceof FluidStack fs) return fs.isEmpty();
        return false;
    }

    @Override
    public boolean canInsertToTarget(IFluidHandler handle, Object value, FaceConfigComposite targetCfg) {
        if (!(value instanceof FluidStack stack) || stack.isEmpty()) return false;
        return FilterEvaluator.isFluidInputAllowed(stack, targetCfg);
    }

    @Override
    public ExtractionResult<?> executeExtract(IFluidHandler handle, ExtractionResult<?> simulated,
                                              long requested,
                                              @Nullable FaceConfigComposite sourceCfg,
                                              boolean isPullMode,
                                              @Nullable TransferContext context) {
        int amount = SaturatedMath.toNonNegativeInt(requested);
        return ExtractionResult.of(handle.drain(amount, IFluidHandler.FluidAction.EXECUTE));
    }

    @Override
    public long amountOf(Object value) {
        return value instanceof FluidStack stack ? stack.getAmount() : -1L;
    }

    @Override
    public Object withAmount(Object value, long amount) {
        if (!(value instanceof FluidStack stack)) return null;
        FluidStack copy = stack.copy();
        copy.setAmount(SaturatedMath.toNonNegativeInt(amount));
        return copy;
    }

    private static boolean isFluidAllowed(FaceConfigComposite config, FluidStack stack, boolean isPullMode) {
        if (config == null) return true;
        return isPullMode
            ? FilterEvaluator.isFluidInputAllowed(stack, config)
            : FilterEvaluator.isFluidOutputAllowed(stack, config);
    }
}
