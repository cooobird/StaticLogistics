package com.coobird.staticlogistics.transfer;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Locale;

/// 将服务端确定的资源类型标识转换为统一的玩家可读文本。
public final class TransferTypeDisplay {
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
        String suffix = "";
        if (value >= 1_000_000_000) {
            value /= 1_000_000_000;
            suffix = "G";
        } else if (value >= 1_000_000) {
            value /= 1_000_000;
            suffix = "M";
        } else if (value >= 1_000) {
            value /= 1_000;
            suffix = "k";
        }
        String number = value == Math.rint(value) ? String.format(Locale.ROOT, "%.0f", value)
            : String.format(Locale.ROOT, "%.1f", value);
        return number + suffix + " " + unit(type) + "/s";
    }

    private static String unit(LogisticsResource<?> type) {
        return type.unit().getString();
    }
}
