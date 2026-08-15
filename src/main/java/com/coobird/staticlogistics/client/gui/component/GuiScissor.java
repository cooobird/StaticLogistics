package com.coobird.staticlogistics.client.gui.component;

import net.minecraft.client.gui.GuiGraphics;

/**
 * 将虚拟界面坐标转换为原版裁剪栈使用的屏幕 GUI 坐标。
 */
public final class GuiScissor {
    private GuiScissor() {
    }

    public static void enable(
        GuiGraphics graphics,
        double interfaceScale,
        int left,
        int top,
        int right,
        int bottom
    ) {
        int scaledLeft = (int) Math.floor(left * interfaceScale);
        int scaledTop = (int) Math.floor(top * interfaceScale);
        int scaledRight = (int) Math.ceil(right * interfaceScale);
        int scaledBottom = (int) Math.ceil(bottom * interfaceScale);
        graphics.enableScissor(scaledLeft, scaledTop, scaledRight, scaledBottom);
    }
}
