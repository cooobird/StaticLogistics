package com.coobird.staticlogistics.gui.screen;

import com.coobird.staticlogistics.api.type.TransferType;
import com.coobird.staticlogistics.core.registration.TransferRegistries;
import com.coobird.staticlogistics.gui.menu.FaceConfiguratorMenu;
import com.coobird.staticlogistics.gui.screen.component.FaceControls;
import com.coobird.staticlogistics.gui.screen.texture.SLGuiTextures;
import com.coobird.staticlogistics.network.c2s.C2SConfigureFacePayload;
import com.coobird.staticlogistics.network.c2s.C2SOpenContainerConfigPayload;
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

/**
 * Face 配置界面 — 使用 {@link FaceControls} 组件化。
 */
public class FaceConfiguratorScreen extends AbstractConfiguratorScreen<FaceConfiguratorMenu> {

    private EditBox priorityBox;
    private int plusX, plusY, minusX, minusY;

    private static final int LEFT_X = 10;
    private static final int IN_BTN_X = LEFT_X, IN_BTN_Y = 20;
    private static final int IN_COLOR_X = LEFT_X + 20, IN_COLOR_Y = 18;
    private static final int PRIORITY_Y = 65, PRIORITY_TEXT_Y = 80;
    private static final int PRIORITY_BOX_X = 10, PRIORITY_BOX_WIDTH = 36;

    private static final int RIGHT_X = 90;
    private static final int OUT_BTN_X = RIGHT_X, OUT_BTN_Y = 20;
    private static final int OUT_COLOR_X = RIGHT_X + 20, OUT_COLOR_Y = 18;
    private static final int STRAT_X = 138, STRAT_Y = 18;
    private static final int EXTRACT_X = 138, EXTRACT_Y = 38;
    private static final int UPGRADE_BTN_X = 138, UPGRADE_BTN_Y = 60;

    private static final int INPUT_FILTER_X = FaceConfiguratorMenu.INPUT_FILTER_SLOT_X;
    private static final int INPUT_FILTER_Y = FaceConfiguratorMenu.INPUT_FILTER_SLOT_Y;
    private static final int OUTPUT_FILTER_X = FaceConfiguratorMenu.OUTPUT_FILTER_SLOT_X;
    private static final int OUTPUT_FILTER_Y = FaceConfiguratorMenu.OUTPUT_FILTER_SLOT_Y;

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

        int px = leftPos + PRIORITY_BOX_X, py = topPos + PRIORITY_Y;
        this.priorityBox = new EditBox(this.font, px, py, PRIORITY_BOX_WIDTH,
            FaceControls.BTN_SIZE, Component.translatable("gui.staticlogistics.label.priority"));
        this.priorityBox.setBordered(true);
        this.priorityBox.setMaxLength(10);
        this.priorityBox.setFilter(s -> s.isEmpty() || s.matches("-?[0-9]*"));
        this.priorityBox.setValue(String.valueOf(menu.getPriority()));
        this.priorityBox.setResponder(s -> {
            try {
                int p = Integer.parseInt(s);
                if (p != menu.getPriority()) sendConfigUpdate("priority", p);
            } catch (NumberFormatException ignored) {
            }
        });
        this.addRenderableWidget(this.priorityBox);

        this.plusX = px + PRIORITY_BOX_WIDTH + 2;
        this.plusY = py;
        this.minusX = plusX + FaceControls.BTN_SIZE + 2;
        this.minusY = plusY;

