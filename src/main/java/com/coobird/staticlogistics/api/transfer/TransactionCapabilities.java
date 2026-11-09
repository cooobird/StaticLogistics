package com.coobird.staticlogistics.api.transfer;

/// 适配器对模拟、拆分和回滚的事务保证。
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

    public static TransactionCapabilities exactCompensating() {
        return new TransactionCapabilities(true, true, RollbackMode.COMPENSATING);
    }

}
