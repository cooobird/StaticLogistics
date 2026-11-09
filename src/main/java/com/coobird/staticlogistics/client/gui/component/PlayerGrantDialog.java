package com.coobird.staticlogistics.client.gui.component;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import static com.coobird.staticlogistics.client.gui.ConfiguratorLayout.GrantDialog.*;

public final class PlayerGrantDialog {
    private final Font font;
    private final EditBox input;
    private final BiConsumer<String, Integer> submit;
    private final Consumer<Boolean> setTeamAccess;
    private final Runnable close;
    private final WindowDrag drag = new WindowDrag();
    private List<Entry> entries = List.of();
    private int x, y, scroll;
    private boolean teamIntegrationActive;
    private boolean teamAccessEnabled;
    private UUID menu;

    private record Entry(UUID id, String name, int permission) {
    }

    public PlayerGrantDialog(Font font, int x, int y, BiConsumer<String, Integer> submit,
                             Consumer<Boolean> setTeamAccess, Runnable close) {
        this.font = font;
        this.x = x;
        this.y = y;
        this.submit = submit;
        this.setTeamAccess = setTeamAccess;
        this.close = close;
        input = new AtlasEditBox(font, x + INPUT_X, y + INPUT_Y, INPUT_WIDTH, INPUT_HEIGHT, Component.empty());
        input.setMaxLength(16);
        input.setHint(Component.translatable("gui.staticlogistics.permissions.player_hint"));
    }

    public void accept(CompoundTag data) {
        teamIntegrationActive = data.getBoolean("team_integration_active");
        teamAccessEnabled = data.getBoolean("my_team_access_enabled");
        var grants = data.getCompound("my_grants");
        var names = data.getCompound("my_grant_names");
        List<Entry> next = new ArrayList<>();
        for (String key : grants.getAllKeys()) {
            try {
                UUID id = UUID.fromString(key);
                int permission = grants.getInt(key);
                if (permission < 1 || permission > 2) continue;
                String name = names.getString(key);
                next.add(new Entry(id, name.isBlank() ? key : name, permission));
            } catch (IllegalArgumentException ignored) {
            }
        }
        next.sort(Comparator.comparing(Entry::name, String.CASE_INSENSITIVE_ORDER));
        entries = List.copyOf(next);
        scroll = Math.min(scroll, Math.max(0, entries.size() - ROWS));
    }

    private static Component tr(String key) {
        return Component.translatable("gui.staticlogistics.permissions." + key);
    }

    private static Component level(int permission) {
        return tr(permission == 2 ? "manage" : permission == 1 ? "view" : "none");
    }

    public void render(GuiGraphics g, int mx, int my) {
        g.pose().pushPose();
        g.pose().translate(0, 0, 600);
        ConfiguratorStyle.panel(g, x, y, WIDTH, HEIGHT);
        g.drawString(font, tr("title"), x + TITLE_X, y + TITLE_Y, ConfiguratorStyle.ACCENT, false);
        g.drawString(font, tr("manual_grants"), x + TITLE_X, y + SUBTITLE_Y, ConfiguratorStyle.MUTED_TEXT, false);
        input.render(g, mx, my, 0);
        ConfiguratorStyle.button(g, x + ADD_X, y + ADD_Y, ADD_WIDTH, ADD_HEIGHT, false, inside(mx, my, x + ADD_X, y + ADD_Y, ADD_WIDTH, ADD_HEIGHT), true);
        drawCentered(g, tr("add"), x + ADD_X + ADD_WIDTH / 2, y + ADD_Y + 4);
        for (int i = 0; i < ROWS && i + scroll < entries.size(); i++) {
            Entry entry = entries.get(i + scroll);
            int rowY = y + LIST_Y + i * ROW_HEIGHT;
            ConfiguratorStyle.button(g, x + LIST_X, rowY, LIST_WIDTH, LIST_ROW_HEIGHT, false, false, true);
            g.drawString(font, font.plainSubstrByWidth(entry.name(), NAME_WIDTH), x + NAME_X, rowY + NAME_Y, ConfiguratorStyle.TEXT, false);
            ConfiguratorStyle.button(g, x + PERMISSION_X, rowY + PERMISSION_Y, PERMISSION_WIDTH, PERMISSION_HEIGHT, false, inside(mx, my, x + PERMISSION_X, rowY + PERMISSION_Y, PERMISSION_WIDTH, PERMISSION_HEIGHT), true);
            g.drawString(font, level(entry.permission()), x + PERMISSION_X + PERMISSION_TEXT_X, rowY + PERMISSION_Y + PERMISSION_TEXT_Y, ConfiguratorStyle.TEXT, false);
        }
        ConfiguratorStyle.button(g, x + DONE_X, y + DONE_Y, DONE_WIDTH, DONE_HEIGHT, false, inside(mx, my, x + DONE_X, y + DONE_Y, DONE_WIDTH, DONE_HEIGHT), true);
        if (teamIntegrationActive) {
            ConfiguratorStyle.button(g, x + TEAM_X, y + TEAM_Y, TEAM_WIDTH, TEAM_HEIGHT, teamAccessEnabled,
                inside(mx, my, x + TEAM_X, y + TEAM_Y, TEAM_WIDTH, TEAM_HEIGHT), true);
            drawCentered(g, tr(teamAccessEnabled ? "team_on" : "team_off"), x + TEAM_X + TEAM_WIDTH / 2, y + TEAM_Y + 4);
        }
        drawCentered(g, Component.translatable("gui.done"), x + DONE_X + DONE_WIDTH / 2, y + DONE_Y + 4);
        if (menu != null) {
            int row = -1;
            for (int i = 0; i < ROWS && i + scroll < entries.size(); i++)
                if (entries.get(i + scroll).id().equals(menu)) row = i;
            if (row >= 0) for (int choice = 0; choice < 3; choice++) {
                int cy = Math.min(y + LIST_Y + row * ROW_HEIGHT + MENU_ROW_OFFSET_Y, y + HEIGHT - MENU_BOTTOM_INSET) + choice * MENU_ROW_HEIGHT;
                ConfiguratorStyle.button(g, x + PERMISSION_X, cy, PERMISSION_WIDTH, MENU_ROW_HEIGHT, false, inside(mx, my, x + PERMISSION_X, cy, PERMISSION_WIDTH, MENU_ROW_HEIGHT), true);
                g.drawString(font, level(choice), x + PERMISSION_X + PERMISSION_TEXT_X, cy + PERMISSION_TEXT_Y, ConfiguratorStyle.TEXT, false);
            }
        }
        g.pose().popPose();
    }

