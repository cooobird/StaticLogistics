package com.coobird.staticlogistics.client.render;

import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.content.menu.MenuLayout;
import net.minecraft.resources.ResourceLocation;

/**
 * {@code gui.png} 的语义化纹理区域。
 *
 * <p>调用方只能引用这里定义的区域，避免 atlas 调整后在各个界面中散落裸 UV。
 */
public final class SLGuiTextures {
    public static final ResourceLocation GUI_ATLAS =
        StaticLogistics.asResource("textures/gui/gui.png");
    public static final int GUI_WIDTH = 512;
    public static final int GUI_HEIGHT = 512;

    private SLGuiTextures() {
    }

    public static final class LinkConfigurator {
        public static final int U = 16;
        public static final int V = 32;
        public static final int WIDTH = 480;
        public static final int HEIGHT = 272;
        public static final int CONTENT_INSET = 1;
        public static final int CONTENT_WIDTH = 478;
        public static final int CONTENT_HEIGHT = 270;

        public static final int TITLE_U = 207;
        public static final int TITLE_V = 11;
        public static final int TITLE_WIDTH = 96;
        public static final int TITLE_HEIGHT = 28;
        public static final int TITLE_CONTENT_INSET = 1;
        public static final int TITLE_CONTENT_WIDTH = 94;
        public static final int TITLE_CONTENT_HEIGHT = 26;

        /**
         * 网络预览外框对应 atlas 的 UV(37,71)–(297,184)。
         * 主背景自身从 {@code leftPos - 1, topPos - 1} 开始绘制，因此这里保存
         * 换算后的屏幕相对坐标。
         */
        public static final int PREVIEW_FRAME_X = 20;
        public static final int PREVIEW_FRAME_Y = 38;
        public static final int PREVIEW_FRAME_WIDTH = 261;
        public static final int PREVIEW_FRAME_HEIGHT = 114;

        /**
         * 网络预览内部可绘制区对应 atlas 的 UV(39,73)–(295,182)。
         */
        public static final int PREVIEW_X = 22;
        public static final int PREVIEW_Y = 40;
        public static final int PREVIEW_WIDTH = 257;
        public static final int PREVIEW_HEIGHT = 110;

        public static final int GROUP_X = 315;
        public static final int GROUP_Y = 38;
        public static final int GROUP_WIDTH = 143;
        public static final int GROUP_HEIGHT = 59;

        public static final int CONNECTION_X = 315;
        public static final int CONNECTION_Y = 110;
        public static final int CONNECTION_WIDTH = 143;
        public static final int CONNECTION_HEIGHT = 47;

        public static final int NODE_CONFIG_X = 2;
        public static final int NODE_CONFIG_Y = 166;
        public static final int NODE_CONFIG_WIDTH = 283;
        public static final int NODE_CONFIG_HEIGHT = 101;

        public static final int INVENTORY_X = 300;
        public static final int INVENTORY_Y = 166;
        public static final int INVENTORY_WIDTH = 176;
        public static final int INVENTORY_HEIGHT = 104;

        private LinkConfigurator() {
        }
    }

    public static final class Background {
        public static final int U = 17;
        public static final int V = 308;
        public static final int WIDTH = MenuLayout.BACKGROUND_WIDTH;
        public static final int HEIGHT = MenuLayout.BACKGROUND_HEIGHT;

        private Background() {
        }
    }

    /**
     * 蓝图选择界面的完整背景。
     *
     * <p>搜索框、搜索图标、列表边框和滚动区域已经包含在该纹理中，
     * 调用方不得再通过拉伸通用背景模拟此界面。
     */
    public static final class BlueprintGroup {
        public static final int U = 277;
        public static final int V = 350;
        public static final int WIDTH = 99;
        public static final int HEIGHT = 125;

        public static final int SEARCH_X = 9;
        public static final int SEARCH_Y = 11;
        public static final int SEARCH_WIDTH = 62;
        public static final int SEARCH_HEIGHT = 8;

