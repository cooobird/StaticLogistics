package com.coobird.staticlogistics.api.transfer;

import java.util.Optional;

/** 模拟提取结果；令牌只用于适配器定位同一次提交，不承载资源值。 */
public record SimulationResult<V>(Optional<ResourceValue<V>> resource, long token) {
    public SimulationResult {
        resource = resource == null ? Optional.empty() : resource;
    }

    public static <V> SimulationResult<V> empty() {
        return new SimulationResult<>(Optional.empty(), -1L);
    }

    public static <V> SimulationResult<V> of(ResourceValue<V> resource, long token) {
        return new SimulationResult<>(Optional.of(resource), token);
    }
}
