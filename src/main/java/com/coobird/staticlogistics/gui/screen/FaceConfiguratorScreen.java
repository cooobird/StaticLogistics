package com.coobird.staticlogistics.gui.screen;

import com.coobird.staticlogistics.api.type.DistributionStrategy;
import com.coobird.staticlogistics.api.type.ExtractionMode;
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
import java.util.function.IntSupplier;

public class FaceConfiguratorScreen extends AbstractConfiguratorScreen<FaceConfiguratorMenu> {

    private static final int EDIT_BOX_W = 36, BTN = FaceControls.BTN_SIZE;

    private static final int INPUT_X = 10;
    private static final int IN_TOGGLE_Y = 20, IN_COLOR_Y = 18;
    private static final int PRIORITY_Y = 65, STOCK_Y = 92;
    private static final int OP_Y_OFFSET = -1; // +/- 按钮相对 EditBox 的 Y 偏移

    private static final int OUTPUT_X = 90;
    private static final int OUT_TOGGLE_Y = 20, OUT_COLOR_Y = 18;
    private static final int CHOICE_X = 138;
    private static final int STRAT_Y = 18, EXTRACT_Y = 38, UPGRADE_Y = 60;

    private EditBox priorityBox, keepStockBox;

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

        priorityBox = makeIntBox(INPUT_X, PRIORITY_Y, EDIT_BOX_W,
            "gui.staticlogistics.label.priority", "priority", menu::getPriority);
        keepStockBox = makeIntBox(INPUT_X, STOCK_Y, EDIT_BOX_W,
            "gui.staticlogistics.label.keep_stock", "keep_stock", menu::getKeepStock);

