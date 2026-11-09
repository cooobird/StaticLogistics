package com.coobird.staticlogistics.client.render;

import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.client.gui.ConfiguratorLayout;
import com.coobird.staticlogistics.content.menu.MenuLayout;
import com.mojang.math.Axis;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/// Atlas 区域不包含美术标注的红框；布局尺寸与素材尺寸分开。
public final class SLGuiTextures {
    public static final ResourceLocation GUI_ATLAS = StaticLogistics.asResource("textures/gui/gui.png");
    public static final int GUI_WIDTH = 512, GUI_HEIGHT = 512;

    // 窗口、物品栏和输入框。
    public static final Region WINDOW = new Region(0, 0, 480, 278);
    public static final Region PANEL = new Region(0, 280, 210, 125);
    public static final Region PLAYER_INVENTORY = new Region(212, 280, 166, 82);
    public static final Region FILTER_GRID = new Region(214, 366, 162, 72);
    public static final Region FILTER_COLUMN = new Region(214, 366, 18, 72);
    public static final Region FILTER_SLOT = new Region(214, 366, 18, 18);
    public static final Region INPUT = new Region(124, 450, 37, 10);
    public static final Region INPUT_FOCUSED = new Region(124, 461, 37, 10);

    // 按钮背景按尺寸和交互状态分类。
    public static final Region BUTTON = new Region(70, 407, 19, 18);
    public static final Region CHECKED_BUTTON = new Region(48, 407, 19, 18);
    public static final Region BUTTON_HOVER = new Region(69, 427, 21, 20);
    public static final Region CHECKED_HOVER = new Region(47, 427, 21, 20);
    public static final Region BUTTON_MIDDLE = new Region(114, 408, 19, 17);
    public static final Region CHECKED_MIDDLE = new Region(92, 408, 19, 17);
    public static final Region BUTTON_MIDDLE_HOVER = new Region(113, 428, 21, 19);
    public static final Region CHECKED_MIDDLE_HOVER = new Region(91, 428, 21, 19);
    public static final Region BUTTON_SMALL = new Region(158, 410, 19, 15);
    public static final Region CHECKED_SMALL = new Region(136, 410, 19, 15);
    public static final Region BUTTON_SMALL_HOVER = new Region(157, 430, 21, 17);
    public static final Region CHECKED_SMALL_HOVER = new Region(135, 430, 21, 17);
    // 按钮图标及其选中状态。
    public static final Region SEARCH = new Region(180, 410, 12, 12);
    public static final Region CONFIRM = new Region(180, 423, 12, 12);
    public static final Region ADD = new Region(180, 436, 12, 12);
    public static final Region CLOSE = new Region(180, 449, 12, 12);
    public static final Region REDUCE = new Region(180, 462, 12, 12);
    public static final Region SEARCH_SELECTED = new Region(193, 410, 12, 12);
    public static final Region CONFIRM_SELECTED = new Region(193, 423, 12, 12);
    public static final Region ADD_SELECTED = new Region(193, 436, 12, 12);
    public static final Region CLOSE_SELECTED = new Region(193, 449, 12, 12);
    public static final Region REDUCE_SELECTED = new Region(193, 462, 12, 12);
    // 过滤器、箭头、分组和列表素材。
    public static final Region FILTER_MATCH = new Region(98, 473, 34, 10);
    public static final Region FILTER_FUZZY = new Region(98, 483, 34, 10);
    public static final Region FILTER_INVERT = new Region(98, 493, 34, 10);
    public static final Region FILTER_WHITELIST = new Region(164, 495, 8, 12);
    public static final Region FILTER_BLACKLIST = new Region(181, 495, 8, 12);
    public static final Region ARROW_DOWN = new Region(58, 474, 8, 5);
    public static final Region DISCLOSURE_RIGHT = new Region(53, 474, 4, 8);
    public static final Region ARROW_RIGHT = new Region(67, 476, 8, 8);
    public static final Region ARROW_LEFT = new Region(77, 476, 8, 8);
    public static final Region SEPARATOR = new Region(1, 283, 1, 1);
    public static final Region DIRECTORY = new Region(194, 493, 16, 16);
    public static final Region DIRECTORY_SELECTED = DIRECTORY;
    public static final Region LIST_SURFACE = new Region(379, 280, 81, 101);
    public static final Region SCROLL_TRACK = new Region(462, 280, 10, 101);
    public static final Region SCROLL_THUMB = new Region(427, 382, 8, 15);
    public static final Region SCROLL_THUMB_IDLE = new Region(418, 382, 8, 15);
    public static final Region NODE_SLOT = new Region(210, 443, 24, 24);
    public static final Region NODE_SLOT_SELECTED = new Region(210, 469, 24, 23);
    public static final Region WHITE = new Region(2, 1, 1, 1);
    public static final int SEARCH_ICON_WIDTH = SEARCH.width(), SEARCH_ICON_HEIGHT = SEARCH.height();

