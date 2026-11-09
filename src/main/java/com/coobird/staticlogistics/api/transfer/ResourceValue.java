package com.coobird.staticlogistics.api.transfer;

/// 带有明确计量值的不可空资源值。
public record ResourceValue<V>(V value, long amount) {
    public ResourceValue {
        if (amount <= 0L) throw new IllegalArgumentException("Resource amount must be positive");
    }
}
