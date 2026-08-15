package com.coobird.staticlogistics.transfer;

import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.api.transfer.TransactionCapabilities;
import com.coobird.staticlogistics.config.SLConfig;
import com.coobird.staticlogistics.logistics.filter.FilterEvaluator;
import com.coobird.staticlogistics.logistics.node.FaceConfigComposite;
import com.coobird.staticlogistics.logistics.util.SaturatedMath;
import com.coobird.staticlogistics.transfer.strategy.ItemExtractionStrategy;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * 物品资源适配器 —— 带提取策略、过滤器检查、存量维持。
 *
 * <p>每次节点激活只扫描一次物理槽位，并由提取会话保存候选位置与遍历进度。
 */
public class ItemResource implements LogisticsResource<IItemHandler> {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ResourceLocation TYPE_ID = StaticLogistics.asResource("item");

    @Override
    public TransactionCapabilities transactionCapabilities() {
        return TransactionCapabilities.exactCompensating();
    }

    private record ExtractionPlan(Object sessionIdentity, int slotIndex) {
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
    public @Nullable IItemHandler resolve(ServerLevel level, BlockPos pos, Direction face) {
        return level.getCapability(Capabilities.ItemHandler.BLOCK, pos, face);
    }

    @Override
    public BlockCapability<IItemHandler, Direction> blockCapability() {
        return Capabilities.ItemHandler.BLOCK;
    }

    @Override
    public ResourceExtractionSession openExtractionSession(
        IItemHandler handle, @Nullable FaceConfigComposite sourceCfg, boolean isPullMode,
        @Nullable TransferContext context
    ) {
        return new ItemExtractionSession(handle, sourceCfg, isPullMode, context);
    }

    @Override
    public long insertTyped(IItemHandler handle, Object value, boolean simulate,
                            @Nullable FaceConfigComposite sourceCfg, boolean isPullMode,
                            @Nullable TransferContext context) {
        if (!(value instanceof ItemStack stack) || stack.isEmpty()) return 0;
        if (!simulate) return insertIntoHandler(handle, stack, false);
        try {
            return insertIntoHandler(handle, stack, true);
        } catch (RuntimeException exception) {
            LOGGER.error("Item insert simulation failed", exception);
            return 0;
        }
    }

