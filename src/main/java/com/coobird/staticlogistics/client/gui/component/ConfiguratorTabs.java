package com.coobird.staticlogistics.client.gui.component;

import com.coobird.staticlogistics.client.data.ToolModeSession;
import com.coobird.staticlogistics.client.render.SLGuiTextures;
import com.coobird.staticlogistics.content.item.LinkConfiguratorItem;
import com.coobird.staticlogistics.content.item.ToolMode;
import com.coobird.staticlogistics.network.c2s.C2SUpdateToolTypesPayload;
import com.coobird.staticlogistics.transfer.LogisticsResource;
import com.coobird.staticlogistics.transfer.TransferRegistries;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static com.coobird.staticlogistics.client.gui.ConfiguratorLayout.Tabs.*;

/// 工具模式页签和向左展开的资源列表；不修改现有连接的节点配置。
public final class ConfiguratorTabs {
    // 页签的宽度。
    public static final int TAB_WIDTH = MIN_TAB_WIDTH;
    // 展开面板的宽度。
    public static final int FLYOUT_WIDTH = RESOURCE_FLYOUT_WIDTH;
    private final Supplier<ItemStack> tool;
    private final Consumer<ToolMode> selectMode;
    private final Runnable selectBlueprint;
    private final List<LogisticsResource<?>> types = new ArrayList<>(TransferRegistries.getAllActive());
    private final Set<ResourceLocation> selected = new LinkedHashSet<>();
    private boolean expanded, typesInitialized;
    private int left, top, visibleRows, scroll;

    public ConfiguratorTabs(Supplier<ItemStack> tool, Consumer<ToolMode> selectMode, Runnable selectBlueprint) {
        this.tool = tool;
        this.selectMode = selectMode;
        this.selectBlueprint = selectBlueprint;
    }

    public boolean isExpanded() {
        return expanded;
    }

    public void collapse() {
        expanded = false;
    }

    public int width() {
        return expanded ? maximumWidth() : tabWidth();
    }

    public int maximumWidth() {
        return tabWidth() + FLYOUT_WIDTH + FLYOUT_GAP;
    }

    private int tabWidth() {
        Font font = Minecraft.getInstance().font;
        int textWidth = font.width(Component.translatable("mode.staticlogistics.blueprint"));
        for (ToolMode mode : ToolMode.ORDER) textWidth = Math.max(textWidth, font.width(mode.getDisplayName()));
        return Math.max(TAB_WIDTH, TAB_TEXT_X + textWidth + TAB_TEXT_RIGHT_PADDING);
    }

    public void render(GuiGraphics g, Font font, int mainLeft, int mainTop, int height,
                       int mx, int my, boolean blueprint) {
        int tabWidth = tabWidth();
        left = mainLeft - tabWidth;
        top = mainTop + TAB_TOP;
        visibleRows = Math.clamp((height - FLYOUT_HEIGHT_RESERVE - FLYOUT_LIST_Y - FLYOUT_BOTTOM_PADDING) / ROW, 1, Math.max(1, types.size()));
        ToolMode mode = ToolModeSession.currentMode(tool.get());
        for (int index = 0; index < 5; index++) {
            int y = top + index * TAB_STEP;
            boolean active = index == 4 ? blueprint : !blueprint && ToolMode.ORDER.get(index) == mode;
            ConfiguratorStyle.button(g, left, y, tabWidth + TAB_DRAW_EXTRA_WIDTH, TAB_HEIGHT, active, inside(mx, my, left, y, tabWidth, TAB_HEIGHT));

            ToolMode entry = index == 4 ? null : ToolMode.ORDER.get(index);
            int v = index == 4 ? SLGuiTextures.Icon.BLUEPRINT_V : switch (entry) {
                case WRENCH -> SLGuiTextures.Icon.WRENCH_V;
                case LINK_AS_EXTRACT -> SLGuiTextures.Icon.OUTPUT_V;
                case LINK_AS_INSERT -> SLGuiTextures.Icon.INPUT_V;
                case REMOVE -> SLGuiTextures.Icon.DISCONNECT_V;
                case BLUEPRINT -> SLGuiTextures.Icon.BLUEPRINT_V;
            };
            new SLGuiTextures.Region(active ? SLGuiTextures.Icon.SELECTED_U : SLGuiTextures.Icon.NORMAL_U,
                v, SLGuiTextures.Icon.WIDTH, SLGuiTextures.Icon.HEIGHT).draw(g, left + TAB_ICON_X, y + TAB_ICON_Y);
            Component name = index == 4 ? Component.translatable("mode.staticlogistics.blueprint") : entry.getDisplayName();
            g.drawString(font, name, left + TAB_TEXT_X, y + TAB_TEXT_Y, ConfiguratorStyle.TEXT, false);
        }
        if (!expanded) return;
        int x = left - FLYOUT_WIDTH - FLYOUT_GAP, y = top + TAB_STEP;
        ConfiguratorStyle.panel(g, x, y, FLYOUT_WIDTH, flyoutHeight());
        g.drawString(font, Component.translatable("gui.staticlogistics.tabs.types"), x + FLYOUT_TITLE_X, y + FLYOUT_TITLE_Y, ConfiguratorStyle.ACCENT, false);
        for (int i = 0; i < visibleRows && i + scroll < types.size(); i++) {
            var type = types.get(i + scroll);
            int rowY = y + FLYOUT_LIST_Y + i * ROW;
            boolean checked = selected.contains(type.typeId());
            ConfiguratorStyle.button(g, x + FLYOUT_ROW_X, rowY, FLYOUT_WIDTH - FLYOUT_ROW_WIDTH_INSET, ROW - FLYOUT_ROW_HEIGHT_INSET, checked,
                inside(mx, my, x + FLYOUT_ROW_X, rowY, FLYOUT_WIDTH - FLYOUT_ROW_WIDTH_INSET, ROW - FLYOUT_ROW_HEIGHT_INSET));
            g.renderFakeItem(type.getIcon(), x + FLYOUT_ICON_X, rowY + FLYOUT_ICON_Y);
            g.drawString(font, font.plainSubstrByWidth(Component.translatable(type.translationKey()).getString(), FLYOUT_TEXT_WIDTH), x + FLYOUT_TEXT_X, rowY + (ROW - FLYOUT_ROW_HEIGHT_INSET - font.lineHeight) / 2 + FLYOUT_TEXT_Y_OFFSET, ConfiguratorStyle.TEXT, false);
            int checkboxX = x + FLYOUT_WIDTH - FLYOUT_CHECKBOX_RIGHT_INSET;
            int checkboxY = rowY + (ROW - FLYOUT_ROW_HEIGHT_INSET - SLGuiTextures.CONFIRM.height()) / 2 + FLYOUT_CHECKBOX_Y_OFFSET;
            if (checked) SLGuiTextures.CONFIRM.draw(g, checkboxX, checkboxY);
            else SLGuiTextures.BUTTON.frame(g, checkboxX, checkboxY,
                SLGuiTextures.CONFIRM.width(), SLGuiTextures.CONFIRM.height(), 3);
        }
        if (types.size() > visibleRows) {
            int track = visibleRows * ROW, thumb = SLGuiTextures.Scrollbar.HEIGHT;
            int thumbY = y + FLYOUT_LIST_Y + (track - thumb) * scroll / (types.size() - visibleRows);
            ConfiguratorStyle.scrollbar(g, x + FLYOUT_WIDTH - FLYOUT_SCROLL_RIGHT_INSET, y + FLYOUT_LIST_Y, track, thumbY - y - FLYOUT_LIST_Y, true);
        }
    }

