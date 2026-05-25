package com.coobird.staticlogistics.gui.screen.component;

import com.coobird.staticlogistics.gui.screen.texture.SLGuiTextures;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * 标题栏组件（9-slice Button.Small 拼接）。
 */
public class TitleBar {

    public static final int WIDTH = 110;
    public static final int COLOR = 0x98FB98;

    public static void render(GuiGraphics g, Font font, int leftPos, int topPos,
                              int backgroundWidth, String titleText) {
        int tw = WIDTH;
        int tx = leftPos + (backgroundWidth - tw) / 2;
        int ty = topPos - 8;

        g.blit(SLGuiTextures.GUI_ATLAS, tx + tw - 2, ty,
            SLGuiTextures.Title.U + SLGuiTextures.Button.Small.DISABLED_WIDTH - 2,
            SLGuiTextures.Title.V, 2, SLGuiTextures.Button.Small.DISABLED_HEIGHT,
            SLGuiTextures.GUI_WIDTH, SLGuiTextures.GUI_HEIGHT);
        g.blit(SLGuiTextures.GUI_ATLAS, tx, ty,
            SLGuiTextures.Title.U, SLGuiTextures.Title.V,
            2, SLGuiTextures.Button.Small.DISABLED_HEIGHT,
            SLGuiTextures.GUI_WIDTH, SLGuiTextures.GUI_HEIGHT);
        g.blit(SLGuiTextures.GUI_ATLAS, tx + 2, ty,
            tw - 4, SLGuiTextures.Button.Small.DISABLED_HEIGHT,
            SLGuiTextures.Title.U + 2, SLGuiTextures.Title.V,
            1, SLGuiTextures.Button.Small.DISABLED_HEIGHT,
            SLGuiTextures.GUI_WIDTH, SLGuiTextures.GUI_HEIGHT);

        int textWidth = font.width(titleText);
        g.drawString(font, titleText,
            tx + (tw - textWidth) / 2, ty + 4, COLOR, false);
    }
}