    private static long insertIntoHandler(IItemHandler handle, ItemStack stack, boolean simulate) {
        ItemStack remain = simulate ? stack.copy() : stack;
        int accepted = 0;
        for (int i = 0; i < handle.getSlots(); i++) {
            ItemStack next = handle.insertItem(i, remain, simulate);
            if (next == null) throw new IllegalStateException("Item handler returned null");
            remain = next;
            accepted = Math.max(0, Math.min(
                stack.getCount(), stack.getCount() - remain.getCount()));
            if (remain.isEmpty()) break;
        }
        return accepted;
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
        // 输入过滤器检查
        if (!FilterEvaluator.isItemInputAllowed(stack, targetCfg)) return false;
        // 存量维持检查
        int keepStock = targetCfg.linkConfig.getKeepStock();
        if (keepStock > 0) {
            int alreadyHas = 0;
            for (int i = 0; i < handle.getSlots(); i++) {
                ItemStack targetStack = handle.getStackInSlot(i);
                if (ItemStack.isSameItemSameComponents(stack, targetStack)) {
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
            if (ItemStack.isSameItemSameComponents(stack, targetStack)) {
                alreadyHas += targetStack.getCount();
            }
        }
        return Math.max(0L, (long) keepStock - alreadyHas);
    }

    @Override
    public long amountOf(Object value) {
        return value instanceof ItemStack stack ? stack.getCount() : -1L;
    }

    @Override
    public Object withAmount(Object value, long amount) {
        return value instanceof ItemStack stack
            ? stack.copyWithCount(SaturatedMath.toNonNegativeInt(amount)) : null;
    }

    /**
     * 单次激活以流式游标遍历物理槽位，每个槽位本轮最多检查一次。
     */
    private static final class ItemExtractionSession implements ResourceExtractionSession {
        private final IItemHandler handle;
        private final @Nullable FaceConfigComposite sourceCfg;
        private final boolean pullMode;
        private final @Nullable TransferContext context;
        private final int slotCount;
        private final int startSlot;
        private final Object sessionIdentity = new Object();
        private int scannedSlots;
        private int pendingSlot = -1;

        private ItemExtractionSession(IItemHandler handle, @Nullable FaceConfigComposite sourceCfg,
                                      boolean pullMode, @Nullable TransferContext context) {
            this.handle = handle;
            this.sourceCfg = sourceCfg;
            this.pullMode = pullMode;
            this.context = context;
            this.slotCount = readSlotCount(handle);
            this.startSlot = sourceCfg != null && context != null && slotCount > 0
                ? ItemExtractionStrategy.forMode(sourceCfg.linkConfig.getExtractionMode())
                .beginActivation(slotCount, context) : 0;
        }

        @Override
        public ExtractionResult<?> simulate(long amount) {
            if (sourceCfg == null) return ExtractionResult.of(ItemStack.EMPTY);
            int limit = SaturatedMath.toNonNegativeInt(amount);
            if (pendingSlot >= 0) {
                try {
                    ItemStack stack = handle.extractItem(pendingSlot, limit, true);
                    if (!stack.isEmpty() && isAllowed(stack)) {
                        return ExtractionResult.of(
                            stack, new ExtractionPlan(sessionIdentity, pendingSlot));
                    }
                } catch (Exception e) {
                    LOGGER.error("Item extract simulation failed", e);
                }
                resolvePendingSlot();
            }
            while (pendingSlot < 0 && scannedSlots < slotCount
                && (context == null || context.hasTimeRemaining())) {
                int slot = (startSlot + scannedSlots++) % slotCount;
                try {
                    ItemStack stack = handle.extractItem(slot, limit, true);
                    if (!stack.isEmpty() && isAllowed(stack)) {
                        pendingSlot = slot;
                        return ExtractionResult.of(stack, new ExtractionPlan(sessionIdentity, slot));
                    }
                } catch (Exception e) {
                    LOGGER.error("Item extract simulation failed", e);
                }
                advanceCursor(slot);
            }
            return ExtractionResult.of(ItemStack.EMPTY);
        }

        @Override
        public ExtractionResult<?> execute(ExtractionResult<?> simulated, long requested) {
            if (!(simulated.value() instanceof ItemStack expected)
                || !(simulated.context() instanceof ExtractionPlan plan)
                || plan.sessionIdentity() != sessionIdentity
                || pendingSlot != plan.slotIndex()) {
                return ExtractionResult.of(ItemStack.EMPTY);
            }
            try {
                int amount = SaturatedMath.toNonNegativeInt(requested);
                ItemStack current;
                try {
                    current = handle.extractItem(plan.slotIndex(), amount, true);
                } catch (RuntimeException exception) {
                    LOGGER.error("Item extract simulation failed", exception);
                    return ExtractionResult.of(ItemStack.EMPTY, plan);
                }
                if (current.isEmpty() || !ItemStack.isSameItemSameComponents(expected, current)
                    || sourceCfg == null || !isAllowed(current)) {
                    return ExtractionResult.of(ItemStack.EMPTY, plan);
                }
                return ExtractionResult.of(handle.extractItem(plan.slotIndex(), amount, false), plan);
            } finally {
                resolvePendingSlot();
            }
        }

        @Override
        public boolean advanceRejected(ExtractionResult<?> simulated) {
            if (!(simulated.context() instanceof ExtractionPlan plan)
                || plan.sessionIdentity() != sessionIdentity
                || pendingSlot != plan.slotIndex()) {
                return false;
            }
            resolvePendingSlot();
            return true;
        }

        @Override
        public void onCompleted() {
            if (sourceCfg == null || context == null) return;
            // 只有真正遍历完全部物理槽位才结束一轮顺序扫描。若只是本次传输额度耗尽，
            // 必须保留下一个槽位游标，避免高产容器持续补充前部槽位时饿死后部槽位。
            if (scannedSlots >= slotCount) {
                ItemExtractionStrategy.forMode(sourceCfg.linkConfig.getExtractionMode()).finishActivation(context);
            }
        }

        private boolean isAllowed(ItemStack stack) {
            if (sourceCfg == null) return false;
            return pullMode ? FilterEvaluator.isItemInputAllowed(stack, sourceCfg)
                : FilterEvaluator.isItemOutputAllowed(stack, sourceCfg);
        }

        private void resolvePendingSlot() {
            if (pendingSlot < 0) return;
            int slot = pendingSlot;
            pendingSlot = -1;
            advanceCursor(slot);
        }

        private void advanceCursor(int slot) {
            if (sourceCfg == null || context == null) return;
            ItemExtractionStrategy.forMode(sourceCfg.linkConfig.getExtractionMode())
                .advanceAfterAttempt(slot, slotCount, context);
        }

        private static int readSlotCount(IItemHandler handle) {
            try {
                return Math.max(0, handle.getSlots());
            } catch (Exception e) {
                LOGGER.error("Item slot count query failed", e);
                return 0;
            }
        }
    }
}
