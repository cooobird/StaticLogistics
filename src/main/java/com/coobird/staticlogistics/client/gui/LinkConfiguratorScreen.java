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

public class LinkConfiguratorScreen extends Screen {
    private final ItemStack stack;
    private EditBox groupEdit;
    private EditBox priorityEdit;
    private Button modeButton;
    private Button typeButton;
    private int currentMode;
    private TransferType currentType;

    private static final int COLOR_LABEL = 0xFFE1E1E1;

    public LinkConfiguratorScreen(ItemStack stack) {
        super(Component.translatable("gui.staticlogistics.linker_settings"));
        this.stack = stack;
        this.currentMode = stack.getOrDefault(SLDataComponents.TOOL_MODE.get(), 0);
        this.currentType = stack.getOrDefault(SLDataComponents.SELECTED_TYPE.get(), TransferType.ITEM);
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int centerY = this.height / 2;

        createModeButton(centerX - 105, centerY - 60);
        createTypeButton(centerX + 5, centerY - 60);
        createInputBoxes(centerX, centerY);

        this.addRenderableWidget(Button.builder(
            Component.translatable("gui.staticlogistics.save").withStyle(ChatFormatting.BOLD),
            b -> saveAndClose()
        ).bounds(centerX - 50, centerY + 50, 100, 20).build());
    }

    private void createModeButton(int x, int y) {
        this.modeButton = Button.builder(getModeComponent(), b -> {
            currentMode = (currentMode + 1) % LinkConfiguratorItem.ToolMode.values().length;
            b.setMessage(getModeComponent());
        }).bounds(x, y, 100, 20).build();
        this.addRenderableWidget(modeButton);
    }

    private void createTypeButton(int x, int y) {
        this.typeButton = Button.builder(getTypeComponent(), b -> {
            int nextIdx = (currentType.ordinal() + 1) % TransferType.values().length;
            currentType = TransferType.values()[nextIdx];
            b.setMessage(getTypeComponent());
        }).bounds(x, y, 100, 20).build();
        this.addRenderableWidget(typeButton);
    }

    private void createInputBoxes(int centerX, int centerY) {
        this.priorityEdit = new EditBox(this.font, centerX - 45, centerY - 20, 150, 20, Component.translatable("gui.staticlogistics.label.priority"));
        this.priorityEdit.setValue(String.valueOf(stack.getOrDefault(SLDataComponents.PRIORITY.get(), 0)));
        this.priorityEdit.setFilter(s -> s.matches("-?\\d*"));
        this.priorityEdit.setHint(Component.literal("0").withStyle(ChatFormatting.DARK_GRAY));
        this.addRenderableWidget(priorityEdit);

        this.groupEdit = new EditBox(this.font, centerX - 45, centerY + 10, 150, 20, Component.translatable("gui.staticlogistics.label.group"));
        this.groupEdit.setValue(stack.getOrDefault(SLDataComponents.SELECTED_GROUP.get(), "1"));
        this.groupEdit.setHint(Component.literal("Group Name/ID").withStyle(ChatFormatting.DARK_GRAY));
        this.addRenderableWidget(groupEdit);
    }

    private Component getModeComponent() {
        Component modeName = LinkConfiguratorItem.ToolMode.values()[currentMode].getDisplayName();
        return Component.translatable("tooltip.staticlogistics.mode", modeName);
    }

    private Component getTypeComponent() {
        String typeKey = "type.staticlogistics." + currentType.getSerializedName();
        Component typeName = Component.translatable(typeKey).withStyle(style -> style.withColor(currentType.getColor()));
        return Component.translatable("tooltip.staticlogistics.type", typeName);
    }

    private void saveAndClose() {
        int priority = 0;
        try {
            priority = Integer.parseInt(priorityEdit.getValue());
        } catch (NumberFormatException ignored) {}

        String group = groupEdit.getValue().trim();

        stack.set(SLDataComponents.PRIORITY.get(), priority);
        stack.set(SLDataComponents.SELECTED_GROUP.get(), group);
        stack.set(SLDataComponents.TOOL_MODE.get(), currentMode);
        stack.set(SLDataComponents.SELECTED_TYPE.get(), currentType);

        PacketDistributor.sendToServer(new C2SUpdateToolSettingsPayload(
            priority,
            group,
            currentMode,
            this.currentType
        ));

        this.onClose();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);

        graphics.drawCenteredString(this.font, this.title, this.width / 2, 20, 0xF8F8FF);

        int centerX = this.width / 2;
        int centerY = this.height / 2;

        renderLabel(graphics, "gui.staticlogistics.label.priority", centerX - 55, centerY - 20 + 6);
        renderLabel(graphics, "gui.staticlogistics.label.group", centerX - 55, centerY + 10 + 6);
    }

    private void renderLabel(GuiGraphics graphics, String translationKey, int x, int y) {
        Component text = Component.translatable(translationKey).withStyle(ChatFormatting.YELLOW);
        int textWidth = this.font.width(text);
        graphics.drawString(this.font, text, x - textWidth, y, COLOR_LABEL, true);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}