package com.coobird.staticlogistics.client.gui.component;

import com.coobird.staticlogistics.api.type.ExtractionMode;
import com.coobird.staticlogistics.client.key.SLKeyMappings;
import com.coobird.staticlogistics.client.render.SLGuiTextures;
import com.coobird.staticlogistics.content.menu.LinkConfiguratorMenu;
import com.coobird.staticlogistics.logistics.node.FaceConfigurationEdit;
import com.coobird.staticlogistics.logistics.util.NodeDisplayText;
import com.coobird.staticlogistics.network.c2s.C2SConfigureFacePayload;
import com.coobird.staticlogistics.transfer.DistributionStrategyRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * 连接配置器底部的节点配置区域。
 */
public final class NodeConfigurationPanel {
    private static final int BUTTON_SIZE = NodeConfigControls.BTN;
    private static final int EDIT_BOX_WIDTH = 36;
    private static final int CONFIG_Y = SLGuiTextures.LinkConfigurator.NODE_CONFIG_Y;

    private static final int HEADER_X = 12;
    private static final int HEADER_Y = CONFIG_Y + 8;
    private static final int INPUT_SIDE_X = 194;
    private static final int OUTPUT_SIDE_X = 236;
    private static final int SIDE_Y = CONFIG_Y + 3;
    private static final int SIDE_WIDTH = 40;
    private static final int SIDE_HEIGHT = 17;

    private static final int UTILITY_Y = CONFIG_Y + 23;
    private static final int TOGGLE_X = 12;
    private static final int STATUS_X = 34;
    private static final int FILTER_LABEL_X = 76;
    private static final int UPGRADE_LABEL_X = 202;
    private static final int UPGRADE_LABEL_Y_OFFSET = 3;

    private static final int INPUT_ROW_ONE_Y = CONFIG_Y + 52;
    private static final int INPUT_ROW_TWO_Y = CONFIG_Y + 76;
    private static final int VALUE_LABEL_X = 12;
    private static final int VALUE_BOX_X = 72;
    private static final int VALUE_OPERATOR_X = 112;

    private static final int STRATEGY_X = 12;
    private static final int EXTRACTION_X = 12;
    private static final int STRATEGY_Y = CONFIG_Y + 50;
    private static final int EXTRACTION_Y = CONFIG_Y + 70;
    private static final int OUTPUT_ACTION_WIDTH = 124;

    private final LinkConfiguratorMenu menu;
    private final Font font;
    private final Runnable openFilter;
    private final Consumer<Boolean> selectSide;
    private final Runnable playClick;

    private EditBox priorityBox;
    private EditBox keepStockBox;
    private int left;
    private int top;

    public NodeConfigurationPanel(
        LinkConfiguratorMenu menu,
        Font font,
        Runnable openFilter,
        Consumer<Boolean> selectSide,
        Runnable playClick
    ) {
        this.menu = menu;
        this.font = font;
        this.openFilter = openFilter;
        this.selectSide = selectSide;
        this.playClick = playClick;
    }

    public void init(int left, int top, Consumer<EditBox> addWidget) {
        this.left = left;
        this.top = top;
        priorityBox = makeBox(INPUT_ROW_ONE_Y - 2, "priority", true,
            menu.getPriority(), "-?[0-9]*", 10);
        keepStockBox = makeBox(INPUT_ROW_TWO_Y - 2, "keepStock", false,
            menu.getKeepStock(), "[0-9]*", 6);
        addWidget.accept(priorityBox);
        addWidget.accept(keepStockBox);
        updateWidgetVisibility();
    }

    private EditBox makeBox(
        int y,
        String labelKey,
        boolean priority,
        int initialValue,
        String pattern,
        int maximumLength
    ) {
        EditBox box = new EditBox(font, left + VALUE_BOX_X, top + y,
            EDIT_BOX_WIDTH, BUTTON_SIZE,
            Component.translatable("gui.staticlogistics.label." + labelKey));
        box.setBordered(true);
        box.setMaxLength(maximumLength);
        box.setFilter(value -> value.isEmpty() || value.matches(pattern));
        box.setValue(String.valueOf(initialValue));
        box.setResponder(value -> {
            try {
                int parsed = value.isEmpty() ? 0 : Integer.parseInt(value);
                int current = priority ? menu.getPriority() : menu.getKeepStock();
                if (parsed != current) {
                    send(new FaceConfigurationEdit.NumberEdit(
                        priority
                            ? FaceConfigurationEdit.NumberField.PRIORITY
                            : FaceConfigurationEdit.NumberField.KEEP_STOCK,
                        parsed));
                }
            } catch (NumberFormatException ignored) {
            }
        });
        return box;
    }

