package com.coobird.staticlogistics.client.gui.component;

import com.coobird.staticlogistics.client.gui.ConfiguratorLayout.Tooltip;
import com.coobird.staticlogistics.logistics.filter.FilterRule;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.function.Consumer;

import static com.coobird.staticlogistics.client.gui.ConfiguratorLayout.FilterNumberDialog.*;

/// 编辑组件数值或剩余耐久条件的可拖动弹窗。
public final class FilterNumberDialog {
    private static final String[] OPERATORS = {"gt", "lt", "eq"};
    private final Font font;
    private final String component;
    private final Consumer<FilterRule> apply;
    private final Runnable close;
    private final WindowDrag drag = new WindowDrag();
    private int x, y, comparison;
    private boolean percent;
    private String value = "0";

    public FilterNumberDialog(Font font, int x, int y, String component, FilterRule rule,
                              Consumer<FilterRule> apply, Runnable close) {
        this.font = font;
        this.x = x;
        this.y = y;
        this.component = component;
        this.apply = apply;
        this.close = close;
        if (rule != null) {
            String[] parts = rule.key().split("\\|", -1);
            String operator = parts[component == null ? 0 : 1];
            comparison = operator.equals("lt") ? 1 : operator.equals("eq") ? 2 : 0;
            percent = component == null && parts[1].equals("percent");
            value = parts[2];
        }
    }

