package com.coobird.staticlogistics.api.transfer;

/**
 * 适配器对模拟、拆分和回滚的事务保证。
 */
public record TransactionCapabilities(
    boolean exactSimulation,
    boolean exactSplit,
    RollbackMode rollbackMode
) {
    public enum RollbackMode {
        NATIVE,
        COMPENSATING,
        NONE
    }

    public TransactionCapabilities {
        if (rollbackMode == null) throw new IllegalArgumentException("Rollback mode must not be null");
    }

    public static TransactionCapabilities exactCompensating() {
        return new TransactionCapabilities(true, true, RollbackMode.COMPENSATING);
    }

    /**
     * 用于提交结果严格等于同 tick 模拟结果、但源句柄不支持反向写入的能力。
     */
    public static TransactionCapabilities exactSimulationOnly() {
        return new TransactionCapabilities(true, true, RollbackMode.NONE);
    }
}
