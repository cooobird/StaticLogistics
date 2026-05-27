package com.coobird.staticlogistics.gui.screen.component;

import com.coobird.staticlogistics.gui.screen.texture.SLGuiTextures;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 左侧边栏组件：渲染模式选择标签页及其图标。
 */
public class LeftSidebar {

    public static final int MODE_COUNT = 5;

    // ---- 渲染 ----

    public static void render(GuiGraphics g, Font font, int leftPos, int topPos, int modeIdx) {
        for (int i = 0; i < MODE_COUNT; i++) {
            int ry = topPos + 13 + (i * 19);
            boolean sel = (i == modeIdx);
            int bw = sel ? SLGuiTextures.Button.Middle.SELECTED_WIDTH : SLGuiTextures.Button.Middle.WIDTH;
            int bh = sel ? SLGuiTextures.Button.Middle.SELECTED_HEIGHT : SLGuiTextures.Button.Middle.HEIGHT;
            int bx = leftPos - bw;
            int by = sel ? ry - 1 : ry;

            g.blit(SLGuiTextures.GUI_ATLAS, bx, by,
                sel ? SLGuiTextures.Button.Middle.SELECTED_U : SLGuiTextures.Button.Middle.DISABLED_U,
                sel ? SLGuiTextures.Button.Middle.SELECTED_V : SLGuiTextures.Button.Middle.DISABLED_V,
                bw, bh, SLGuiTextures.GUI_WIDTH, SLGuiTextures.GUI_HEIGHT);

            int iconU, iconV;
            if (i == 0) {
                iconU = SLGuiTextures.Icon.WRANCH_U;
                iconV = SLGuiTextures.Icon.WRANCH_V;
            } else {
                iconU = sel ? SLGuiTextures.Icon.SELECTED_U : SLGuiTextures.Icon.NORMAL_U;
                iconV = switch (i) {
                    case 1 -> SLGuiTextures.Icon.INPUT_V;
                    case 2 -> SLGuiTextures.Icon.OUTPUT_V;
                    case 3 -> SLGuiTextures.Icon.DISCONNECT_V;
                    default -> SLGuiTextures.Icon.CONFIG_V;
                };
            }
            g.blit(SLGuiTextures.GUI_ATLAS,
                bx + (bw - 19) / 2, by + (bh - 15) / 2 - 1,
                iconU, iconV, 19, 15,
                SLGuiTextures.GUI_WIDTH, SLGuiTextures.GUI_HEIGHT);
        }
    }

    // ---- 点击检测 ----

    /**
     * 检测鼠标是否点击了某个模式标签，返回索引（0..MODE_COUNT-1），未命中返回 -1。
     */
    public static int getClickedMode(double mx, double my, int leftPos, int topPos, int modeIdx) {
        if (mx < leftPos - 25 || mx >= leftPos) return -1;
        for (int i = 0; i < MODE_COUNT; i++) {
            int ry = topPos + 7 + (i * 19);
            boolean isSel = (i == modeIdx);
            int bh = isSel ? SLGuiTextures.Button.Middle.SELECTED_HEIGHT : SLGuiTextures.Button.Middle.HEIGHT;
            int by = isSel ? ry - 1 : ry;
            if (my >= by && my < by + bh) {
                return i;
            }
        }
        return -1;
    }

    // ---- Tooltip ----

    public static void renderTooltip(GuiGraphics g, Font font, int mx, int my,
                                     int leftPos, int topPos) {
        for (int i = 0; i < MODE_COUNT; i++) {
            int ry = topPos + 13 + (i * 19);
            int bw = SLGuiTextures.Button.Middle.WIDTH;
            int bh = SLGuiTextures.Button.Middle.HEIGHT;
            if (mx >= leftPos - 25 + bw - 26 && mx < leftPos && my >= ry - 1 && my < ry - 1 + bh) {
                List<Component> tooltip = new ArrayList<>();
                String key = switch (i) {
                    case 0 -> "mode.staticlogistics.wrench";
                    case 1 -> "mode.staticlogistics.link_as_input";
                    case 2 -> "mode.staticlogistics.link_as_output";
                    case 3 -> "mode.staticlogistics.remove";
                    default -> "mode.staticlogistics.node_config";
                };
                tooltip.add(Component.translatable(key).withStyle(ChatFormatting.YELLOW));
                tooltip.add(Component.translatable(key + ".desc").withStyle(ChatFormatting.GRAY));
                g.renderComponentTooltip(font, tooltip, mx, my);
                return;
            }
        }
    }
}
