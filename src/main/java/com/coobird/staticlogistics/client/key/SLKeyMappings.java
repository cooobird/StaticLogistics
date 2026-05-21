package com.coobird.staticlogistics.client.key;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;

/**
 * 蓝图预览键位映射。
 */
public class SLKeyMappings {
    public static final KeyMapping BLUEPRINT_PREVIEW_MOVE =
        new KeyMapping("key.staticlogistics.blueprint_preview_move", InputConstants.KEY_LSHIFT, "key.categories.staticlogistics");

    public static final KeyMapping BLUEPRINT_PREVIEW_ROTATE =
        new KeyMapping("key.staticlogistics.blueprint_preview_rotate", InputConstants.KEY_LCONTROL, "key.categories.staticlogistics");

    public static final KeyMapping BLUEPRINT_PREVIEW_MOVE_Y =
        new KeyMapping("key.staticlogistics.blueprint_preview_move_y", InputConstants.KEY_LALT, "key.categories.staticlogistics");
}
