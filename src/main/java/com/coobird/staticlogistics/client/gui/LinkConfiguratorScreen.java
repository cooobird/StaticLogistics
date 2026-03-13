package com.coobird.staticlogistics.client.gui;

import com.coobird.staticlogistics.common.init.SLDataComponents;
import com.coobird.staticlogistics.common.item.LinkConfiguratorItem;
import com.coobird.staticlogistics.core.TransferType;
import com.coobird.staticlogistics.network.c2s.C2SUpdateToolSettingsPayload;
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
    private int currentMode;
    private TransferType currentType;

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

        this.addRenderableWidget(Button.builder(
            Component.translatable("tooltip.staticlogistics.linker.mode", LinkConfiguratorItem.ToolMode.values()[currentMode].getDisplayName()),
            b -> {
                currentMode = (currentMode + 1) % LinkConfiguratorItem.ToolMode.values().length;
                b.setMessage(Component.translatable("tooltip.staticlogistics.linker.mode", LinkConfiguratorItem.ToolMode.values()[currentMode].getDisplayName()));
            }).bounds(centerX - 105, centerY - 60, 100, 20).build());

        this.addRenderableWidget(Button.builder(
            Component.translatable("tooltip.staticlogistics.linker.type", Component.translatable("type.staticlogistics." + currentType.getSerializedName())),
            b -> {
                int nextIdx = (currentType.ordinal() + 1) % TransferType.values().length;
                currentType = TransferType.values()[nextIdx];
                b.setMessage(Component.translatable("tooltip.staticlogistics.linker.type", Component.translatable("type.staticlogistics." + currentType.getSerializedName())));
            }).bounds(centerX + 5, centerY - 60, 100, 20).build());

        this.priorityEdit = new EditBox(this.font, centerX - 45, centerY - 25, 150, 20, Component.translatable("gui.staticlogistics.label.priority"));
        this.priorityEdit.setValue(String.valueOf(stack.getOrDefault(SLDataComponents.PRIORITY.get(), 0)));
        this.priorityEdit.setFilter(s -> s.matches("-?\\d*"));
        this.addRenderableWidget(priorityEdit);

        this.groupEdit = new EditBox(this.font, centerX - 45, centerY + 5, 150, 20, Component.translatable("gui.staticlogistics.label.group"));
        this.groupEdit.setValue(stack.getOrDefault(SLDataComponents.SELECTED_GROUP.get(), "default"));
        this.addRenderableWidget(groupEdit);

        this.addRenderableWidget(Button.builder(Component.translatable("gui.staticlogistics.save"), b -> {
            saveAndClose();
        }).bounds(centerX - 50, centerY + 45, 100, 20).build());
    }

    private int getPriority() {
        try {
            return Integer.parseInt(priorityEdit.getValue());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private void saveAndClose() {
        PacketDistributor.sendToServer(new C2SUpdateToolSettingsPayload(
            getPriority(),
            groupEdit.getValue(),
            currentMode,
            currentType.ordinal()
        ));
        this.onClose();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);

        graphics.drawCenteredString(this.font, this.title, this.width / 2, 20, 0xFFFFFF);

        int centerX = this.width / 2;
        int centerY = this.height / 2;

        Component priorityText = Component.translatable("gui.staticlogistics.label.priority").withStyle(ChatFormatting.YELLOW);
        int pTextWidth = this.font.width(priorityText);
        graphics.drawString(this.font, priorityText, centerX - 55 - pTextWidth, centerY - 25 + 6, 0xFFFFFF, true);

        Component groupText = Component.translatable("gui.staticlogistics.label.group").withStyle(ChatFormatting.YELLOW);
        int gTextWidth = this.font.width(groupText);
        graphics.drawString(this.font, groupText, centerX - 55 - gTextWidth, centerY + 5 + 6, 0xFFFFFF, true);
    }
}