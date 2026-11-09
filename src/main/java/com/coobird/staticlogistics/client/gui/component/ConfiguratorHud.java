package com.coobird.staticlogistics.client.gui.component;

import com.coobird.staticlogistics.client.data.ToolModeSession;
import com.coobird.staticlogistics.client.key.SLKeyMappings;
import com.coobird.staticlogistics.content.item.BlueprintModeHandler;
import com.coobird.staticlogistics.content.item.LinkConfiguratorItem.ToolSettings;
import com.coobird.staticlogistics.content.item.ToolMode;
import com.coobird.staticlogistics.logistics.SLDataComponents;
import com.coobird.staticlogistics.logistics.blueprint.BlueprintData;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

import static com.coobird.staticlogistics.client.gui.ConfiguratorLayout.Hud.*;

/// 配置器模式和操作提示面板。
public final class ConfiguratorHud {
    // 面板背景颜色和透明度。
    private static final int PANEL_BACKGROUND = 0xA0202020;

    private record Hint(List<Component> keys, Component action) {
    }

    private record ResourceIcon(ItemStack icon, Component name, int x, int y) {
    }

    private ConfiguratorHud() {
    }

    public static void render(GuiGraphics graphics, Font font, ToolSettings settings, ItemStack stack) {
        var mc = Minecraft.getInstance();
        var options = mc.options;
        ToolMode mode = ToolModeSession.currentMode(stack);
        boolean blueprint = mode == ToolMode.BLUEPRINT;
        boolean placing = blueprint && BlueprintModeHandler.isPlacing(stack);
        boolean copied = !stack.getOrDefault(SLDataComponents.BLUEPRINT_DATA.get(), BlueprintData.EMPTY).isEmpty();
        boolean positioned = placing && mc.level != null
            && !stack.getOrDefault(SLDataComponents.BLUEPRINT_PREVIEW_ANCHOR.get(), "").isEmpty()
            && mc.level.dimension().location().toString().equals(
            stack.getOrDefault(SLDataComponents.BLUEPRINT_PREVIEW_DIMENSION.get(), ""));
        List<Hint> hints = new ArrayList<>();
        Component title;

        if (blueprint) {
            title = placing ? text(positioned ? "preview" : "target") : mode.getDisplayName();
            if (placing) {
                hints.add(hint(positioned ? "confirm" : "position", keys(options.keyUse)));
                if (positioned) {
                    hints.add(hint("move", scrollKeys(SLKeyMappings.BLUEPRINT_PREVIEW_MOVE)));
                    hints.add(hint("rotate", scrollKeys(SLKeyMappings.BLUEPRINT_PREVIEW_ROTATE)));
                    hints.add(hint("height", scrollKeys(SLKeyMappings.BLUEPRINT_PREVIEW_MOVE_Y)));
                }
                var cancel = new ArrayList<>(keys(options.keyShift));
                cancel.addAll(keys(options.keyUse));
                hints.add(hint("cancel", cancel));
            } else {
                hints.add(hint("blueprint_open", keys(options.keyUse)));
                if (copied) {
                    hints.add(hint("position_block", keys(options.keyUse)));
                    hints.add(hint("paste", keys(SLKeyMappings.BLUEPRINT_PASTE)));
                }
            }
            if (copied) hints.add(hint("undo", keys(SLKeyMappings.BLUEPRINT_UNDO)));
        } else {
            title = mode.getDisplayName();
            String operation = switch (mode) {
                case WRENCH -> "rotate_block";
                case REMOVE -> "remove";
                case LINK_AS_EXTRACT ->
                    settings.storedMode() == ToolMode.LINK_AS_INSERT ? "connect_extract" : "select_extract";
                case LINK_AS_INSERT ->
                    settings.storedMode() == ToolMode.LINK_AS_EXTRACT ? "connect_insert" : "select_insert";
                case BLUEPRINT -> "blueprint_open";
            };
            hints.add(hint(operation, keys(options.keyUse)));
            if (mode == ToolMode.WRENCH) {
                var dismantle = new ArrayList<>(keys(options.keyShift));
                dismantle.addAll(keys(options.keyUse));
                hints.add(hint("dismantle", dismantle));
            }
            hints.add(hint("open", keys(options.keyUse)));
            if (mode.isLinkMode()) {
                var bulk = new ArrayList<>(keys(SLKeyMappings.BULK_NODE_SELECTION));
                bulk.addAll(keys(options.keyUse));
                hints.add(hint("bulk", bulk));
                if (!settings.storedNodes().isEmpty())
                    hints.add(hint("clear", keys(SLKeyMappings.CLEAR_STORED_NODES)));
            }
        }
        if (!positioned) hints.add(hint("switch", scrollKeys(options.keyShift)));

        int keyColumns = hints.stream().mapToInt(h -> h.keys().size()).max().orElse(0);
        int[] columnWidths = new int[keyColumns];
        for (Hint hint : hints)
            for (int i = 0; i < hint.keys().size(); i++)
                columnWidths[i] = Math.max(columnWidths[i], Math.max(KEY_MIN_WIDTH, font.width(hint.keys().get(i)) + KEY_TEXT_WIDTH_INSET));
        int actionX = 0;
        for (int columnWidth : columnWidths) actionX += columnWidth + KEY_GAP;
        actionX += ACTION_GAP;
        Component channel = mode.isLinkMode()
            ? text("channel", settings.channel() + 1) : Component.empty();
        Component selected = mode.isLinkMode() && !settings.storedNodes().isEmpty()
            ? text("selection", settings.storedNodes().size()) : Component.empty();
        int width = font.width(title) + (mode.isLinkMode() ? HEADER_GAP + font.width(channel) : 0);
        width = Math.max(width, font.width(selected));
        for (Hint hint : hints) width = Math.max(width, actionX + font.width(hint.action()));

        /// 资源图标与名称同行，类型较多时换行，不撑宽整块 HUD。
        List<ResourceIcon> icons = new ArrayList<>();
        int resourceHeight = 0;
        if (mode.isLinkMode()) {
            int rowWidth = Math.max(width, Math.min(RESOURCE_ROW_MAX_WIDTH, graphics.guiWidth() / 3));
            int x = 0, y = 0;
            for (var type : settings.getSelectedTypes()) {
                Component name = Component.translatable(type.translationKey());
                int cellWidth = RESOURCE_TEXT_X + font.width(name) + RESOURCE_GAP;
                if (x > 0 && x + cellWidth > rowWidth) {
                    x = 0;
                    y += RESOURCE_ROW_HEIGHT;
                }
                icons.add(new ResourceIcon(type.getIcon(), name, x, y));
                width = Math.max(width, x + cellWidth);
                x += cellWidth;
            }
            if (!icons.isEmpty()) resourceHeight = y + RESOURCE_ROW_HEIGHT;
        }
        int selectedHeight = selected.getString().isEmpty() ? 0 : font.lineHeight + SELECTED_BOTTOM_GAP;
        int hintsY = font.lineHeight + HEADER_BOTTOM_GAP + selectedHeight + resourceHeight;
        int height = hintsY + hints.size() * ROW_HEIGHT;
        int panelWidth = width + PANEL_PADDING_X * 2;
        int panelHeight = height + PANEL_PADDING_Y * 2;
        float scale = Math.min(1.0F, Math.min(Math.max(1, graphics.guiWidth() - SCREEN_MARGIN * 2) / (float) panelWidth,
            Math.max(1, graphics.guiHeight() - BOTTOM_MARGIN - SCREEN_MARGIN) / (float) panelHeight));
        int left = Math.max(SCREEN_MARGIN, graphics.guiWidth() - RIGHT_MARGIN - (int) Math.ceil(panelWidth * scale));
        int top = Math.max(SCREEN_MARGIN, graphics.guiHeight() - (int) Math.ceil(panelHeight * scale) - BOTTOM_MARGIN);
        graphics.pose().pushPose();
        try {
            graphics.pose().translate(left, top, 0);
            graphics.pose().scale(scale, scale, 1);
            graphics.fill(0, 0, panelWidth, panelHeight, PANEL_BACKGROUND);
            graphics.pose().translate(PANEL_PADDING_X, PANEL_PADDING_Y, 0);
            graphics.drawString(font, title, 0, 0, blueprint ? 0xDD88FF : 0xFFFFFF, false);
            if (mode.isLinkMode())
                graphics.drawString(font, channel, width - font.width(channel), 0, 0xDFE7EB, false);
            if (selectedHeight > 0)
                graphics.drawString(font, selected, 0, font.lineHeight + HEADER_BOTTOM_GAP, 0xDFE7EB, false);
            for (ResourceIcon icon : icons) {
                int y = font.lineHeight + HEADER_BOTTOM_GAP + selectedHeight + icon.y();
                graphics.renderFakeItem(icon.icon(), icon.x(), y);
                graphics.drawString(font, icon.name(), icon.x() + RESOURCE_TEXT_X, y + RESOURCE_TEXT_Y, 0xDFE7EB, false);
            }
            for (int row = 0; row < hints.size(); row++) {
                Hint hint = hints.get(row);
                int x = 0, y = hintsY + row * ROW_HEIGHT;
                for (int column = 0; column < hint.keys().size(); column++) {
                    int w = columnWidths[column];
                    ConfiguratorStyle.button(graphics, x, y, w, KEY_HEIGHT, false, false);
                    Component key = hint.keys().get(column);
                    graphics.drawString(font, key, x + (w - font.width(key)) / 2, y + KEY_TEXT_Y, ConfiguratorStyle.TEXT, false);
                    x += w + KEY_GAP;
                }
                graphics.drawString(font, hint.action(), actionX, y + ACTION_TEXT_Y, 0xDFE7EB, false);
            }
        } finally {
            graphics.pose().popPose();
        }
    }

