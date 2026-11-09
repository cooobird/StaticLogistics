package com.coobird.staticlogistics.client.gui.component;

import net.minecraft.util.Mth;

/// 只处理标题栏拖动；窗口负责同步移动控件，内容区域保留各自的鼠标行为。
public final class WindowDrag {
    private boolean active;
    private double offsetX, offsetY;

    public boolean begin(double mouseX, double mouseY, int button, int x, int y, int titleX, int titleY, int titleWidth, int titleHeight) {
        active = button == 0 && mouseX >= titleX && mouseX < titleX + titleWidth && mouseY >= titleY && mouseY < titleY + titleHeight;
        if (active) {
            offsetX = mouseX - x;
            offsetY = mouseY - y;
        }
        return active;
    }

    public boolean active(int button) {
        return active && button == 0;
    }

    /// 返回指定坐标范围内的位置；边界由调用方按完整窗口尺寸计算。
    public int boundedX(double mouseX, int minimum, int maximum) {
        return Mth.clamp((int) Math.round(mouseX - offsetX), minimum, Math.max(minimum, maximum));
    }

    public int boundedY(double mouseY, int minimum, int maximum) {
        return Mth.clamp((int) Math.round(mouseY - offsetY), minimum, Math.max(minimum, maximum));
    }

    /// viewportOrigin 表示视口左上角在当前窗口坐标系中的位置。
    public int viewportX(double mouseX, int width, int viewportOrigin, int viewportWidth) {
        return boundedX(mouseX, viewportOrigin, viewportOrigin + Math.max(0, viewportWidth - width));
    }

    /// 打开窗口或视口尺寸变化时使用，与拖动共用完整窗口的边界规则。
    public static int constrain(int position, int size, int viewportOrigin, int viewportSize) {
        return Mth.clamp(position, viewportOrigin, viewportOrigin + Math.max(0, viewportSize - size));
    }

    public int viewportY(double mouseY, int height, int viewportOrigin, int viewportHeight) {
        return boundedY(mouseY, viewportOrigin, viewportOrigin + Math.max(0, viewportHeight - height));
    }

    public int x(double mouseX, int width, int viewportWidth) {
        return viewportX(mouseX, width, 0, viewportWidth);
    }

    public int y(double mouseY, int height, int viewportHeight, int topInset) {
        return boundedY(mouseY, topInset, Math.max(topInset, viewportHeight - height));
    }

    public boolean release() {
        boolean wasActive = active;
        active = false;
        return wasActive;
    }
}
