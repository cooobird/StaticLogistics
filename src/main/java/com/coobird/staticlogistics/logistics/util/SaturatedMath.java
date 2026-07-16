package com.coobird.staticlogistics.logistics.util;

/** 为只接受整数数量的外部能力提供饱和转换。 */
public final class SaturatedMath {
    private SaturatedMath() {
    }

    public static int toNonNegativeInt(long value) {
        if (value <= 0L) return 0;
        return value >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) value;
    }
}
