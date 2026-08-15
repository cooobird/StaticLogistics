package com.coobird.staticlogistics.transfer;

import com.coobird.staticlogistics.api.LogisticsNode;

/**
 * 对单个源与目标执行不复制优先的提交协议。
 */
public final class TransferTransaction {
    public enum Failure {
        NONE,
        SOURCE_COMMIT_FAILED,
        COMMIT_STATE_UNKNOWN,
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
        // 在真实提取前确认协议能够计量资源，旧协议因此安全停止而不会先丢失资源。
        long simulatedAmount;
        try {
            simulatedAmount = protocol.amountOf(simulated.value());
        } catch (RuntimeException exception) {
            return new Result(0L, Failure.SOURCE_COMMIT_FAILED);
        }
        if (simulatedAmount <= 0L) {
            return new Result(0L, Failure.SOURCE_COMMIT_FAILED);
        }
        ExtractionResult<T> actual;
        try {
            actual = protocol.executeExtract(source, simulated, simulatedAccepted);
        } catch (RuntimeException exception) {
            return new Result(0L, Failure.COMMIT_STATE_UNKNOWN);
        }
        if (actual == null) return new Result(0L, Failure.COMMIT_STATE_UNKNOWN);
        try {
            if (protocol.isEmpty(actual)) return new Result(0L, Failure.SOURCE_COMMIT_FAILED);
        } catch (RuntimeException exception) {
            return rollbackAll(protocol, source, actual);
        }
        long rawActualAmount;
        try {
            rawActualAmount = protocol.amountOf(actual.value());
        } catch (RuntimeException exception) {
            return rollbackAll(protocol, source, actual);
        }
        if (rawActualAmount <= 0L) return rollbackAll(protocol, source, actual);
        long actualAmount = Math.min(simulatedAccepted, rawActualAmount);

        if (rawActualAmount > actualAmount) {
            T limitedValue;
            try {
                limitedValue = protocol.withAmount(actual.value(), actualAmount);
            } catch (RuntimeException exception) {
                return rollbackAll(protocol, source, actual);
            }
            if (limitedValue == null || !rollbackRemainder(protocol, source, actual, actualAmount)) {
                return new Result(0L, Failure.ROLLBACK_FAILED);
            }
            actual = new ExtractionResult<>(limitedValue, actual.context());
        }
        long currentlyAccepted;
        try {
            if (!protocol.canInsert(target, actual.value(), targetNode)) {
                return rollbackAll(protocol, source, actual);
            }
            currentlyAccepted = Math.min(actualAmount,
                Math.max(0L, protocol.simulateInsert(target, actual.value())));
        } catch (RuntimeException exception) {
            return rollbackAll(protocol, source, actual);
        }
        if (currentlyAccepted <= 0L) return rollbackAll(protocol, source, actual);
        T insertValue;
        try {
            insertValue = protocol.withAmount(actual.value(), currentlyAccepted);
        } catch (RuntimeException exception) {
            return rollbackAll(protocol, source, actual);
        }
        if (insertValue == null) return rollbackAll(protocol, source, actual);
        long accepted;
        try {
            accepted = Math.min(currentlyAccepted,
                Math.max(0L, protocol.executeInsert(target, insertValue)));
        } catch (RuntimeException exception) {
            return new Result(0L, Failure.COMMIT_STATE_UNKNOWN);
        }
        if (accepted < actualAmount && !rollbackRemainder(protocol, source, actual, accepted)) {
            return new Result(accepted, Failure.ROLLBACK_FAILED);
        }
        return new Result(accepted, Failure.NONE);
    }

    private static <C, T> Result rollbackAll(TransferUtils.TransferProtocol<C, T> protocol,
                                             C source, ExtractionResult<T> actual) {
        return rollbackRemainder(protocol, source, actual, 0L)
            ? new Result(0L, Failure.NONE)
            : new Result(0L, Failure.ROLLBACK_FAILED);
    }

    private static <C, T> boolean rollbackRemainder(
        TransferUtils.TransferProtocol<C, T> protocol, C source,
        ExtractionResult<T> actual, long accepted
    ) {
        try {
            return protocol.rollbackRemainder(source, actual, accepted);
        } catch (RuntimeException exception) {
            return false;
        }
    }
}
