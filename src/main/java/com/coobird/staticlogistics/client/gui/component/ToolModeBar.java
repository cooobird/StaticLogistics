package com.coobird.staticlogistics.client.gui.component;

import com.coobird.staticlogistics.client.render.SLGuiTextures;
import com.coobird.staticlogistics.content.SLKeyNames;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 顶部工具模式栏：横向显示四种连接器模式。
 */
public final class ToolModeBar {
    public static final int MODE_COUNT = 4;
    private static final int MODE_X = 8;
    private static final int MODE_Y = 7;
    private static final int MODE_STEP = 22;

    private ToolModeBar() {
    }

    public static void render(GuiGraphics g, Font font, int leftPos, int topPos, int modeIdx) {
        for (int i = 0; i < MODE_COUNT; i++) {
            int rx = leftPos + MODE_X + (i * MODE_STEP);
            int ry = topPos + MODE_Y;
            boolean sel = (i == modeIdx);
            int bw = sel ? SLGuiTextures.Button.Middle.SELECTED_WIDTH : SLGuiTextures.Button.Middle.WIDTH;
            int bh = sel ? SLGuiTextures.Button.Middle.SELECTED_HEIGHT : SLGuiTextures.Button.Middle.HEIGHT;
            int bx = sel ? rx - 1 : rx;
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
                    default -> SLGuiTextures.Icon.DISCONNECT_V;
                };
            }
            g.blit(SLGuiTextures.GUI_ATLAS, bx + (bw - 19) / 2, by + (bh - 13) / 2 - 2, iconU, iconV, 19, 15, SLGuiTextures.GUI_WIDTH, SLGuiTextures.GUI_HEIGHT);
        }
    }

    /**
     * 返回鼠标命中的模式索引；未命中时返回 {@code -1}。
     */
    public static int getClickedMode(double mx, double my, int leftPos, int topPos) {
        for (int i = 0; i < MODE_COUNT; i++) {
            if (isModeHit(mx, my, leftPos, topPos, i)) {
                return i;
            }
        }
        return -1;
    }

    private static boolean isModeHit(double mx, double my, int leftPos, int topPos, int index) {
        int x = leftPos + MODE_X + index * MODE_STEP - 1;
        int y = topPos + MODE_Y - 1;
        return mx >= x && mx < x + SLGuiTextures.Button.Middle.SELECTED_WIDTH
            && my >= y && my < y + SLGuiTextures.Button.Middle.SELECTED_HEIGHT;
    }

    public static void renderTooltip(GuiGraphics g, Font font, int mx, int my,
                                     int leftPos, int topPos) {
        for (int i = 0; i < MODE_COUNT; i++) {
            if (isModeHit(mx, my, leftPos, topPos, i)) {
                List<Component> tooltip = new ArrayList<>();
                String key = switch (i) {
                    case 0 -> "mode.staticlogistics.wrench";
                    case 1 -> "mode.staticlogistics.link_as_input";
                    case 2 -> "mode.staticlogistics.link_as_output";
                    default -> "mode.staticlogistics.remove";
                };
                tooltip.add(Component.translatable(key).withStyle(ChatFormatting.YELLOW));
                tooltip.add(Component.translatable(key + ".desc",
                        Component.keybind(SLKeyNames.TOOL_MODE_SCROLL))
                    .withStyle(ChatFormatting.GRAY));
                g.renderComponentTooltip(font, tooltip, mx, my);
                return;
            }
        }
    }
}