        public static final int LIST_X = 5;
        public static final int LIST_Y = 27;
        public static final int LIST_WIDTH = 80;
        public static final int LIST_HEIGHT = 84;

        public static final int SCROLLBAR_X = 88;
        public static final int SCROLLBAR_Y = 23;
        public static final int SCROLL_TRACK_HEIGHT = 88;

        private BlueprintGroup() {
        }
    }

    public static final class Inventory {
        public static final int U = 317;
        public static final int V = 198;
        public static final int WIDTH = MenuLayout.INVENTORY_WIDTH;
        public static final int HEIGHT = MenuLayout.INVENTORY_HEIGHT;

        public static final int SLOT_U = 270;
        public static final int SLOT_V = 305;
        public static final int SLOT_WIDTH = 18;
        public static final int SLOT_HEIGHT = 18;

        private Inventory() {
        }
    }

    /**
     * 节点配置区的过滤器与升级槽位。
     *
     * <p>Atlas 中的原始素材为 26×25，界面中统一缩放为菜单槽位使用的 18×18。
     */
    public static final class NodeSlot {
        public static final int U = 421;
        public static final int V = 350;
        public static final int SOURCE_WIDTH = 26;
        public static final int SOURCE_HEIGHT = 25;
        public static final int WIDTH = 18;
        public static final int HEIGHT = 18;

        private NodeSlot() {
        }
    }

    public static final class Button {
        public static final class Big {
            public static final int NORMAL_U = 290;
            public static final int NORMAL_V = 305;
            public static final int WIDTH = 19;
            public static final int HEIGHT = 18;
            public static final int SELECTED_U = 289;
            public static final int SELECTED_V = 325;
            public static final int SELECTED_WIDTH = 21;
            public static final int SELECTED_HEIGHT = 20;
            public static final int DISABLED_U = 312;
            public static final int DISABLED_V = 305;
            public static final int DISABLED_WIDTH = 19;
            public static final int DISABLED_HEIGHT = 18;

            private Big() {
            }
        }

        public static final class Middle {
            public static final int NORMAL_U = 334;
            public static final int NORMAL_V = 306;
            public static final int WIDTH = 19;
            public static final int HEIGHT = 17;
            public static final int SELECTED_U = 333;
            public static final int SELECTED_V = 326;
            public static final int SELECTED_WIDTH = 21;
            public static final int SELECTED_HEIGHT = 19;
            public static final int DISABLED_U = 356;
            public static final int DISABLED_V = 306;
            public static final int DISABLED_WIDTH = 19;
            public static final int DISABLED_HEIGHT = 17;

            private Middle() {
            }
        }

        public static final class Small {
            public static final int NORMAL_U = 378;
            public static final int NORMAL_V = 308;
            public static final int NORMAL_WIDTH = 19;
            public static final int NORMAL_HEIGHT = 15;
            public static final int SELECTED_U = 377;
            public static final int SELECTED_V = 328;
            public static final int SELECTED_WIDTH = 21;
            public static final int SELECTED_HEIGHT = 17;
            public static final int DISABLED_U = 400;
            public static final int DISABLED_V = 308;
            public static final int DISABLED_WIDTH = 19;
            public static final int DISABLED_HEIGHT = 15;

            private Small() {
            }
        }

        public static final class Push {
            public static final int U = 420;
            public static final int V = 305;
            public static final int WIDTH = 18;
            public static final int HEIGHT = 10;
            public static final int DISABLED_U = 420;
            public static final int DISABLED_V = 316;

            private Push() {
            }
        }

        private Button() {
        }
    }

    public static final class Scrollbar {
        public static final int ENABLED_U = 249;
        public static final int DISABLED_U = 260;
        public static final int V = 306;
        public static final int ENABLED_V = V;
        public static final int DISABLED_V = V;
        public static final int WIDTH = 8;
        public static final int HEIGHT = 15;
        public static final int TRACK_HEIGHT = 42;

        private Scrollbar() {
        }
    }

    public static final class EditBox {
        public static final int DEFAULT_U = 421;
        public static final int DEFAULT_V = 330;
        public static final int WIDTH = 37;
        public static final int HEIGHT = 10;