    public void tick() {
        updateWidgetVisibility();
        syncBox(priorityBox, menu.getPriority());
        syncBox(keepStockBox, menu.getKeepStock());
    }

    private void updateWidgetVisibility() {
        boolean visible = menu.hasTarget()
            && menu.isInputSideVisible()
            && menu.isGlobalInputEnabled();
        if (priorityBox != null) {
            priorityBox.setVisible(visible);
        }
        if (keepStockBox != null) {
            keepStockBox.setVisible(visible);
        }
    }

    private static void syncBox(EditBox box, int value) {
        if (box == null || !box.isVisible() || box.isFocused()) {
            return;
        }
        String text = String.valueOf(value);
        if (!Objects.equals(box.getValue(), text)) {
            box.setValue(text);
        }
    }

    public void render(GuiGraphics graphics, int mouseX, int mouseY) {
        if (!menu.hasTarget()) {
            renderEmpty(graphics);
            return;
        }
        renderHeader(graphics);
        if (menu.isInputSideVisible()) {
            renderInputUtilityRow(graphics, mouseX, mouseY);
            renderInputControls(graphics, mouseX, mouseY);
        } else {
            renderOutputUtilityRow(graphics, mouseX, mouseY);
            renderOutputControls(graphics, mouseX, mouseY);
        }
    }

    private void renderEmpty(GuiGraphics graphics) {
        graphics.drawString(font,
            Component.translatable("gui.staticlogistics.node_configuration"),
            left + HEADER_X, top + CONFIG_Y + 10, 0xFF98FB98, false);
        graphics.drawString(font,
            Component.translatable(
                "gui.staticlogistics.network_preview.select_node_to_configure"),
            left + HEADER_X, top + CONFIG_Y + 30, 0xFF888888, false);
    }

    private void renderHeader(GuiGraphics graphics) {
        Component title = Component.translatable("gui.staticlogistics.node_configuration")
            .append(Component.literal(" · " + menu.getPos().toShortString() + " · "))
            .append(NodeDisplayText.direction(menu.getFace()));
        int titleWidth = INPUT_SIDE_X - HEADER_X - 4;
        String text = font.plainSubstrByWidth(title.getString(), titleWidth);
        graphics.drawString(font, text, left + HEADER_X, top + HEADER_Y, 0xFF98FB98, false);
        renderSideButton(graphics, left + INPUT_SIDE_X, top + SIDE_Y, Component.translatable("gui.staticlogistics.input"), menu.isInputSideVisible());
        renderSideButton(graphics, left + OUTPUT_SIDE_X, top + SIDE_Y, Component.translatable("gui.staticlogistics.output"), menu.isOutputSideVisible());
    }

    /**
     * 输入侧只呈现接收行为：启用状态与输入过滤器。
     * 优先级和存量维持由下方输入控制区负责，容器升级不属于输入侧。
     */
    private void renderInputUtilityRow(
        GuiGraphics graphics,
        int mouseX,
        int mouseY
    ) {
        renderEnabledState(graphics, menu.isGlobalInputEnabled());
        renderFilterSlot(graphics, mouseX, mouseY, 0);
    }

    /**
     * 输出侧呈现发送行为：启用状态、输出过滤器与三类容器升级。
     */
    private void renderOutputUtilityRow(
        GuiGraphics graphics,
        int mouseX,
        int mouseY
    ) {
        renderEnabledState(graphics, menu.isGlobalOutputEnabled());
        renderFilterSlot(graphics, mouseX, mouseY, 1);

        graphics.drawString(font,
            Component.translatable("gui.staticlogistics.upgrades"), left + UPGRADE_LABEL_X, top + UTILITY_Y + UPGRADE_LABEL_Y_OFFSET, 0xFFAAAAAA, false);
        for (int index = 2; index < 5; index++) {
            Slot slot = menu.getSlot(index);
            NodeConfigControls.drawSlotBg(
                graphics, left + slot.x, top + slot.y);
        }
    }

