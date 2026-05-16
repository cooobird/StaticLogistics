package com.coobird.staticlogistics.transfer.handler;

import javax.annotation.Nullable;

public record ExtractionResult<T>(T value, @Nullable Object context) {
    public static <T> ExtractionResult<T> of(T value) {
        return new ExtractionResult<>(value, null);
    }

    public static <T> ExtractionResult<T> of(T value, Object context) {
        return new ExtractionResult<>(value, context);
    }

    public boolean isEmpty() {
        return value == null;
    }

    @Nullable
    public <C> C getContext() {
        return (C) context;
    }
}