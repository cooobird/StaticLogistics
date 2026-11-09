package com.coobird.staticlogistics.transfer;

import javax.annotation.Nullable;

public record ExtractionResult<T>(T value, @Nullable Object context, Status status) {
    public enum Status {COMPLETE, YIELDED}

    public ExtractionResult(T value, @Nullable Object context) {
        this(value, context, Status.COMPLETE);
    }

    /// 预算中断不是“容器已空”，也不是可以提交的模拟结果。
    public static <T> ExtractionResult<T> yielded(T emptyValue) {
        return new ExtractionResult<>(emptyValue, null, Status.YIELDED);
    }

    public boolean isYielded() {
        return status == Status.YIELDED;
    }

    public static <T> ExtractionResult<T> of(T value) {
        return new ExtractionResult<>(value, null);
    }

    public static <T> ExtractionResult<T> of(T value, Object context) {
        return new ExtractionResult<>(value, context);
    }

    public boolean isEmpty() {
        return isYielded() || value == null;
    }
}