    private void renderEnabledState(GuiGraphics graphics, boolean enabled) {
        NodeConfigControls.renderToggle(
            graphics, left + TOGGLE_X, top + UTILITY_Y, enabled);
        graphics.drawString(font, Component.translatable(enabled
                ? "gui.staticlogistics.enabled"
                : "gui.staticlogistics.disabled"),
            left + STATUS_X, top + UTILITY_Y + 2,
            enabled ? 0xFF98FB98 : 0xFF888888, false);
    }

    private void renderFilterSlot(
        GuiGraphics graphics,
        int mouseX,
        int mouseY,
        int slotIndex
    ) {
        graphics.drawString(font,
            Component.translatable("gui.staticlogistics.filter"),
            left + FILTER_LABEL_X, top + UTILITY_Y + 2,
            0xFFAAAAAA, false);
        Slot filter = menu.getSlot(slotIndex);
        NodeConfigControls.drawSlotBg(graphics, left + filter.x, top + filter.y);
        if (!filter.getItem().isEmpty()) {
            NodeConfigControls.renderFilterCfgBtn(
                graphics, left + filter.x, top + filter.y,
                NodeConfigControls.hitFilterCfgBtn(
                    mouseX, mouseY, left + filter.x, top + filter.y));
        }
    }

    private void renderSideButton(GuiGraphics graphics, int x, int y, Component label, boolean selected) {
        int u = selected ? SLGuiTextures.Button.Middle.SELECTED_U : SLGuiTextures.Button.Middle.DISABLED_U;
        int v = selected ? SLGuiTextures.Button.Middle.SELECTED_V : SLGuiTextures.Button.Middle.DISABLED_V;
        int sourceWidth = selected ? SLGuiTextures.Button.Middle.SELECTED_WIDTH : SLGuiTextures.Button.Middle.DISABLED_WIDTH;
        int height = selected ? SLGuiTextures.Button.Middle.SELECTED_HEIGHT : SLGuiTextures.Button.Middle.DISABLED_HEIGHT;
        int drawX = selected ? x - 1 : x;
        int drawY = selected ? y - 1 : y;
        int drawWidth = selected ? SIDE_WIDTH + 2 : SIDE_WIDTH;
        int capWidth = 2;
        graphics.blit(SLGuiTextures.GUI_ATLAS, drawX, drawY, u, v, capWidth, height,
            SLGuiTextures.GUI_WIDTH, SLGuiTextures.GUI_HEIGHT);
        graphics.blit(SLGuiTextures.GUI_ATLAS, drawX + drawWidth - capWidth, drawY,
            u + sourceWidth - capWidth, v, capWidth, height,
            SLGuiTextures.GUI_WIDTH, SLGuiTextures.GUI_HEIGHT);
        graphics.blit(SLGuiTextures.GUI_ATLAS, drawX + capWidth, drawY,
            drawWidth - capWidth * 2, height, u + capWidth, v, 1, height,
            SLGuiTextures.GUI_WIDTH, SLGuiTextures.GUI_HEIGHT);
        graphics.drawString(font, label, x + (SIDE_WIDTH - font.width(label)) / 2, drawY + (height - font.lineHeight) / 2, selected ? 0xFF18351B : 0xFFAAAAAA, false);
    }

    private void renderInputControls(
        GuiGraphics graphics,
        int mouseX,
        int mouseY
    ) {
        if (!menu.isGlobalInputEnabled()) {
            return;
        }
        renderNumberRow(graphics, mouseX, mouseY, INPUT_ROW_ONE_Y, "gui.staticlogistics.label.priority");
        renderNumberRow(graphics, mouseX, mouseY, INPUT_ROW_TWO_Y, "gui.staticlogistics.label.keep_stock");
    }

