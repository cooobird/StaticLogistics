package com.coobird.staticlogistics.client.gui.component;

import com.coobird.staticlogistics.client.render.SLGuiTextures;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import static com.coobird.staticlogistics.client.gui.ConfiguratorLayout.HierarchyDialog.*;

/// 目录命名和归类弹窗，所有操作按钮复用 atlas。
public final class GroupHierarchyDialog {
    // 列表的高度。
    private static final int LIST_HEIGHT = LIST_ROW_HEIGHT * LIST_VISIBLE_ROWS;
    // 移动分组弹窗的总高度。
    private static final int MOVE_HEIGHT = MOVE_BUTTON_Y + CONTROL_HEIGHT + BOTTOM_PADDING;
    // 名称输入框的高度。
    private static final int NAME_HEIGHT = NAME_BUTTON_Y + CONTROL_HEIGHT + BOTTOM_PADDING;

    public record Destination(String id, String name, String parent, boolean selectable) {
    }

    private final List<Destination> destinations;
    private final Set<String> collapsed = new HashSet<>();
    private Consumer<String> presetDelete = ignored -> {
    };

    public void onPresetDelete(Consumer<String> handler) {
        presetDelete = handler;
    }

    private final boolean moving;
    private final boolean reviewing;
    private final Set<String> selectedFields = new HashSet<>();
    private String selectedDestination;
    private int scroll;
    private AtlasButton moveButton;
    private final EditBox name;
    private final List<AtlasButton> buttons = new ArrayList<>();
    private final Component title;
    private final String chooseHint;
    private final Runnable close;
    private int x, y;
    private final WindowDrag drag = new WindowDrag();
    private final List<FormattedCharSequence> deleteWarning;
    private final int panelHeight;
    private final boolean showName;

    public GroupHierarchyDialog(Font font, int x, int y, String titleKey, String initialName, boolean create, boolean movable,
                                String operation, List<Destination> destinations, BiConsumer<String, String> action, Runnable close) {
        this.x = x;
        this.y = y;
        this.close = close;
        reviewing = operation.equals("preset_review") || operation.equals("preset_review_batch");
        chooseHint = reviewing ? "gui.staticlogistics.preset.choose_fields" : operation.equals("preset_target") ? "gui.staticlogistics.preset.target_hint" : operation.equals("preset_select") ? "gui.staticlogistics.preset.choose" : "gui.staticlogistics.directory.choose_destination";
        this.destinations = List.copyOf(destinations);
        if (reviewing) destinations.forEach(row -> selectedFields.add(row.id()));
        moving = operation.equals("move") || operation.equals("preset_select") || operation.equals("preset_target") || reviewing;
        String resolvedTitle = operation.isEmpty() || operation.equals("preset_select") || operation.equals("preset_target") || reviewing || titleKey.startsWith("gui.staticlogistics.compact.channel_") || titleKey.equals("gui.staticlogistics.preset.delete_title") || titleKey.contains(".batch_") ? titleKey : "gui.staticlogistics.directory." + operation;
        title = reviewing ? Component.translatable(resolvedTitle, initialName) : Component.translatable(resolvedTitle);
        showName = create || operation.isEmpty() || operation.equals("rename");
        name = showName ? new AtlasEditBox(font, x + NAME_X, y + NAME_Y, NAME_WIDTH, NAME_INPUT_HEIGHT, Component.empty()) : null;
        if (showName) {
            name.setMaxLength(32);
            name.setValue(initialName);
            name.setFocused(true);
        }
        if (operation.equals("delete")) {
            Component warning = titleKey.equals("gui.staticlogistics.preset.delete_title")
                ? Component.translatable("gui.staticlogistics.preset.confirm_delete", initialName)
                : titleKey.contains(".channel_")
                ? Component.translatable("gui.staticlogistics.directory.confirm_delete_channel", initialName)
                : titleKey.endsWith("batch_delete")
                ? Component.translatable("gui.staticlogistics.directory.confirm_delete_links")
                : titleKey.endsWith(".manage")
                ? Component.translatable("gui.staticlogistics.directory.confirm_delete")
                : Component.translatable("gui.staticlogistics.confirm_delete_connection", initialName);
            deleteWarning = font.split(warning, WIDTH - PADDING * 2);
            int buttonY = WARNING_Y + WARNING_BUTTON_GAP + deleteWarning.size() * font.lineHeight;
            panelHeight = buttonY + CONTROL_HEIGHT + BOTTOM_PADDING;
            buttons.add(AtlasButton.atlasBuilder(Component.translatable("gui.staticlogistics.directory.delete_action"), button -> {
                action.accept("delete", "");
                close.run();
            }).bounds(x + PADDING, y + buttonY, BUTTON_WIDTH, CONTROL_HEIGHT).build());
            buttons.add(AtlasButton.atlasBuilder(Component.translatable("gui.cancel"), button -> close.run())
                .bounds(x + PADDING + BUTTON_WIDTH + BUTTON_GAP, y + buttonY, BUTTON_WIDTH, CONTROL_HEIGHT).build());
            return;
        }
        deleteWarning = List.of();
        panelHeight = moving ? MOVE_HEIGHT : NAME_HEIGHT;
        if (showName)
            buttons.add(AtlasButton.atlasBuilder(Component.translatable(create && !titleKey.endsWith("batch_rename") ? "gui.staticlogistics.directory.create" : "gui.staticlogistics.directory.rename"), b -> {
                if (!name.getValue().isBlank()) {
                    action.accept(create ? "create" : "rename", name.getValue());
                    close.run();
                }
            }).bounds(x + PADDING, y + NAME_BUTTON_Y, BUTTON_WIDTH, CONTROL_HEIGHT).build());
        buttons.add(AtlasButton.atlasBuilder(Component.translatable(operation.equals("preset_review_batch") ? "gui.back" : "gui.cancel"), b -> {
            close.run();
            if (operation.equals("preset_review_batch")) action.accept("back", "");
        }).bounds(x + PADDING + BUTTON_WIDTH + BUTTON_GAP, y + (moving ? MOVE_BUTTON_Y : NAME_BUTTON_Y), BUTTON_WIDTH, CONTROL_HEIGHT).build());
        if (moving) {
            moveButton = AtlasButton.atlasBuilder(Component.translatable(operation.equals("preset_target") ? "gui.staticlogistics.preset.preview" : reviewing || operation.equals("preset_select") ? "gui.staticlogistics.preset.apply" : "gui.staticlogistics.directory.move"), b -> {
                if (reviewing && !selectedFields.isEmpty()) {
                    String selected = String.join(",", selectedFields);
                    close.run();
                    action.accept("apply", selected);
                } else if (!reviewing && selectedDestination != null) {
                    String selected = selectedDestination;
                    close.run();
                    action.accept("move", selected);
                }
            }).bounds(x + PADDING, y + MOVE_BUTTON_Y, BUTTON_WIDTH, CONTROL_HEIGHT).build();
            moveButton.active = reviewing && !selectedFields.isEmpty();
            buttons.add(moveButton);
        }

    }

