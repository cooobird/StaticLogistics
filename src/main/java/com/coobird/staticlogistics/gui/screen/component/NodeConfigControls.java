package com.coobird.staticlogistics.gui.screen.component;

import com.coobird.staticlogistics.client.util.RenderConstants;
import com.coobird.staticlogistics.gui.screen.texture.SLGuiTextures;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public class NodeConfigControls {

    public static final int BTN = 12;
    public static final ResourceLocation ATLAS = SLGuiTextures.GUI_ATLAS;

    // 开关按钮
    public static void renderToggle(GuiGraphics g, int absX, int absY, boolean on) {
        int u = on ? SLGuiTextures.Button.Push.U : SLGuiTextures.Button.Push.DISABLED_U;
        int v = on ? SLGuiTextures.Button.Push.V : SLGuiTextures.Button.Push.DISABLED_V;
        g.blit(ATLAS, absX, absY, u, v,
            SLGuiTextures.Button.Push.WIDTH, SLGuiTextures.Button.Push.HEIGHT,
            SLGuiTextures.GUI_WIDTH, SLGuiTextures.GUI_HEIGHT);
    }

    public static boolean hitToggle(double mx, double my, int absX, int absY) {
        return mx >= absX && mx < absX + SLGuiTextures.Button.Push.WIDTH
            && my >= absY && my < absY + SLGuiTextures.Button.Push.HEIGHT;
    }

    // 频道色块
    public static void renderChannel(GuiGraphics g, int absX, int absY, int ch) {
        int c = RenderConstants.DYE_COLORS[Math.clamp(ch - 1, 0, 15)];
        g.fill(absX, absY, absX + 14, absY + 14, 0xFF000000);
        g.fill(absX + 1, absY + 1, absX + 13, absY + 13, (c & 0xFFFFFF) | 0xFF000000);
    }

    public static boolean hitChannel(double mx, double my, int absX, int absY) {
        return mx >= absX && mx < absX + 14 && my >= absY && my < absY + 14;
    }

    // 九宫格选择按钮
    public static void renderCycleBtn(GuiGraphics g, Font font, int absX, int absY,
                                      Component label, boolean hover) {
        int textW = font.width(label);
        int totalW = Math.max(textW + 12, SLGuiTextures.Button.Middle.WIDTH);
        int h = SLGuiTextures.Button.Middle.HEIGHT;
        int u = hover ? SLGuiTextures.Button.Middle.NORMAL_U : SLGuiTextures.Button.Middle.DISABLED_U;
        int v = hover ? SLGuiTextures.Button.Middle.NORMAL_V : SLGuiTextures.Button.Middle.DISABLED_V;
        int sw = hover ? SLGuiTextures.Button.Middle.WIDTH : SLGuiTextures.Button.Middle.DISABLED_WIDTH;
        int sh = hover ? SLGuiTextures.Button.Middle.HEIGHT : SLGuiTextures.Button.Middle.DISABLED_HEIGHT;
        int cap = 2;
        g.blit(ATLAS, absX, absY, u, v, cap, h, SLGuiTextures.GUI_WIDTH, SLGuiTextures.GUI_HEIGHT);
        g.blit(ATLAS, absX + totalW - cap, absY, u + sw - cap, v, cap, h, SLGuiTextures.GUI_WIDTH, SLGuiTextures.GUI_HEIGHT);
        g.blit(ATLAS, absX + cap, absY, totalW - cap * 2, h, u + cap, v, 1, h, SLGuiTextures.GUI_WIDTH, SLGuiTextures.GUI_HEIGHT);
        g.drawString(font, label, absX + (totalW - textW) / 2, absY + (h - 8) / 2, 0xFFFFFFFF, false);
    }

    public static boolean hitCycleBtn(double mx, double my, int absX, int absY, Component label, Font font) {
        int totalW = Math.max(font.width(label) + 12, SLGuiTextures.Button.Middle.WIDTH);
        return mx >= absX && mx < absX + totalW && my >= absY && my < absY + SLGuiTextures.Button.Middle.HEIGHT;
    }

    // 加减按钮
    public static void renderOpBtn(GuiGraphics g, int absX, int absY, boolean plus, boolean hover) {
        int iu = plus ? SLGuiTextures.Operator.ADD_U : SLGuiTextures.Operator.REDUCE_U;
        int iv = plus ? SLGuiTextures.Operator.ADD_V : SLGuiTextures.Operator.REDUCE_V;
        g.blit(ATLAS, absX, absY, iu, iv, BTN, BTN,
            SLGuiTextures.GUI_WIDTH, SLGuiTextures.GUI_HEIGHT);
        if (hover) g.fill(absX, absY, absX + BTN, absY + BTN, 0x22FFFFFF);
    }

    public static boolean hitOpBtn(double mx, double my, int absX, int absY) {
        return mx >= absX && mx < absX + BTN && my >= absY && my < absY + BTN;
    }

    // 过滤配置按钮（渲染在槽位下方居中）
    public static void renderFilterCfgBtn(GuiGraphics g, int absSlotX, int absSlotY, boolean hover) {
        int bw = SLGuiTextures.Button.Middle.SELECTED_WIDTH;
        int bh = SLGuiTextures.Button.Middle.SELECTED_HEIGHT;
        int bx = absSlotX + (16 - bw) / 2;
        int by = absSlotY + 16 + 2;
        int u = hover ? SLGuiTextures.Button.Middle.SELECTED_U : SLGuiTextures.Button.Middle.DISABLED_U;
        int v = hover ? SLGuiTextures.Button.Middle.SELECTED_V : SLGuiTextures.Button.Middle.DISABLED_V;
        int sw = hover ? SLGuiTextures.Button.Middle.SELECTED_WIDTH : SLGuiTextures.Button.Middle.WIDTH;
        int sh = hover ? SLGuiTextures.Button.Middle.SELECTED_HEIGHT : SLGuiTextures.Button.Middle.HEIGHT;
        g.blit(ATLAS, bx, by, u, v, sw, sh, SLGuiTextures.GUI_WIDTH, SLGuiTextures.GUI_HEIGHT);
        int iu = hover ? SLGuiTextures.Icon.SELECTED_U : SLGuiTextures.Icon.NORMAL_U;
        g.blit(ATLAS, bx + (sw - 19) / 2, by + (sh - 15) / 2 - 1, iu, SLGuiTextures.Icon.CONFIG_V, 19, 15,
            SLGuiTextures.GUI_WIDTH, SLGuiTextures.GUI_HEIGHT);
    }

    public static boolean hitFilterCfgBtn(double mx, double my, int absSlotX, int absSlotY) {
        int bw = SLGuiTextures.Button.Middle.SELECTED_WIDTH;
        int bh = SLGuiTextures.Button.Middle.SELECTED_HEIGHT;
        int bx = absSlotX + (16 - bw) / 2;
        int by = absSlotY + 16 + 2;
        return mx >= bx && mx < bx + bw && my >= by && my < by + bh;
    }

    // 槽位背景
    public static void drawSlotBg(GuiGraphics g, int absX, int absY) {
        g.blit(ATLAS, absX, absY, 16, 16,
            SLGuiTextures.Upgrade.U, SLGuiTextures.Upgrade.V,
            SLGuiTextures.Upgrade.WIDTH, SLGuiTextures.Upgrade.HEIGHT,
            SLGuiTextures.GUI_WIDTH, SLGuiTextures.GUI_HEIGHT);
    }
}