    private void renderNumberRow(GuiGraphics graphics, int mouseX, int mouseY, int rowY, String labelKey
    ) {
        graphics.drawString(font, Component.translatable(labelKey), left + VALUE_LABEL_X, top + rowY, 0xFFAAAAAA, false);
        int operatorY = top + rowY - 2;
        NodeConfigControls.renderOpBtn(graphics, left + VALUE_OPERATOR_X, operatorY, true, NodeConfigControls.hitOpBtn(mouseX, mouseY, left + VALUE_OPERATOR_X, operatorY));
        NodeConfigControls.renderOpBtn(graphics, left + VALUE_OPERATOR_X + BUTTON_SIZE + 2, operatorY, false, NodeConfigControls.hitOpBtn(mouseX, mouseY, left + VALUE_OPERATOR_X + BUTTON_SIZE + 2, operatorY));
    }

    private void renderOutputControls(
        GuiGraphics graphics,
        int mouseX,
        int mouseY
    ) {
        if (!menu.isGlobalOutputEnabled()) {
            return;
        }

        boolean strategyHovered = NodeConfigControls.hitCycleBtn(
            mouseX, mouseY,
            left + STRATEGY_X, top + STRATEGY_Y,
            OUTPUT_ACTION_WIDTH);
        NodeConfigControls.renderCycleBtn(
            graphics, font,
            left + STRATEGY_X, top + STRATEGY_Y,
            OUTPUT_ACTION_WIDTH,
            menu.getStrategy().getDisplayName(),
            strategyHovered);

        boolean extractionHovered = NodeConfigControls.hitCycleBtn(
            mouseX, mouseY,
            left + EXTRACTION_X, top + EXTRACTION_Y,
            OUTPUT_ACTION_WIDTH);
        NodeConfigControls.renderCycleBtn(
            graphics, font,
            left + EXTRACTION_X, top + EXTRACTION_Y,
            OUTPUT_ACTION_WIDTH,
            menu.getExtractionMode().getDisplayName(),
            extractionHovered);
    }

    public void renderTooltips(GuiGraphics graphics, int mouseX, int mouseY) {
        if (!menu.hasTarget()) {
            return;
        }
        renderConfigSlotTooltip(graphics, mouseX, mouseY);

        if (keepStockBox != null
            && keepStockBox.isVisible()
            && keepStockBox.isMouseOver(mouseX, mouseY)) {
            graphics.renderTooltip(font,
                Component.translatable("gui.staticlogistics.keep_stock.tooltip"),
                mouseX, mouseY);
            return;
        }

        if (menu.isInputSideVisible() && menu.isGlobalInputEnabled()) {
            if (isAnyOperatorHovered(mouseX, mouseY)) {
                graphics.renderTooltip(font,
                    Component.translatable(
                        "gui.staticlogistics.priority.tooltip",
                        SLKeyMappings.PRIORITY_X10.getTranslatedKeyMessage(),
                        SLKeyMappings.PRIORITY_X5.getTranslatedKeyMessage()),
                    mouseX, mouseY);
            }
            return;
        }

        if (!menu.isGlobalOutputEnabled()) {
            return;
        }
        if (NodeConfigControls.hitCycleBtn(
            mouseX, mouseY,
            left + STRATEGY_X, top + STRATEGY_Y,
            OUTPUT_ACTION_WIDTH)) {
            graphics.renderComponentTooltip(font, List.of(
                Component.translatable("gui.staticlogistics.strategy").withStyle(ChatFormatting.BLUE), menu.getStrategy().getDisplayName()
            ), mouseX, mouseY);
        } else if (NodeConfigControls.hitCycleBtn(
            mouseX, mouseY,
            left + EXTRACTION_X, top + EXTRACTION_Y,
            OUTPUT_ACTION_WIDTH)) {
            graphics.renderComponentTooltip(font, List.of(
                Component.translatable("gui.staticlogistics.extraction_mode").withStyle(ChatFormatting.BLUE), menu.getExtractionMode().getDisplayName()
            ), mouseX, mouseY);
        }
    }

