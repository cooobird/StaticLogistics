package com.coobird.staticlogistics.transfer;

import com.coobird.staticlogistics.api.LogisticsNode;

/** 对单个源与目标执行不复制优先的提交协议。 */
public final class TransferTransaction {
    public enum Failure {
        NONE,
        SOURCE_COMMIT_FAILED,
        ROLLBACK_FAILED
    }

    public record Result(long accepted, Failure failure) {
    }

    private TransferTransaction() {
    }

    public static <C, T> Result commit(TransferUtils.TransferProtocol<C, T> protocol,
                                      C source, C target, ExtractionResult<T> simulated,
                                      long simulatedAccepted) {
        return commit(protocol, source, target, simulated, simulatedAccepted, null);
    }

    public static <C, T> Result commit(TransferUtils.TransferProtocol<C, T> protocol,
                                      C source, C target, ExtractionResult<T> simulated,
                                      long simulatedAccepted, LogisticsNode targetNode) {
        // 在真实提取前确认协议能够计量资源，旧协议因此安全地停止而不会先丢失资源。
        if (protocol.amountOf(simulated.value()) <= 0L) {
            return new Result(0L, Failure.SOURCE_COMMIT_FAILED);
        }
        ExtractionResult<T> actual = protocol.executeExtract(source, simulated, simulatedAccepted);
        if (actual == null || protocol.isEmpty(actual)) {
            return new Result(0L, Failure.SOURCE_COMMIT_FAILED);
        }
        long rawActualAmount = protocol.amountOf(actual.value());
        if (rawActualAmount <= 0L) return rollbackAll(protocol, source, actual);
        long actualAmount = Math.min(simulatedAccepted, rawActualAmount);

        if (rawActualAmount > actualAmount) {
            T limitedValue = protocol.withAmount(actual.value(), actualAmount);
            if (limitedValue == null || !protocol.rollbackRemainder(source, actual, actualAmount)) {
                return new Result(0L, Failure.ROLLBACK_FAILED);
            }
            actual = new ExtractionResult<>(limitedValue, actual.context());
        }

        if (!protocol.canInsert(target, actual.value(), targetNode)) {
            return rollbackAll(protocol, source, actual);
        }
        long currentlyAccepted = Math.min(actualAmount,
            Math.max(0L, protocol.simulateInsert(target, actual.value())));
        if (currentlyAccepted <= 0L) return rollbackAll(protocol, source, actual);
        T insertValue = protocol.withAmount(actual.value(), currentlyAccepted);
        if (insertValue == null) return rollbackAll(protocol, source, actual);
        long accepted = Math.min(currentlyAccepted, Math.max(0L, protocol.executeInsert(target, insertValue)));
        if (accepted < actualAmount && !protocol.rollbackRemainder(source, actual, accepted)) {
            return new Result(accepted, Failure.ROLLBACK_FAILED);
        }
        return new Result(accepted, Failure.NONE);
    }

    private static <C, T> Result rollbackAll(TransferUtils.TransferProtocol<C, T> protocol,
                                             C source, ExtractionResult<T> actual) {
        return protocol.rollbackRemainder(source, actual, 0L)
            ? new Result(0L, Failure.NONE)
            : new Result(0L, Failure.ROLLBACK_FAILED);
    }
}
