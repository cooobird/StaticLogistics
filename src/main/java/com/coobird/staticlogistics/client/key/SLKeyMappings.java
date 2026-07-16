package com.coobird.staticlogistics.client.key;

import com.coobird.staticlogistics.content.SLKeyNames;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.settings.IKeyConflictContext;
import net.minecraftforge.client.settings.KeyModifier;
import org.lwjgl.glfw.GLFW;

/**
 * 客户端键位映射的唯一注册入口。
 */
public final class SLKeyMappings {
    public static final KeyMapping BLUEPRINT_PREVIEW_MOVE = heldKey(
        SLKeyNames.BLUEPRINT_PREVIEW_MOVE, SLKeyConflictContexts.BLUEPRINT_IN_GAME, GLFW.GLFW_KEY_LEFT_SHIFT);
    public static final KeyMapping BLUEPRINT_PREVIEW_ROTATE = heldKey(
        SLKeyNames.BLUEPRINT_PREVIEW_ROTATE, SLKeyConflictContexts.BLUEPRINT_IN_GAME, GLFW.GLFW_KEY_LEFT_CONTROL);
    public static final KeyMapping BLUEPRINT_PREVIEW_MOVE_Y = heldKey(
        SLKeyNames.BLUEPRINT_PREVIEW_MOVE_Y, SLKeyConflictContexts.BLUEPRINT_IN_GAME, GLFW.GLFW_KEY_LEFT_ALT);
    public static final KeyMapping TOOL_MODE_SCROLL = heldKey(
        SLKeyNames.TOOL_MODE_SCROLL, SLKeyConflictContexts.LINK_CONFIGURATOR_IN_GAME, GLFW.GLFW_KEY_LEFT_SHIFT);

    public static final KeyMapping CLEAR_STORED_NODES = modifiedKey(
        SLKeyNames.CLEAR_STORED_NODES, SLKeyConflictContexts.LINK_CONFIGURATOR_IN_GAME,
        KeyModifier.SHIFT, GLFW.GLFW_KEY_C);
    public static final KeyMapping BLUEPRINT_UNDO = modifiedKey(
        SLKeyNames.BLUEPRINT_UNDO, SLKeyConflictContexts.BLUEPRINT_IN_GAME,
        KeyModifier.CONTROL, GLFW.GLFW_KEY_Z);

    public static final KeyMapping QUICK_FILTER_MARK = heldKey(
        SLKeyNames.QUICK_FILTER_MARK, SLKeyConflictContexts.FILTER_GUI, GLFW.GLFW_KEY_LEFT_SHIFT);
    public static final KeyMapping PRIORITY_X10 = heldKey(
        SLKeyNames.PRIORITY_X10, SLKeyConflictContexts.NODE_CONFIGURATOR_GUI, GLFW.GLFW_KEY_LEFT_SHIFT);
    public static final KeyMapping PRIORITY_X5 = heldKey(
        SLKeyNames.PRIORITY_X5, SLKeyConflictContexts.NODE_CONFIGURATOR_GUI, GLFW.GLFW_KEY_LEFT_CONTROL);
    public static final KeyMapping GROUP_DETAILS_AND_EXPORT = heldKey(
        SLKeyNames.GROUP_DETAILS_AND_EXPORT, SLKeyConflictContexts.GROUP_SCREEN, GLFW.GLFW_KEY_LEFT_SHIFT);

    private SLKeyMappings() {
    }

    private static KeyMapping heldKey(String name, IKeyConflictContext context, int keyCode) {
        return modifiedKey(name, context, KeyModifier.NONE, keyCode);
    }

    private static KeyMapping modifiedKey(
        String name,
        IKeyConflictContext context,
        KeyModifier modifier,
        int keyCode
    ) {
        return new KeyMapping(
            name,
            context,
            modifier,
            InputConstants.Type.KEYSYM.getOrCreate(keyCode),
            SLKeyNames.CATEGORY
        );
    }

    /**
     * 普通界面打开时 Minecraft 不会维护 KeyMapping 的按下状态，因此直接读取实际输入设备。
     */
    public static boolean isGuiKeyDown(KeyMapping mapping) {
        if (mapping.isUnbound() || !mapping.getKeyConflictContext().isActive()) return false;

        InputConstants.Key key = mapping.getKey();
        long window = Minecraft.getInstance().getWindow().getWindow();
        boolean keyDown;
        if (key.getType() == InputConstants.Type.MOUSE) {
            keyDown = GLFW.glfwGetMouseButton(window, key.getValue()) == GLFW.GLFW_PRESS;
        } else if (key.getType() == InputConstants.Type.KEYSYM) {
            keyDown = InputConstants.isKeyDown(window, key.getValue());
        } else {
            keyDown = isScanCodeDown(window, key.getValue());
        }
        if (!keyDown) return false;

        // Shift、Ctrl、Alt 本身作为主键时，不应再被 NONE 修饰键规则反向排除。
        return KeyModifier.isKeyCodeModifier(key)
            || mapping.getKeyModifier().isActive(mapping.getKeyConflictContext());
    }

    private static boolean isScanCodeDown(long window, int scanCode) {
        for (int keyCode = GLFW.GLFW_KEY_SPACE; keyCode <= GLFW.GLFW_KEY_LAST; keyCode++) {
            if (GLFW.glfwGetKeyScancode(keyCode) == scanCode
                && InputConstants.isKeyDown(window, keyCode)) {
                return true;
            }
        }
        return false;
    }
}
