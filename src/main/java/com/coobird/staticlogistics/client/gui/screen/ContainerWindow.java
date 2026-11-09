package com.coobird.staticlogistics.client.gui.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;

/// 内嵌容器视图，不注册为游戏 Screen，也不触发外部 Screen.Init 控件注入。
/// 仅持有本模组显式添加的控件；复用原版槽位点击、拖拽分配和快捷键逻辑。
public abstract class ContainerWindow<T extends AbstractContainerMenu> extends AbstractContainerScreen<T> {
    protected ContainerWindow(T menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    public final void layout(Minecraft client, int width, int height, int left, int top,
                             int contentWidth, int contentHeight) {
        this.minecraft = client;
        this.font = client.font;
        this.width = width;
        this.height = height;
        this.leftPos = left;
        this.topPos = top;
        this.imageWidth = contentWidth;
        this.imageHeight = contentHeight;
        clearWidgets();
        setFocused(null);
        setDragging(false);
        hoveredSlot = null;
    }

    public final <W extends AbstractWidget> W addControl(W control) {
        return addRenderableWidget(control);
    }

    public final void renderItemTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBg(graphics, partialTick, mouseX, mouseY);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
    }
}
