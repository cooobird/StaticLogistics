package com.coobird.staticlogistics.client.gui.component;

import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;

import java.util.List;

/// 外层 Screen 的控件路由：保留原始屏幕坐标，不进入内嵌容器的槽位处理。
public final class ScreenWidgetLayer implements ContainerEventHandler {
    private final ContainerEventHandler screen;

    public ScreenWidgetLayer(ContainerEventHandler screen) {
        this.screen = screen;
    }

    @Override
    public List<? extends GuiEventListener> children() {
        return screen.children();
    }

    @Override
    public GuiEventListener getFocused() {
        return screen.getFocused();
    }

    @Override
    public void setFocused(GuiEventListener focused) {
        screen.setFocused(focused);
    }

    @Override
    public boolean isDragging() {
        return screen.isDragging();
    }

    @Override
    public void setDragging(boolean dragging) {
        screen.setDragging(dragging);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        setDragging(false);
        GuiEventListener focused = getFocused();
        return focused != null && focused.mouseReleased(mouseX, mouseY, button);
    }
}
