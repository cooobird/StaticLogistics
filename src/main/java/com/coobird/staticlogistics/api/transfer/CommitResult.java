package com.coobird.staticlogistics.api.transfer;

import java.util.Optional;

/**
 * 真实提取的提交结果。
 */
public record CommitResult<V>(Status status, Optional<ResourceValue<V>> resource) {
    public enum Status {
        SUCCESS,
        EMPTY,
        REJECTED,
        FAILED
    }

    public CommitResult {
        if (status == null) throw new IllegalArgumentException("Commit status must not be null");
        resource = resource == null ? Optional.empty() : resource;
        if (status == Status.SUCCESS && resource.isEmpty()) {
            throw new IllegalArgumentException("Successful commit must contain a resource");
        }
        if (status != Status.SUCCESS && resource.isPresent()) {
            throw new IllegalArgumentException("Failed commit must not contain a resource");
        }
    }

    public static <V> CommitResult<V> success(ResourceValue<V> resource) {
        return new CommitResult<>(Status.SUCCESS, Optional.of(resource));
    }

    public static <V> CommitResult<V> empty() {
        return new CommitResult<>(Status.EMPTY, Optional.empty());
    }

    public static <V> CommitResult<V> failed() {
        return new CommitResult<>(Status.FAILED, Optional.empty());
    }
}
