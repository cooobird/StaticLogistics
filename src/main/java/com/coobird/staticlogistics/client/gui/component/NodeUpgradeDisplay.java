package com.coobird.staticlogistics.client.gui.component;

import com.coobird.staticlogistics.config.SLConfig;
import com.coobird.staticlogistics.logistics.node.ContainerConfig;
import com.coobird.staticlogistics.transfer.LogisticsCalculator;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.ArrayList;
import java.util.List;

/**
 * 节点升级数值的统一显示口径。
 */
public final class NodeUpgradeDisplay {
    private static final int SPEED_SLOT_INDEX = 2;
    private static final int RANGE_SLOT_INDEX = 3;
    private static final int STACK_SLOT_INDEX = 4;

    private NodeUpgradeDisplay() {
    }

    public static List<Component> all(
        long speedMultiplier,
        long rangeMultiplier,
        long stackMultiplier,
        boolean dimensionEffective
    ) {
        List<Component> lines = new ArrayList<>();
        lines.addAll(forSlot(SPEED_SLOT_INDEX, speedMultiplier, rangeMultiplier,
            stackMultiplier, dimensionEffective));
        lines.addAll(forSlot(RANGE_SLOT_INDEX, speedMultiplier, rangeMultiplier,
            stackMultiplier, dimensionEffective));
        lines.addAll(forSlot(STACK_SLOT_INDEX, speedMultiplier, rangeMultiplier,
            stackMultiplier, dimensionEffective));
        return lines;
    }

    public static List<Component> forSlot(
        int slotIndex,
        long speedMultiplier,
        long rangeMultiplier,
        long stackMultiplier,
        boolean dimensionEffective
    ) {
        int baseInterval = SLConfig.getDefaultTickInterval();
        int baseRadius = SLConfig.getDefaultRadius();
        long infinity = ContainerConfig.INFINITY_MARKER;
        return switch (slotIndex) {
            case SPEED_SLOT_INDEX -> List.of(
                Component.translatable("gui.staticlogistics.stat.speed")
                    .withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(" "))
                    .append(statValue(
                        LogisticsCalculator.calcSpeedInterval(
                            baseInterval, speedMultiplier),
                        speedMultiplier > 1))
                    .append(statUnit(
                        "gui.staticlogistics.unit.ticks", speedMultiplier > 1))
                    .append(baseValue(
                        baseInterval, "gui.staticlogistics.unit.ticks")));
            case RANGE_SLOT_INDEX -> {
                boolean infinite = dimensionEffective
                    || rangeMultiplier >= infinity
                    || exceedsProduct(baseRadius, rangeMultiplier);
                MutableComponent range = infinite
                    ? Component.translatable("gui.staticlogistics.infinite")
                    .withStyle(ChatFormatting.LIGHT_PURPLE)
                    : statValue(baseRadius * rangeMultiplier, rangeMultiplier > 1)
                    .append(statUnit(
                        "gui.staticlogistics.unit.meters",
                        rangeMultiplier > 1));
                yield List.of(
                    Component.translatable("gui.staticlogistics.stat.range")
                        .withStyle(ChatFormatting.GRAY)
                        .append(Component.literal(" "))
                        .append(range)
                        .append(baseValue(
                            baseRadius, "gui.staticlogistics.unit.meters")),
                    Component.translatable("gui.staticlogistics.stat.dimension")
                        .withStyle(ChatFormatting.GRAY)
                        .append(Component.literal(" "))
                        .append(Component.translatable(dimensionEffective
                                ? "gui.staticlogistics.true"
                                : "gui.staticlogistics.false")
                            .withStyle(dimensionEffective
                                ? ChatFormatting.GREEN
                                : ChatFormatting.GRAY)));
            }
            case STACK_SLOT_INDEX -> {
                boolean infinite = stackMultiplier >= infinity;
                MutableComponent value = infinite
                    ? Component.translatable("gui.staticlogistics.infinite")
                    .withStyle(ChatFormatting.GREEN)
                    : Component.translatable(
                        "gui.staticlogistics.unit.multiplier")
                    .withStyle(stackMultiplier > 1
                        ? ChatFormatting.GREEN
                        : ChatFormatting.GRAY)
                    .append(statValue(stackMultiplier, stackMultiplier > 1));
                yield List.of(
                    Component.translatable("gui.staticlogistics.stat.stack")
                        .withStyle(ChatFormatting.GRAY)
                        .append(Component.literal(" "))
                        .append(value)
                        .append(Component.literal("  (×1)")
                            .withStyle(ChatFormatting.DARK_GRAY)));
            }
            default -> List.of();
        };
    }

    private static MutableComponent baseValue(long value, String unitKey) {
        return Component.literal("  (" + value)
            .withStyle(ChatFormatting.DARK_GRAY)
            .append(Component.translatable(unitKey)
                .withStyle(ChatFormatting.DARK_GRAY))
            .append(Component.literal(")")
                .withStyle(ChatFormatting.DARK_GRAY));
    }

    private static MutableComponent statValue(long value, boolean boosted) {
        return Component.literal(String.valueOf(value))
            .withStyle(boosted ? ChatFormatting.GREEN : ChatFormatting.GRAY);
    }

    private static MutableComponent statUnit(String key, boolean boosted) {
        return Component.translatable(key)
            .withStyle(boosted ? ChatFormatting.GREEN : ChatFormatting.GRAY);
    }

    private static boolean exceedsProduct(long base, long multiplier) {
        return base > 0 && multiplier > Long.MAX_VALUE / base;
    }
}
