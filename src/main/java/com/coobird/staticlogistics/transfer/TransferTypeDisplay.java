package com.coobird.staticlogistics.transfer;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * 将服务端确定的资源类型标识转换为统一的玩家可读文本。
 */
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

    /**
     * 按运行时相同规则生成各资源类型的基础传输量与当前传输量。
     */
    public static List<Component> formatTransferAmounts(
        List<ResourceLocation> typeIds,
        long stackMultiplier
    ) {
        List<Component> lines = new ArrayList<>(typeIds.size());
        for (ResourceLocation typeId : typeIds) {
            LogisticsResource<?> type = TransferRegistries.get(typeId);
            if (type == null) continue;
            long current = LogisticsCalculator.calcTransferLimit(
                type, stackMultiplier);
            lines.add(Component.translatable(
                "gui.staticlogistics.transfer_amount",
                Component.translatable(type.translationKey())
                    .withStyle(style -> style.withColor(type.color())),
                amount(type.getBaseStackSize(), false),
                amount(current, true)
            ).withStyle(ChatFormatting.GRAY));
        }
        return List.copyOf(lines);
    }

    private static Component amount(long value, boolean current) {
        return value == Long.MAX_VALUE
            ? Component.translatable("gui.staticlogistics.infinite")
            .withStyle(ChatFormatting.LIGHT_PURPLE)
            : Component.literal(Long.toString(value))
            .withStyle(current
                ? ChatFormatting.GREEN
                : ChatFormatting.DARK_GRAY);
    }
}
