package com.coobird.staticlogistics.transfer;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Locale;

/// 将服务端确定的资源类型标识转换为统一的玩家可读文本。
public final class TransferTypeDisplay {
    private static final String[] RATE_SUFFIXES = {"", "k", "M", "G", "T", "P", "E", "Z", "Y"};

    private TransferTypeDisplay() {
    }

    public static Component format(List<ResourceLocation> typeIds, String emptyTranslationKey) {
        MutableComponent result = Component.empty();
        boolean hasType = false;
        for (ResourceLocation typeId : typeIds) {
            if (hasType) {
                result.append(Component.literal(", ").withStyle(ChatFormatting.GRAY));
            }
            LogisticsResource<?> type = TransferRegistries.get(typeId);
            if (type == null) {
                result.append(Component.literal(typeId.toString()).withStyle(ChatFormatting.DARK_GRAY));
            } else {
                result.append(Component.translatable(type.translationKey())
                    .withStyle(style -> style.withColor(type.color())));
            }
            hasType = true;
        }
        return hasType ? result : Component.translatable(emptyTranslationKey);
    }

    public static Component transferLimit(LogisticsResource<?> type, long multiplier) {
        long value = LogisticsCalculator.calcTransferLimit(type, multiplier);
        if (value == Long.MAX_VALUE) return Component.translatable("gui.staticlogistics.infinite");
        String unit = unit(type);
        return Component.literal(value + (unit.isEmpty() ? "" : " " + unit));
    }

    public static String rate(LogisticsResource<?> type, double value) {
        return rateValue(value) + " " + unit(type) + "/s";
    }

    public static String rateValue(double value) {
        if (!Double.isFinite(value)) return "—";
        double magnitude = Math.abs(value);
        int unit = 0;
        while (magnitude >= 1_000 && unit < RATE_SUFFIXES.length - 1) {
            magnitude /= 1_000;
            unit++;
        }
        long tenths = Math.round(magnitude * 10);
        /// 显示值四舍五入到 1000 时也进位，避免出现 1000G 之类的写法。
        if (tenths >= 10_000) {
            if (unit == RATE_SUFFIXES.length - 1) return String.format(Locale.ROOT, "%.1e", value);
            unit++;
            tenths = 10;
        }
        String sign = value < 0 && tenths != 0 ? "-" : "";
        long fraction = tenths % 10;
        String number = sign + tenths / 10;
        return (fraction == 0 ? number : number + "." + fraction) + RATE_SUFFIXES[unit];
    }

    private static String unit(LogisticsResource<?> type) {
        return type.unit().getString();
    }
}
