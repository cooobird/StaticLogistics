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
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
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
    public TransactionCapabilities transactionCapabilities() {
        return TransactionCapabilities.exactSimulationOnly();
    }

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
    public @Nullable IFluidHandler resolve(ServerLevel level, BlockPos pos, Direction face) {
        return level.getCapability(Capabilities.FluidHandler.BLOCK, pos, face);
    }

    @Override
    public BlockCapability<IFluidHandler, Direction> blockCapability() {
        return Capabilities.FluidHandler.BLOCK;
    }

    @Override
    public ExtractionResult<?> extractTyped(IFluidHandler handle, long amount, boolean simulate,
                                            @Nullable FaceConfigComposite sourceCfg, boolean isPullMode,
                                            @Nullable TransferContext context) {
        try {
            FluidStack drained = handle.drain(SaturatedMath.toNonNegativeInt(amount), IFluidHandler.FluidAction.SIMULATE);
            if (drained.isEmpty()) return ExtractionResult.of(FluidStack.EMPTY);
            // 输出过滤器检查
            if (sourceCfg != null && !isFluidAllowed(sourceCfg, drained, isPullMode)) {
                return ExtractionResult.of(FluidStack.EMPTY);
            }
            if (!simulate) {
                FluidStack actual = handle.drain(drained.getAmount(), IFluidHandler.FluidAction.EXECUTE);
                return ExtractionResult.of(actual);
            }
            return ExtractionResult.of(drained);
        } catch (Exception e) {
            LOGGER.error("Fluid extract failed", e);
            return ExtractionResult.of(FluidStack.EMPTY);
        }
    }

    @Override
    public long insertTyped(IFluidHandler handle, Object value, boolean simulate,
                            @Nullable FaceConfigComposite sourceCfg, boolean isPullMode,
                            @Nullable TransferContext context) {
        if (!(value instanceof FluidStack stack) || stack.isEmpty()) return 0;
        try {
            return handle.fill(stack, simulate ? IFluidHandler.FluidAction.SIMULATE : IFluidHandler.FluidAction.EXECUTE);
        } catch (Exception e) {
            LOGGER.error("Fluid insert failed", e);
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
    public ExtractionResult<?> executeExtract(IFluidHandler handle, ExtractionResult<?> simulated, long requested,
                                              @Nullable FaceConfigComposite sourceCfg, boolean isPullMode,
                                              @Nullable TransferContext context) {
        FluidStack drained = handle.drain(SaturatedMath.toNonNegativeInt(requested), IFluidHandler.FluidAction.EXECUTE);
        return ExtractionResult.of(drained);
    }

    @Override
    public long amountOf(Object value) {
        return value instanceof FluidStack stack ? stack.getAmount() : -1L;
    }

    @Override
    public Object withAmount(Object value, long amount) {
        return value instanceof FluidStack stack
            ? stack.copyWithAmount(SaturatedMath.toNonNegativeInt(amount)) : null;
    }

    private static boolean isFluidAllowed(FaceConfigComposite config, FluidStack stack, boolean isPullMode) {
        if (config == null) return true;
        return isPullMode
            ? FilterEvaluator.isFluidInputAllowed(stack, config)
            : FilterEvaluator.isFluidOutputAllowed(stack, config);
    }
}
