package com.coobird.staticlogistics.gui.screen.component;

import com.coobird.staticlogistics.client.util.RenderConstants;
import com.coobird.staticlogistics.gui.screen.texture.SLGuiTextures;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * Face 配置界面的控件集合：开关按钮、颜色选择、策略/模式选择、过滤按钮、升级按钮。
 */
public class FaceControls {

    public static final int BTN_SIZE = 12;

    // 开关按钮

    public static void renderToggle(GuiGraphics g, Font font, int leftPos, int topPos,
                                    int x, int y, boolean enabled, ResourceLocation atlas) {
        int bx = leftPos + x;
        int by = topPos + y;
        int u = enabled ? SLGuiTextures.Button.Push.U : SLGuiTextures.Button.Push.DISABLED_U;
        int v = enabled ? SLGuiTextures.Button.Push.V : SLGuiTextures.Button.Push.DISABLED_V;
        g.blit(atlas, bx, by, u, v,
            SLGuiTextures.Button.Push.WIDTH, SLGuiTextures.Button.Push.HEIGHT,
            SLGuiTextures.GUI_WIDTH, SLGuiTextures.GUI_HEIGHT);
    }

    public static boolean isToggleHovered(double mx, double my, int leftPos, int topPos,
                                          int x, int y) {
        int bx = leftPos + x;
        int by = topPos + y;
        return mx >= bx && mx < bx + SLGuiTextures.Button.Push.WIDTH
            && my >= by && my < by + SLGuiTextures.Button.Push.HEIGHT;
    }

    // 颜色按钮

    public static void renderColor(GuiGraphics g, int leftPos, int topPos,
                                   int x, int y, int channel) {
        int bx = leftPos + x;
        int by = topPos + y;
        int colorIdx = (channel >= 1 && channel <= 16) ? channel - 1 : 0;
        g.fill(bx, by, bx + 14, by + 14, 0xFF000000);
        g.fill(bx + 1, by + 1, bx + 13, by + 13,
            (RenderConstants.DYE_COLORS[colorIdx] & 0xFFFFFF) | 0xFF000000);
    }

    public static boolean isColorHovered(double mx, double my, int leftPos, int topPos,
                                         int x, int y) {
        int bx = leftPos + x;
        int by = topPos + y;
        return mx >= bx && mx < bx + 14 && my >= by && my < by + 14;
    }

    // 操作按钮（+/-）

    public static void renderOperator(GuiGraphics g, int x, int y,
                                      int iconU, int iconV, int mx, int my,
                                      ResourceLocation atlas) {
        boolean hover = mx >= x && mx < x + BTN_SIZE && my >= y && my < y + BTN_SIZE;
        int bgU = SLGuiTextures.Button.Middle.DISABLED_U;
        int bgV = SLGuiTextures.Button.Middle.DISABLED_V;
        int bgW = SLGuiTextures.Button.Middle.WIDTH;
        int bgH = SLGuiTextures.Button.Middle.HEIGHT;
        g.blit(atlas, x, y, BTN_SIZE, BTN_SIZE, bgU, bgV, bgW, bgH,
            SLGuiTextures.GUI_WIDTH, SLGuiTextures.GUI_HEIGHT);
        g.blit(atlas, x, y, iconU, iconV, BTN_SIZE, BTN_SIZE,
            SLGuiTextures.GUI_WIDTH, SLGuiTextures.GUI_HEIGHT);
        if (hover) {
            g.fill(x, y, x + BTN_SIZE, y + BTN_SIZE, 0x22FFFFFF);
        }
    }

    // 策略/模式按钮

    public static void renderChoiceButton(GuiGraphics g, Font font,
                                          int leftPos, int topPos,
                                          int x, int y, Component label,
                                          int mx, int my, ResourceLocation atlas) {
        int textWidth = font.width(label);
        int totalWidth = Math.max(textWidth + 12, SLGuiTextures.Button.Middle.WIDTH);
        int bx = leftPos + x;
        int by = topPos + y;
        int height = SLGuiTextures.Button.Middle.HEIGHT;
        boolean hover = mx >= bx && mx < bx + totalWidth
            && my >= by && my < by + height;
        int u = hover ? 350 : 372;
        int v = 2;
        g.blit(atlas, bx, by, u, v, 2, height,
            SLGuiTextures.GUI_WIDTH, SLGuiTextures.GUI_HEIGHT);
        g.blit(atlas, bx + totalWidth - 2, by,
            u + SLGuiTextures.Button.Middle.WIDTH - 2, v,
            2, height, SLGuiTextures.GUI_WIDTH, SLGuiTextures.GUI_HEIGHT);
        g.blit(atlas, bx + 2, by, totalWidth - 4, height, u + 2, v, 1, height,
            SLGuiTextures.GUI_WIDTH, SLGuiTextures.GUI_HEIGHT);
        g.drawString(font, label, bx + (totalWidth - textWidth) / 2,
            by + (height - 8) / 2, 0xFFFFFFFF, false);
    }

