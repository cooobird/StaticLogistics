package com.coobird.staticlogistics.client.gui.component;

import java.math.BigDecimal;
import java.util.Locale;

/// 十进制单位输入；使用 64 位整数，拒绝溢出和非整数结果。
public final class CompactNumberInput {
    private CompactNumberInput() {
    }

    public static long parse(String input) {
        String text = input.trim().toLowerCase(Locale.ROOT);
        if (!text.matches("-?[0-9]+([.][0-9]+)?[kmg]?")) throw new IllegalArgumentException("Invalid number");
        char suffix = text.charAt(text.length() - 1);
        int exponent = switch (suffix) {
            case 'k' -> 3;
            case 'm' -> 6;
            case 'g' -> 9;
            default -> 0;
        };
        if (exponent != 0) text = text.substring(0, text.length() - 1);
        try {
            return new BigDecimal(text).scaleByPowerOfTen(exponent).longValueExact();
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("Number exceeds current protocol range", exception);
        }
    }
}