    public boolean click(double mx, double my, int button) {
        if (button != 0) return true;
        if (menu != null) {
            for (int i = 0; i < ROWS && i + scroll < entries.size(); i++) {
                Entry entry = entries.get(i + scroll);
                if (!entry.id().equals(menu)) continue;
                for (int choice = 0; choice < 3; choice++) {
                    int cy = Math.min(y + LIST_Y + i * ROW_HEIGHT + MENU_ROW_OFFSET_Y, y + HEIGHT - MENU_BOTTOM_INSET) + choice * MENU_ROW_HEIGHT;
                    if (inside(mx, my, x + PERMISSION_X, cy, PERMISSION_WIDTH, MENU_ROW_HEIGHT)) {
                        submit.accept(entry.id().toString(), choice);
                        menu = null;
                        return true;
                    }
                }
            }
            menu = null;
            return true;
        }
        boolean inputHit = input.isMouseOver(mx, my);
        input.setFocused(inputHit);
        if (inputHit) {
            input.mouseClicked(mx, my, button);
            return true;
        }
        if (inside(mx, my, x + ADD_X, y + ADD_Y, ADD_WIDTH, ADD_HEIGHT)) {
            String name = input.getValue().trim();
            if (!name.isEmpty()) submit.accept(name, 1);
            return true;
        }
        if (teamIntegrationActive && inside(mx, my, x + TEAM_X, y + TEAM_Y, TEAM_WIDTH, TEAM_HEIGHT)) {
            setTeamAccess.accept(!teamAccessEnabled);
            return true;
        }
        if (inside(mx, my, x + DONE_X, y + DONE_Y, DONE_WIDTH, DONE_HEIGHT)) {
            close.run();
            return true;
        }
        for (int i = 0; i < ROWS && i + scroll < entries.size(); i++)
            if (inside(mx, my, x + PERMISSION_X, y + LIST_Y + PERMISSION_Y + i * ROW_HEIGHT, PERMISSION_WIDTH, PERMISSION_HEIGHT)) {
                menu = entries.get(i + scroll).id();
                return true;
            }
        drag.begin(mx, my, button, x, y, x, y, WIDTH, DRAG_HEIGHT);
        return true;
    }

    public boolean key(int key, int scan, int modifiers) {
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            if (menu != null) menu = null;
            else close.run();
            return true;
        }
        if (input.isFocused()) input.keyPressed(key, scan, modifiers);
        return true;
    }

    public boolean typed(char c, int modifiers) {
        if (input.isFocused()) input.charTyped(c, modifiers);
        return true;
    }

    public void scroll(double delta) {
        scroll = Math.max(0, Math.min(Math.max(0, entries.size() - ROWS), scroll - (int) Math.signum(delta)));
        menu = null;
    }

    public void drag(double mx, double my, int button, int vx, int vy, int w, int h) {
        if (drag.active(button)) move(drag.viewportX(mx, WIDTH, vx, w), drag.viewportY(my, HEIGHT, vy, h));
    }

    public void constrain(int vx, int vy, int w, int h) {
        move(WindowDrag.constrain(x, WIDTH, vx, w), WindowDrag.constrain(y, HEIGHT, vy, h));
    }

    private void move(int nx, int ny) {
        x = nx;
        y = ny;
        input.setX(x + INPUT_X);
        input.setY(y + INPUT_Y);
    }

    public void release() {
        drag.release();
    }

    public Rect2i bounds() {
        return new Rect2i(x, y, WIDTH, HEIGHT);
    }

    private static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private void drawCentered(GuiGraphics graphics, Component text, int centerX, int y) {
        graphics.drawString(font, text, centerX - font.width(text) / 2, y, ConfiguratorStyle.TEXT, false);
    }
}
