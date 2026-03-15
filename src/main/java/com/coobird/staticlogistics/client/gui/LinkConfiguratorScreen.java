package com.coobird.staticlogistics.client.gui;

import com.coobird.staticlogistics.common.init.SLDataComponents;
import com.coobird.staticlogistics.common.item.LinkConfiguratorItem;
import com.coobird.staticlogistics.network.c2s.C2SUpdateToolSettingsPayload;
import com.coobird.staticlogistics.transfer.TransferType;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

public class LinkConfiguratorScreen extends Screen {
    private final ItemStack stack;
    private EditBox groupEdit;
    private EditBox priorityEdit;
    private int currentMode;
    private TransferType currentType;

    private static final int COLOR_LABEL = 0xFFE1E1E1;

    public LinkConfiguratorScreen(ItemStack stack) {
        super(Component.translatable("gui.staticlogistics.linker_settings"));
        this.stack = stack;
        this.currentMode = stack.getOrDefault(SLDataComponents.TOOL_MODE.get(), 0);

        TransferType savedType = stack.getOrDefault(SLDataComponents.SELECTED_TYPE.get(), TransferType.ITEM);
        this.currentType = savedType.isAvailable() ? savedType : TransferType.ITEM;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int centerY = this.height / 2;

        this.addRenderableWidget(Button.builder(getModeComponent(), b -> {
            currentMode = (currentMode + 1) % LinkConfiguratorItem.ToolMode.values().length;
            b.setMessage(getModeComponent());
        }).bounds(centerX - 105, centerY - 60, 100, 20).build());

        this.addRenderableWidget(Button.builder(getTypeComponent(), b -> {
            TransferType[] types = TransferType.values();
            int nextIdx = currentType.ordinal();
            do {
                nextIdx = (nextIdx + 1) % types.length;
            } while (!types[nextIdx].isAvailable());

            currentType = types[nextIdx];
            b.setMessage(getTypeComponent());
        }).bounds(centerX + 5, centerY - 60, 100, 20).build());

        this.priorityEdit = new EditBox(this.font, centerX - 45, centerY - 20, 150, 20, Component.empty());
        this.priorityEdit.setValue(String.valueOf(stack.getOrDefault(SLDataComponents.PRIORITY.get(), 0)));
        this.priorityEdit.setFilter(s -> s.matches("-?\\d*"));
        this.addRenderableWidget(priorityEdit);

        this.groupEdit = new EditBox(this.font, centerX - 45, centerY + 10, 150, 20, Component.empty());
        this.groupEdit.setValue(stack.getOrDefault(SLDataComponents.SELECTED_GROUP.get(), "1"));
        this.addRenderableWidget(groupEdit);

        this.addRenderableWidget(Button.builder(
            Component.translatable("gui.staticlogistics.save").withStyle(ChatFormatting.BOLD),
            b -> saveAndClose()
        ).bounds(centerX - 50, centerY + 50, 100, 20).build());
    }

    private Component getModeComponent() {
        Component modeName = LinkConfiguratorItem.ToolMode.values()[currentMode].getDisplayName();
        return Component.translatable("tooltip.staticlogistics.mode", modeName);
    }

    private Component getTypeComponent() {
        String typeKey = "type.staticlogistics." + currentType.getSerializedName();
        return Component.translatable("tooltip.staticlogistics.type",
            Component.translatable(typeKey).withStyle(s -> s.withColor(currentType.getColor())));
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            saveAndClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void saveAndClose() {
        int priority = 0;
        try {
            priority = Integer.parseInt(priorityEdit.getValue());
        } catch (NumberFormatException ignored) {
        }

        String group = groupEdit.getValue().trim();
        if (group.isEmpty()) group = "1";

        PacketDistributor.sendToServer(new C2SUpdateToolSettingsPayload(
            priority, group, currentMode, currentType
        ));

        this.onClose();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);

        int centerX = this.width / 2;
        int centerY = this.height / 2;

        graphics.fill(centerX - 120, centerY - 80, centerX + 120, centerY + 80, 0x88000000);

        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(this.font, this.title, centerX, centerY - 75, 0xFFFFFF);

        renderLabel(graphics, "gui.staticlogistics.label.priority", centerX - 55, centerY - 20 + 6);
        renderLabel(graphics, "gui.staticlogistics.label.group", centerX - 55, centerY + 10 + 6);
    }

    private void renderLabel(GuiGraphics graphics, String translationKey, int x, int y) {
        Component text = Component.translatable(translationKey).withStyle(ChatFormatting.YELLOW);
        graphics.drawString(this.font, text, x - this.font.width(text), y, COLOR_LABEL, true);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}