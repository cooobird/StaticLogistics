package com.coobird.staticlogistics.transfer;

import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.api.transfer.TransactionCapabilities;
import com.coobird.staticlogistics.config.SLConfig;
import com.coobird.staticlogistics.logistics.filter.FilterEvaluator;
import com.coobird.staticlogistics.logistics.node.EndpointConfig;
import com.coobird.staticlogistics.logistics.util.SaturatedMath;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.Objects;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/// 流体资源适配器 —— 带过滤器检查。
public class FluidResource implements LogisticsResource<IFluidHandler> {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ResourceLocation TYPE_ID = StaticLogistics.asResource("fluid");

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
        return 0xFF3366FF;
    }

    @Override
    public Component unit() {
        return Component.translatable("gui.staticlogistics.unit.fluid");
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
    public LongSupplier baseStackSizeSupplier() {
        return SLConfig::getFluidStack;
    }

    @Override
    public @Nullable IFluidHandler resolve(ServerLevel level, BlockPos pos, Direction face) {
        return CapabilityCache.get(level, pos, face, ForgeCapabilities.FLUID_HANDLER);
    }

    @Override
    public Capability<IFluidHandler> blockCapability() {
        return ForgeCapabilities.FLUID_HANDLER;
    }

    @Override
    public ExtractionResult<?> extractTyped(IFluidHandler handle, long amount, boolean simulate,
                                            @Nullable EndpointConfig sourceCfg, boolean isPullMode,
                                            @Nullable TransferContext context) {
        FluidStack drained;
        try {
            drained = handle.drain(SaturatedMath.toNonNegativeInt(amount), IFluidHandler.FluidAction.SIMULATE);
        } catch (RuntimeException exception) {
            LOGGER.error("Fluid extract simulation failed", exception);
            return ExtractionResult.of(FluidStack.EMPTY);
        }
        if (drained.isEmpty()) return ExtractionResult.of(FluidStack.EMPTY);
        /// 在真实提取前完成输出过滤，避免先修改外部容器再拒绝事务。
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
                            @Nullable EndpointConfig sourceCfg, boolean isPullMode,
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
    public boolean canInsertToTarget(IFluidHandler handle, Object value, EndpointConfig targetCfg) {
        if (!(value instanceof FluidStack stack) || stack.isEmpty()) return false;
        return FilterEvaluator.isFluidInputAllowed(stack, targetCfg);
    }

    @Override
    public ExtractionResult<?> executeExtract(IFluidHandler handle, ExtractionResult<?> simulated, long requested,
                                              @Nullable EndpointConfig sourceCfg, boolean isPullMode,
                                              @Nullable TransferContext context) {
        if (!(simulated.value() instanceof FluidStack expected) || expected.isEmpty() || !isFluidAllowed(sourceCfg, expected, isPullMode))
            return ExtractionResult.of(FluidStack.EMPTY);
        /// 固定模拟时的流体，防止多流体容器在提交时切换候选。
        FluidStack drained = handle.drain(new FluidStack(expected, SaturatedMath.toNonNegativeInt(requested)), IFluidHandler.FluidAction.EXECUTE);
        return ExtractionResult.of(drained);
    }

    private record CandidateKey(int tank, Fluid fluid, CompoundTag components) {
    }

    @Override
    public ResourceExtractionSession openExtractionSession(IFluidHandler handle,
                                                           @Nullable EndpointConfig sourceCfg, boolean pullMode, @Nullable TransferContext context) {
        if (getClass() != FluidResource.class) {
            return LogisticsResource.super.openExtractionSession(handle, sourceCfg, pullMode, context);
        }
        return new ResourceExtractionSession() {
            private final Object identity = new Object();
            private final int tanks = handle.getTanks();
            private final int start = tanks == 0 ? 0 : Math.floorMod(
                context == null ? 0 : context.slotCursor(), tanks);
            private int scanned;
            private int pending = -1;
            private FluidStack candidate = FluidStack.EMPTY;
            private CandidateKey checkpoint;

            @Override
            public ExtractionResult<?> simulate(long amount) {
                while (context == null || context.hasTimeRemaining()) {
                    if (pending < 0) {
                        if (scanned >= tanks) return ExtractionResult.of(FluidStack.EMPTY);
                        pending = (start + scanned++) % tanks;
                        candidate = handle.getFluidInTank(pending).copy();
                    }
                    if (!candidate.isEmpty() && isFluidAllowed(sourceCfg, candidate, pullMode)) {
                        FluidStack simulated = handle.drain(new FluidStack(candidate,
                            SaturatedMath.toNonNegativeInt(amount)), IFluidHandler.FluidAction.SIMULATE);
                        if (!simulated.isEmpty() && candidate.isFluidEqual(simulated)
                            && isFluidAllowed(sourceCfg, simulated, pullMode)) {
                            return ExtractionResult.of(simulated, new FluidCandidate(identity, pending));
                        }
                    }
                    advance();
                }
                return ExtractionResult.yielded(FluidStack.EMPTY);
            }

            @Override
            public ExtractionResult<?> execute(ExtractionResult<?> simulated, long requested) {
                if (!matches(simulated)) return ExtractionResult.of(FluidStack.EMPTY);
                ExtractionResult<?> actual = executeExtract(handle, simulated, requested, sourceCfg, pullMode, context);
                /// 保留本次候选以便用完额度；下次激活从下一个罐开始，避免长期偏向首罐。
                if (context != null) context.slotCursor(tanks == 0 ? 0 : (pending + 1) % tanks);
                return actual;
            }

            @Override
            public Object candidateKey(ExtractionResult<?> simulated) {
                if (!(simulated.value() instanceof FluidStack stack) || !matches(simulated)) return null;
                if (checkpoint == null || checkpoint.tank() != pending || checkpoint.fluid() != stack.getFluid()
                    || !Objects.equals(checkpoint.components(), stack.getTag())) {
                    checkpoint = new CandidateKey(pending, stack.getFluid(), stack.copy().getTag());
                }
                return checkpoint;
            }

            @Override
            public boolean advanceRejected(ExtractionResult<?> simulated) {
                if (!matches(simulated)) return false;
                advance();
                return true;
            }

            private boolean matches(ExtractionResult<?> simulated) {
                return simulated.context() instanceof FluidCandidate plan
                    && plan.session() == identity && plan.tank() == pending;
            }

            private void advance() {
                if (context != null) {
                    context.slotCursor(tanks == 0 ? 0 : (pending + 1) % tanks);
                }
                pending = -1;
                candidate = FluidStack.EMPTY;
            }
        };
    }

    private record FluidCandidate(Object session, int tank) {
    }

    @Override
    public long amountOf(Object value) {
        return value instanceof FluidStack stack ? stack.getAmount() : -1L;
    }

    @Override
    public Object withAmount(Object value, long amount) {
        return value instanceof FluidStack stack ? new FluidStack(stack, SaturatedMath.toNonNegativeInt(amount)) : null;
    }

    private static boolean isFluidAllowed(EndpointConfig config, FluidStack stack, boolean isPullMode) {
        if (config == null) return true;
        return isPullMode ? FilterEvaluator.isFluidInputAllowed(stack, config) : FilterEvaluator.isFluidOutputAllowed(stack, config);
    }

    @Override
    public long maxInsertToTarget(IFluidHandler handle, Object value, EndpointConfig config) {
        long remaining = config.linkConfig.getKeepStock();
        if (remaining <= 0) return Long.MAX_VALUE;
        if (!(value instanceof FluidStack requested)) return 0;
        for (int tank = 0; tank < handle.getTanks(); tank++) {
            FluidStack stored = handle.getFluidInTank(tank);
            if (stored.isFluidEqual(requested)) remaining -= Math.min(remaining, stored.getAmount());
            if (remaining == 0) break;
        }
        return remaining;
    }

}