    private void renderConfigSlotTooltip(
        GuiGraphics graphics,
        int mouseX,
        int mouseY
    ) {
        for (int index = 0; index < 5; index++) {
            Slot slot = menu.getSlot(index);
            if (slot == null || !slot.isActive()
                || !hit(mouseX, mouseY, left + slot.x, top + slot.y, 16, 16)) {
                continue;
            }
            if (!slot.getItem().isEmpty()) {
                graphics.renderTooltip(
                    font, slot.getItem(), mouseX, mouseY);
            } else {
                // 空过滤器槽位无需重复说明输入/输出；所在侧已经由面板页签明确表达。
                if (index < 2) {
                    return;
                }
                String key = switch (index) {
                    case 2 -> "gui.staticlogistics.hint.speed";
                    case 3 -> "gui.staticlogistics.hint.range";
                    default -> "gui.staticlogistics.hint.stack";
                };
                graphics.renderTooltip(
                    font, Component.translatable(key), mouseX, mouseY);
            }
            return;
        }
    }

    private boolean isAnyOperatorHovered(double mouseX, double mouseY) {
        for (int rowY : new int[]{INPUT_ROW_ONE_Y, INPUT_ROW_TWO_Y}) {
            int y = top + rowY - 2;
            if (NodeConfigControls.hitOpBtn(
                mouseX, mouseY, left + VALUE_OPERATOR_X, y)
                || NodeConfigControls.hitOpBtn(
                mouseX, mouseY,
                left + VALUE_OPERATOR_X + BUTTON_SIZE + 2, y)) {
                return true;
            }
        }
        return false;
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!menu.hasTarget()) {
            return false;
        }
        if (hit(mouseX, mouseY, left + INPUT_SIDE_X, top + SIDE_Y,
            SIDE_WIDTH, SIDE_HEIGHT)) {
            if (!menu.isInputSideVisible()) {
                selectSide.accept(true);
            }
            return true;
        }
        if (hit(mouseX, mouseY, left + OUTPUT_SIDE_X, top + SIDE_Y,
            SIDE_WIDTH, SIDE_HEIGHT)) {
            if (!menu.isOutputSideVisible()) {
                selectSide.accept(false);
            }
            return true;
        }
        if (NodeConfigControls.hitToggle(
            mouseX, mouseY, left + TOGGLE_X, top + UTILITY_Y)) {
            toggleCurrentSide();
            return true;
        }

        Slot filter = menu.getSlot(menu.isInputSideVisible() ? 0 : 1);
        if (!filter.getItem().isEmpty()
            && NodeConfigControls.hitFilterCfgBtn(
            mouseX, mouseY, left + filter.x, top + filter.y)) {
            openFilter.run();
            playClick.run();
            return true;
        }

