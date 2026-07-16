package com.coobird.staticlogistics.api.transfer;

import java.util.Objects;

/**
 * 带有明确计量值的非空资源。
 */
public record ResourceValue<V>(V value, long amount) {
    public ResourceValue {
        Objects.requireNonNull(value, "Resource value must not be null");
        if (amount <= 0L) throw new IllegalArgumentException("Resource amount must be positive");
    }
}
