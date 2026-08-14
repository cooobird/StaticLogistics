package com.coobird.staticlogistics.transfer;

import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.api.transfer.TransactionCapabilities;
import com.coobird.staticlogistics.config.SLConfig;
import com.coobird.staticlogistics.logistics.filter.FilterEvaluator;
import com.coobird.staticlogistics.logistics.node.FaceConfigComposite;
import com.coobird.staticlogistics.logistics.util.LogisticsConstants;
import com.coobird.staticlogistics.logistics.util.SaturatedMath;
import com.coobird.staticlogistics.transfer.strategy.ItemExtractionStrategy;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * 物品资源适配器 —— 带提取策略、过滤器检查、存量维持。
 *
 * <p>extractTyped 返回 {@code ExtractionResult<ItemStack>}，context 中携带精确槽位及其轮询位置。
 * executeExtract 使用该计划从源容器提交提取，并在成功后推进轮询游标。
 */
public class ItemResource implements LogisticsResource<IItemHandler> {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ResourceLocation TYPE_ID = StaticLogistics.asResource("item");

    // 动态复用候选槽位，兼容聚合存储控制器而不设静默 1024 槽上限。
    private static final ThreadLocal<IntArrayList> TL_SLOT_ORDER =
        ThreadLocal.withInitial(() -> new IntArrayList(64));

    private record ExtractionPlan(int slotIndex, int orderIndex, int passCount) {
    }

    @Override
    public ResourceLocation typeId() {
        return TYPE_ID;
    }

    @Override
    public int color() {
        return 0xFFFFFFFF;
    }

    @Override
    public String translationKey() {
        return "transfer_type.staticlogistics.item";
    }

    @Override
    public Supplier<ItemStack> iconSupplier() {
        return () -> new ItemStack(Items.IRON_INGOT);
    }

    @Override
    public IntSupplier baseStackSizeSupplier() {
        return SLConfig::getItemStack;
    }

    @Override
    public int maxTransactionsPerActivation() {
        return LogisticsConstants.Performance.getMaxItemTransactionsPerActivation();
    }

    @Override
    public TransactionCapabilities transactionCapabilities() {
        return TransactionCapabilities.exactSimulationOnly();
    }

