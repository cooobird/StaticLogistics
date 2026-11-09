package com.coobird.staticlogistics.client.gui.component;

import net.minecraft.client.gui.GuiGraphics;

/// 将虚拟界面坐标转换为原版裁剪栈使用的屏幕 GUI 坐标。
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
        var pose = graphics.pose().last().pose();
        int scaledLeft = (int) Math.floor(left * interfaceScale + pose.m30());
        int scaledTop = (int) Math.floor(top * interfaceScale + pose.m31());
        int scaledRight = (int) Math.ceil(right * interfaceScale + pose.m30());
        int scaledBottom = (int) Math.ceil(bottom * interfaceScale + pose.m31());
        graphics.flush();
        graphics.enableScissor(scaledLeft, scaledTop, scaledRight, scaledBottom);
    }
}
