package com.coobird.staticlogistics.client.gui.component;

import com.coobird.staticlogistics.client.render.SLGuiTextures;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

/// 配置窗口统一使用 atlas 底板与按钮。
public final class ConfiguratorStyle {
    // 长名称滚动前后的停顿时间。
    private static final long TEXT_SCROLL_PAUSE_MS = 1000L;
    // 长名称每移动一个字符所需的时间。
    private static final long TEXT_SCROLL_STEP_MS = 180L;

    private ConfiguratorStyle() {
    }

    public static String scrollingText(Font font, String text, int width, boolean hovered) {
        if (width <= 0) return "";
        if (font.width(text) <= width) return text;
        if (!hovered) {
            String ellipsis = "…";
            if (font.width(ellipsis) >= width) return font.plainSubstrByWidth(ellipsis, width);
            return font.plainSubstrByWidth(text, Math.max(0, width - font.width(ellipsis))) + ellipsis;
        }
        int endOffset = 0;
        int steps = 0;
        while (endOffset < text.length() && font.width(text.substring(endOffset)) > width) {
            endOffset = text.offsetByCodePoints(endOffset, 1);
            steps++;
        }
        long travel = steps * TEXT_SCROLL_STEP_MS;
        long phase = System.currentTimeMillis() % (travel + TEXT_SCROLL_PAUSE_MS * 2);
        int currentStep = (int) Math.min(steps, Math.max(0L, phase - TEXT_SCROLL_PAUSE_MS) / TEXT_SCROLL_STEP_MS);
        return font.plainSubstrByWidth(text.substring(text.offsetByCodePoints(0, currentStep)), width);
    }

    public static double interfaceScale(int width, int height) {
        return interfaceScale(width, height, 0);
    }

    public static double interfaceScale(int width, int height, int extraWidth) {
        return Mth.clamp(Math.min((width - 8.0) / (SLGuiTextures.LinkConfigurator.WIDTH + extraWidth),
            (height - 8.0) / (SLGuiTextures.LinkConfigurator.HEIGHT - SLGuiTextures.LinkConfigurator.CONTENT_INSET)), 0.1, 1.0);
    }

    public static void slot(GuiGraphics graphics, int x, int y) {
        SLGuiTextures.NODE_SLOT.draw(graphics, x - 4, y - 4);
    }

    public static void slot(GuiGraphics graphics, int x, int y, boolean selected) {
        SLGuiTextures.NODE_SLOT.draw(graphics, x - 4, y - 4);
        if (selected) SLGuiTextures.NODE_SLOT_SELECTED.draw(graphics, x - 4, y - 4);
    }

    /// 连同底板、外留边和快捷栏间隔一起原尺寸绘制。
    public static void inventory(GuiGraphics graphics, int x, int y) {
        SLGuiTextures.PLAYER_INVENTORY.draw(graphics, x, y);
    }

    public static final int TEXT = 0xFF25252D;
    public static final int MUTED_TEXT = 0xFF444450;
    public static final int ACCENT = 0xFF303E77;

    public static void window(GuiGraphics g, int x, int y, int w, int h) {
        SLGuiTextures.WINDOW.frame(g, x, y, w, h, 3);
    }

    public static void panel(GuiGraphics g, int x, int y, int w, int h) {
        SLGuiTextures.PANEL.frame(g, x, y, w, h, 7);
    }

    public static void button(GuiGraphics g, int x, int y, int w, int h, boolean selected, boolean hovered) {
        button(g, x, y, w, h, selected, hovered, true);
    }

    public static void button(GuiGraphics g, int x, int y, int w, int h, boolean selected, boolean hovered, boolean enabled) {

        boolean checked = selected && enabled;
        if (h < 20) {
            var surface = h < 17
                ? hovered && enabled ? checked ? SLGuiTextures.CHECKED_SMALL_HOVER : SLGuiTextures.BUTTON_SMALL_HOVER
                : checked ? SLGuiTextures.CHECKED_SMALL : SLGuiTextures.BUTTON_SMALL
                : hovered && enabled ? checked ? SLGuiTextures.CHECKED_MIDDLE_HOVER : SLGuiTextures.BUTTON_MIDDLE_HOVER
                : checked ? SLGuiTextures.CHECKED_MIDDLE : SLGuiTextures.BUTTON_MIDDLE;
            surface.frame(g, x, y, w, h, 3);
            return;
        }
        var surface = hovered && enabled
            ? checked ? SLGuiTextures.CHECKED_HOVER : SLGuiTextures.BUTTON_HOVER
            : checked ? SLGuiTextures.CHECKED_BUTTON : SLGuiTextures.BUTTON;
        surface.frame(g, x, y, w, h, 3);
    }

    public static void directory(GuiGraphics g, int x, int y, boolean selected) {
        (selected ? SLGuiTextures.DIRECTORY_SELECTED : SLGuiTextures.DIRECTORY).draw(g, x, y);
    }

    public static void row(GuiGraphics g, int x, int y, int w, int h, boolean selected, boolean hovered) {
        button(g, x, y, w, h, selected, hovered);
    }

    public static void disclosure(GuiGraphics g, int x, int y, boolean collapsed) {
        if (collapsed) SLGuiTextures.DISCLOSURE_RIGHT.draw(g, x + 2, y - 1);
        else SLGuiTextures.ARROW_DOWN.draw(g, x, y);
    }

    public static void scrollbar(GuiGraphics g, int x, int y, int h, int offset, boolean active) {
        scrollbar(g, x, y, SLGuiTextures.Scrollbar.WIDTH, h, offset, 1, active);
    }

    public static void scrollbar(GuiGraphics g, int x, int y, int width, int height, int offset, int thumbXOffset, boolean active) {
        SLGuiTextures.SCROLL_TRACK.frame(g, x, y, width, height, 1);
        (active ? SLGuiTextures.SCROLL_THUMB : SLGuiTextures.SCROLL_THUMB_IDLE).draw(g, x + thumbXOffset, y + offset);
    }

    public static void separator(GuiGraphics g, int x, int y, int w) {
        SLGuiTextures.SEPARATOR.surface(g, x, y, w, 1);
    }

    public static void shade(GuiGraphics g, int x, int y, int w, int h, float opacity) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        g.setColor(0, 0, 0, opacity);
        SLGuiTextures.WHITE.surface(g, x, y, w, h);
        g.setColor(1, 1, 1, 1);
        RenderSystem.disableBlend();
    }
}
