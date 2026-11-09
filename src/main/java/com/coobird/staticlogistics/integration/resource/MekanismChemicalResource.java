package com.coobird.staticlogistics.integration.resource;

import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.api.transfer.TransactionCapabilities;
import com.coobird.staticlogistics.config.SLConfig;
import com.coobird.staticlogistics.logistics.node.EndpointConfig;
import com.coobird.staticlogistics.transfer.ExtractionResult;
import com.coobird.staticlogistics.transfer.LogisticsResource;
import com.coobird.staticlogistics.transfer.ResourceExtractionSession;
import com.coobird.staticlogistics.transfer.TransferContext;
import com.mojang.logging.LogUtils;
import mekanism.api.Action;
import mekanism.api.chemical.Chemical;
import mekanism.api.chemical.ChemicalStack;
import mekanism.api.chemical.IChemicalHandler;
import mekanism.common.capabilities.Capabilities;
import mekanism.common.registries.MekanismBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.BlockCapability;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.function.LongSupplier;
import java.util.function.Supplier;

/// 接入 Mekanism 化学品传输。
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
    public Component unit() {
        return Component.translatable("gui.staticlogistics.unit.fluid");
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
    public LongSupplier baseStackSizeSupplier() {
        return SLConfig::getMekChemicalStack;
    }

    @Override
    public @Nullable IChemicalHandler resolve(ServerLevel level, BlockPos pos, Direction face) {
        return level.getCapability(Capabilities.CHEMICAL.block(), pos, face);
    }

    @Override
    public BlockCapability<IChemicalHandler, Direction> blockCapability() {
        return Capabilities.CHEMICAL.block();
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
        return value instanceof ChemicalStack stack ? stack.copyWithAmount(amount) : null;
    }

    @Override
    public boolean canInsertToTarget(IChemicalHandler handle, Object value, EndpointConfig targetCfg) {
        return value instanceof ChemicalStack stack && !stack.isEmpty();
        /// 容器接收能力由管线的 simulateInsert 检查，此处不重复遍历化学罐。
    }

    @Override
    public ExtractionResult<?> executeExtract(IChemicalHandler handle, ExtractionResult<?> simulated, long requested,
                                              @Nullable EndpointConfig config, boolean pull,
                                              @Nullable TransferContext context) {
        if (!(simulated.value() instanceof ChemicalStack stack) || stack.isEmpty())
            return ExtractionResult.of(ChemicalStack.EMPTY);
        /// 按模拟时的化学类型提取，不能在回调改变罐内容后取出另一种化学品。
        return ExtractionResult.of(handle.extractChemical(stack.copyWithAmount(requested), Action.EXECUTE));
    }

    private record ChemicalCandidate(Object session, int tank) {
    }

    private record CandidateKey(int tank, Chemical chemical) {
    }

    @Override
    public ResourceExtractionSession openExtractionSession(IChemicalHandler handle, @Nullable EndpointConfig config,
                                                           boolean pull, @Nullable TransferContext context) {
        if (getClass() != MekanismChemicalResource.class)
            return LogisticsResource.super.openExtractionSession(handle, config, pull, context);
        return new ResourceExtractionSession() {
            private final Object identity = new Object();
            private final int tanks = handle.getChemicalTanks();
            private final int start = tanks == 0 ? 0 : Math.floorMod(context == null ? 0 : context.slotCursor(), tanks);
            private int scanned, pending = -1;
            private CandidateKey checkpoint;

            @Override
            public ExtractionResult<?> simulate(long amount) {
                while (context == null || context.hasTimeRemaining()) {
                    if (pending < 0) {
                        if (scanned >= tanks) return ExtractionResult.of(ChemicalStack.EMPTY);
                        pending = (start + scanned++) % tanks;
                    }
                    /// 已拒绝的罐不从头重扫，也不让首种化学品阻塞后续可传的种类。
                    ChemicalStack stack = handle.extractChemical(pending, amount, Action.SIMULATE);
                    if (!stack.isEmpty()) return ExtractionResult.of(stack, new ChemicalCandidate(identity, pending));
                    advance();
                }
                return ExtractionResult.yielded(ChemicalStack.EMPTY);
            }

            @Override
            public ExtractionResult<?> execute(ExtractionResult<?> simulated, long requested) {
                if (!matches(simulated) || !(simulated.value() instanceof ChemicalStack expected)
                    || handle.getChemicalInTank(pending).getChemical() != expected.getChemical())
                    return ExtractionResult.of(ChemicalStack.EMPTY);
                ChemicalStack actual = handle.extractChemical(pending, requested, Action.EXECUTE);
                if (context != null) context.slotCursor((pending + 1) % tanks);
                return ExtractionResult.of(actual);
            }

            @Override
            public Object candidateKey(ExtractionResult<?> simulated) {
                if (!matches(simulated) || !(simulated.value() instanceof ChemicalStack stack)) return null;
                if (checkpoint == null || checkpoint.tank() != pending || checkpoint.chemical() != stack.getChemical())
                    checkpoint = new CandidateKey(pending, stack.getChemical());
                return checkpoint;
            }

            @Override
            public boolean advanceRejected(ExtractionResult<?> simulated) {
                if (!matches(simulated)) return false;
                advance();
                return true;
            }

            private boolean matches(ExtractionResult<?> simulated) {
                return simulated.context() instanceof ChemicalCandidate(Object session, int tank)
                    && session == identity && tank == pending;
            }

            private void advance() {
                if (context != null) context.slotCursor(tanks == 0 ? 0 : (pending + 1) % tanks);
                pending = -1;
            }
        };
    }

    @Override
    public long maxInsertToTarget(IChemicalHandler handle, Object value, EndpointConfig config) {
        long remaining = config.linkConfig.getKeepStock();
        if (remaining <= 0) return Long.MAX_VALUE;
        if (!(value instanceof ChemicalStack requested)) return 0;
        for (int tank = 0; tank < handle.getChemicalTanks(); tank++) {
            ChemicalStack stored = handle.getChemicalInTank(tank);
            if (stored.getChemical() == requested.getChemical()) remaining -= Math.min(remaining, stored.getAmount());
            if (remaining == 0) break;
        }
        return remaining;
    }

}