    public boolean mouseClicked(double x, double y, int button) {
        if (button != 0) return false;
        if (inside(x, y, left, top, tabWidth(), 5 * TAB_STEP)) {
            int index = (int) (y - top) / TAB_STEP;
            if ((y - top) % TAB_STEP >= TAB_HEIGHT) return true;
            if (index == 4) {
                expanded = false;
                selectBlueprint.run();
            } else {
                ToolMode mode = ToolMode.ORDER.get(index);
                if (mode == ToolMode.LINK_AS_EXTRACT) {
                    if (!typesInitialized && tool.get().getItem() instanceof LinkConfiguratorItem item) {
                        selected.clear();
                        selected.addAll(item.getSettings(tool.get()).selectedTypeIds());
                        typesInitialized = true;
                    }
                    expanded = !expanded;
                } else expanded = false;
                selectMode.accept(mode);
            }
            SoundUtil.playClickSound();
            return true;
        }
        if (!expanded) return false;
        int popupX = left - FLYOUT_WIDTH - FLYOUT_GAP, popupY = top + TAB_STEP;
        if (inside(x, y, popupX, popupY, FLYOUT_WIDTH, flyoutHeight())) {
            int index = (int) (y - popupY - FLYOUT_LIST_Y) / ROW + scroll;
            if (y >= popupY + FLYOUT_LIST_Y && index >= scroll && index < Math.min(types.size(), scroll + visibleRows)) {
                ResourceLocation id = types.get(index).typeId();
                if (!selected.add(id)) selected.remove(id);
                PacketDistributor.sendToServer(new C2SUpdateToolTypesPayload(List.copyOf(selected)));
                SoundUtil.playClickSound();
            }
            return true;
        }
        /// 收起时吞掉本次点击，避免界面平移后误点节点或背包。
        expanded = false;
        return true;
    }

    public boolean mouseScrolled(double x, double y, double delta) {
        if (!expanded || !inside(x, y, left - FLYOUT_WIDTH - FLYOUT_GAP, top + TAB_STEP, FLYOUT_WIDTH, flyoutHeight()))
            return false;
        scroll = Math.clamp(scroll - (int) Math.signum(delta), 0, Math.max(0, types.size() - visibleRows));
        return true;
    }

    private int flyoutHeight() {
        return FLYOUT_LIST_Y + visibleRows * ROW + FLYOUT_BOTTOM_PADDING;
    }

    private static boolean inside(double x, double y, int left, int top, int width, int height) {
        return x >= left && x < left + width && y >= top && y < top + height;
    }
}
