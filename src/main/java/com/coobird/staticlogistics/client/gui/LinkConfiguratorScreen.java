package com.coobird.staticlogistics.client.gui;

import com.coobird.staticlogistics.client.ClientLinkCache;
import com.coobird.staticlogistics.common.init.SLDataComponents;
import com.coobird.staticlogistics.common.item.LinkConfiguratorItem;
import com.coobird.staticlogistics.network.c2s.C2SUpdateToolSettingsPayload;
import com.coobird.staticlogistics.storage.GroupService;
import com.coobird.staticlogistics.transfer.TransferType;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class LinkConfiguratorScreen extends Screen {
    private final ItemStack stack;
    private EditBox groupEdit;
    private EditBox priorityEdit;
    private int modeIdx;
    private TransferType currentType;

    public LinkConfiguratorScreen(ItemStack stack) {
        super(Component.translatable("gui.staticlogistics.linker_settings"));
        this.stack = stack;
        this.modeIdx = stack.getOrDefault(SLDataComponents.TOOL_MODE.get(), 0);
        this.currentType = stack.getOrDefault(SLDataComponents.SELECTED_TYPE.get(), TransferType.ITEM);
    }

    public void onDataSynced() {
        if (this.groupEdit != null && this.groupEdit.getValue().isEmpty()) {
            this.groupEdit.setValue(calculateDefaultGroup());
        }
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int centerY = this.height / 2;

        this.addRenderableWidget(Button.builder(getModeComponent(), b -> {
            modeIdx = (modeIdx + 1) % LinkConfiguratorItem.ToolMode.values().length;
            b.setMessage(getModeComponent());
        }).bounds(centerX - 105, centerY - 60, 100, 20).build());

        this.addRenderableWidget(Button.builder(getTypeComponent(), b -> {
            List<TransferType> available = TransferType.getAvailableTypes();

            if (available.isEmpty()) return;

            int currentIndex = available.indexOf(currentType);

            if (currentIndex == -1) {
                currentType = available.getFirst();
            } else {
                currentType = available.get((currentIndex + 1) % available.size());
            }

            b.setMessage(getTypeComponent());
        }).bounds(centerX + 5, centerY - 60, 100, 20).build());

        this.priorityEdit = new EditBox(this.font, centerX - 45, centerY - 20, 150, 20, Component.empty());
        this.priorityEdit.setValue(String.valueOf(stack.getOrDefault(SLDataComponents.PRIORITY.get(), 0)));
        this.priorityEdit.setFilter(s -> s.matches("-?\\d*"));
        this.addRenderableWidget(priorityEdit);

        this.groupEdit = new EditBox(this.font, centerX - 45, centerY + 10, 150, 20, Component.empty());
        String savedGroup = stack.getOrDefault(SLDataComponents.SELECTED_GROUP.get(), "");
        this.groupEdit.setValue(savedGroup.isEmpty() ? calculateDefaultGroup() : savedGroup);
        this.groupEdit.setMaxLength(32);
        this.addRenderableWidget(groupEdit);

        this.addRenderableWidget(Button.builder(
            Component.translatable("gui.staticlogistics.save").withStyle(ChatFormatting.BOLD),
            b -> saveAndClose()
        ).bounds(centerX - 50, centerY + 50, 100, 20).build());
    }

    private String calculateDefaultGroup() {
        Set<String> existing = new HashSet<>();
        ClientLinkCache.getAllLinks().forEach(l -> existing.add(l.groupId()));
        return GroupService.getNextGroupId("1", existing);
    }

    private Component getModeComponent() {
        LinkConfiguratorItem.ToolMode mode = LinkConfiguratorItem.ToolMode.values()[modeIdx];
        return Component.translatable("tooltip.staticlogistics.mode",
            mode.getDisplayName().copy().withStyle(ChatFormatting.AQUA));
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
            priority = Mth.clamp(priority, -128, 127);
        } catch (NumberFormatException ignored) {
        }

        String group = groupEdit.getValue().trim();
        group = group.replaceAll("[^a-zA-Z0-9_\\-]", "");
        if (group.isEmpty() || group.equalsIgnoreCase("default")) group = "1";

        PacketDistributor.sendToServer(new C2SUpdateToolSettingsPayload(priority, group, modeIdx, currentType));
        this.onClose();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        int centerX = this.width / 2;
        int centerY = this.height / 2;

        graphics.fill(centerX - 120, centerY - 85, centerX + 120, centerY + 85, 0xCC000000);
        graphics.renderOutline(centerX - 120, centerY - 85, 240, 170, 0xFFAAAAAA);

        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(this.font, this.title, centerX, centerY - 78, 0xFFFFFF);

        renderLabel(graphics, "gui.staticlogistics.label.priority", centerX - 55, centerY - 20 + 6);
        renderLabel(graphics, "gui.staticlogistics.label.group", centerX - 55, centerY + 10 + 6);
    }

    private void renderLabel(GuiGraphics graphics, String translationKey, int x, int y) {
        Component text = Component.translatable(translationKey).withStyle(ChatFormatting.YELLOW);
        graphics.drawString(this.font, text, x - this.font.width(text) - 4, y, 0xFFE1E1E1, true);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}