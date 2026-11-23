package com.coobird.staticlogistics.client.gui.component;

import com.coobird.staticlogistics.client.data.ToolModeSession;
import com.coobird.staticlogistics.client.render.SLGuiTextures;
import com.coobird.staticlogistics.content.item.LinkConfiguratorItem;
import com.coobird.staticlogistics.content.item.ToolMode;
import com.coobird.staticlogistics.network.c2s.C2SUpdateToolTypesPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static com.coobird.staticlogistics.client.gui.ConfiguratorLayout.Tabs.*;

/// 工具模式页签和向左展开的资源列表；不修改现有连接的节点配置。
public final class ConfiguratorTabs {
    private final Supplier<ItemStack> tool;
    private final Consumer<ToolMode> selectMode;
    private final Runnable selectBlueprint;
    private ExtractionTypePicker picker;
    private boolean expanded;
    private int left, top;

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

    private int tabWidth() {
        Font font = Minecraft.getInstance().font;
        int textWidth = font.width(Component.translatable("mode.staticlogistics.blueprint"));
        for (ToolMode mode : ToolMode.ORDER) textWidth = Math.max(textWidth, font.width(mode.getDisplayName()));
        return Math.clamp(TAB_TEXT_X + textWidth + TAB_TEXT_RIGHT_PADDING, MIN_TAB_WIDTH, 63);
    }

    public void render(GuiGraphics g, Font font, int mainLeft, int mainTop, int height,
                       int mx, int my, boolean blueprint) {
        int tabWidth = tabWidth();
        left = mainLeft + 7;
        top = mainTop + 2;
        ToolMode mode = ToolModeSession.currentMode(tool.get());
        for (int index = 0; index < 5; index++) {
            int x = left + index * (tabWidth + TAB_GAP);
            int y = top;
            boolean active = index == 4 ? blueprint : !blueprint && ToolMode.ORDER.get(index) == mode;
            ConfiguratorStyle.button(g, x, y, tabWidth, TAB_HEIGHT, active, inside(mx, my, x, y, tabWidth, TAB_HEIGHT));

            ToolMode entry = index == 4 ? null : ToolMode.ORDER.get(index);
            int v = index == 4 ? SLGuiTextures.Icon.BLUEPRINT_V : switch (entry) {
                case WRENCH -> SLGuiTextures.Icon.WRENCH_V;
                case LINK_AS_EXTRACT -> SLGuiTextures.Icon.OUTPUT_V;
                case LINK_AS_INSERT -> SLGuiTextures.Icon.INPUT_V;
                case REMOVE -> SLGuiTextures.Icon.DISCONNECT_V;
                case BLUEPRINT -> SLGuiTextures.Icon.BLUEPRINT_V;
            };
            new SLGuiTextures.Region(active ? SLGuiTextures.Icon.SELECTED_U : SLGuiTextures.Icon.NORMAL_U,
                v, SLGuiTextures.Icon.WIDTH, SLGuiTextures.Icon.HEIGHT).draw(g, x + TAB_ICON_X, y + TAB_ICON_Y);
            Component name = index == 4 ? Component.translatable("mode.staticlogistics.blueprint") : entry.getDisplayName();
            ConfiguratorStyle.fittedLabel(g, font, name, x + TAB_TEXT_X, y,
                tabWidth - TAB_TEXT_X - TAB_TEXT_RIGHT_PADDING, TAB_HEIGHT, ConfiguratorStyle.TEXT, false);
        }
        if (!expanded) return;
        int x = left + tabWidth() + TAB_GAP, y = top + TAB_STEP;
        picker.resize(height - FLYOUT_HEIGHT_RESERVE, 1);
        picker.setPosition(x, y);
        ConfiguratorStyle.panel(g, x, y, picker.width(), picker.height());
        g.drawString(font, Component.translatable("gui.staticlogistics.tabs.types"),
            x + FLYOUT_TITLE_X, y + FLYOUT_TITLE_Y, ConfiguratorStyle.ACCENT, false);
        picker.render(g, font, mx, my);
    }

    public boolean mouseClicked(double x, double y, int button) {
        if (button != 0) return false;
        int stride = tabWidth() + TAB_GAP;
        if (inside(x, y, left, top, 5 * stride - TAB_GAP, TAB_HEIGHT)) {
            int index = (int) (x - left) / stride;
            if ((x - left) % stride >= tabWidth()) return false;
            if (index == 4) {
                expanded = false;
                selectBlueprint.run();
            } else {
                ToolMode mode = ToolMode.ORDER.get(index);
                if (mode == ToolMode.LINK_AS_EXTRACT) {
                    if (picker == null) {
                        ItemStack stack = tool.get();
                        picker = new ExtractionTypePicker(stack.getItem() instanceof LinkConfiguratorItem item
                            ? item.getSettings(stack).selectedTypeIds() : List.of());
                    }
                    expanded = !expanded;
                } else expanded = false;
                selectMode.accept(mode);
            }
            SoundUtil.playClickSound();
            return true;
        }
        if (!expanded) return false;
        if (picker.contains(x, y)) {
            if (picker.toggleAt(x, y, button))
                PacketDistributor.sendToServer(new C2SUpdateToolTypesPayload(picker.selectedTypes()));
            return true;
        }
        /// 收起时吞掉本次点击，避免界面平移后误点节点或背包。
        expanded = false;
        return true;
    }

    public boolean mouseScrolled(double x, double y, double delta) {
        return expanded && picker.mouseScrolled(x, y, delta);
    }

    private static boolean inside(double x, double y, int left, int top, int width, int height) {
        return x >= left && x < left + width && y >= top && y < top + height;
    }
}
