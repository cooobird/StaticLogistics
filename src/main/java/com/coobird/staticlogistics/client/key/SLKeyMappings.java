package com.coobird.staticlogistics.client.key;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;

/**
 * 键位映射。
 */
public class SLKeyMappings {
    /**
     * 蓝图预览移动键位映射
     * 用于在蓝图预览模式下移动蓝图
     * 默认键位：左Shift键
     */
    public static final KeyMapping BLUEPRINT_PREVIEW_MOVE =
        new KeyMapping("key.staticlogistics.blueprint_preview_move", InputConstants.KEY_LSHIFT, "key.categories.staticlogistics");

    /**
     * 蓝图预览旋转键位映射
     * 用于在蓝图预览模式下旋转蓝图
     * 默认键位：左Control键
     */
    public static final KeyMapping BLUEPRINT_PREVIEW_ROTATE =
        new KeyMapping("key.staticlogistics.blueprint_preview_rotate", InputConstants.KEY_LCONTROL, "key.categories.staticlogistics");

    /**
     * 蓝图预览Y轴移动键位映射
     * 用于在蓝图预览模式下沿Y轴移动蓝图
     * 默认键位：左Alt键
     */
    public static final KeyMapping BLUEPRINT_PREVIEW_MOVE_Y =
        new KeyMapping("key.staticlogistics.blueprint_preview_move_y", InputConstants.KEY_LALT, "key.categories.staticlogistics");

    /**
     * 清空已存储的节点键位映射
     * 用于清空所有已存储的节点或元素
     * 默认键位：C键
     */
    public static final KeyMapping CLEAR_STORED_NODES =
        new KeyMapping("key.staticlogistics.clear_stored_nodes", InputConstants.KEY_C, "key.categories.staticlogistics");
}
