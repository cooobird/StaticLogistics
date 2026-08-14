package com.coobird.staticlogistics.client.gui.component;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * 配置器内部使用的模态确认框，避免切换界面时重建容器菜单。
 */
public final class ConfirmationDialog {
    private static final int WIDTH = 190;
    private static final int HEIGHT = 72;
    private static final int BUTTON_WIDTH = 72;
    private static final int BUTTON_GAP = 10;
    private static final int BUTTON_Y = 48;

    private final Component title;
    private final Component message;
    private final Runnable confirmAction;
    private boolean open = true;

    public ConfirmationDialog(Component title, Component message, Runnable confirmAction) {
        this.title = title;
        this.message = message;
        this.confirmAction = confirmAction;
    }

    public boolean isOpen() {
        return open;
    }

    public void render(GuiGraphics graphics, Font font, int screenWidth, int screenHeight,
                       int mouseX, int mouseY) {
        int left = (screenWidth - WIDTH) / 2;
        int top = (screenHeight - HEIGHT) / 2;
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 600);
        graphics.fill(0, 0, screenWidth, screenHeight, 0x99000000);
        graphics.fill(left - 2, top - 2, left + WIDTH + 2, top + HEIGHT + 2, 0xFF111111);
        graphics.fill(left, top, left + WIDTH, top + HEIGHT, 0xFF454545);
        graphics.fill(left + 2, top + 2, left + WIDTH - 2, top + HEIGHT - 2, 0xFF333333);

        String titleText = font.plainSubstrByWidth(title.getString(), WIDTH - 16);
        graphics.drawString(font, titleText, left + (WIDTH - font.width(titleText)) / 2,
            top + 8, 0xFF98FB98, false);
        String messageText = font.plainSubstrByWidth(message.getString(), WIDTH - 16);
        graphics.drawString(font, messageText, left + (WIDTH - font.width(messageText)) / 2,
            top + 27, 0xFFDDDDDD, false);

        int confirmX = left + (WIDTH - BUTTON_WIDTH * 2 - BUTTON_GAP) / 2;
        int cancelX = confirmX + BUTTON_WIDTH + BUTTON_GAP;
        NodeConfigControls.renderCycleBtn(graphics, font, confirmX, top + BUTTON_Y,
            BUTTON_WIDTH, Component.translatable("gui.staticlogistics.confirm"),
            isOver(mouseX, mouseY, confirmX, top + BUTTON_Y));
        NodeConfigControls.renderCycleBtn(graphics, font, cancelX, top + BUTTON_Y,
            BUTTON_WIDTH, Component.translatable("gui.staticlogistics.cancel"),
            isOver(mouseX, mouseY, cancelX, top + BUTTON_Y));
        graphics.pose().popPose();
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button,
                                int screenWidth, int screenHeight) {
        if (!open) return false;
        if (button != 0) return true;
        int left = (screenWidth - WIDTH) / 2;
        int top = (screenHeight - HEIGHT) / 2;
        int confirmX = left + (WIDTH - BUTTON_WIDTH * 2 - BUTTON_GAP) / 2;
        int cancelX = confirmX + BUTTON_WIDTH + BUTTON_GAP;
        if (isOver(mouseX, mouseY, confirmX, top + BUTTON_Y)) {
            open = false;
            confirmAction.run();
        } else if (isOver(mouseX, mouseY, cancelX, top + BUTTON_Y)) {
            open = false;
        }
        return true;
    }

    public boolean keyPressed(int keyCode) {
        if (!open) return false;
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            open = false;
            confirmAction.run();
        } else if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            open = false;
        }
        return true;
    }

    private static boolean isOver(double mouseX, double mouseY, int x, int y) {
        return NodeConfigControls.hitCycleBtn(mouseX, mouseY, x, y, BUTTON_WIDTH);
    }
}
