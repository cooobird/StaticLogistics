package com.coobird.staticlogistics.client.gui.component;

import com.coobird.staticlogistics.client.render.SLGuiTextures;
import net.minecraft.client.gui.GuiGraphics;

/**
 * 使用网络预览原始边框纹理拼接任意尺寸的预览面板。
 */
public final class NetworkPreviewFrame {
    private static final int SOURCE_U = 37;
    private static final int SOURCE_V = 71;
    private static final int SOURCE_WIDTH = 261;
    private static final int SOURCE_HEIGHT = 114;
    private static final int BORDER = 2;

    private NetworkPreviewFrame() {
    }

    public static void render(GuiGraphics graphics, int x, int y, int width, int height) {
        int middleWidth = width - BORDER * 2;
        int middleHeight = height - BORDER * 2;
        blit(graphics, x, y, BORDER, BORDER, SOURCE_U, SOURCE_V, BORDER, BORDER);
        blit(graphics, x + width - BORDER, y, BORDER, BORDER,
            SOURCE_U + SOURCE_WIDTH - BORDER, SOURCE_V, BORDER, BORDER);
        blit(graphics, x, y + height - BORDER, BORDER, BORDER,
            SOURCE_U, SOURCE_V + SOURCE_HEIGHT - BORDER, BORDER, BORDER);
        blit(graphics, x + width - BORDER, y + height - BORDER, BORDER, BORDER,
            SOURCE_U + SOURCE_WIDTH - BORDER, SOURCE_V + SOURCE_HEIGHT - BORDER,
            BORDER, BORDER);
        blit(graphics, x + BORDER, y, middleWidth, BORDER,
            SOURCE_U + BORDER, SOURCE_V, 1, BORDER);
        blit(graphics, x + BORDER, y + height - BORDER, middleWidth, BORDER,
            SOURCE_U + BORDER, SOURCE_V + SOURCE_HEIGHT - BORDER, 1, BORDER);
        blit(graphics, x, y + BORDER, BORDER, middleHeight,
            SOURCE_U, SOURCE_V + BORDER, BORDER, 1);
        blit(graphics, x + width - BORDER, y + BORDER, BORDER, middleHeight,
            SOURCE_U + SOURCE_WIDTH - BORDER, SOURCE_V + BORDER, BORDER, 1);
        blit(graphics, x + BORDER, y + BORDER, middleWidth, middleHeight,
            SOURCE_U + BORDER, SOURCE_V + BORDER, 1, 1);
    }

    private static void blit(GuiGraphics graphics, int x, int y, int width, int height,
                             int u, int v, int sourceWidth, int sourceHeight) {
        graphics.blit(SLGuiTextures.GUI_ATLAS, x, y, width, height,
            u, v, sourceWidth, sourceHeight,
            SLGuiTextures.GUI_WIDTH, SLGuiTextures.GUI_HEIGHT);
    }
}
