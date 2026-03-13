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

    // UI 组件
    private EditBox groupEdit;
    private EditBox priorityEdit;
    private Button modeButton;
    private Button typeButton;

    // 临时状态
    private int currentMode;
    private TransferType currentType;

    private static final int COLOR_GOLD = 0xFFFFAA00;
    private static final int COLOR_WHITE = 0xFFFFFFFF;

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
        ).bounds(centerX - 50, centerY + 45, 100, 20).build());
    }

    /**
     * 创建工具模式切换按钮（连接、断开等）
     */
    private void createModeButton(int x, int y) {
        this.modeButton = Button.builder(getModeComponent(), b -> {
            currentMode = (currentMode + 1) % LinkConfiguratorItem.ToolMode.values().length;
            b.setMessage(getModeComponent());
        }).bounds(x, y, 100, 20).build();
        this.addRenderableWidget(modeButton);
    }

    /**
     * 创建传输类型切换按钮（物品、流体、能量）
     */
    private void createTypeButton(int x, int y) {
        this.typeButton = Button.builder(getTypeComponent(), b -> {
            int nextIdx = (currentType.ordinal() + 1) % TransferType.values().length;
            currentType = TransferType.values()[nextIdx];
            b.setMessage(getTypeComponent());
        }).bounds(x, y, 100, 20).build();
        this.addRenderableWidget(typeButton);
    }

    /**
     * 初始化数值输入框及其标签
     */
    private void createInputBoxes(int centerX, int centerY) {
        // 优先级输入框
        this.priorityEdit = new EditBox(this.font, centerX - 45, centerY - 25, 150, 20, Component.literal("Priority"));
        this.priorityEdit.setValue(String.valueOf(stack.getOrDefault(SLDataComponents.PRIORITY.get(), 0)));
        this.priorityEdit.setFilter(s -> s.matches("-?\\d*")); // 仅允许数字和负号
        this.addRenderableWidget(priorityEdit);

        // 分组 ID 输入框
        this.groupEdit = new EditBox(this.font, centerX - 45, centerY + 5, 150, 20, Component.literal("Group ID"));
        this.groupEdit.setValue(stack.getOrDefault(SLDataComponents.SELECTED_GROUP.get(), "1"));
        this.addRenderableWidget(groupEdit);
    }

    /**
     * 获取带样式的模式文本
     */
    private Component getModeComponent() {
        Component modeName = LinkConfiguratorItem.ToolMode.values()[currentMode].getDisplayName();
        return Component.translatable("tooltip.staticlogistics.linker.mode", modeName);
    }

    /**
     * 获取带样式的传输类型文本（颜色与 TransferType 对应）
     */
    private Component getTypeComponent() {
        String typeKey = "type.staticlogistics." + currentType.getSerializedName();
        return Component.translatable("tooltip.staticlogistics.linker.type",
            Component.translatable(typeKey).withColor(currentType.getColor()));
    }

    private void saveAndClose() {
        int priority = 0;
        try {
            priority = Integer.parseInt(priorityEdit.getValue());
        } catch (NumberFormatException ignored) {
        }

        PacketDistributor.sendToServer(new C2SUpdateToolSettingsPayload(
            priority,
            groupEdit.getValue(),
            currentMode,
            this.currentType
        ));

        this.onClose();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // 渲染暗色背景
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);

        // 绘制标题
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 20, COLOR_WHITE);

        int centerX = this.width / 2;
        int centerY = this.height / 2;

        // 绘制输入框左侧的标签文本
        renderLabel(graphics, "gui.staticlogistics.label.priority", centerX - 55, centerY - 25 + 6);
        renderLabel(graphics, "gui.staticlogistics.label.group", centerX - 55, centerY + 5 + 6);
    }

    /**
     * 渲染右对齐的黄色标签
     */
    private void renderLabel(GuiGraphics graphics, String translationKey, int x, int y) {
        Component text = Component.translatable(translationKey).withStyle(ChatFormatting.YELLOW);
        int textWidth = this.font.width(text);
        graphics.drawString(this.font, text, x - textWidth, y, COLOR_WHITE, true);
    }
}