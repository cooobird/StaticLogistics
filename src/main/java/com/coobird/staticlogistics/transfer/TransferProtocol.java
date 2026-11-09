package com.coobird.staticlogistics.transfer;

import com.coobird.staticlogistics.api.LogisticsNode;

/// 实时资源模拟与提交契约；模拟同样可能调用容器，不能作为后台规划接口。
public interface TransferProtocol<C, T> {
    ExtractionResult<T> simulateExtract(C source, long max);

    long executeInsert(C dest, T stack);

    default long simulateInsert(C dest, T stack) {
        return 0L;
    }

    default ExtractionResult<T> executeExtract(C source, ExtractionResult<T> simulated, long requested) {
        throw new UnsupportedOperationException("Transfer protocol must report the actual extracted resource");
    }

    default long amountOf(T value) {
        throw new UnsupportedOperationException("Transfer protocol must report resource amounts");
    }

    default T withAmount(T value, long amount) {
        return null;
    }

    default boolean rollbackRemainder(C source, ExtractionResult<T> extracted, long accepted) {
        return false;
    }

    boolean isEmpty(ExtractionResult<T> result);

    default boolean canInsert(C dest, T stack, LogisticsNode targetNode) {
        return true;
    }

    /// 单次激活正常结束后，清理仅应在本轮内保留的扫描状态。
    default void onActivationCompleted() {
    }

    default Object candidateKey(ExtractionResult<T> simulated) {
        return null;
    }

    default boolean advanceRejectedCandidate(ExtractionResult<T> simulated) {
        return false;
    }
}
