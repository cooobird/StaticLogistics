package com.coobird.staticlogistics.gui.screen.texture;

import com.coobird.staticlogistics.Staticlogistics;
import net.minecraft.resources.ResourceLocation;

public class SLGuiTextures {
    public static final ResourceLocation GUI_ATLAS = Staticlogistics.asResource("textures/gui/gui.png");

    public static final int GUI_WIDTH = 512, GUI_HEIGHT = 512;

    public static final class Background {
        public static final int U = 0, V = 0;
        public static final int WIDTH = 210, HEIGHT = 127;
        public static final int BY_GROUP_WIDTH = 99, BY_GROUP_HEIGHT = 127;
    }

    public static final class Inventory {
        public static final int U = 0, V = 272;
        public static final int WIDTH = 176, HEIGHT = 94;
        public static final int SLOT_U = 286, SLOT_V = 1;
        public static final int SLOT_WIDTH = 18, SLOT_HEIGHT = 18;
    }

    public static final class Button {
        public static final class Big {
            public static final int NORMAL_U = 306, NORMAL_V = 1, WIDTH = 19, HEIGHT = 18;
            public static final int SELECTED_U = 305, SELECTED_V = 21, SELECTED_WIDTH = 21, SELECTED_HEIGHT = 20;
            public static final int DISABLED_U = 328, DISABLED_V = 1, DISABLED_WIDTH = 19, DISABLED_HEIGHT = 18;
        }

        public static final class Middle {
            public static final int NORMAL_U = 350, NORMAL_V = 2, WIDTH = 19, HEIGHT = 17;
            public static final int SELECTED_U = 349, SELECTED_V = 22, SELECTED_WIDTH = 21, SELECTED_HEIGHT = 19;
            public static final int DISABLED_U = 372, DISABLED_V = 2, DISABLED_WIDTH = 19, DISABLED_HEIGHT = 17;
        }

        public static final class Small {
            public static final int NORMAL_WIDTH = 19, NORMAL_HEIGHT = 15;
            public static final int SELECTED_WIDTH = 21, SELECTED_HEIGHT = 17;
            public static final int DISABLED_WIDTH = 19, DISABLED_HEIGHT = 15;
        }

        public static final class Push {
            public static final int U = 436, V = 1, WIDTH = 18, HEIGHT = 10;
            public static final int DISABLED_U = 436, DISABLED_V = 12;
        }
    }

    public static final class Scrollbar {
        public static final int DISABLED_U = 113, DISABLED_V = 170;
        public static final int ENABLED_U = 102, ENABLED_V = 170, ENABLED_WIDTH = 8, ENABLED_HEIGHT = 15;
        public static final int TRACK_HEIGHT = 95;
    }

    public static final class EditBox {
        public static final int DEFAULT_U = 437, DEFAULT_V = 24, WIDTH = 37, HEIGHT = 10;
    }

    public static final class Title {
        public static final int U = 416, V = 4;
    }

    public static final class Icon {
        public static final int SELECTED_U = 394, NORMAL_U = 416;
        public static final int WRANCH_U = 416, WRANCH_V = 170, INPUT_V = 74, OUTPUT_V = 90, DISCONNECT_V = 106, CONFIG_V = 122;
    }

    public static final class List {
        public static final int WIDTH = 81, HEIGHT = 66, ITEM_H = 12;
    }

    public static final class Upgrade {
        public static final int U = 437, V = 46, WIDTH = 26, HEIGHT = 25;
    }


    public static final class NbtIcon {
        public static final int WIDTH = 19, HEIGHT = 15;
        public static final int FULL_MATCH_ENABLED_U = 394, FULL_MATCH_ENABLED_V = 138;
        public static final int FULL_MATCH_DISABLED_U = 416, FULL_MATCH_DISABLED_V = 138;
        public static final int PART_MATCH_ENABLED_U = 394, PART_MATCH_ENABLED_V = 154;
        public static final int PART_MATCH_DISABLED_U = 416, PART_MATCH_DISABLED_V = 154;
    }

    public static final class DeleteTag {
        public static final int U = 394, V = 170, WIDTH = 19, HEIGHT = 15;
    }

    public static final class Operator {
        public static final int ADD_U = 476, ADD_V = 50, WIDTH = 12, HEIGHT = 12;
        public static final int REDUCE_U = 476, REDUCE_V = 76;
    }

    public static final int ZOOM_WIDTH = 12, ZOOM_HEIGHT = 12;
}