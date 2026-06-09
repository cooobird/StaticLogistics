package com.coobird.staticlogistics.transfer.resource;

import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.api.LogisticsResource;
import com.coobird.staticlogistics.config.SLConfig;
import com.coobird.staticlogistics.filter.FilterEvaluator;
import com.coobird.staticlogistics.storage.model.FaceConfigComposite;
import com.coobird.staticlogistics.transfer.TransferContext;
import com.coobird.staticlogistics.transfer.handler.ExtractionResult;
import com.coobird.staticlogistics.transfer.strategy.extract.ItemExtractionStrategy;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * 物品资源适配器 —— 带提取策略、过滤器检查、存量维持。
 *
 * <p>extractTyped 返回 {@code ExtractionResult<ItemStack>}，context 中携带槽位索引（{@code Integer}）。
 * commitExtract 使用该索引从源容器精确提取。
 */
public class ItemResource implements LogisticsResource<IItemHandler> {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ResourceLocation TYPE_ID = StaticLogistics.asResource("item");

    // ThreadLocal 复用槽位数组，避免每次 extractTyped 分配
    private static final int MAX_SLOTS = 1024;
    private static final ThreadLocal<int[]> TL_SLOT_ORDER = ThreadLocal.withInitial(() -> new int[MAX_SLOTS]);
    private static final ThreadLocal<int[]> TL_PRIORITIES = ThreadLocal.withInitial(() -> new int[MAX_SLOTS]);

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
    public ExtractionResult<?> extractTyped(IItemHandler handle, long amount, boolean simulate,
                                            @Nullable FaceConfigComposite sourceCfg, boolean isPullMode,
                                            @Nullable TransferContext context) {
        if (sourceCfg == null) return ExtractionResult.of(ItemStack.EMPTY);
        try {
            int limit = (int) amount;
            int slots = handle.getSlots();
            if (slots > MAX_SLOTS) return ExtractionResult.of(ItemStack.EMPTY);

            // 复用 ThreadLocal 数组
            int[] slotOrder = TL_SLOT_ORDER.get();
            int[] priorities = TL_PRIORITIES.get();
            int passCount = 0;
            for (int s = 0; s < slots; s++) {
                ItemStack sim = handle.extractItem(s, limit, true);
                if (sim.isEmpty()) continue;
                if (!FilterEvaluator.isItemOutputAllowed(sim, sourceCfg)) continue;
                priorities[s] = sourceCfg.linkConfig.getPriority();
                slotOrder[passCount++] = s;
            }
            if (passCount == 0) return ExtractionResult.of(ItemStack.EMPTY);

            // priority 降序排序（插入排序）
            for (int i = 1; i < passCount; i++) {
                int key = slotOrder[i];
                int keyPrio = priorities[key];
                int j = i - 1;
                while (j >= 0 && priorities[slotOrder[j]] < keyPrio) {
                    slotOrder[j + 1] = slotOrder[j];
                    j--;
                }
                slotOrder[j + 1] = key;
            }

            // 提取策略
            int startIdx = 0;
            if (context != null) {
                ItemExtractionStrategy strategy = ItemExtractionStrategy.forMode(
                    sourceCfg.linkConfig.getExtractionMode());
                startIdx = strategy.beginTick(passCount, context);
            }

            // 按策略顺序查找第一个可用槽位
            for (int count = 0; count < passCount; count++) {
                int idx = (startIdx + count) % passCount;
                int s = slotOrder[idx];
                ItemStack sim = handle.extractItem(s, limit, true);
                if (!sim.isEmpty()) {
                    return ExtractionResult.of(sim, s);
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
    public void commitExtract(IItemHandler handle, ExtractionResult<?> result, long actual,
                              @Nullable FaceConfigComposite sourceCfg, boolean isPullMode,
                              @Nullable TransferContext context) {
        try {
            // 从 context 中获取槽位索引进行精确提取
            if (result.context() instanceof Integer slotIdx) {
                handle.extractItem(slotIdx, (int) actual, false);
            }
        } catch (Exception e) {
            LOGGER.error("Item commitExtract failed", e);
        }
    }
}
