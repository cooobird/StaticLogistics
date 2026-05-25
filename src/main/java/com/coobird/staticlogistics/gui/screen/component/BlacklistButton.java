package com.coobird.staticlogistics.gui.screen.component;

import com.coobird.staticlogistics.gui.screen.texture.SLGuiTextures;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * 黑名单/白名单切换按钮。
 */
public class BlacklistButton {

    public static final int WIDTH = 60;
    public static final int HEIGHT = SLGuiTextures.Button.Big.DISABLED_HEIGHT;

    public static int getX(int leftPos, int topPos, int xOffset) {
        return leftPos + FilterGridWidget.START_X
            + (FilterGridWidget.GRID_COLS * FilterGridWidget.SLOT_SIZE - WIDTH) / 2 + xOffset;
    }

    public static int getY(int leftPos, int topPos) {
        return topPos + FilterGridWidget.START_Y
            + FilterGridWidget.GRID_ROWS * FilterGridWidget.SLOT_SIZE + 6;
    }

    public static void render(GuiGraphics g, Font font, int leftPos, int topPos,
                              int mx, int my, boolean isBlacklist, int xOffset) {
        int btnX = getX(leftPos, topPos, xOffset);
        int btnY = getY(leftPos, topPos);

        int u = SLGuiTextures.Button.Big.DISABLED_U;
        int v = SLGuiTextures.Button.Big.DISABLED_V;
        int bw = SLGuiTextures.Button.Big.DISABLED_WIDTH;

        g.blit(SLGuiTextures.GUI_ATLAS, btnX, btnY, u, v,
            2, HEIGHT, SLGuiTextures.GUI_WIDTH, SLGuiTextures.GUI_HEIGHT);
        g.blit(SLGuiTextures.GUI_ATLAS, btnX + WIDTH - 2, btnY, u + bw - 2, v,
            2, HEIGHT, SLGuiTextures.GUI_WIDTH, SLGuiTextures.GUI_HEIGHT);
        g.blit(SLGuiTextures.GUI_ATLAS, btnX + 2, btnY,
            WIDTH - 4, HEIGHT, u + 2, v, 1, HEIGHT,
            SLGuiTextures.GUI_WIDTH, SLGuiTextures.GUI_HEIGHT);

        String textKey = isBlacklist
            ? "gui.staticlogistics.blacklist_button"
            : "gui.staticlogistics.whitelist_button";
        String text = Component.translatable(textKey).getString();
        boolean hover = mx >= btnX && mx < btnX + WIDTH
            && my >= btnY && my < btnY + HEIGHT;
        int color = hover ? 0xFFFF55 : 0xCCCCCC;
        int textWidth = font.width(text);
        g.drawString(font, text,
            btnX + (WIDTH - textWidth) / 2,
            btnY + (HEIGHT - 12) / 2, color, false);
    }

    public static boolean isHovered(double mx, double my, int leftPos, int topPos,
                                    int xOffset) {
        int btnX = getX(leftPos, topPos, xOffset);
        int btnY = getY(leftPos, topPos);
        return mx >= btnX && mx < btnX + WIDTH
            && my >= btnY && my < btnY + HEIGHT;
    }
}