    public void render(GuiGraphics graphics, Font font, int mouseX, int mouseY) {
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 600);
        ConfiguratorStyle.panel(graphics, x, y, WIDTH, panelHeight);
        graphics.drawString(font, title, x + PADDING, y + TITLE_Y, ConfiguratorStyle.ACCENT, false);
        if (showName) name.render(graphics, mouseX, mouseY, 0);
        for (int line = 0; line < deleteWarning.size(); line++)
            graphics.drawString(font, deleteWarning.get(line), x + PADDING, y + WARNING_Y + line * font.lineHeight, ConfiguratorStyle.TEXT, false);
        if (moving) {
            graphics.drawString(font, font.plainSubstrByWidth(Component.translatable(chooseHint).getString(), WIDTH - PADDING * 2), x + PADDING, y + DESTINATION_HINT_Y, ConfiguratorStyle.MUTED_TEXT, false);
            ConfiguratorStyle.panel(graphics, x + LIST_PANEL_X, y + LIST_PANEL_Y, LIST_PANEL_WIDTH, LIST_PANEL_HEIGHT);
            List<Destination> rows = visibleDestinations();
            scroll = Mth.clamp(scroll, 0, Math.max(0, rows.size() - LIST_VISIBLE_ROWS));
            for (int i = scroll; i < Math.min(rows.size(), scroll + LIST_VISIBLE_ROWS); i++) {
                Destination row = rows.get(i);
                int ry = y + LIST_Y + (i - scroll) * LIST_ROW_HEIGHT;
                boolean root = row.parent().isEmpty();
                int indent = root ? 0 : DESTINATION_INDENT;
                boolean selected = reviewing ? selectedFields.contains(row.id()) : row.id().equals(selectedDestination);
                if (selected)
                    ConfiguratorStyle.row(graphics, x + LIST_X, ry, DESTINATION_ROW_WIDTH, DESTINATION_ROW_HEIGHT, true, false);
                if (reviewing)
                    graphics.drawString(font, selected ? "[x]" : "[ ]", x + DESTINATION_ICON_X, ry + DESTINATION_TEXT_Y, ConfiguratorStyle.TEXT, false);
                else
                    ConfiguratorStyle.directory(graphics, x + DESTINATION_ICON_X + indent, ry + DESTINATION_ICON_Y, selected);
                graphics.drawString(font, font.plainSubstrByWidth(row.name(), DESTINATION_TEXT_WIDTH - indent), x + DESTINATION_TEXT_X + indent, ry + DESTINATION_TEXT_Y, ConfiguratorStyle.TEXT, false);
            }
            if (rows.isEmpty())
                graphics.drawString(font, Component.translatable("gui.staticlogistics.directory.no_destination"), x + EMPTY_MESSAGE_X, y + EMPTY_MESSAGE_Y, ConfiguratorStyle.MUTED_TEXT, false);
            if (rows.size() > LIST_VISIBLE_ROWS) {
                int knob = SLGuiTextures.Scrollbar.HEIGHT;
                int offset = scroll * (LIST_HEIGHT - knob) / (rows.size() - LIST_VISIBLE_ROWS);
                ConfiguratorStyle.scrollbar(graphics, x + LIST_X + LIST_WIDTH, y + LIST_Y, LIST_HEIGHT, offset, true);
            }
        }
        for (AtlasButton button : buttons) button.render(graphics, mouseX, mouseY, 0);
        if (reviewing && mouseX >= x + LIST_X && mouseX < x + LIST_X + LIST_WIDTH
            && mouseY >= y + LIST_Y && mouseY < y + LIST_Y + LIST_HEIGHT) {
            int index = scroll + (mouseY - y - LIST_Y) / LIST_ROW_HEIGHT;
            if (index < destinations.size())
                graphics.renderTooltip(font, Component.literal(destinations.get(index).name()), mouseX, mouseY);
        }
        graphics.pose().popPose();
    }

    public boolean mouseClicked(double x, double y, int button) {
        if (drag.begin(x, y, button, this.x, this.y, this.x + DRAG_INSET, this.y + DRAG_INSET, WIDTH - DRAG_INSET * 2, CONTROL_HEIGHT))
            return true;
        if (moving && button == 1 && x >= this.x + LIST_X && x < this.x + LIST_X + LIST_WIDTH
            && y >= this.y + LIST_Y && y < this.y + LIST_Y + LIST_HEIGHT) {
            List<Destination> rows = visibleDestinations();
            int index = scroll + (int) (y - this.y - LIST_Y) / LIST_ROW_HEIGHT;
            if (index < rows.size()) presetDelete.accept(rows.get(index).id());
            return true;
        }
        if (moving && button == 0 && x >= this.x + LIST_X && x < this.x + LIST_X + LIST_WIDTH && y >= this.y + LIST_Y && y < this.y + LIST_Y + LIST_HEIGHT) {
            List<Destination> rows = visibleDestinations();
            int index = scroll + (int) (y - this.y - LIST_Y) / LIST_ROW_HEIGHT;
            if (index < rows.size()) {
                Destination row = rows.get(index);
                if (reviewing) {
                    if (!selectedFields.add(row.id())) selectedFields.remove(row.id());
                    moveButton.active = !selectedFields.isEmpty();
                } else if (!row.selectable()) {
                    if (!collapsed.add(row.id())) collapsed.remove(row.id());
                } else {
                    selectedDestination = row.id();
                    moveButton.active = true;
                }
            }
            return true;
        }
        if (showName) name.setFocused(name.mouseClicked(x, y, button));
        for (AtlasButton widget : buttons) if (widget.mouseClicked(x, y, button)) break;
        return true;
    }

    public boolean mouseDragged(double mx, double my, int button, int width, int height) {
        return mouseDragged(mx, my, button, 0, 0, width, height);
    }

    public Rect2i bounds() {
        return new Rect2i(x, y, WIDTH, panelHeight);
    }

    public void constrainToViewport(int viewportX, int viewportY, int width, int height) {
        moveTo(WindowDrag.constrain(x, WIDTH, viewportX, width),
            WindowDrag.constrain(y, panelHeight, viewportY, height));
    }

    private void moveTo(int nextX, int nextY) {
        if (showName) {
            name.setX(name.getX() + nextX - x);
            name.setY(name.getY() + nextY - y);
        }
        for (var widget : buttons) {
            widget.setX(widget.getX() + nextX - x);
            widget.setY(widget.getY() + nextY - y);
        }
        x = nextX;
        y = nextY;
    }

    public boolean mouseDragged(double mx, double my, int button, int viewportX, int viewportY, int width, int height) {
        if (drag.active(button))
            moveTo(drag.viewportX(mx, WIDTH, viewportX, width),
                drag.viewportY(my, panelHeight, viewportY, height));
        return true;
    }

    public boolean mouseReleased() {
        drag.release();
        return true;
    }

    private List<Destination> visibleDestinations() {
        List<Destination> rows = new ArrayList<>();
        for (Destination root : destinations) {
            if (!root.parent().isEmpty()) continue;
            rows.add(root);
        }
        return rows;
    }

    public boolean mouseScrolled(double delta) {
        if (moving)
            scroll = Mth.clamp(scroll - (int) Math.signum(delta), 0, Math.max(0, visibleDestinations().size() - LIST_VISIBLE_ROWS));
        return true;
    }

    public boolean keyPressed(int key, int scan, int modifiers) {
        if (key == GLFW.GLFW_KEY_ESCAPE) close.run();
        else if (showName) name.keyPressed(key, scan, modifiers);
        return true;
    }

    public boolean charTyped(char character, int modifiers) {
        if (showName) name.charTyped(character, modifiers);
        return true;
    }
}
