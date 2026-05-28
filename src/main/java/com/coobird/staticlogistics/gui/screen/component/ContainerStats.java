package com.coobird.staticlogistics.gui.screen.component;

import com.coobird.staticlogistics.config.SLConfig;
import com.coobird.staticlogistics.storage.config.ContainerConfig;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * 容器升级统计面板：范围、速度、维度、堆叠倍率。
 */
public class ContainerStats {

    public static void render(GuiGraphics g, Font font, int leftPos, int topPos,
                              long speedMult, long rangeMult, long stackMult,
                              boolean hasDimension) {
        int infoX = leftPos + 122;
        int infoY = topPos + 16;
        int spacing = 14;

        // 速度
        int baseInterval = SLConfig.getDefaultTickInterval();
        int actualInterval = (int) Math.max(1, baseInterval / Math.sqrt(speedMult));
        String speedText = actualInterval + Component.translatable("gui.staticlogistics.unit.ticks").getString();
        int speedColor = speedMult > 1 ? 0x55FF55 : 0xCCCCCC;
        drawStat(g, font, Component.translatable("gui.staticlogistics.stat.speed"),
            speedText, infoX, infoY + spacing, 0xFFFFFF, speedColor);

        // 范围
        int baseRange = SLConfig.getDefaultRadius();
        boolean isRangeInfinite = hasDimension || rangeMult >= ContainerConfig.INFINITY_MARKER;
        String rangeText = isRangeInfinite
            ? Component.translatable("gui.staticlogistics.infinite").getString()
            : (baseRange * rangeMult) + Component.translatable("gui.staticlogistics.unit.meters").getString();
        int rangeColor = isRangeInfinite ? 0xFF55FF : 0x55FFFF;
        drawStat(g, font, Component.translatable("gui.staticlogistics.stat.range"),
            rangeText, infoX, infoY, 0xFFFFFF, rangeColor);

        // 维度
        String dimensionText = hasDimension
            ? Component.translatable("gui.staticlogistics.true").getString()
            : Component.translatable("gui.staticlogistics.false").getString();
        int dimensionColor = hasDimension ? 0x55FF55 : 0xCCCCCC;
        drawStat(g, font, Component.translatable("gui.staticlogistics.stat.dimension"),
            dimensionText, infoX, infoY + spacing * 2, 0xFFFFFF, dimensionColor);

        // 堆叠
        String stackText;
        int stackColor;
        if (stackMult >= ContainerConfig.INFINITY_MARKER) {
            stackText = Component.translatable("gui.staticlogistics.infinite").getString();
            stackColor = 0x55FF55;
        } else {
            stackText = stackMult + Component.translatable("gui.staticlogistics.unit.multiplier").getString();
            stackColor = stackMult > 1 ? 0x55FF55 : 0xCCCCCC;
        }
        drawStat(g, font, Component.translatable("gui.staticlogistics.stat.stack"),
            stackText, infoX, infoY + spacing * 3, 0xFFFFFF, stackColor);
    }

    private static void drawStat(GuiGraphics g, Font font, Component label,
                                 String value, int x, int y,
                                 int labelColor, int valueColor) {
        g.drawString(font, label, x, y, labelColor, false);
        int labelWidth = font.width(label);
        g.drawString(font, value, x + labelWidth + 4, y, valueColor, false);
    }
}