    private SLGuiTextures() {
    }

    public static Region activeIcon(Region icon) {
        if (icon.equals(SEARCH)) return SEARCH_SELECTED;
        if (icon.equals(CONFIRM)) return CONFIRM_SELECTED;
        if (icon.equals(ADD)) return ADD_SELECTED;
        if (icon.equals(CLOSE)) return CLOSE_SELECTED;
        if (icon.equals(REDUCE)) return REDUCE_SELECTED;
        return icon;
    }

    public record Region(int u, int v, int width, int height) {
        public void draw(GuiGraphics g, int x, int y) {
            g.blit(GUI_ATLAS, x, y, u, v, width, height, GUI_WIDTH, GUI_HEIGHT);
        }

        public void surface(GuiGraphics g, int x, int y, int w, int h) {
            g.blit(GUI_ATLAS, x, y, w, h, u, v, width, height, GUI_WIDTH, GUI_HEIGHT);
        }

        public void upsideDown(GuiGraphics g, int x, int y) {
            g.pose().pushPose();
            g.pose().translate(x + width, y + height, 0);
            g.pose().mulPose(Axis.ZP.rotationDegrees(180));
            draw(g, 0, 0);
            g.pose().popPose();
        }

        /// 保持四角的像素尺寸，只拉伸边线与填充。
        public void frame(GuiGraphics g, int x, int y, int w, int h, int border) {

            for (int row = 0; row < 3; row++) {
                int sourceY = row == 0 ? v : row == 1 ? v + border : v + height - border;
                int sourceH = row == 1 ? height - border * 2 : border;
                int targetY = row == 0 ? y : row == 1 ? y + border : y + h - border;
                int targetH = row == 1 ? h - border * 2 : border;
                for (int col = 0; col < 3; col++) {
                    int sourceX = col == 0 ? u : col == 1 ? u + border : u + width - border;
                    int sourceW = col == 1 ? width - border * 2 : border;
                    int targetX = col == 0 ? x : col == 1 ? x + border : x + w - border;
                    int targetW = col == 1 ? w - border * 2 : border;
                    g.blit(GUI_ATLAS, targetX, targetY, targetW, targetH,
                        sourceX, sourceY, sourceW, sourceH, GUI_WIDTH, GUI_HEIGHT);
                }
            }
        }
    }

    // 以下为界面布局尺寸，不是 atlas 裁剪区域。
    public static final class LinkConfigurator {
        public static final int GROUP_X = ConfiguratorLayout.SharedScreen.GROUP_X;
        public static final int WIDTH = ConfiguratorLayout.SharedScreen.WIDTH, HEIGHT = ConfiguratorLayout.SharedScreen.HEIGHT, CONTENT_INSET = ConfiguratorLayout.SharedScreen.CONTENT_INSET;
        public static final int CONTENT_WIDTH = ConfiguratorLayout.SharedScreen.CONTENT_WIDTH, CONTENT_HEIGHT = ConfiguratorLayout.SharedScreen.CONTENT_HEIGHT;
        public static final int GROUP_CONTENT_X = ConfiguratorLayout.SharedScreen.GROUP_CONTENT_X, GROUP_CONTENT_Y = ConfiguratorLayout.SharedScreen.GROUP_CONTENT_Y;
        public static final int GROUP_CONTENT_WIDTH = ConfiguratorLayout.SharedScreen.GROUP_CONTENT_WIDTH, GROUP_CONTENT_HEIGHT = ConfiguratorLayout.SharedScreen.GROUP_CONTENT_HEIGHT;
        public static final int NODE_CONFIG_Y = ConfiguratorLayout.SharedScreen.NODE_CONFIG_Y, NODE_CONFIG_HEIGHT = ConfiguratorLayout.SharedScreen.NODE_CONFIG_HEIGHT;
        public static final int INVENTORY_X = ConfiguratorLayout.SharedScreen.INVENTORY_X, INVENTORY_Y = ConfiguratorLayout.SharedScreen.INVENTORY_Y;
    }

    public static final class Background {
        public static final int WIDTH = MenuLayout.BACKGROUND_WIDTH, HEIGHT = MenuLayout.BACKGROUND_HEIGHT;
    }

    public static final class Inventory {
        public static final int WIDTH = MenuLayout.INVENTORY_WIDTH, HEIGHT = MenuLayout.INVENTORY_HEIGHT;
    }

    public static final class Scrollbar {
        public static final int WIDTH = SCROLL_TRACK.width(), HEIGHT = SCROLL_THUMB.height();
    }

    public static final class Icon {
        public static final int SELECTED_U = 3, NORMAL_U = 25;
        public static final int INPUT_V = 429, OUTPUT_V = 445, DISCONNECT_V = 461, WRENCH_V = 477, BLUEPRINT_V = 493;
        public static final int WIDTH = 19, HEIGHT = 14;
    }

}
