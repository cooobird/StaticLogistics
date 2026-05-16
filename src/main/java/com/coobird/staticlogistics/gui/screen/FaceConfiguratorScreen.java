package com.coobird.staticlogistics.gui.screen;

import com.coobird.staticlogistics.api.type.DistributionStrategy;
import com.coobird.staticlogistics.api.type.TransferType;
import com.coobird.staticlogistics.client.util.RenderConstants;
import com.coobird.staticlogistics.core.registration.TransferRegistries;
import com.coobird.staticlogistics.gui.menu.FaceConfiguratorMenu;
import com.coobird.staticlogistics.gui.screen.texture.SLGuiTextures;
import com.coobird.staticlogistics.network.c2s.C2SConfigureFacePayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class FaceConfiguratorScreen extends AbstractConfiguratorScreen<FaceConfiguratorMenu> {
    private EditBox priorityBox;
    private int plusX, plusY, minusX, minusY;
    private static final int BTN_SIZE = 12;

    private static final int LEFT_X = 10;
    private static final int IN_BTN_X = LEFT_X, IN_BTN_Y = 20;
    private static final int IN_COLOR_X = LEFT_X + 20, IN_COLOR_Y = 18;
    private static final int PRIORITY_Y = 65;
    private static final int PRIORITY_TEXT_Y = 80;
    private static final int PRIORITY_BOX_X = 10;
    private static final int PRIORITY_BOX_WIDTH = 36;

    private static final int RIGHT_X = 90;
    private static final int OUT_BTN_X = RIGHT_X, OUT_BTN_Y = 20;
    private static final int OUT_COLOR_X = RIGHT_X + 20, OUT_COLOR_Y = 18;
    private static final int STRAT_X = RIGHT_X, STRAT_Y = 65;

    private static final int INPUT_FILTER_X = FaceConfiguratorMenu.INPUT_FILTER_SLOT_X;
    private static final int INPUT_FILTER_Y = FaceConfiguratorMenu.INPUT_FILTER_SLOT_Y;
    private static final int OUTPUT_FILTER_X = FaceConfiguratorMenu.OUTPUT_FILTER_SLOT_X;
    private static final int OUTPUT_FILTER_Y = FaceConfiguratorMenu.OUTPUT_FILTER_SLOT_Y;

    private static final int FILTER_BTN_WIDTH = SLGuiTextures.Button.Middle.SELECTED_WIDTH;
    private static final int FILTER_BTN_HEIGHT = SLGuiTextures.Button.Middle.SELECTED_HEIGHT;
    private static final int FILTER_BTN_GAP = 2;

    public FaceConfiguratorScreen(FaceConfiguratorMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected void init() {
        this.imageWidth = SLGuiTextures.Background.WIDTH + SLGuiTextures.Background.BY_GROUP_WIDTH + 2;
        this.leftPos = (this.width - this.imageWidth) / 2;
        this.topPos = (this.height - this.imageHeight) / 2;
        super.init();
        this.titleLabelX = this.imageWidth - this.font.width(this.title) - 8;
        this.titleLabelY = 6;
        this.inventoryLabelY = 1000;

        int priorityBoxX = leftPos + PRIORITY_BOX_X;
        int priorityBoxY = topPos + PRIORITY_Y;
        this.priorityBox = new EditBox(this.font,
            priorityBoxX, priorityBoxY,
            PRIORITY_BOX_WIDTH, BTN_SIZE, Component.translatable("gui.staticlogistics.label.priority"));
        this.priorityBox.setBordered(true);
        this.priorityBox.setMaxLength(5);
        this.priorityBox.setValue(String.valueOf(menu.getPriority()));
        this.priorityBox.setResponder(s -> {
            try {
                int p = Integer.parseInt(s);
                if (p != menu.getPriority()) {
                    sendConfigUpdate("priority", p);
                }
            } catch (NumberFormatException ignored) {
            }
        });
        this.addRenderableWidget(this.priorityBox);

        this.plusX = priorityBoxX + PRIORITY_BOX_WIDTH + 2;
        this.plusY = priorityBoxY;
        this.minusX = plusX + BTN_SIZE + 2;
        this.minusY = plusY;

        updateWidgetVisibility();
    }

    @Override
    protected void updateWidgetVisibility() {
        super.updateWidgetVisibility();
        if (this.priorityBox != null) {
            this.priorityBox.setVisible(menu.isGlobalInputEnabled());
        }
    }

    @Override
    protected int getItemHeight() {
        return 18;
    }

    @Override
    protected String getSearchHintKey() {
        return "gui.staticlogistics.search_types";
    }

    @Override
    protected List<TransferType> getTypeList() {
        return new ArrayList<>(TransferRegistries.getAllActive());
    }

    @Override
    protected int getSelectedTypesMask() {
        return menu.getSelectedTypesMask();
    }

    @Override
    protected void renderTypeListItem(GuiGraphics g, TransferType type, int x, int y, boolean isSelected) {
        ItemStack iconStack = type.getIcon();
        g.pose().pushPose();
        g.pose().translate(x + 4, y + 2, 0);
        g.pose().scale(0.75f, 0.75f, 1.0f);
        g.renderFakeItem(iconStack, 0, 0);
        g.pose().popPose();

        Component typeName = Component.translatable(type.translationKey());
        String nameStr = typeName.getString();
        String displayName = font.plainSubstrByWidth(nameStr, 55);
        int color = isSelected ? 0x98FB98 : 0xCCCCCC;
        g.drawString(this.font, displayName, x + 18, y + 5, color, false);
    }

    @Override
    protected void renderHoveredTypeTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        if (this.hoveredType == null) return;
        TransferType type = this.hoveredType;
        List<Component> tooltip = new ArrayList<>();
        int safeColor = type.color() | 0xFF000000;
        tooltip.add(Component.translatable(type.translationKey()).withStyle(style -> style.withColor(safeColor)));
        tooltip.add(Component.translatable(type.translationKey() + ".desc").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.empty());
        tooltip.add(Component.translatable("gui.staticlogistics.tooltip.toggle_type").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        graphics.renderComponentTooltip(this.font, tooltip, mouseX, mouseY);
    }

    @Override
    protected void onTypeClicked(TransferType type) {
        menu.toggleTypeSelection(type);
        syncTypeSelection();
        playClickSound();
    }

    @Override
    public void containerTick() {
        super.containerTick();
        updateWidgetVisibility();
        if (this.priorityBox != null && this.priorityBox.isVisible() && !this.priorityBox.isFocused()) {
            String currentVal = String.valueOf(menu.getPriority());
            if (!Objects.equals(this.priorityBox.getValue(), currentVal)) {
                this.priorityBox.setValue(currentVal);
            }
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderCustomTooltips(graphics, mouseX, mouseY);
        this.renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderCustomContent(GuiGraphics graphics, int mouseX, int mouseY) {
        renderFilterSlots(graphics);
        renderFilterHints(graphics);

        renderToggleButton(graphics, IN_BTN_X, IN_BTN_Y, menu.isGlobalInputEnabled());
        int inChannel = menu.getInputChannel();
        int inColorIdx = (inChannel >= 1 && inChannel <= 16) ? inChannel - 1 : 0;
        renderColorButton(graphics, IN_COLOR_X, IN_COLOR_Y, inColorIdx);
        if (menu.isGlobalInputEnabled()) {
            Component priorityLabel = Component.translatable("gui.staticlogistics.label.priority");
            graphics.drawString(this.font, priorityLabel,
                leftPos + PRIORITY_BOX_X, topPos + PRIORITY_TEXT_Y,
                0xFFFFFFFF, false);

            drawOperatorButton(graphics, plusX, plusY, SLGuiTextures.Operator.ADD_U, SLGuiTextures.Operator.ADD_V, mouseX, mouseY);
            drawOperatorButton(graphics, minusX, minusY, SLGuiTextures.Operator.REDUCE_U, SLGuiTextures.Operator.REDUCE_V, mouseX, mouseY);
            if (mouseX >= plusX && mouseX < plusX + BTN_SIZE && mouseY >= plusY && mouseY < plusY + BTN_SIZE) {
                graphics.renderTooltip(font, Component.translatable("gui.staticlogistics.priority.tooltip"), mouseX, mouseY);
            } else if (mouseX >= minusX && mouseX < minusX + BTN_SIZE && mouseY >= minusY && mouseY < minusY + BTN_SIZE) {
                graphics.renderTooltip(font, Component.translatable("gui.staticlogistics.priority.tooltip"), mouseX, mouseY);
            }
        }

        renderToggleButton(graphics, OUT_BTN_X, OUT_BTN_Y, menu.isGlobalOutputEnabled());
        int outChannel = menu.getOutputChannel();
        int outColorIdx = (outChannel >= 1 && outChannel <= 16) ? outChannel - 1 : 0;
        renderColorButton(graphics, OUT_COLOR_X, OUT_COLOR_Y, outColorIdx);
        if (menu.isGlobalOutputEnabled()) {
            renderStrategyButton(graphics, mouseX, mouseY);
        }

        if (menu.isGlobalInputEnabled() && !menu.getSlot(0).getItem().isEmpty()) {
            renderFilterConfigButtonAt(graphics, mouseX, mouseY, INPUT_FILTER_X, INPUT_FILTER_Y, true);
        }
        if (menu.isGlobalOutputEnabled() && !menu.getSlot(1).getItem().isEmpty()) {
            renderFilterConfigButtonAt(graphics, mouseX, mouseY, OUTPUT_FILTER_X, OUTPUT_FILTER_Y, false);
        }
    }

    private void drawOperatorButton(GuiGraphics g, int x, int y, int iconU, int iconV, int mx, int my) {
        boolean hover = mx >= x && mx < x + BTN_SIZE && my >= y && my < y + BTN_SIZE;
        int bgU = SLGuiTextures.Button.Middle.DISABLED_U;
        int bgV = SLGuiTextures.Button.Middle.DISABLED_V;
        int bgW = SLGuiTextures.Button.Middle.WIDTH;
        int bgH = SLGuiTextures.Button.Middle.HEIGHT;
        g.blit(SLGuiTextures.GUI_ATLAS, x, y, BTN_SIZE, BTN_SIZE, bgU, bgV, bgW, bgH, SLGuiTextures.GUI_WIDTH, SLGuiTextures.GUI_HEIGHT);
        g.blit(SLGuiTextures.GUI_ATLAS, x, y, iconU, iconV, BTN_SIZE, BTN_SIZE, SLGuiTextures.GUI_WIDTH, SLGuiTextures.GUI_HEIGHT);
        if (hover) {
            g.fill(x, y, x + BTN_SIZE, y + BTN_SIZE, 0x22FFFFFF);
        }
    }

    private void adjustPriority(int delta) {
        if (delta == 0) return;
        if (hasShiftDown() && hasControlDown()) delta *= 64;
        else if (hasShiftDown()) delta *= 10;
        else if (hasControlDown()) delta *= 5;
        int newPriority = menu.getPriority() + delta;
        newPriority = Math.max(-9999, Math.min(9999, newPriority));
        sendConfigUpdate("priority", newPriority);
    }

    private void renderFilterConfigButtonAt(GuiGraphics g, int mx, int my, int slotX, int slotY, boolean isInput) {
        int btnX = leftPos + slotX + 16 + FILTER_BTN_GAP;
        int btnY = topPos + slotY + (16 - FILTER_BTN_HEIGHT) / 2;

        boolean hover = mx >= btnX && mx < btnX + FILTER_BTN_WIDTH &&
            my >= btnY && my < btnY + FILTER_BTN_HEIGHT;

        int bgU, bgV, bw, bh;
        if (hover) {
            bgU = SLGuiTextures.Button.Middle.SELECTED_U;
            bgV = SLGuiTextures.Button.Middle.SELECTED_V;
            bw = SLGuiTextures.Button.Middle.SELECTED_WIDTH;
            bh = SLGuiTextures.Button.Middle.SELECTED_HEIGHT;
        } else {
            bgU = SLGuiTextures.Button.Middle.DISABLED_U;
            bgV = SLGuiTextures.Button.Middle.DISABLED_V;
            bw = SLGuiTextures.Button.Middle.WIDTH;
            bh = SLGuiTextures.Button.Middle.HEIGHT;
        }

        g.blit(SLGuiTextures.GUI_ATLAS, btnX, btnY, bgU, bgV, bw, bh,
            SLGuiTextures.GUI_WIDTH, SLGuiTextures.GUI_HEIGHT);

        int iconU = hover ? SLGuiTextures.Icon.SELECTED_U : SLGuiTextures.Icon.NORMAL_U;
        int iconV = SLGuiTextures.Icon.CONFIG_V;
        int iconW = 19, iconH = 15;
        int iconX = btnX + (bw - iconW) / 2;
        int iconY = btnY + (bh - iconH) / 2 - 1;
        g.blit(SLGuiTextures.GUI_ATLAS, iconX, iconY, iconU, iconV, iconW, iconH,
            SLGuiTextures.GUI_WIDTH, SLGuiTextures.GUI_HEIGHT);
    }

    private void renderFilterSlots(GuiGraphics graphics) {
        graphics.blit(GUI_TEXTURE, leftPos + INPUT_FILTER_X, topPos + INPUT_FILTER_Y,
            16, 16,
            SLGuiTextures.Upgrade.U, SLGuiTextures.Upgrade.V,
            SLGuiTextures.Upgrade.WIDTH, SLGuiTextures.Upgrade.HEIGHT,
            SLGuiTextures.GUI_WIDTH, SLGuiTextures.GUI_HEIGHT);
        graphics.blit(GUI_TEXTURE, leftPos + OUTPUT_FILTER_X, topPos + OUTPUT_FILTER_Y,
            16, 16,
            SLGuiTextures.Upgrade.U, SLGuiTextures.Upgrade.V,
            SLGuiTextures.Upgrade.WIDTH, SLGuiTextures.Upgrade.HEIGHT,
            SLGuiTextures.GUI_WIDTH, SLGuiTextures.GUI_HEIGHT);
    }

    private void renderFilterHints(GuiGraphics g) {
        Component inputHint = Component.translatable("gui.staticlogistics.hint.input_filter");
        Component outputHint = Component.translatable("gui.staticlogistics.hint.output_filter");
        int hintX;

        hintX = leftPos + INPUT_FILTER_X - 2;
        g.pose().pushPose();
        g.pose().scale(0.8f, 0.8f, 0.8f);
        g.drawString(this.font, inputHint,
            (int) (hintX / 0.8f), (int) ((topPos + INPUT_FILTER_Y + 18) / 0.8f),
            0x88FFFFFF, false);
        g.pose().popPose();

        hintX = leftPos + OUTPUT_FILTER_X - 2;
        g.pose().pushPose();
        g.pose().scale(0.8f, 0.8f, 0.8f);
        g.drawString(this.font, outputHint,
            (int) (hintX / 0.8f), (int) ((topPos + OUTPUT_FILTER_Y + 18) / 0.8f),
            0x88FFFFFF, false);
        g.pose().popPose();
    }

    private void renderToggleButton(GuiGraphics g, int x, int y, boolean enabled) {
        int bx = leftPos + x;
        int by = topPos + y;
        int u = enabled ? SLGuiTextures.Button.Push.U : SLGuiTextures.Button.Push.DISABLED_U;
        int v = enabled ? SLGuiTextures.Button.Push.V : SLGuiTextures.Button.Push.DISABLED_V;
        g.blit(GUI_TEXTURE, bx, by, u, v,
            SLGuiTextures.Button.Push.WIDTH, SLGuiTextures.Button.Push.HEIGHT,
            SLGuiTextures.GUI_WIDTH, SLGuiTextures.GUI_HEIGHT);
    }

    private void renderColorButton(GuiGraphics g, int x, int y, int colorIdx) {
        int bx = leftPos + x;
        int by = topPos + y;
        g.fill(bx, by, bx + 14, by + 14, 0xFF000000);
        g.fill(bx + 1, by + 1, bx + 13, by + 13,
            (RenderConstants.DYE_COLORS[colorIdx] & 0xFFFFFF) | 0xFF000000);
    }

    private int getStrategyButtonWidth() {
        return this.font.width(menu.getStrategy().getDisplayName()) + 12;
    }

    private void renderStrategyButton(GuiGraphics g, int mx, int my) {
        Component label = menu.getStrategy().getDisplayName();
        int textWidth = this.font.width(label);
        int totalWidth = Math.max(textWidth + 12, SLGuiTextures.Button.Middle.WIDTH);
        int bx = leftPos + STRAT_X, by = topPos + STRAT_Y;
        int height = SLGuiTextures.Button.Middle.HEIGHT;
        boolean hover = isMouseOver(mx, my, STRAT_X, STRAT_Y, totalWidth, height);
        int u = hover ? 350 : 372;
        int v = 2;
        g.blit(GUI_TEXTURE, bx, by, u, v, 2, height, SLGuiTextures.GUI_WIDTH, SLGuiTextures.GUI_HEIGHT);
        g.blit(GUI_TEXTURE, bx + totalWidth - 2, by, u + SLGuiTextures.Button.Middle.WIDTH - 2, v, 2, height, SLGuiTextures.GUI_WIDTH, SLGuiTextures.GUI_HEIGHT);
        g.blit(GUI_TEXTURE, bx + 2, by, totalWidth - 4, height, u + 2, v, 1, height, SLGuiTextures.GUI_WIDTH, SLGuiTextures.GUI_HEIGHT);
        g.drawString(this.font, label, bx + (totalWidth - textWidth) / 2, by + (height - 8) / 2, 0xFFFFFFFF, false);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (menu.isGlobalInputEnabled()) {
            if (mx >= plusX && mx < plusX + BTN_SIZE && my >= plusY && my < plusY + BTN_SIZE) {
                adjustPriority(1);
                playClickSound();
                return true;
            }
            if (mx >= minusX && mx < minusX + BTN_SIZE && my >= minusY && my < minusY + BTN_SIZE) {
                adjustPriority(-1);
                playClickSound();
                return true;
            }
        }

        boolean handled = false;

        if (menu.isGlobalInputEnabled() && !menu.getSlot(0).getItem().isEmpty() && isFilterBtnHover(mx, my, INPUT_FILTER_X, INPUT_FILTER_Y)) {
            CompoundTag tag = new CompoundTag();
            tag.putBoolean("open_filter", true);
            tag.putBoolean("is_input", true);
            PacketDistributor.sendToServer(new C2SConfigureFacePayload(menu.getPos(), menu.getFace(), tag));
            playClickSound();
            handled = true;
        } else if (menu.isGlobalOutputEnabled() && !menu.getSlot(1).getItem().isEmpty() && isFilterBtnHover(mx, my, OUTPUT_FILTER_X, OUTPUT_FILTER_Y)) {
            CompoundTag tag = new CompoundTag();
            tag.putBoolean("open_filter", true);
            tag.putBoolean("is_input", false);
            PacketDistributor.sendToServer(new C2SConfigureFacePayload(menu.getPos(), menu.getFace(), tag));
            playClickSound();
            handled = true;
        } else if (isMouseOver(mx, my, IN_BTN_X, IN_BTN_Y, SLGuiTextures.Button.Push.WIDTH, SLGuiTextures.Button.Push.HEIGHT)) {
            sendConfigUpdate("globalInput", !menu.isGlobalInputEnabled());
            playClickSound();
            handled = true;
        } else if (isMouseOver(mx, my, OUT_BTN_X, OUT_BTN_Y, SLGuiTextures.Button.Push.WIDTH, SLGuiTextures.Button.Push.HEIGHT)) {
            sendConfigUpdate("globalOutput", !menu.isGlobalOutputEnabled());
            playClickSound();
            handled = true;
        } else if (isMouseOver(mx, my, IN_COLOR_X, IN_COLOR_Y, 14, 14)) {
            int current = menu.getInputChannel();
            int nextChannel;
            if (button == 1) {
                nextChannel = current - 1;
                if (nextChannel < 1) nextChannel = 16;
            } else {
                nextChannel = current + 1;
                if (nextChannel > 16) nextChannel = 1;
            }
            sendConfigUpdate("inputChannel", nextChannel);
            playClickSound();
            handled = true;
        } else if (isMouseOver(mx, my, OUT_COLOR_X, OUT_COLOR_Y, 14, 14)) {
            int current = menu.getOutputChannel();
            int nextChannel;
            if (button == 1) {
                nextChannel = current - 1;
                if (nextChannel < 1) nextChannel = 16;
            } else {
                nextChannel = current + 1;
                if (nextChannel > 16) nextChannel = 1;
            }
            sendConfigUpdate("outputChannel", nextChannel);
            playClickSound();
            handled = true;
        } else if (menu.isGlobalOutputEnabled() && isMouseOver(mx, my, STRAT_X, STRAT_Y, getStrategyButtonWidth(), SLGuiTextures.Button.Middle.HEIGHT)) {
            int nextOrd = (menu.getStrategy().ordinal() + 1) % DistributionStrategy.values().length;
            sendConfigUpdate("strategy", DistributionStrategy.values()[nextOrd].getSerializedName());
            playClickSound();
            handled = true;
        }

        if (!handled) {
            handled = super.mouseClicked(mx, my, button);
        }

        if (this.priorityBox != null) {
            boolean clickedOnPriorityBox = this.priorityBox.isMouseOver(mx, my);
            if (!clickedOnPriorityBox && this.priorityBox.isFocused()) {
                this.priorityBox.setFocused(false);
            }
        }

        return handled;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        if (menu.isGlobalOutputEnabled()) {
            if (super.mouseScrolled(mx, my, dx, dy)) return true;
        }
        return super.mouseScrolled(mx, my, dx, dy);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        return super.mouseReleased(mx, my, button);
    }

    private boolean isFilterBtnHover(double mx, double my, int slotX, int slotY) {
        int btnX = leftPos + slotX + 16 + FILTER_BTN_GAP;
        int btnY = topPos + slotY + (16 - FILTER_BTN_HEIGHT) / 2;
        return mx >= btnX && mx < btnX + FILTER_BTN_WIDTH &&
            my >= btnY && my < btnY + FILTER_BTN_HEIGHT;
    }

    private void renderCustomTooltips(GuiGraphics g, int mx, int my) {
        if (isMouseOver(mx, my, IN_BTN_X, IN_BTN_Y, SLGuiTextures.Button.Push.WIDTH, SLGuiTextures.Button.Push.HEIGHT)) {
            g.renderTooltip(this.font, Component.translatable("gui.mode.staticlogistics.input"), mx, my);
        }
        if (isMouseOver(mx, my, OUT_BTN_X, OUT_BTN_Y, SLGuiTextures.Button.Push.WIDTH, SLGuiTextures.Button.Push.HEIGHT)) {
            g.renderTooltip(this.font, Component.translatable("gui.mode.staticlogistics.output"), mx, my);
        }
        if (menu.isGlobalOutputEnabled() && isMouseOver(mx, my, STRAT_X, STRAT_Y, getStrategyButtonWidth(), SLGuiTextures.Button.Middle.HEIGHT)) {
            g.renderComponentTooltip(this.font, List.of(
                Component.translatable("gui.staticlogistics.strategy"),
                menu.getStrategy().getDisplayName().copy().withStyle(ChatFormatting.AQUA)), mx, my);
        }

        int inputSlotX = leftPos + INPUT_FILTER_X;
        int inputSlotY = topPos + INPUT_FILTER_Y;
        if (menu.isGlobalInputEnabled() && mx >= inputSlotX && mx < inputSlotX + 16 && my >= inputSlotY && my < inputSlotY + 16) {
            ItemStack stack = menu.getSlot(0).getItem();
            if (!stack.isEmpty()) g.renderTooltip(font, stack, mx, my);
        }
        int outputSlotX = leftPos + OUTPUT_FILTER_X;
        int outputSlotY = topPos + OUTPUT_FILTER_Y;
        if (menu.isGlobalOutputEnabled() && mx >= outputSlotX && mx < outputSlotX + 16 && my >= outputSlotY && my < outputSlotY + 16) {
            ItemStack stack = menu.getSlot(1).getItem();
            if (!stack.isEmpty()) g.renderTooltip(font, stack, mx, my);
        }

        if (menu.isGlobalInputEnabled() && !menu.getSlot(0).getItem().isEmpty() && isFilterBtnHover(mx, my, INPUT_FILTER_X, INPUT_FILTER_Y)) {
            g.renderTooltip(this.font, Component.translatable("gui.staticlogistics.open_filter"), mx, my);
        }
        if (menu.isGlobalOutputEnabled() && !menu.getSlot(1).getItem().isEmpty() && isFilterBtnHover(mx, my, OUTPUT_FILTER_X, OUTPUT_FILTER_Y)) {
            g.renderTooltip(this.font, Component.translatable("gui.staticlogistics.open_filter"), mx, my);
        }
    }

    private void sendConfigUpdate(String key, Object value) {
        CompoundTag tag = new CompoundTag();
        switch (value) {
            case String s -> tag.putString(key, s);
            case Integer i -> tag.putInt(key, i);
            case Boolean b -> tag.putBoolean(key, b);
            default -> {
            }
        }
        PacketDistributor.sendToServer(new C2SConfigureFacePayload(menu.getPos(), menu.getFace(), tag));
    }

    private void syncTypeSelection() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("selected_types_mask", menu.getSelectedTypesMask());
        PacketDistributor.sendToServer(new C2SConfigureFacePayload(menu.getPos(), menu.getFace(), tag));
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 257 || keyCode == 335) {
            if (this.priorityBox.isFocused()) {
                this.priorityBox.setFocused(false);
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}