    private static Hint hint(String action, List<Component> keys) {
        return new Hint(keys, "cancel".equals(action) ? Component.translatable("gui.staticlogistics.cancel") : text(action));
    }

    private static List<Component> scrollKeys(KeyMapping mapping) {
        var keys = new ArrayList<>(keys(mapping));
        keys.add(text("scroll"));
        return keys;
    }

    /// 从实际绑定拆出修饰键，不解析本地化后的组合键字符串。
    private static List<Component> keys(KeyMapping mapping) {
        List<Component> result = new ArrayList<>();
        if (!mapping.isUnbound()) {
            switch (mapping.getKeyModifier()) {
                case SHIFT -> result.add(Component.literal("Shift"));
                case CONTROL -> result.add(Component.literal("Ctrl"));
                case ALT -> result.add(Component.literal("Alt"));
                default -> {
                }
            }
        }
        String alias = switch (mapping.getKey().getName()) {
            case "key.keyboard.left.shift" -> "Shift";
            case "key.keyboard.right.shift" -> "RShift";
            case "key.keyboard.left.control" -> "Ctrl";
            case "key.keyboard.right.control" -> "RCtrl";
            case "key.keyboard.left.alt" -> "Alt";
            case "key.keyboard.right.alt" -> "RAlt";
            default -> null;
        };
        Component key = switch (mapping.getKey().getName()) {
            case "key.mouse.left" -> text("mouse_left");
            case "key.mouse.right" -> text("mouse_right");
            case "key.mouse.middle" -> text("mouse_middle");
            default -> alias == null ? mapping.getKey().getDisplayName() : Component.literal(alias);
        };
        result.add(key);
        return result;
    }

    private static Component text(String key, Object... args) {
        return Component.translatable("gui.staticlogistics.hud." + key, args);
    }
}