        addRenderableWidget(priorityBox);
        addRenderableWidget(keepStockBox);
        updateWidgetVisibility();
    }

    private EditBox makeIntBox(int x, int y, int w, String labelKey, String cfgKey, IntSupplier getter) {
        int ax = leftPos + x, ay = topPos + y;
        EditBox box = new EditBox(this.font, ax, ay, w, BTN, Component.translatable(labelKey));
        box.setBordered(true);
        box.setMaxLength(cfgKey.equals("priority") ? 10 : 6);
        box.setFilter(s -> s.isEmpty() || s.matches(cfgKey.equals("priority") ? "-?[0-9]*" : "[0-9]*"));
        box.setValue(String.valueOf(getter.getAsInt()));
        box.setResponder(s -> {
            try {
                int v = s.isEmpty() ? 0 : Integer.parseInt(s);
                if (v != getter.getAsInt()) sendConfigUpdate(cfgKey, v);
            } catch (NumberFormatException ignored) {
            }
        });
        return box;
    }

    @Override
    protected void updateWidgetVisibility() {
        super.updateWidgetVisibility();
        priorityBox.setVisible(menu.isGlobalInputEnabled());
        keepStockBox.setVisible(menu.isGlobalInputEnabled());
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
        g.pose().pushPose();
        g.pose().translate(x + 4, y + 2, 0);
        g.pose().scale(0.75f, 0.75f, 1.0f);
        g.renderFakeItem(type.getIcon(), 0, 0);
        g.pose().popPose();
        String name = font.plainSubstrByWidth(Component.translatable(type.translationKey()).getString(), 55);
        g.drawString(font, name, x + 18, y + 5, isSelected ? 0x98FB98 : 0xCCCCCC, false);
    }

    @Override
    protected void renderHoveredTypeTooltip(GuiGraphics g, int mx, int my) {
        if (hoveredType == null) return;
        List<Component> t = new ArrayList<>();
        t.add(Component.translatable(hoveredType.translationKey()).withStyle(s -> s.withColor(hoveredType.color() | 0xFF000000)));
        t.add(Component.translatable(hoveredType.translationKey() + ".desc").withStyle(ChatFormatting.GRAY));
        t.add(Component.empty());
        t.add(Component.translatable("gui.staticlogistics.tooltip.toggle_type").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        g.renderComponentTooltip(font, t, mx, my);
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
        syncBoxValue(priorityBox, menu.getPriority());
        syncBoxValue(keepStockBox, menu.getKeepStock());
    }

    private void syncBoxValue(EditBox box, int expected) {
        if (box == null || !box.isVisible() || box.isFocused()) return;
        String v = String.valueOf(expected);
        if (!Objects.equals(box.getValue(), v)) box.setValue(v);
    }

    @Override
    protected void renderCustomContent(GuiGraphics g, int mx, int my) {
        renderFilterSlots(g);
        renderFilterHints(g);
        renderInputSection(g, mx, my);
        renderOutputSection(g, mx, my);
        renderFilterButtons(g, mx, my);
    }

    private void renderInputSection(GuiGraphics g, int mx, int my) {
        FaceControls.renderToggle(g, font, leftPos, topPos, INPUT_X, IN_TOGGLE_Y, menu.isGlobalInputEnabled(), GUI_TEXTURE);
        FaceControls.renderColor(g, leftPos, topPos, INPUT_X + 20, IN_COLOR_Y, menu.getInputChannel());

        if (!menu.isGlobalInputEnabled()) return;

        int labelColor = 0xFFFFFFFF;
        g.drawString(font, Component.translatable("gui.staticlogistics.label.priority"),
            leftPos + INPUT_X, topPos + PRIORITY_Y + BTN + 1, labelColor, false);
        g.drawString(font, Component.translatable("gui.staticlogistics.label.keep_stock"),
            leftPos + INPUT_X, topPos + STOCK_Y + BTN + 1, labelColor, false);

        int px = leftPos + INPUT_X + EDIT_BOX_W + 2, py = topPos + PRIORITY_Y + OP_Y_OFFSET;
        renderOpButton(g, mx, my, px, py, true);
        renderOpButton(g, mx, my, px + BTN + 2, py, false);

        if (this.keepStockBox != null && this.keepStockBox.isVisible() && this.keepStockBox.isMouseOver(mx, my))
            g.renderTooltip(font, Component.translatable("gui.staticlogistics.keep_stock.tooltip"), mx, my);
    }

    private void renderOpButton(GuiGraphics g, int mx, int my, int x, int y, boolean isPlus) {
        FaceControls.renderOperator(g, x, y,
            isPlus ? SLGuiTextures.Operator.ADD_U : SLGuiTextures.Operator.REDUCE_U,
            isPlus ? SLGuiTextures.Operator.ADD_V : SLGuiTextures.Operator.REDUCE_V,
            mx, my, GUI_TEXTURE);
        if (mx >= x && mx < x + BTN && my >= y && my < y + BTN)
            g.renderTooltip(font, Component.translatable("gui.staticlogistics.priority.tooltip"), mx, my);
    }

    private void renderOutputSection(GuiGraphics g, int mx, int my) {
        FaceControls.renderToggle(g, font, leftPos, topPos, OUTPUT_X, OUT_TOGGLE_Y, menu.isGlobalOutputEnabled(), GUI_TEXTURE);
        FaceControls.renderColor(g, leftPos, topPos, OUTPUT_X + 20, OUT_COLOR_Y, menu.getOutputChannel());

        if (!menu.isGlobalOutputEnabled()) return;

        FaceControls.renderChoiceButton(g, font, leftPos, topPos, CHOICE_X, STRAT_Y,
            menu.getStrategy().getDisplayName(), mx, my, GUI_TEXTURE);
        FaceControls.renderChoiceButton(g, font, leftPos, topPos, CHOICE_X, EXTRACT_Y,
            menu.getExtractionMode().getDisplayName(), mx, my, GUI_TEXTURE);

        Component lbl = Component.translatable("gui.staticlogistics.upgrade_config");
        boolean hover = FaceControls.isTextButtonHovered(mx, my, leftPos, topPos, CHOICE_X, UPGRADE_Y, lbl, font);
        FaceControls.renderTextButton(g, font, leftPos, topPos, CHOICE_X, UPGRADE_Y, lbl, hover, GUI_TEXTURE);
    }

    private void renderFilterButtons(GuiGraphics g, int mx, int my) {
        int ix = FaceConfiguratorMenu.INPUT_FILTER_SLOT_X, iy = FaceConfiguratorMenu.INPUT_FILTER_SLOT_Y;
        int ox = FaceConfiguratorMenu.OUTPUT_FILTER_SLOT_X, oy = FaceConfiguratorMenu.OUTPUT_FILTER_SLOT_Y;
        if (menu.isGlobalInputEnabled() && !menu.getSlot(0).getItem().isEmpty()) {
            FaceControls.renderFilterConfigBtn(g, leftPos, topPos, ix, iy,
                FaceControls.isFilterConfigBtnHovered(mx, my, leftPos, topPos, ix, iy), GUI_TEXTURE);
        }
        if (menu.isGlobalOutputEnabled() && !menu.getSlot(1).getItem().isEmpty()) {
            FaceControls.renderFilterConfigBtn(g, leftPos, topPos, ox, oy,
                FaceControls.isFilterConfigBtnHovered(mx, my, leftPos, topPos, ox, oy), GUI_TEXTURE);
        }
    }

    private void renderFilterSlots(GuiGraphics g) {
        int ix = FaceConfiguratorMenu.INPUT_FILTER_SLOT_X, iy = FaceConfiguratorMenu.INPUT_FILTER_SLOT_Y;
        int ox = FaceConfiguratorMenu.OUTPUT_FILTER_SLOT_X, oy = FaceConfiguratorMenu.OUTPUT_FILTER_SLOT_Y;
        if (menu.isGlobalInputEnabled())
            g.blit(GUI_TEXTURE, leftPos + ix, topPos + iy, 16, 16,
                SLGuiTextures.Upgrade.U, SLGuiTextures.Upgrade.V,
                SLGuiTextures.Upgrade.WIDTH, SLGuiTextures.Upgrade.HEIGHT, SLGuiTextures.GUI_WIDTH, SLGuiTextures.GUI_HEIGHT);
        if (menu.isGlobalOutputEnabled())
            g.blit(GUI_TEXTURE, leftPos + ox, topPos + oy, 16, 16,
                SLGuiTextures.Upgrade.U, SLGuiTextures.Upgrade.V,
                SLGuiTextures.Upgrade.WIDTH, SLGuiTextures.Upgrade.HEIGHT, SLGuiTextures.GUI_WIDTH, SLGuiTextures.GUI_HEIGHT);
    }

    private void renderFilterHints(GuiGraphics g) {
        int ix = FaceConfiguratorMenu.INPUT_FILTER_SLOT_X, iy = FaceConfiguratorMenu.INPUT_FILTER_SLOT_Y;
        int ox = FaceConfiguratorMenu.OUTPUT_FILTER_SLOT_X, oy = FaceConfiguratorMenu.OUTPUT_FILTER_SLOT_Y;
        if (menu.isGlobalInputEnabled()) renderFilterHint(g, ix, iy, "gui.staticlogistics.hint.input_filter");
        if (menu.isGlobalOutputEnabled()) renderFilterHint(g, ox, oy, "gui.staticlogistics.hint.output_filter");
    }

    private void renderFilterHint(GuiGraphics g, int x, int y, String key) {
        g.pose().pushPose();
        g.pose().scale(0.8f, 0.8f, 0.8f);
        g.drawString(font, Component.translatable(key), (int) ((leftPos + x - 2) / 0.8f), (int) ((topPos + y + 18) / 0.8f), 0x88FFFFFF, false);
        g.pose().popPose();
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        renderCustomTooltips(g, mx, my);
        this.renderTooltip(g, mx, my);
    }

    private void renderCustomTooltips(GuiGraphics g, int mx, int my) {
        if (FaceControls.isToggleHovered(mx, my, leftPos, topPos, INPUT_X, IN_TOGGLE_Y))
            g.renderTooltip(font, Component.translatable("gui.mode.staticlogistics.input"), mx, my);
        if (FaceControls.isToggleHovered(mx, my, leftPos, topPos, OUTPUT_X, OUT_TOGGLE_Y))
            g.renderTooltip(font, Component.translatable("gui.mode.staticlogistics.output"), mx, my);

        if (menu.isGlobalOutputEnabled() && FaceControls.isChoiceHovered(mx, my, leftPos, topPos, CHOICE_X, STRAT_Y,
            menu.getStrategy().getDisplayName(), font))
            g.renderComponentTooltip(font, List.of(Component.translatable("gui.staticlogistics.strategy"),
                menu.getStrategy().getDisplayName().copy().withStyle(ChatFormatting.AQUA)), mx, my);
        if (menu.isGlobalOutputEnabled() && FaceControls.isChoiceHovered(mx, my, leftPos, topPos, CHOICE_X, EXTRACT_Y,
            menu.getExtractionMode().getDisplayName(), font))
            g.renderComponentTooltip(font, List.of(Component.translatable("gui.staticlogistics.extraction_mode"),
                menu.getExtractionMode().getDisplayName().copy().withStyle(ChatFormatting.AQUA)), mx, my);

        int ix = FaceConfiguratorMenu.INPUT_FILTER_SLOT_X, iy = FaceConfiguratorMenu.INPUT_FILTER_SLOT_Y;
        int ox = FaceConfiguratorMenu.OUTPUT_FILTER_SLOT_X, oy = FaceConfiguratorMenu.OUTPUT_FILTER_SLOT_Y;
        slotTooltip(g, mx, my, menu.isGlobalInputEnabled(), 0, ix, iy);
        slotTooltip(g, mx, my, menu.isGlobalOutputEnabled(), 1, ox, oy);
        if (menu.isGlobalInputEnabled() && !menu.getSlot(0).getItem().isEmpty() && FaceControls.isFilterConfigBtnHovered(mx, my, leftPos, topPos, ix, iy))
            g.renderTooltip(font, Component.translatable("gui.staticlogistics.open_filter"), mx, my);
        if (menu.isGlobalOutputEnabled() && !menu.getSlot(1).getItem().isEmpty() && FaceControls.isFilterConfigBtnHovered(mx, my, leftPos, topPos, ox, oy))
            g.renderTooltip(font, Component.translatable("gui.staticlogistics.open_filter"), mx, my);
    }

    private void slotTooltip(GuiGraphics g, int mx, int my, boolean visible, int slotIdx, int sx, int sy) {
        if (!visible) return;
        if (mx < leftPos + sx || mx >= leftPos + sx + 16 || my < topPos + sy || my >= topPos + sy + 16) return;
        ItemStack st = menu.getSlot(slotIdx).getItem();
        if (!st.isEmpty()) g.renderTooltip(font, st, mx, my);
    }

    // ── mouse / key ──

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        // +/- 按钮
        int px = leftPos + INPUT_X + EDIT_BOX_W + 2, py = topPos + PRIORITY_Y + OP_Y_OFFSET;
        if (menu.isGlobalInputEnabled() && inRect(mx, my, px, py, BTN, BTN)) {
            adjustPriority(1);
            playClickSound();
            return true;
        }
        if (menu.isGlobalInputEnabled() && inRect(mx, my, px + BTN + 2, py, BTN, BTN)) {
            adjustPriority(-1);
            playClickSound();
            return true;
        }

        int ix = FaceConfiguratorMenu.INPUT_FILTER_SLOT_X, iy = FaceConfiguratorMenu.INPUT_FILTER_SLOT_Y;
        int ox = FaceConfiguratorMenu.OUTPUT_FILTER_SLOT_X, oy = FaceConfiguratorMenu.OUTPUT_FILTER_SLOT_Y;

        if (menu.isGlobalInputEnabled() && !menu.getSlot(0).getItem().isEmpty()
            && FaceControls.isFilterConfigBtnHovered(mx, my, leftPos, topPos, ix, iy)) {
            sendConfigUpdate("open_filter", "input");
            playClickSound();
            return true;
        }
        if (menu.isGlobalOutputEnabled() && !menu.getSlot(1).getItem().isEmpty()
            && FaceControls.isFilterConfigBtnHovered(mx, my, leftPos, topPos, ox, oy)) {
            sendConfigUpdate("open_filter", "output");
            playClickSound();
            return true;
        }

        if (FaceControls.isToggleHovered(mx, my, leftPos, topPos, INPUT_X, IN_TOGGLE_Y)) {
            sendConfigUpdate("globalInput", !menu.isGlobalInputEnabled());
            playClickSound();
            return true;
        }
        if (FaceControls.isToggleHovered(mx, my, leftPos, topPos, OUTPUT_X, OUT_TOGGLE_Y)) {
            sendConfigUpdate("globalOutput", !menu.isGlobalOutputEnabled());
            playClickSound();
            return true;
        }

        if (FaceControls.isColorHovered(mx, my, leftPos, topPos, INPUT_X + 20, IN_COLOR_Y)) {
            cycleChannel("inputChannel", menu.getInputChannel(), button);
            return true;
        }
        if (FaceControls.isColorHovered(mx, my, leftPos, topPos, OUTPUT_X + 20, OUT_COLOR_Y)) {
            cycleChannel("outputChannel", menu.getOutputChannel(), button);
            return true;
        }

        if (menu.isGlobalOutputEnabled() && FaceControls.isChoiceHovered(mx, my, leftPos, topPos, CHOICE_X, STRAT_Y,
            menu.getStrategy().getDisplayName(), font)) {
            var vals = DistributionStrategy.values();
            int ord = menu.getStrategy().ordinal();
            int next = button == 1 ? (ord - 1 + vals.length) % vals.length : (ord + 1) % vals.length;
            sendConfigUpdate("strategy", vals[next].getSerializedName());
            playClickSound();
            return true;
        }
        if (menu.isGlobalOutputEnabled() && FaceControls.isChoiceHovered(mx, my, leftPos, topPos, CHOICE_X, EXTRACT_Y,
            menu.getExtractionMode().getDisplayName(), font)) {
            var vals = ExtractionMode.values();
            int ord = menu.getExtractionMode().ordinal();
            int next = button == 1 ? (ord - 1 + vals.length) % vals.length : (ord + 1) % vals.length;
            sendConfigUpdate("extractionMode", vals[next].getSerializedName());
            playClickSound();
            return true;
        }

        if (menu.isGlobalOutputEnabled() && FaceControls.isTextButtonHovered(mx, my, leftPos, topPos, CHOICE_X, UPGRADE_Y,
            Component.translatable("gui.staticlogistics.upgrade_config"), font)) {
            PacketDistributor.sendToServer(new C2SOpenContainerConfigPayload(menu.getPos(), menu.getFace()));
            playClickSound();
            return true;
        }

        boolean handled = super.mouseClicked(mx, my, button);

        unfocusIfOutside(priorityBox, mx, my);
        unfocusIfOutside(keepStockBox, mx, my);
        return handled;
    }

    private static boolean inRect(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private void cycleChannel(String key, int cur, int button) {
        int next = button == 1 ? (cur - 1 < 1 ? 16 : cur - 1) : (cur + 1 > 16 ? 1 : cur + 1);
        sendConfigUpdate(key, next);
        playClickSound();
    }

    private void unfocusIfOutside(EditBox box, double mx, double my) {
        if (box != null && !box.isMouseOver(mx, my) && box.isFocused()) box.setFocused(false);
    }

    private void adjustPriority(int delta) {
        if (delta == 0) return;
        if (hasShiftDown() && hasControlDown()) delta *= 64;
        else if (hasShiftDown()) delta *= 10;
        else if (hasControlDown()) delta *= 5;
        sendConfigUpdate("priority", menu.getPriority() + delta);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if ((keyCode == 257 || keyCode == 335) && priorityBox.isFocused()) {
            priorityBox.setFocused(false);
            return true;
        }
        if ((keyCode == 257 || keyCode == 335) && keepStockBox != null && keepStockBox.isFocused()) {
            keepStockBox.setFocused(false);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    // ── network ──

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
}