    public static boolean isChoiceHovered(double mx, double my, int leftPos, int topPos,
                                          int x, int y, Component label,
                                          Font font) {
        int textWidth = font.width(label);
        int totalWidth = Math.max(textWidth + 12, SLGuiTextures.Button.Middle.WIDTH);
        int bx = leftPos + x;
        int by = topPos + y;
        int height = SLGuiTextures.Button.Middle.HEIGHT;
        return mx >= bx && mx < bx + totalWidth
            && my >= by && my < by + height;
    }

    // 过滤配置按钮

    public static void renderFilterConfigBtn(GuiGraphics g, int leftPos, int topPos,
                                             int slotX, int slotY, boolean hover,
                                             ResourceLocation atlas) {
        int btnWidth = SLGuiTextures.Button.Middle.SELECTED_WIDTH;
        int btnHeight = SLGuiTextures.Button.Middle.SELECTED_HEIGHT;
        int gap = 2;
        int btnX = leftPos + slotX + 16 + gap;
        int btnY = topPos + slotY + (16 - btnHeight) / 2;

        int bgU, bgV, bw, bh;
        if (hover) {
            bgU = SLGuiTextures.Button.Middle.SELECTED_U;
            bgV = SLGuiTextures.Button.Middle.SELECTED_V;
            bw = SLGuiTextures.Button.Middle.SELECTED_WIDTH;
            bh = SLGuiTextures.Button.Middle.SELECTED_HEIGHT;
        } else {
            bgU = SLGuiTextures.Button.Middle.DISABLED_U;
            bgV = SLGuiTextures.Button.Middle.DISABLED_V;
            bw = SLGuiTextures.Button.Middle.WIDTH;
            bh = SLGuiTextures.Button.Middle.HEIGHT;
        }
        g.blit(atlas, btnX, btnY, bgU, bgV, bw, bh,
            SLGuiTextures.GUI_WIDTH, SLGuiTextures.GUI_HEIGHT);

        int iconU = hover ? SLGuiTextures.Icon.SELECTED_U : SLGuiTextures.Icon.NORMAL_U;
        int iconV = SLGuiTextures.Icon.CONFIG_V;
        int iconW = 19, iconH = 15;
        g.blit(atlas, btnX + (bw - iconW) / 2, btnY + (bh - iconH) / 2 - 1,
            iconU, iconV, iconW, iconH,
            SLGuiTextures.GUI_WIDTH, SLGuiTextures.GUI_HEIGHT);
    }

    public static boolean isFilterConfigBtnHovered(double mx, double my,
                                                   int leftPos, int topPos,
                                                   int slotX, int slotY) {
        int btnWidth = SLGuiTextures.Button.Middle.SELECTED_WIDTH;
        int btnHeight = SLGuiTextures.Button.Middle.SELECTED_HEIGHT;
        int gap = 2;
        int btnX = leftPos + slotX + 16 + gap;
        int btnY = topPos + slotY + (16 - btnHeight) / 2;
        return mx >= btnX && mx < btnX + btnWidth
            && my >= btnY && my < btnY + btnHeight;
    }

    // 通用按钮

    public static void renderTextButton(GuiGraphics g, Font font,
                                        int leftPos, int topPos, int x, int y,
                                        Component label, boolean hover,
                                        ResourceLocation atlas) {
        int textW = font.width(label);
        int bw = Math.max(textW + 12, SLGuiTextures.Button.Middle.WIDTH);
        int bh = SLGuiTextures.Button.Middle.HEIGHT;
        int bx = leftPos + x, by = topPos + y;

        int u = hover ? SLGuiTextures.Button.Middle.SELECTED_U
            : SLGuiTextures.Button.Middle.DISABLED_U;
        int v = hover ? SLGuiTextures.Button.Middle.SELECTED_V
            : SLGuiTextures.Button.Middle.DISABLED_V;
        int srcW = hover ? SLGuiTextures.Button.Middle.SELECTED_WIDTH
            : SLGuiTextures.Button.Middle.WIDTH;
        int srcH = hover ? SLGuiTextures.Button.Middle.SELECTED_HEIGHT
            : SLGuiTextures.Button.Middle.HEIGHT;
        int capW = 2;

        g.blit(atlas, bx, by, u, v, capW, srcH,
            SLGuiTextures.GUI_WIDTH, SLGuiTextures.GUI_HEIGHT);
        g.blit(atlas, bx + bw - capW, by, u + srcW - capW, v, capW, srcH,
            SLGuiTextures.GUI_WIDTH, SLGuiTextures.GUI_HEIGHT);
        g.blit(atlas, bx + capW, by, bw - capW * 2, srcH, u + capW, v, 1, srcH,
            SLGuiTextures.GUI_WIDTH, SLGuiTextures.GUI_HEIGHT);
        g.drawCenteredString(font, label, bx + bw / 2, by + (srcH - 11) / 2, 0xFFFFFF);
    }

    public static boolean isTextButtonHovered(double mx, double my,
                                              int leftPos, int topPos,
                                              int x, int y, Component label,
                                              Font font) {
        int textW = font.width(label);
        int bw = Math.max(textW + 12, SLGuiTextures.Button.Middle.WIDTH);
        int bh = SLGuiTextures.Button.Middle.HEIGHT;
        int bx = leftPos + x;
        int by = topPos + y;
        return mx >= bx && mx < bx + bw && my >= by && my < by + bh;
    }
}