    @Override
    public @Nullable IItemHandler resolve(ServerLevel level, BlockPos pos, Direction face) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null) return null;
        return CapabilityCache.get(level, pos, face, ForgeCapabilities.ITEM_HANDLER);
    }

    @Override
    public ExtractionResult<?> extractTyped(IItemHandler handle, long amount, boolean simulate,
                                            @Nullable FaceConfigComposite sourceCfg, boolean isPullMode,
                                            @Nullable TransferContext context) {
        if (sourceCfg == null) return ExtractionResult.of(ItemStack.EMPTY);
        try {
            int limit = SaturatedMath.toNonNegativeInt(amount);
            int slots = handle.getSlots();
            if (slots <= 0) return ExtractionResult.of(ItemStack.EMPTY);

            IntArrayList slotOrder = TL_SLOT_ORDER.get();
            slotOrder.clear();
            for (int s = 0; s < slots; s++) {
                ItemStack sim = handle.extractItem(s, limit, true);
                if (sim.isEmpty()) continue;
                if (!FilterEvaluator.isItemOutputAllowed(sim, sourceCfg)) continue;
                slotOrder.add(s);
            }
            int passCount = slotOrder.size();
            if (passCount == 0) return ExtractionResult.of(ItemStack.EMPTY);

            int startIdx = 0;
            if (context != null) {
                ItemExtractionStrategy strategy = ItemExtractionStrategy.forMode(
                    sourceCfg.linkConfig.getExtractionMode());
                startIdx = strategy.beginTick(passCount, context);
            }

            for (int count = 0; count < passCount; count++) {
                int idx = (startIdx + count) % passCount;
                int s = slotOrder.getInt(idx);
                ItemStack sim = handle.extractItem(s, limit, true);
                if (!sim.isEmpty()) {
                    return ExtractionResult.of(sim, new ExtractionPlan(s, idx, passCount));
                }
            }
            return ExtractionResult.of(ItemStack.EMPTY);
        } catch (Exception e) {
            LOGGER.error("Item extract failed", e);
            return ExtractionResult.of(ItemStack.EMPTY);
        }
    }

    @Override
    public long insertTyped(IItemHandler handle, Object value, boolean simulate,
                            @Nullable FaceConfigComposite sourceCfg, boolean isPullMode,
                            @Nullable TransferContext context) {
        if (!(value instanceof ItemStack stack) || stack.isEmpty()) return 0;
        try {
            ItemStack remain = simulate ? stack.copy() : stack;
            for (int i = 0; i < handle.getSlots(); i++) {
                remain = handle.insertItem(i, remain, simulate);
                if (remain.isEmpty()) break;
            }
            return stack.getCount() - remain.getCount();
        } catch (Exception e) {
            LOGGER.error("Item insert failed", e);
            return 0;
        }
    }

    @Override
    public boolean isEmptyResult(@Nullable Object value) {
        if (value == null) return true;
        if (value instanceof ItemStack is) return is.isEmpty();
        return false;
    }

    @Override
    public boolean canInsertToTarget(IItemHandler handle, Object value, FaceConfigComposite targetCfg) {
        if (!(value instanceof ItemStack stack) || stack.isEmpty()) return false;
        if (!FilterEvaluator.isItemInputAllowed(stack, targetCfg)) return false;
        int keepStock = targetCfg.linkConfig.getKeepStock();
        if (keepStock > 0) {
            int alreadyHas = 0;
            for (int i = 0; i < handle.getSlots(); i++) {
                ItemStack targetStack = handle.getStackInSlot(i);
                if (ItemStack.isSameItemSameTags(stack, targetStack)) {
                    alreadyHas += targetStack.getCount();
                }
            }
            return alreadyHas < keepStock;
        }
        return true;
    }

    @Override
    public long maxInsertToTarget(IItemHandler handle, Object value, FaceConfigComposite targetCfg) {
        if (!(value instanceof ItemStack stack) || stack.isEmpty()) return 0L;
        int keepStock = targetCfg.linkConfig.getKeepStock();
        if (keepStock <= 0) return Long.MAX_VALUE;
        long alreadyHas = 0L;
        for (int i = 0; i < handle.getSlots(); i++) {
            ItemStack targetStack = handle.getStackInSlot(i);
            if (ItemStack.isSameItemSameTags(stack, targetStack)) {
                alreadyHas += targetStack.getCount();
            }
        }
        return Math.max(0L, (long) keepStock - alreadyHas);
    }

    @Override
    public ExtractionResult<?> executeExtract(IItemHandler handle, ExtractionResult<?> simulated,
                                              long requested,
                                              @Nullable FaceConfigComposite sourceCfg,
                                              boolean isPullMode,
                                              @Nullable TransferContext context) {
        if (!(simulated.context() instanceof ExtractionPlan plan)) {
            return ExtractionResult.of(ItemStack.EMPTY);
        }
        int amount = SaturatedMath.toNonNegativeInt(requested);
        ItemStack extracted = handle.extractItem(plan.slotIndex(), amount, false);
        if (!extracted.isEmpty()) advanceStrategy(sourceCfg, context, plan);
        return ExtractionResult.of(extracted, plan);
    }

    @Override
    public boolean advanceRejectedCandidate(ExtractionResult<?> simulated,
                                            @Nullable FaceConfigComposite sourceCfg,
                                            @Nullable TransferContext context) {
        if (!(simulated.context() instanceof ExtractionPlan plan) || sourceCfg == null || context == null) {
            return false;
        }
        ItemExtractionStrategy strategy = ItemExtractionStrategy.forMode(sourceCfg.linkConfig.getExtractionMode());
        if (!strategy.supportsRejectedCandidateAdvance()) return false;
        strategy.advanceAfterAttempt(plan.orderIndex(), plan.passCount(), context);
        return true;
    }

    @Override
    public long amountOf(Object value) {
        return value instanceof ItemStack stack ? stack.getCount() : -1L;
    }

    @Override
    public Object withAmount(Object value, long amount) {
        if (!(value instanceof ItemStack stack)) return null;
        int count = SaturatedMath.toNonNegativeInt(amount);
        return stack.copyWithCount(count);
    }

    private static void advanceStrategy(@Nullable FaceConfigComposite sourceCfg,
                                        @Nullable TransferContext context,
                                        ExtractionPlan plan) {
        if (sourceCfg == null || context == null) return;
        ItemExtractionStrategy.forMode(sourceCfg.linkConfig.getExtractionMode())
            .advanceAfterAttempt(plan.orderIndex(), plan.passCount(), context);
    }
}