        private EditBox() {
        }
    }

    public static final class Title {
        public static final int U = LinkConfigurator.TITLE_U;
        public static final int V = LinkConfigurator.TITLE_V;
        public static final int WIDTH = LinkConfigurator.TITLE_WIDTH;
        public static final int HEIGHT = LinkConfigurator.TITLE_HEIGHT;
        public static final int CONTENT_INSET =
            LinkConfigurator.TITLE_CONTENT_INSET;
        public static final int CONTENT_WIDTH =
            LinkConfigurator.TITLE_CONTENT_WIDTH;
        public static final int CONTENT_HEIGHT =
            LinkConfigurator.TITLE_CONTENT_HEIGHT;

        private Title() {
        }
    }

    public static final class Icon {
        public static final int SELECTED_U = 378;
        public static final int NORMAL_U = 400;
        public static final int WRANCH_U = NORMAL_U;
        public static final int INPUT_V = 350;
        public static final int OUTPUT_V = 366;
        public static final int DISCONNECT_V = 382;
        public static final int CONFIG_V = 398;
        public static final int WRANCH_V = 446;
        public static final int WIDTH = 19;
        public static final int HEIGHT = 15;

        private Icon() {
        }
    }

    public static final class NbtIcon {
        public static final int WIDTH = 20;
        public static final int HEIGHT = 15;
        public static final int FULL_MATCH_ENABLED_U = 378;
        public static final int FULL_MATCH_ENABLED_V = 414;
        public static final int FULL_MATCH_DISABLED_U = 400;
        public static final int FULL_MATCH_DISABLED_V = 414;
        public static final int PART_MATCH_ENABLED_U = 378;
        public static final int PART_MATCH_ENABLED_V = 430;
        public static final int PART_MATCH_DISABLED_U = 400;
        public static final int PART_MATCH_DISABLED_V = 430;

        private NbtIcon() {
        }
    }

    public static final class DeleteTag {
        public static final int U = 378;
        public static final int V = 446;
        public static final int WIDTH = 20;
        public static final int HEIGHT = 15;

        private DeleteTag() {
        }
    }

    public static final class Operator {
        public static final int ADD_U = 460;
        public static final int ADD_V = 354;
        public static final int REDUCE_U = 460;
        public static final int REDUCE_V = 380;
        public static final int WIDTH = 12;
        public static final int HEIGHT = 12;

        private Operator() {
        }
    }

    /**
     * Atlas 中用于分页和折叠状态的方向图标。
     */
    public static final class Direction {
        public static final int ENABLED_V = 395;
        public static final int DISABLED_V = 405;

        public static final int LEFT_U = 461;
        public static final int RIGHT_U = 467;
        public static final int HORIZONTAL_WIDTH = 4;
        public static final int HORIZONTAL_HEIGHT = 8;

        public static final int DOWN_U = 471;
        public static final int DOWN_WIDTH = 8;
        public static final int DOWN_HEIGHT = 4;

        public static final int CONNECTION_RIGHT_U = 480;
        public static final int CONNECTION_LEFT_U = 490;
        public static final int CONNECTION_V = 396;
        public static final int CONNECTION_WIDTH = 8;
        public static final int CONNECTION_HEIGHT = 7;

        public static final int CONNECTION_BIDIRECTIONAL_U = 500;
        public static final int CONNECTION_BIDIRECTIONAL_V = 394;
        public static final int CONNECTION_BIDIRECTIONAL_WIDTH = 8;
        public static final int CONNECTION_BIDIRECTIONAL_HEIGHT = 9;

        private Direction() {
        }
    }

    public static final class List {
        public static final int WIDTH = 126;
        public static final int HEIGHT = 42;
        public static final int ITEM_H = 12;
        public static final int ITEM_HEIGHT = 12;

        private List() {
        }
    }

    public static final int SEARCH_ICON_WIDTH = 12;
    public static final int SEARCH_ICON_HEIGHT = 12;
}