    public void render(GuiGraphics graphics, int mx, int my, int viewportWidth, int viewportHeight) {
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 500);
        ConfiguratorStyle.panel(graphics, x, y, WIDTH, HEIGHT);
        String title = component == null ? text("durability").getString() : component;
        graphics.drawString(font, font.plainSubstrByWidth(title, TITLE_WIDTH), x + TITLE_X, y + TITLE_Y, ConfiguratorStyle.ACCENT, false);
        button(graphics, COMPARISON_X, COMPARISON_Y, COMPARISON_WIDTH, COMPARISON_HEIGHT, comparison == 0 ? ">" : comparison == 1 ? "<" : "=");
        button(graphics, VALUE_X, VALUE_Y, VALUE_WIDTH, VALUE_HEIGHT, value + "_");
        if (component == null)
            button(graphics, UNIT_X, UNIT_Y, UNIT_WIDTH, UNIT_HEIGHT, percent ? "%" : text("points").getString());
        else
            graphics.drawString(font, text("points"), x + UNIT_X + UNIT_TEXT_INSET, y + UNIT_Y + UNIT_TEXT_INSET, ConfiguratorStyle.TEXT, false);
        if (!valid())
            graphics.drawString(font, text(value.isEmpty() || component != null ? "durability_empty" : "durability_invalid"),
                x + ERROR_X, y + ERROR_Y, 0xFFB02020, false);
        button(graphics, CANCEL_X, CANCEL_Y, CANCEL_WIDTH, CANCEL_HEIGHT, Component.translatable("gui.staticlogistics.cancel").getString());
        button(graphics, CONFIRM_X, CONFIRM_Y, CONFIRM_WIDTH, CONFIRM_HEIGHT, Component.translatable("gui.staticlogistics.confirm").getString());
        if (font.width(title) > TITLE_WIDTH && inside(mx, my, TITLE_X, TITLE_Y, TITLE_WIDTH, font.lineHeight)) {
            int tipWidth = Math.min(viewportWidth - Tooltip.SCREEN_WIDTH_INSET, Math.max(Tooltip.MIN_WIDTH, font.width(title) + Tooltip.TEXT_WIDTH_INSET));
            var lines = font.split(Component.literal(title), tipWidth - Tooltip.TEXT_WIDTH_INSET);
            int tipHeight = lines.size() * font.lineHeight + Tooltip.TEXT_HEIGHT_INSET;
            int tipX = Math.clamp(mx + Tooltip.MOUSE_X_OFFSET, Tooltip.SCREEN_MARGIN, viewportWidth - tipWidth - Tooltip.SCREEN_MARGIN);
            int tipY = Math.clamp(my - tipHeight - Tooltip.MOUSE_Y_GAP, Tooltip.SCREEN_MARGIN, viewportHeight - tipHeight - Tooltip.SCREEN_MARGIN);
            ConfiguratorStyle.panel(graphics, tipX, tipY, tipWidth, tipHeight);
            for (int i = 0; i < lines.size(); i++)
                graphics.drawString(font, lines.get(i), tipX + Tooltip.TEXT_X, tipY + Tooltip.TEXT_Y + i * font.lineHeight, ConfiguratorStyle.TEXT, false);
        }
        graphics.pose().popPose();
    }

    private void button(GuiGraphics graphics, int bx, int by, int width, int height, String label) {
        ConfiguratorStyle.button(graphics, x + bx, y + by, width, height, false, false);
        String visible = font.plainSubstrByWidth(label, width - BUTTON_TEXT_WIDTH_INSET);
        graphics.drawString(font, visible, x + bx + (width - font.width(visible)) / 2,
            y + by + BUTTON_TEXT_Y, ConfiguratorStyle.TEXT, false);
    }

    private static Component text(String key) {
        return Component.translatable("gui.staticlogistics.filter.rule." + key);
    }

    private boolean inside(double mx, double my, int bx, int by, int width, int height) {
        return mx >= x + bx && mx < x + bx + width && my >= y + by && my < y + by + height;
    }

    public void mouseClicked(double mx, double my, int button) {
        if (button != 0 || drag.begin(mx, my, button, x, y, x, y, WIDTH, DRAG_HEIGHT)) return;
        if (inside(mx, my, COMPARISON_X, COMPARISON_Y, COMPARISON_WIDTH, COMPARISON_HEIGHT))
            comparison = (comparison + 1) % 3;
        else if (component == null && inside(mx, my, UNIT_X, UNIT_Y, UNIT_WIDTH, UNIT_HEIGHT)) percent = !percent;
        else if (inside(mx, my, CANCEL_X, CANCEL_Y, CANCEL_WIDTH, CANCEL_HEIGHT)) close.run();
        else if (inside(mx, my, CONFIRM_X, CONFIRM_Y, CONFIRM_WIDTH, CONFIRM_HEIGHT)) confirm();
    }

    public void mouseDragged(double mx, double my, int button, int viewportWidth, int viewportHeight) {
        if (drag.active(button)) {
            x = drag.x(mx, WIDTH, viewportWidth);
            y = drag.y(my, HEIGHT, viewportHeight, 0);
        }
    }

    public void mouseReleased() {
        drag.release();
    }

    public void keyPressed(int key) {
        if (key == GLFW.GLFW_KEY_ESCAPE) close.run();
        else if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) confirm();
        else if (key == GLFW.GLFW_KEY_A && Screen.hasControlDown()) value = "";
        else if (key == GLFW.GLFW_KEY_BACKSPACE && !value.isEmpty()) value = value.substring(0, value.length() - 1);
    }

    public void charTyped(char character) {
        int limit = component == null ? 9 : 24;
        if (character >= '0' && character <= '9' && value.length() < limit)
            value = value.equals("0") ? Character.toString(character) : value + character;
        else if (component != null && value.length() < limit) {
            if (character == '-' && value.isEmpty()) value = "-";
            else if (character == '.' && !value.isEmpty() && !value.contains(".")) value += ".";
        }
    }

    private boolean valid() {
        if (value.isEmpty()) return false;
        return component != null ? value.matches("-?[0-9]+(\\.[0-9]+)?") : !percent || Integer.parseInt(value) <= 100;
    }

    private void confirm() {
        if (!valid()) return;
        FilterRule.Kind kind = component == null ? FilterRule.Kind.DURABILITY : FilterRule.Kind.NUMERIC;
        String key = component == null ? OPERATORS[comparison] + "|" + (percent ? "percent" : "points") + "|" + Integer.parseInt(value)
            : component + "|" + OPERATORS[comparison] + "|" + value;
        apply.accept(new FilterRule(kind, FilterRule.Mode.MATCH, key));
        close.run();
    }
}
