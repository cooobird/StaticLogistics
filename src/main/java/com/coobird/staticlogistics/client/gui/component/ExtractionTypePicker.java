package com.coobird.staticlogistics.client.gui.component;

import com.coobird.staticlogistics.client.render.SLGuiTextures;
import com.coobird.staticlogistics.transfer.LogisticsResource;
import com.coobird.staticlogistics.transfer.TransferRegistries;
import com.coobird.staticlogistics.transfer.TransferTypeSelection;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static com.coobird.staticlogistics.client.gui.ConfiguratorLayout.Tabs.*;

/// 配置器页签和快捷界面共用的提取类型列表。
public final class ExtractionTypePicker {
    private final List<LogisticsResource<?>> types = List.copyOf(TransferRegistries.getAllActive());
    private final Set<ResourceLocation> selected;
    private int x, y, visibleRows, scroll;
    private int columns = 1;

    public ExtractionTypePicker(List<ResourceLocation> selected) {
        this.selected = new LinkedHashSet<>(selected);
    }

    public void resize(int availableHeight, int maxColumns) {
        columns = Mth.clamp(types.size(), 1, maxColumns);
        visibleRows = Mth.clamp((availableHeight - FLYOUT_LIST_Y - FLYOUT_BOTTOM_PADDING) / ROW,
            1, Math.max(1, (types.size() + columns - 1) / columns));
        scroll = Math.min(scroll, maxScroll());
    }

    public void setPosition(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public int height() {
        return FLYOUT_LIST_Y + visibleRows * ROW + FLYOUT_BOTTOM_PADDING;
    }

    public int width() {
        return RESOURCE_FLYOUT_WIDTH + (columns - 1) * columnStride();
    }

    private int columnStride() {
        return RESOURCE_FLYOUT_WIDTH - FLYOUT_ROW_X;
    }

    private int maxScroll() {
        return Math.max(0, (types.size() + columns - 1) / columns - visibleRows);
    }

    public List<ResourceLocation> selectedTypes() {
        return List.copyOf(selected);
    }

    public void render(GuiGraphics g, Font font, int mx, int my) {
        for (int i = 0; i < visibleRows * columns && i + scroll * columns < types.size(); i++) {
            var type = types.get(i + scroll * columns);
            int columnX = x + i % columns * columnStride();
            int rowY = y + FLYOUT_LIST_Y + i / columns * ROW;
            boolean checked = selected.contains(type.typeId());
            ConfiguratorStyle.button(g, columnX + FLYOUT_ROW_X, rowY, RESOURCE_FLYOUT_WIDTH - FLYOUT_ROW_WIDTH_INSET, ROW - FLYOUT_ROW_HEIGHT_INSET, checked,
                inside(mx, my, columnX + FLYOUT_ROW_X, rowY, RESOURCE_FLYOUT_WIDTH - FLYOUT_ROW_WIDTH_INSET, ROW - FLYOUT_ROW_HEIGHT_INSET));
            g.renderFakeItem(type.getIcon(), columnX + FLYOUT_ICON_X, rowY + FLYOUT_ICON_Y);
            ConfiguratorStyle.fittedLabel(g, font, Component.translatable(type.translationKey()),
                columnX + FLYOUT_TEXT_X, rowY, FLYOUT_TEXT_WIDTH, ROW - FLYOUT_ROW_HEIGHT_INSET,
                ConfiguratorStyle.TEXT, false);
            int checkboxX = columnX + RESOURCE_FLYOUT_WIDTH - FLYOUT_CHECKBOX_RIGHT_INSET;
            int checkboxY = rowY + (ROW - FLYOUT_ROW_HEIGHT_INSET - SLGuiTextures.CONFIRM.height()) / 2 + FLYOUT_CHECKBOX_Y_OFFSET;
            if (checked) SLGuiTextures.CONFIRM.draw(g, checkboxX, checkboxY);
            else SLGuiTextures.BUTTON.frame(g, checkboxX, checkboxY,
                SLGuiTextures.CONFIRM.width(), SLGuiTextures.CONFIRM.height(), 3);
        }
        if (maxScroll() > 0) {
            int track = visibleRows * ROW, thumb = SLGuiTextures.SCROLL_THUMB.height();
            int thumbOffset = (track - thumb) * scroll / maxScroll();
            ConfiguratorStyle.scrollbar(g, x + width() - FLYOUT_SCROLL_RIGHT_INSET, y + FLYOUT_LIST_Y, track, thumbOffset, true);
        }
    }

    public boolean contains(double mx, double my) {
        return inside(mx, my, x, y, width(), height());
    }

    public boolean toggleAt(double mx, double my, int button) {
        if (button != 0 || !contains(mx, my)) return false;
        int listY = y + FLYOUT_LIST_Y;
        if (my < listY || mx < x + FLYOUT_ROW_X) return false;
        int row = (int) (my - listY) / ROW;
        int column = (int) (mx - x - FLYOUT_ROW_X) / columnStride();
        int index = (scroll + row) * columns + column;
        if (row >= visibleRows || column >= columns || index >= types.size()
            || !inside(mx, my, x + column * columnStride() + FLYOUT_ROW_X, listY + row * ROW,
            RESOURCE_FLYOUT_WIDTH - FLYOUT_ROW_WIDTH_INSET, ROW - FLYOUT_ROW_HEIGHT_INSET)) return false;
        ResourceLocation id = types.get(index).typeId();
        if (!selected.remove(id)) {
            if (selected.size() >= TransferTypeSelection.MAX_SELECTED_TYPES) return false;
            selected.add(id);
        }
        SoundUtil.playClickSound();
        return true;
    }

    public boolean mouseScrolled(double mx, double my, double delta) {
        if (!contains(mx, my)) return false;
        scroll = Mth.clamp(scroll - (int) Math.signum(delta), 0, maxScroll());
        return true;
    }

    private static boolean inside(double x, double y, int left, int top, int width, int height) {
        return x >= left && x < left + width && y >= top && y < top + height;
    }
}