        updateWidgetVisibility();
    }

    @Override
    protected void updateWidgetVisibility() {
        super.updateWidgetVisibility();
        if (this.priorityBox != null)
            this.priorityBox.setVisible(menu.isGlobalInputEnabled());
    }

    @Override
    protected int getItemHeight() {
        return 18;
    }

    @Override
    protected boolean shouldShowTypePanel() {
        return menu.isGlobalOutputEnabled();
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
        ItemStack icon = type.getIcon();
        g.pose().pushPose();
        g.pose().translate(x + 4, y + 2, 0);
        g.pose().scale(0.75f, 0.75f, 1.0f);
        g.renderFakeItem(icon, 0, 0);
        g.pose().popPose();
        String name = font.plainSubstrByWidth(
            Component.translatable(type.translationKey()).getString(), 55);
        g.drawString(this.font, name, x + 18, y + 5,
            isSelected ? 0x98FB98 : 0xCCCCCC, false);
    }

    @Override
    protected void renderHoveredTypeTooltip(GuiGraphics g, int mx, int my) {
        if (this.hoveredType == null) return;
        TransferType t = this.hoveredType;
        List<Component> tooltip = new ArrayList<>();
        tooltip.add(Component.translatable(t.translationKey())
            .withStyle(s -> s.withColor(t.color() | 0xFF000000)));
        tooltip.add(Component.translatable(t.translationKey() + ".desc")
            .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.empty());
        tooltip.add(Component.translatable("gui.staticlogistics.tooltip.toggle_type")
            .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        g.renderComponentTooltip(this.font, tooltip, mx, my);
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
        if (this.priorityBox != null && this.priorityBox.isVisible()
            && !this.priorityBox.isFocused()) {
            String v = String.valueOf(menu.getPriority());
            if (!Objects.equals(this.priorityBox.getValue(), v))
                this.priorityBox.setValue(v);
        }
    }

    @Override
    protected void renderCustomContent(GuiGraphics g, int mx, int my) {
        renderFilterSlots(g);
        renderFilterHints(g);

        FaceControls.renderToggle(g, this.font, leftPos, topPos,
            IN_BTN_X, IN_BTN_Y, menu.isGlobalInputEnabled(), GUI_TEXTURE);
        FaceControls.renderColor(g, leftPos, topPos,
            IN_COLOR_X, IN_COLOR_Y, menu.getInputChannel());

        if (menu.isGlobalInputEnabled()) {
            g.drawString(this.font,
                Component.translatable("gui.staticlogistics.label.priority"),
                leftPos + PRIORITY_BOX_X, topPos + PRIORITY_TEXT_Y,
                0xFFFFFFFF, false);
            FaceControls.renderOperator(g, plusX, plusY,
                SLGuiTextures.Operator.ADD_U, SLGuiTextures.Operator.ADD_V,
                mx, my, GUI_TEXTURE);
            FaceControls.renderOperator(g, minusX, minusY,
                SLGuiTextures.Operator.REDUCE_U, SLGuiTextures.Operator.REDUCE_V,
                mx, my, GUI_TEXTURE);
            if (mx >= plusX && mx < plusX + FaceControls.BTN_SIZE
                && my >= plusY && my < plusY + FaceControls.BTN_SIZE) {
                g.renderTooltip(font,
                    Component.translatable("gui.staticlogistics.priority.tooltip"), mx, my);
            } else if (mx >= minusX && mx < minusX + FaceControls.BTN_SIZE
                && my >= minusY && my < minusY + FaceControls.BTN_SIZE) {
                g.renderTooltip(font,
                    Component.translatable("gui.staticlogistics.priority.tooltip"), mx, my);
            }
        }

        // 输出开关 + 颜色
        FaceControls.renderToggle(g, this.font, leftPos, topPos,
            OUT_BTN_X, OUT_BTN_Y, menu.isGlobalOutputEnabled(), GUI_TEXTURE);
        FaceControls.renderColor(g, leftPos, topPos,
            OUT_COLOR_X, OUT_COLOR_Y, menu.getOutputChannel());

        if (menu.isGlobalOutputEnabled()) {
            FaceControls.renderChoiceButton(g, this.font, leftPos, topPos,
                STRAT_X, STRAT_Y, menu.getStrategy().getDisplayName(),
                mx, my, GUI_TEXTURE);
            FaceControls.renderChoiceButton(g, this.font, leftPos, topPos,
                EXTRACT_X, EXTRACT_Y, menu.getExtractionMode().getDisplayName(),
                mx, my, GUI_TEXTURE);

            Component upgradeLabel = Component.translatable("gui.staticlogistics.upgrade_config");
            boolean upgradeHover = FaceControls.isTextButtonHovered(mx, my,
                leftPos, topPos, UPGRADE_BTN_X, UPGRADE_BTN_Y,
                upgradeLabel, this.font);
            FaceControls.renderTextButton(g, this.font, leftPos, topPos,
                UPGRADE_BTN_X, UPGRADE_BTN_Y, upgradeLabel, upgradeHover, GUI_TEXTURE);
        }

        // 过滤配置按钮
        if (menu.isGlobalInputEnabled()
            && !menu.getSlot(0).getItem().isEmpty()) {
            boolean hover = FaceControls.isFilterConfigBtnHovered(mx, my,
                leftPos, topPos, INPUT_FILTER_X, INPUT_FILTER_Y);
            FaceControls.renderFilterConfigBtn(g, leftPos, topPos,
                INPUT_FILTER_X, INPUT_FILTER_Y, hover, GUI_TEXTURE);
        }
        if (menu.isGlobalOutputEnabled()
            && !menu.getSlot(1).getItem().isEmpty()) {
            boolean hover = FaceControls.isFilterConfigBtnHovered(mx, my,
                leftPos, topPos, OUTPUT_FILTER_X, OUTPUT_FILTER_Y);
            FaceControls.renderFilterConfigBtn(g, leftPos, topPos,
                OUTPUT_FILTER_X, OUTPUT_FILTER_Y, hover, GUI_TEXTURE);
        }
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        renderCustomTooltips(g, mx, my);
        this.renderTooltip(g, mx, my);
    }

    private void renderFilterSlots(GuiGraphics g) {
        if (menu.isGlobalInputEnabled())
            g.blit(GUI_TEXTURE, leftPos + INPUT_FILTER_X, topPos + INPUT_FILTER_Y,
                16, 16, SLGuiTextures.Upgrade.U, SLGuiTextures.Upgrade.V,
                SLGuiTextures.Upgrade.WIDTH, SLGuiTextures.Upgrade.HEIGHT,
                SLGuiTextures.GUI_WIDTH, SLGuiTextures.GUI_HEIGHT);
        if (menu.isGlobalOutputEnabled())
            g.blit(GUI_TEXTURE, leftPos + OUTPUT_FILTER_X, topPos + OUTPUT_FILTER_Y,
                16, 16, SLGuiTextures.Upgrade.U, SLGuiTextures.Upgrade.V,
                SLGuiTextures.Upgrade.WIDTH, SLGuiTextures.Upgrade.HEIGHT,
                SLGuiTextures.GUI_WIDTH, SLGuiTextures.GUI_HEIGHT);
    }

    private void renderFilterHints(GuiGraphics g) {
        if (menu.isGlobalInputEnabled()) {
            Component h = Component.translatable("gui.staticlogistics.hint.input_filter");
            g.pose().pushPose();
            g.pose().scale(0.8f, 0.8f, 0.8f);
            g.drawString(this.font, h,
                (int) ((leftPos + INPUT_FILTER_X - 2) / 0.8f),
                (int) ((topPos + INPUT_FILTER_Y + 18) / 0.8f),
                0x88FFFFFF, false);
            g.pose().popPose();
        }
        if (menu.isGlobalOutputEnabled()) {
            Component h = Component.translatable("gui.staticlogistics.hint.output_filter");
            g.pose().pushPose();
            g.pose().scale(0.8f, 0.8f, 0.8f);
            g.drawString(this.font, h,
                (int) ((leftPos + OUTPUT_FILTER_X - 2) / 0.8f),
                (int) ((topPos + OUTPUT_FILTER_Y + 18) / 0.8f),
                0x88FFFFFF, false);
            g.pose().popPose();
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        boolean handled = false;

        if (menu.isGlobalInputEnabled()) {
            if (mx >= plusX && mx < plusX + FaceControls.BTN_SIZE
                && my >= plusY && my < plusY + FaceControls.BTN_SIZE) {
                adjustPriority(1);
                playClickSound();
                return true;
            }
            if (mx >= minusX && mx < minusX + FaceControls.BTN_SIZE
                && my >= minusY && my < minusY + FaceControls.BTN_SIZE) {
                adjustPriority(-1);
                playClickSound();
                return true;
            }
        }

        // 过滤配置按钮
        if (menu.isGlobalInputEnabled() && !menu.getSlot(0).getItem().isEmpty()
            && FaceControls.isFilterConfigBtnHovered(mx, my, leftPos, topPos,
            INPUT_FILTER_X, INPUT_FILTER_Y)) {
            sendConfigUpdate("open_filter", "input");
            playClickSound();
            handled = true;
        } else if (menu.isGlobalOutputEnabled() && !menu.getSlot(1).getItem().isEmpty()
            && FaceControls.isFilterConfigBtnHovered(mx, my, leftPos, topPos,
            OUTPUT_FILTER_X, OUTPUT_FILTER_Y)) {
            sendConfigUpdate("open_filter", "output");
            playClickSound();
            handled = true;
        } else if (FaceControls.isToggleHovered(mx, my, leftPos, topPos,
            IN_BTN_X, IN_BTN_Y)) {
            sendConfigUpdate("globalInput", !menu.isGlobalInputEnabled());
            playClickSound();
            handled = true;
        } else if (FaceControls.isToggleHovered(mx, my, leftPos, topPos,
            OUT_BTN_X, OUT_BTN_Y)) {
            sendConfigUpdate("globalOutput", !menu.isGlobalOutputEnabled());
            playClickSound();
            handled = true;
        } else if (FaceControls.isColorHovered(mx, my, leftPos, topPos,
            IN_COLOR_X, IN_COLOR_Y)) {
            int cur = menu.getInputChannel();
            int next = button == 1
                ? (cur - 1 < 1 ? 16 : cur - 1)
                : (cur + 1 > 16 ? 1 : cur + 1);
            sendConfigUpdate("inputChannel", next);
            playClickSound();
            handled = true;
        } else if (FaceControls.isColorHovered(mx, my, leftPos, topPos,
            OUT_COLOR_X, OUT_COLOR_Y)) {
            int cur = menu.getOutputChannel();
            int next = button == 1
                ? (cur - 1 < 1 ? 16 : cur - 1)
                : (cur + 1 > 16 ? 1 : cur + 1);
            sendConfigUpdate("outputChannel", next);
            playClickSound();
            handled = true;
        } else if (menu.isGlobalOutputEnabled()
            && FaceControls.isChoiceHovered(mx, my, leftPos, topPos,
            STRAT_X, STRAT_Y, menu.getStrategy().getDisplayName(), this.font)) {
            var vals = com.coobird.staticlogistics.api.type.DistributionStrategy.values();
            int ord = menu.getStrategy().ordinal();
            int next = button == 1
                ? (ord - 1 + vals.length) % vals.length
                : (ord + 1) % vals.length;
            sendConfigUpdate("strategy", vals[next].getSerializedName());
            playClickSound();
            handled = true;
        } else if (menu.isGlobalOutputEnabled()
            && FaceControls.isChoiceHovered(mx, my, leftPos, topPos,
            EXTRACT_X, EXTRACT_Y,
            menu.getExtractionMode().getDisplayName(), this.font)) {
            var vals = com.coobird.staticlogistics.api.type.ExtractionMode.values();
            int ord = menu.getExtractionMode().ordinal();
            int next = button == 1
                ? (ord - 1 + vals.length) % vals.length
                : (ord + 1) % vals.length;
            sendConfigUpdate("extractionMode", vals[next].getSerializedName());
            playClickSound();
            handled = true;
        }

        if (!handled && menu.isGlobalOutputEnabled()) {
            Component label = Component.translatable("gui.staticlogistics.upgrade_config");
            if (FaceControls.isTextButtonHovered(mx, my, leftPos, topPos,
                UPGRADE_BTN_X, UPGRADE_BTN_Y, label, this.font)) {
                PacketDistributor.sendToServer(new C2SOpenContainerConfigPayload(
                    menu.getPos(), menu.getFace()));
                playClickSound();
                handled = true;
            }
        }

        if (!handled) handled = super.mouseClicked(mx, my, button);

        if (this.priorityBox != null
            && !this.priorityBox.isMouseOver(mx, my)
            && this.priorityBox.isFocused()) {
            this.priorityBox.setFocused(false);
        }
        return handled;
    }

    private void renderCustomTooltips(GuiGraphics g, int mx, int my) {
        if (FaceControls.isToggleHovered(mx, my, leftPos, topPos,
            IN_BTN_X, IN_BTN_Y))
            g.renderTooltip(this.font,
                Component.translatable("gui.mode.staticlogistics.input"), mx, my);
        if (FaceControls.isToggleHovered(mx, my, leftPos, topPos,
            OUT_BTN_X, OUT_BTN_Y))
            g.renderTooltip(this.font,
                Component.translatable("gui.mode.staticlogistics.output"), mx, my);
        if (menu.isGlobalOutputEnabled()
            && FaceControls.isChoiceHovered(mx, my, leftPos, topPos,
            STRAT_X, STRAT_Y, menu.getStrategy().getDisplayName(), this.font))
            g.renderComponentTooltip(this.font, List.of(
                    Component.translatable("gui.staticlogistics.strategy"),
                    menu.getStrategy().getDisplayName().copy().withStyle(ChatFormatting.AQUA)),
                mx, my);
        if (menu.isGlobalOutputEnabled()
            && FaceControls.isChoiceHovered(mx, my, leftPos, topPos,
            EXTRACT_X, EXTRACT_Y,
            menu.getExtractionMode().getDisplayName(), this.font))
            g.renderComponentTooltip(this.font, List.of(
                Component.translatable("gui.staticlogistics.extraction_mode"),
                menu.getExtractionMode().getDisplayName().copy()
                    .withStyle(ChatFormatting.AQUA)), mx, my);

        // 槽位 tooltip
        if (menu.isGlobalInputEnabled()) {
            int sx = leftPos + INPUT_FILTER_X, sy = topPos + INPUT_FILTER_Y;
            if (mx >= sx && mx < sx + 16 && my >= sy && my < sy + 16) {
                ItemStack st = menu.getSlot(0).getItem();
                if (!st.isEmpty()) g.renderTooltip(font, st, mx, my);
            }
        }
        if (menu.isGlobalOutputEnabled()) {
            int sx = leftPos + OUTPUT_FILTER_X, sy = topPos + OUTPUT_FILTER_Y;
            if (mx >= sx && mx < sx + 16 && my >= sy && my < sy + 16) {
                ItemStack st = menu.getSlot(1).getItem();
                if (!st.isEmpty()) g.renderTooltip(font, st, mx, my);
            }
        }

        if (menu.isGlobalInputEnabled() && !menu.getSlot(0).getItem().isEmpty()
            && FaceControls.isFilterConfigBtnHovered(mx, my, leftPos, topPos,
            INPUT_FILTER_X, INPUT_FILTER_Y))
            g.renderTooltip(this.font,
                Component.translatable("gui.staticlogistics.open_filter"), mx, my);
        if (menu.isGlobalOutputEnabled() && !menu.getSlot(1).getItem().isEmpty()
            && FaceControls.isFilterConfigBtnHovered(mx, my, leftPos, topPos,
            OUTPUT_FILTER_X, OUTPUT_FILTER_Y))
            g.renderTooltip(this.font,
                Component.translatable("gui.staticlogistics.open_filter"), mx, my);
    }

    private void adjustPriority(int delta) {
        if (delta == 0) return;
        if (hasShiftDown() && hasControlDown()) delta *= 64;
        else if (hasShiftDown()) delta *= 10;
        else if (hasControlDown()) delta *= 5;
        sendConfigUpdate("priority", menu.getPriority() + delta);
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
        PacketDistributor.sendToServer(
            new C2SConfigureFacePayload(menu.getPos(), menu.getFace(), tag));
    }

    private void syncTypeSelection() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("selected_types_mask", menu.getSelectedTypesMask());
        PacketDistributor.sendToServer(
            new C2SConfigureFacePayload(menu.getPos(), menu.getFace(), tag));
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if ((keyCode == 257 || keyCode == 335) && this.priorityBox.isFocused()) {
            this.priorityBox.setFocused(false);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
