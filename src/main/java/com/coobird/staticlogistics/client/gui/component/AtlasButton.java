package com.coobird.staticlogistics.client.gui.component;

import com.coobird.staticlogistics.client.render.SLGuiTextures;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.function.BooleanSupplier;

/// 复用 atlas 按钮边框，保留原生按钮的输入和旁白行为。
public final class AtlasButton extends Button {
    private BooleanSupplier selected = () -> false;
    private boolean selectionButton;
    private boolean iconOnly;
    private boolean rateIcon;
    private int textInset = 6;
    private SLGuiTextures.Region icon;

    private AtlasButton(int x, int y, int width, int height, Component text, OnPress press) {
        super(x, y, width, height, text, press, DEFAULT_NARRATION);
    }

    public AtlasButton selection(BooleanSupplier selected) {
        this.selected = selected;
        selectionButton = true;
        return this;
    }

    public AtlasButton icon(SLGuiTextures.Region icon) {
        this.icon = icon;
        return this;
    }

    public AtlasButton iconOnly() {
        iconOnly = true;
        return this;
    }

    public AtlasButton rateIcon() {
        rateIcon = true;
        return this;
    }

    public AtlasButton textInset(int inset) {
        textInset = inset;
        return this;
    }

    /// 图标和文字共用按钮正面的中心线，不把底部阴影计入可视中心。
    public int contentY(int contentHeight) {
        return getY() + (height - 2 - contentHeight) / 2;
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (!iconOnly) ConfiguratorStyle.button(graphics, getX(), getY(), width, height,
            selectionButton && selected.getAsBoolean(), active && isHoveredOrFocused(), active);
        if (rateIcon) {
            int x = getX() + (width - 9) / 2;
            int y = contentY(9);
            int color = active ? ConfiguratorStyle.TEXT : ConfiguratorStyle.MUTED_TEXT;
            graphics.fill(x, y + 6, x + 2, y + 9, color);
            graphics.fill(x + 3, y + 3, x + 5, y + 9, color);
            graphics.fill(x + 6, y, x + 8, y + 9, color);
            return;
        }
        if (icon != null) {
            var displayedIcon = active && (isHoveredOrFocused() || selectionButton && selected.getAsBoolean())
                ? SLGuiTextures.activeIcon(icon) : icon;
            int iconY = iconOnly ? getY() + (height - icon.height()) / 2 : contentY(icon.height());
            displayedIcon.draw(graphics, getX() + (width - icon.width()) / 2, iconY);
            return;
        }
        var font = Minecraft.getInstance().font;
        String text = font.plainSubstrByWidth(getMessage().getString(), width - textInset - 3);
        int x = textInset > 6 ? getX() + textInset : getX() + (width - font.width(text)) / 2;
        graphics.drawString(font, text, x, contentY(font.lineHeight),
            active ? ConfiguratorStyle.TEXT : 0xFF686873, false);
    }

    public static AtlasBuilder atlasBuilder(Component text, OnPress press) {
        return new AtlasBuilder(text, press);
    }

    public static final class AtlasBuilder {
        private final Component text;
        private final OnPress press;
        private int x, y, width, height;

        private AtlasBuilder(Component text, OnPress press) {
            this.text = text;
            this.press = press;
        }

        public AtlasBuilder bounds(int x, int y, int width, int height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            return this;
        }

        public AtlasButton build() {
            return new AtlasButton(x, y, width, height, text, press);
        }
    }
}
