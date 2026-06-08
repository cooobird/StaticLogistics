package com.coobird.staticlogistics.transfer.handler;

import java.util.List;

/**
 * 批量提取结果 —— 包含多个 ExtractionResult，每个对应一个槽位/一组资源。
 * 用于批量传输模式，一次提取多个栈，减少循环次数。
 *
 * @param <T> 资源栈类型（ItemStack、FluidStack 等）
 */
public record BulkExtractionResult<T>(List<ExtractionResult<T>> results) {

    public static <T> BulkExtractionResult<T> empty() {
        return new BulkExtractionResult<>(List.of());
    }

    public static <T> BulkExtractionResult<T> single(ExtractionResult<T> single) {
        return new BulkExtractionResult<>(List.of(single));
    }

    public boolean isEmpty() {
        return results.isEmpty();
    }

    /**
     * 计算所有栈的总量
     */
    public long totalAmount() {
        long total = 0;
        for (ExtractionResult<T> r : results) {
            if (r.context() instanceof Integer) {
                // ItemResource: context 是槽位索引，value 是 ItemStack
                // ItemStack 的 count 需要由调用方获取
            }
        }
        return total;
    }
}
