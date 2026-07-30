package com.coobird.staticlogistics.client.gui.component;

import com.coobird.staticlogistics.client.render.SLGuiTextures;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * 节点配置区域使用的无状态基础控件。
 */
public final class NodeConfigControls {
    public static final int BTN = 12;
    public static final ResourceLocation ATLAS = SLGuiTextures.GUI_ATLAS;

    private NodeConfigControls() {
    }

    public static void renderToggle(GuiGraphics graphics, int x, int y, boolean enabled) {
        int u = enabled ? SLGuiTextures.Button.Push.U : SLGuiTextures.Button.Push.DISABLED_U;
        int v = enabled ? SLGuiTextures.Button.Push.V : SLGuiTextures.Button.Push.DISABLED_V;
        graphics.blit(ATLAS, x, y, u, v,
            SLGuiTextures.Button.Push.WIDTH, SLGuiTextures.Button.Push.HEIGHT,
            SLGuiTextures.GUI_WIDTH, SLGuiTextures.GUI_HEIGHT);
    }

    public static boolean hitToggle(double mouseX, double mouseY, int x, int y) {
        return mouseX >= x && mouseX < x + SLGuiTextures.Button.Push.WIDTH
            && mouseY >= y && mouseY < y + SLGuiTextures.Button.Push.HEIGHT;
    }

    /**
     * 绘制固定宽度的循环选择按钮，避免翻译文本改变区域布局。
     */
    public static void renderCycleBtn(
        GuiGraphics graphics,
        Font font,
        int x,
        int y,
        int width,
        Component label,
        boolean hovered
    ) {
        int height = SLGuiTextures.Button.Middle.HEIGHT;
        int u = hovered
            ? SLGuiTextures.Button.Middle.NORMAL_U
            : SLGuiTextures.Button.Middle.DISABLED_U;
        int v = hovered
            ? SLGuiTextures.Button.Middle.NORMAL_V
            : SLGuiTextures.Button.Middle.DISABLED_V;
        int sourceWidth = hovered
            ? SLGuiTextures.Button.Middle.WIDTH
            : SLGuiTextures.Button.Middle.DISABLED_WIDTH;
        int cap = 2;
        graphics.blit(ATLAS, x, y, u, v, cap, height,
            SLGuiTextures.GUI_WIDTH, SLGuiTextures.GUI_HEIGHT);
        graphics.blit(ATLAS, x + width - cap, y, u + sourceWidth - cap, v,
            cap, height, SLGuiTextures.GUI_WIDTH, SLGuiTextures.GUI_HEIGHT);
        graphics.blit(ATLAS, x + cap, y, width - cap * 2, height,
            u + cap, v, 1, height,
            SLGuiTextures.GUI_WIDTH, SLGuiTextures.GUI_HEIGHT);

        String text = font.plainSubstrByWidth(label.getString(), width - 8);
        graphics.drawString(font, text, x + (width - font.width(text)) / 2,
            y + (height - 8) / 2, 0xFFFFFFFF, false);
    }

    public static boolean hitCycleBtn(
        double mouseX,
        double mouseY,
        int x,
        int y,
        int width
    ) {
        return mouseX >= x && mouseX < x + width
            && mouseY >= y && mouseY < y + SLGuiTextures.Button.Middle.HEIGHT;
    }

    public static void renderOpBtn(
        GuiGraphics graphics,
        int x,
        int y,
        boolean plus,
        boolean hovered
    ) {
        int u = plus ? SLGuiTextures.Operator.ADD_U : SLGuiTextures.Operator.REDUCE_U;
        int v = plus ? SLGuiTextures.Operator.ADD_V : SLGuiTextures.Operator.REDUCE_V;
        graphics.blit(ATLAS, x, y, u, v, BTN, BTN,
            SLGuiTextures.GUI_WIDTH, SLGuiTextures.GUI_HEIGHT);
        if (hovered) {
            graphics.fill(x, y, x + BTN, y + BTN, 0x22FFFFFF);
        }
    }

    public static boolean hitOpBtn(double mouseX, double mouseY, int x, int y) {
        return mouseX >= x && mouseX < x + BTN
            && mouseY >= y && mouseY < y + BTN;
    }

    /**
     * 过滤器配置按钮固定放在过滤器槽位右侧。
     */
    public static void renderFilterCfgBtn(GuiGraphics graphics, int slotX, int slotY, boolean hovered) {
        int width = hovered
            ? SLGuiTextures.Button.Middle.SELECTED_WIDTH
            : SLGuiTextures.Button.Middle.DISABLED_WIDTH;
        int height = hovered
            ? SLGuiTextures.Button.Middle.SELECTED_HEIGHT
            : SLGuiTextures.Button.Middle.DISABLED_HEIGHT;
        int baseX = slotX + 18;
        int x = hovered ? baseX - 1 : baseX;
        int y = hovered ? slotY - 1 : slotY;
        int u = hovered
            ? SLGuiTextures.Button.Middle.SELECTED_U
            : SLGuiTextures.Button.Middle.DISABLED_U;
        int v = hovered
            ? SLGuiTextures.Button.Middle.SELECTED_V
            : SLGuiTextures.Button.Middle.DISABLED_V;
        graphics.blit(ATLAS, x, y, u, v, width, height,
            SLGuiTextures.GUI_WIDTH, SLGuiTextures.GUI_HEIGHT);
        int iconU = hovered
            ? SLGuiTextures.Icon.SELECTED_U
            : SLGuiTextures.Icon.NORMAL_U;
        graphics.blit(ATLAS,
            x + (width - SLGuiTextures.Icon.WIDTH) / 2,
            y + (height - SLGuiTextures.Icon.HEIGHT) / 2 - 1,
            iconU, SLGuiTextures.Icon.CONFIG_V,
            SLGuiTextures.Icon.WIDTH, SLGuiTextures.Icon.HEIGHT,
            SLGuiTextures.GUI_WIDTH, SLGuiTextures.GUI_HEIGHT);
    }

    public static boolean hitFilterCfgBtn(
        double mouseX,
        double mouseY,
        int slotX,
        int slotY
    ) {
        int x = slotX + 18;
        int y = slotY + 2;
        return mouseX >= x - 1
            && mouseX < x + SLGuiTextures.Button.Middle.DISABLED_WIDTH + 1
            && mouseY >= y - 1
            && mouseY < y + SLGuiTextures.Button.Middle.DISABLED_HEIGHT + 1;
    }

    public static void drawSlotBg(GuiGraphics graphics, int x, int y) {
        graphics.blit(
            ATLAS,
            x - 1,
            y - 1,
            SLGuiTextures.NodeSlot.WIDTH,
            SLGuiTextures.NodeSlot.HEIGHT,
            SLGuiTextures.NodeSlot.U,
            SLGuiTextures.NodeSlot.V,
            SLGuiTextures.NodeSlot.SOURCE_WIDTH,
            SLGuiTextures.NodeSlot.SOURCE_HEIGHT,
            SLGuiTextures.GUI_WIDTH,
            SLGuiTextures.GUI_HEIGHT
        );
    }
}