        if (menu.isInputSideVisible() && menu.isGlobalInputEnabled()) {
            return handleInputOperatorClick(mouseX, mouseY);
        } else if (menu.isOutputSideVisible() && menu.isGlobalOutputEnabled()) {
            return handleOutputClick(mouseX, mouseY, button);
        }
        return false;
    }

    private void toggleCurrentSide() {
        FaceConfigurationEdit.BooleanField field = menu.isInputSideVisible()
            ? FaceConfigurationEdit.BooleanField.GLOBAL_INPUT
            : FaceConfigurationEdit.BooleanField.GLOBAL_OUTPUT;
        boolean current = menu.isInputSideVisible()
            ? menu.isGlobalInputEnabled()
            : menu.isGlobalOutputEnabled();
        send(new FaceConfigurationEdit.BooleanEdit(field, !current));
        playClick.run();
    }

    private boolean handleInputOperatorClick(
        double mouseX,
        double mouseY
    ) {
        int priorityY = top + INPUT_ROW_ONE_Y - 2;
        if (NodeConfigControls.hitOpBtn(
            mouseX, mouseY, left + VALUE_OPERATOR_X, priorityY)) {
            adjustPriority(1);
            return true;
        }
        if (NodeConfigControls.hitOpBtn(
            mouseX, mouseY,
            left + VALUE_OPERATOR_X + BUTTON_SIZE + 2, priorityY)) {
            adjustPriority(-1);
            return true;
        }

        int stockY = top + INPUT_ROW_TWO_Y - 2;
        if (NodeConfigControls.hitOpBtn(
            mouseX, mouseY, left + VALUE_OPERATOR_X, stockY)) {
            adjustKeepStock(1);
            return true;
        }
        if (NodeConfigControls.hitOpBtn(
            mouseX, mouseY,
            left + VALUE_OPERATOR_X + BUTTON_SIZE + 2, stockY)) {
            adjustKeepStock(-1);
            return true;
        }
        return false;
    }

    private boolean handleOutputClick(
        double mouseX,
        double mouseY,
        int button
    ) {
        if (NodeConfigControls.hitCycleBtn(
            mouseX, mouseY, left + STRATEGY_X, top + STRATEGY_Y,
            OUTPUT_ACTION_WIDTH)) {
            var values = DistributionStrategyRegistry.getValues();
            int current = Math.max(0, values.indexOf(menu.getStrategy()));
            int next = button == 1
                ? (current - 1 + values.size()) % values.size()
                : (current + 1) % values.size();
            send(new FaceConfigurationEdit.StrategyEdit(values.get(next)));
            playClick.run();
            return true;
        }
        if (NodeConfigControls.hitCycleBtn(
            mouseX, mouseY, left + EXTRACTION_X, top + EXTRACTION_Y,
            OUTPUT_ACTION_WIDTH)) {
            ExtractionMode[] values = ExtractionMode.values();
            int next = button == 1
                ? (menu.getExtractionMode().ordinal() - 1 + values.length)
                % values.length
                : (menu.getExtractionMode().ordinal() + 1) % values.length;
            send(new FaceConfigurationEdit.ExtractionEdit(values[next]));
            playClick.run();
            return true;
        }
        return false;
    }

    public boolean keyPressed(int keyCode) {
        if (keyCode != 257 && keyCode != 335) {
            return false;
        }
        if (priorityBox != null && priorityBox.isFocused()) {
            priorityBox.setFocused(false);
            return true;
        }
        if (keepStockBox != null && keepStockBox.isFocused()) {
            keepStockBox.setFocused(false);
            return true;
        }
        return false;
    }

    public void unfocusEditors(double mouseX, double mouseY) {
        unfocus(priorityBox, mouseX, mouseY);
        unfocus(keepStockBox, mouseX, mouseY);
    }

    private static void unfocus(EditBox box, double mouseX, double mouseY) {
        if (box != null && box.isFocused() && !box.isMouseOver(mouseX, mouseY)) {
            box.setFocused(false);
        }
    }

    private void adjustPriority(int direction) {
        int delta = direction * adjustmentMultiplier();
        send(new FaceConfigurationEdit.NumberEdit(
            FaceConfigurationEdit.NumberField.PRIORITY,
            saturatedAdd(menu.getPriority(), delta)));
        playClick.run();
    }

    private void adjustKeepStock(int direction) {
        int delta = direction * adjustmentMultiplier();
        send(new FaceConfigurationEdit.NumberEdit(
            FaceConfigurationEdit.NumberField.KEEP_STOCK,
            Math.max(0, saturatedAdd(menu.getKeepStock(), delta))));
        playClick.run();
    }

    private static int adjustmentMultiplier() {
        boolean timesTen = SLKeyMappings.isGuiKeyDown(
            SLKeyMappings.PRIORITY_X10);
        boolean timesFive = SLKeyMappings.isGuiKeyDown(
            SLKeyMappings.PRIORITY_X5);
        if (timesTen && timesFive) {
            return 64;
        }
        if (timesTen) {
            return 10;
        }
        if (timesFive) {
            return 5;
        }
        return 1;
    }

    private static int saturatedAdd(int value, int delta) {
        long result = (long) value + delta;
        return (int) Math.max(
            Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, result));
    }

    private void send(FaceConfigurationEdit edit) {
        PacketDistributor.sendToServer(new C2SConfigureFacePayload(
            menu.getPos(), menu.getFace(), edit));
    }

    private static boolean hit(
        double mouseX,
        double mouseY,
        int x,
        int y,
        int width,
        int height
    ) {
        return mouseX >= x && mouseX < x + width
            && mouseY >= y && mouseY < y + height;
    }
}
