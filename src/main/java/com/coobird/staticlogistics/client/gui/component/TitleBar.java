package com.coobird.staticlogistics.client.gui.component;

import com.coobird.staticlogistics.client.render.SLGuiTextures;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * 独立配置窗口共用的标题栏。
 */
public final class TitleBar {

    public static final int WIDTH = SLGuiTextures.Title.CONTENT_WIDTH;
    public static final int HEIGHT = SLGuiTextures.Title.CONTENT_HEIGHT;
    public static final int Y_OFFSET = -21;
    public static final int COLOR = 0x98FB98;

    private TitleBar() {
    }

    public static int getX(int leftPos, int backgroundWidth) {
        return leftPos + (backgroundWidth - WIDTH) / 2;
    }

    public static int getY(int topPos) {
        return topPos + Y_OFFSET;
    }

    public static void render(GuiGraphics g, Font font, int leftPos, int topPos,
                              int backgroundWidth, String titleText) {
        int tx = getX(leftPos, backgroundWidth);
        int ty = getY(topPos);
        int inset = SLGuiTextures.Title.CONTENT_INSET;
        g.blit(SLGuiTextures.GUI_ATLAS, tx - inset, ty - inset,
            SLGuiTextures.Title.U, SLGuiTextures.Title.V,
            SLGuiTextures.Title.WIDTH, SLGuiTextures.Title.HEIGHT,
            SLGuiTextures.GUI_WIDTH, SLGuiTextures.GUI_HEIGHT);

        String visibleTitle = font.plainSubstrByWidth(titleText, WIDTH - 12);
        g.drawString(font, visibleTitle,
            tx + (WIDTH - font.width(visibleTitle)) / 2, ty + 10, COLOR, false);
    }

    public static boolean contains(double mouseX, double mouseY,
                                   int leftPos, int topPos, int backgroundWidth) {
        int x = getX(leftPos, backgroundWidth);
        int y = getY(topPos);
        return mouseX >= x && mouseX < x + WIDTH
            && mouseY >= y && mouseY < y + HEIGHT;
    }
}
