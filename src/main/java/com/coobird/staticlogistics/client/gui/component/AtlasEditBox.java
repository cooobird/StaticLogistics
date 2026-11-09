package com.coobird.staticlogistics.client.gui.component;

import com.coobird.staticlogistics.client.render.SLGuiTextures;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

import static com.coobird.staticlogistics.client.gui.ConfiguratorLayout.EditBoxStyle.*;

/// 保留原生输入框行为，只替换输入框表面。
public class AtlasEditBox extends EditBox {
    private int rightInset;

    public void setRightInset(int inset) {
        rightInset = inset;
    }

    private boolean bordered = true;

    public AtlasEditBox(Font font, int x, int y, int width, int height, Component message) {
        super(font, x, y, width, height, message);
        setTextColor(ConfiguratorStyle.TEXT);
        setTextColorUneditable(0xFFC2BCAC);
    }

    @Override
    public void setBordered(boolean bordered) {
        this.bordered = bordered;
        super.setBordered(bordered);
    }

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        boolean bordered = this.bordered;
        int x = getX(), y = getY(), width = getWidth();
        var surface = isFocused() ? SLGuiTextures.INPUT_FOCUSED : SLGuiTextures.INPUT;
        setTextColor(isFocused() ? ConfiguratorStyle.TEXT : 0xFFF4F0E8);
        surface.frame(graphics, bordered ? x : x - FRAME_OUTSET, bordered ? y : y - FRAME_OUTSET,
            bordered ? width : width + 2 * FRAME_OUTSET, bordered ? getHeight() : getHeight() + 2 * FRAME_OUTSET, FRAME_BORDER);
        setBordered(false);
        if (bordered) {
            setX(x + TEXT_HORIZONTAL_PADDING);
            setY(y + (getHeight() - TEXT_BASE_HEIGHT) / 2);
            setWidth(width - 2 * TEXT_HORIZONTAL_PADDING - rightInset);
        } else {
            setWidth(width - rightInset);
        }
        super.renderWidget(graphics, mouseX, mouseY, partialTick);
        setX(x);
        setY(y);
        setWidth(width);
        setBordered(bordered);
    }
}
