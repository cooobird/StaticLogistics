package com.coobird.staticlogistics.gui.screen.component;

import com.coobird.staticlogistics.api.type.NbtMatchMode;
import com.coobird.staticlogistics.gui.screen.texture.SLGuiTextures;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * NBT 匹配模式控件：全匹配/部分匹配切换 + 忽略耐久按钮。
 */
public class NbtModeControls {

    private static final int X_OFFSET = 23;
    private static final int Y_OFFSET = 98;

    public static int getX(int leftPos) {
        return leftPos + X_OFFSET;
    }

    public static int getY(int topPos) {
        return topPos + Y_OFFSET;
    }

    public static void render(GuiGraphics g, Font font, int leftPos, int topPos,
                              int mx, int my, NbtMatchMode mode, boolean ignoreDamage) {
        int startX = getX(leftPos);
        int startY = getY(topPos);

        boolean isPartial = mode == NbtMatchMode.PARTIAL;
        int btnWidth = SLGuiTextures.Button.Middle.WIDTH;
        int btnHeight = SLGuiTextures.Button.Middle.HEIGHT;
        int u = SLGuiTextures.Button.Middle.DISABLED_U;
        int v = SLGuiTextures.Button.Middle.DISABLED_V;

        // 9-slice 背景
        g.blit(SLGuiTextures.GUI_ATLAS, startX, startY, u, v,
            2, btnHeight, SLGuiTextures.GUI_WIDTH, SLGuiTextures.GUI_HEIGHT);
        g.blit(SLGuiTextures.GUI_ATLAS, startX + btnWidth - 2, startY,
            u + SLGuiTextures.Button.Middle.WIDTH - 2, v,
            2, btnHeight, SLGuiTextures.GUI_WIDTH, SLGuiTextures.GUI_HEIGHT);
        g.blit(SLGuiTextures.GUI_ATLAS, startX + 2, startY,
            btnWidth - 4, btnHeight, u + 2, v, 1, btnHeight,
            SLGuiTextures.GUI_WIDTH, SLGuiTextures.GUI_HEIGHT);

        // 图标
        int iconW = SLGuiTextures.NbtIcon.WIDTH;
        int iconH = SLGuiTextures.NbtIcon.HEIGHT;
        int iconU, iconV;
        if (isPartial) {
            iconU = SLGuiTextures.NbtIcon.PART_MATCH_ENABLED_U;
            iconV = SLGuiTextures.NbtIcon.PART_MATCH_ENABLED_V;
        } else {
            iconU = SLGuiTextures.NbtIcon.FULL_MATCH_ENABLED_U;
            iconV = SLGuiTextures.NbtIcon.FULL_MATCH_ENABLED_V;
        }
        g.blit(SLGuiTextures.GUI_ATLAS, startX, startY,
            iconU, iconV, iconW, iconH,
            SLGuiTextures.GUI_WIDTH, SLGuiTextures.GUI_HEIGHT);

        // Tooltip
        if (mx >= startX && mx < startX + btnWidth
            && my >= startY && my < startY + btnHeight) {
            Component tooltip = Component.translatable(
                isPartial ? "gui.staticlogistics.part_match_button"
                    : "gui.staticlogistics.full_match_button");
            g.renderTooltip(font, tooltip, mx, my);
        }

        // 忽略耐久 checkbox
        int checkX = startX + btnWidth + 2;
        int checkW = SLGuiTextures.Button.Push.WIDTH;
        int checkH = SLGuiTextures.Button.Push.HEIGHT;
        int uCheck = ignoreDamage
            ? SLGuiTextures.Button.Push.U
            : SLGuiTextures.Button.Push.DISABLED_U;
        int vCheck = ignoreDamage
            ? SLGuiTextures.Button.Push.V
            : SLGuiTextures.Button.Push.DISABLED_V;
        g.blit(SLGuiTextures.GUI_ATLAS, checkX, startY, uCheck, vCheck,
            checkW, checkH, SLGuiTextures.GUI_WIDTH, SLGuiTextures.GUI_HEIGHT);
        String ignoreText = Component.translatable(
            "gui.staticlogistics.ignore_durability").getString();
        g.drawString(font, ignoreText, checkX, startY + 11, 0xCCCCCC, false);
    }

    public static boolean isModeBtnHovered(double mx, double my, int leftPos, int topPos) {
        int startX = getX(leftPos);
        int startY = getY(topPos);
        int btnWidth = SLGuiTextures.Button.Middle.WIDTH;
        int btnHeight = SLGuiTextures.Button.Middle.HEIGHT;
        return mx >= startX && mx < startX + btnWidth
            && my >= startY && my < startY + btnHeight;
    }

    public static boolean isIgnoreBtnHovered(double mx, double my, int leftPos, int topPos) {
        int startX = getX(leftPos);
        int startY = getY(topPos);
        int btnWidth = SLGuiTextures.Button.Middle.WIDTH;
        int checkX = startX + btnWidth + 2;
        int checkW = SLGuiTextures.Button.Push.WIDTH;
        int checkH = SLGuiTextures.Button.Push.HEIGHT;
        return mx >= checkX && mx < checkX + checkW
            && my >= startY && my < startY + checkH;
    }
}
