package com.coobird.staticlogistics.client.gui.component;

import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.api.NodeRole;
import com.coobird.staticlogistics.api.type.DistributionStrategy;
import com.coobird.staticlogistics.api.type.ExtractionMode;
import com.coobird.staticlogistics.client.data.ClientLinkData;
import com.coobird.staticlogistics.client.data.ConfiguratorEditSession;
import com.coobird.staticlogistics.client.gui.ConfiguratorLayout;
import com.coobird.staticlogistics.client.key.SLKeyMappings;
import com.coobird.staticlogistics.client.render.SLGuiTextures;
import com.coobird.staticlogistics.config.SLConfig;
import com.coobird.staticlogistics.content.menu.LinkConfiguratorMenu;
import com.coobird.staticlogistics.logistics.node.ChannelConfigurationEdit;
import com.coobird.staticlogistics.logistics.util.NodeDisplayText;
import com.coobird.staticlogistics.transfer.DistributionStrategyRegistry;
import com.coobird.staticlogistics.transfer.LogisticsCalculator;
import com.coobird.staticlogistics.transfer.TransferRegistries;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static com.coobird.staticlogistics.client.gui.ConfiguratorLayout.NodeConfiguration.*;

/// 紧凑节点配置。
public final class NodeConfigurationPanel {
    // 面板基准位置与通道新增按钮。
    // 节点配置面板相对界面顶部的位置。
    private static final int Y = SLGuiTextures.LinkConfigurator.NODE_CONFIG_Y;
    private Runnable savePreset = () -> {
    }, applyPreset = () -> {
    };

    public void setPresetActions(Runnable save, Runnable apply) {
        savePreset = save;
        applyPreset = apply;
    }
    private final LinkConfiguratorMenu menu;
    private final Font font;
    private final Runnable openFilter, playClick;
    private EditBox priority, stock;
    private boolean priorityDirty, stockDirty;
    private BiFunction<LogisticsNode, ChannelConfigurationEdit.NumberField, Long> pendingValues = (node, field) -> null;

    public void setPendingValues(BiFunction<LogisticsNode, ChannelConfigurationEdit.NumberField, Long> values) {
        pendingValues = values;
    }

    private LogisticsNode target;
    private ConfiguratorEditSession editSession;
    private Runnable applyBatch = () -> {
    }, resetBatch = () -> {
    };

    public void setBatchActions(ConfiguratorEditSession session, Runnable apply, Runnable reset) {
        editSession = session;
        applyBatch = apply;
        resetBatch = reset;
    }

    private <T extends ChannelConfigurationEdit> T preview(T current) {
        return editSession == null ? current : editSession.preview(current);
    }

    private int batchSize;
    private boolean batchEditing;

    public void setBatchSelection(boolean batch, int count) {
        batchEditing = batch;
        batchSize = count;
    }

    public boolean hasInputError() {
        return !error.isEmpty();
    }

    private int left, top;
    private String error = "";

    public record Draft(LogisticsNode target, String priority, boolean priorityDirty, String stock, boolean stockDirty,
                        String error) {
    }

    public Draft saveDraft() {
        return new Draft(target, priority.getValue(), priorityDirty, stock.getValue(), stockDirty, error);
    }

    public void restoreDraft(Draft draft) {
        if (draft == null || !menu.hasTarget() || !menu.getTargetNode().equals(draft.target())) return;
        target = draft.target();
        if (draft.priorityDirty()) priority.setValue(draft.priority());
        if (draft.stockDirty()) stock.setValue(draft.stock());
        priorityDirty = draft.priorityDirty();
        stockDirty = draft.stockDirty();
        error = draft.error();
    }

    private Consumer<ChannelConfigurationEdit> submit;

    public void setSubmission(Consumer<ChannelConfigurationEdit> submit) {
        this.submit = submit;
    }

    @FunctionalInterface
    public interface ChannelContext {
        void open(LogisticsNode channel, double x, double y);
    }

    private ChannelContext channelContext = (channel, x, y) -> {
    };

    public void setChannelContext(ChannelContext context) {
        channelContext = context;
    }

    private Runnable addChannel = () -> {
    };
    private Consumer<LogisticsNode> selectChannel = ignored -> {
    };
    private Supplier<List<LogisticsNode>> channelOptions = List::of;
    private List<LogisticsNode> dropdownOptions = List.of();
    private boolean channelDropdownOpen;
    private boolean extractionDropdown;
    private List<Component> settingLabels = List.of();
    private List<ChannelConfigurationEdit> settingEdits = List.of();
    private int settingScroll;
    public static final int RESOURCE_ROW_Y = ConfiguratorLayout.NodeConfiguration.RESOURCE_ROW_Y;
    public static final int RESOURCE_BUTTON_X = ConfiguratorLayout.NodeConfiguration.RESOURCE_BUTTON_X;
    public static final int RESOURCE_BUTTON_WIDTH = ConfiguratorLayout.NodeConfiguration.RESOURCE_BUTTON_WIDTH;
    public static final int RESOURCE_BUTTON_HEIGHT = ConfiguratorLayout.NodeConfiguration.RESOURCE_BUTTON_HEIGHT;
    private int channelScroll;

    public void setChannelActions(Runnable add, Supplier<List<LogisticsNode>> options, Consumer<LogisticsNode> select) {
        addChannel = add;
        channelOptions = options;
        selectChannel = select;
    }

    public NodeConfigurationPanel(LinkConfiguratorMenu menu, Font font, Runnable openFilter, Runnable playClick) {
        this.menu = menu;
        this.font = font;
        this.openFilter = openFilter;
        this.playClick = playClick;
    }

    public void init(int left, int top, Consumer<EditBox> add) {
        this.left = left;
        this.top = top;
        priority = field(left + PRIORITY_FIELD_X, top + Y + NUMBER_FIELD_Y, true);
        stock = field(left + STOCK_FIELD_X, top + Y + NUMBER_FIELD_Y, false);
        add.accept(priority);
        add.accept(stock);
        tick();
    }

    private EditBox field(int x, int y, boolean isPriority) {
        EditBox box = new AtlasEditBox(font, x, y, isPriority ? PRIORITY_INITIAL_WIDTH : STOCK_INITIAL_WIDTH, NUMBER_FIELD_HEIGHT, Component.empty()) {
            @Override
            public void setFocused(boolean focused) {
                boolean finished = isFocused() && !focused;
                super.setFocused(focused);
                if (finished) normalizeNumber(this, isPriority);
            }
        };
        box.setMaxLength(24);
        box.setFilter(value -> value.matches("-?[0-9]*([.][0-9]*)?[kKmMgG]?"));
        box.setResponder(value -> {
            if (isPriority) priorityDirty = true;
            else stockDirty = true;
            error = "";
        });
        return box;
    }

    private void normalizeNumber(EditBox box, boolean isPriority) {
        if (!menu.hasTarget() || !Objects.equals(target, menu.getTargetNode())) return;
        try {
            long value = CompactNumberInput.parse(box.getValue());
            if (!isPriority && value < 0) throw new IllegalArgumentException();
            String normalized = Long.toString(value);
            if (!normalized.equals(box.getValue())) box.setValue(normalized);
            box.moveCursorToStart(false);
            error = "";
            if ((isPriority ? priorityDirty : stockDirty) && menu.hasTarget() && Objects.equals(target, menu.getTargetNode())) {
                submitNumber(isPriority, value);
            }
        } catch (IllegalArgumentException exception) {
            error = tr("number_invalid").getString();
        }
    }

    public void tick() {
        LogisticsNode next = menu.hasTarget() ? menu.getTargetNode() : null;
        if (!Objects.equals(target, next)) {
            priority.setFocused(false);
            stock.setFocused(false);
            target = next;
            priorityDirty = false;
            stockDirty = false;
            error = "";
            channelDropdownOpen = false;
            settingLabels = List.of();
            settingEdits = List.of();
        }
        if (!menu.showsOutputSettings()) {
            settingLabels = List.of();
            settingEdits = List.of();
        }
        priority.setY(top + Y + NUMBER_FIELD_Y);
        priority.setWidth(PRIORITY_FIELD_WIDTH);
        stock.setX(left + STOCK_FIELD_X);
        stock.setWidth(STOCK_FIELD_WIDTH);
        priority.setVisible(next != null && !next.isConnectionFace() && menu.showsInputSettings());
        if (!priority.visible) priority.setFocused(false);
        stock.setVisible(next != null && !next.isConnectionFace() && menu.showsInputSettings());
        if (!stock.visible) stock.setFocused(false);
        Long pendingPriority = pendingValues.apply(next, ChannelConfigurationEdit.NumberField.PRIORITY);
        if (batchEditing)
            pendingPriority = preview(new ChannelConfigurationEdit.NumberEdit(ChannelConfigurationEdit.NumberField.PRIORITY, menu.getPriority())).value();
        Long pendingStock = pendingValues.apply(next, ChannelConfigurationEdit.NumberField.KEEP_STOCK);
        if (batchEditing)
            pendingStock = preview(new ChannelConfigurationEdit.NumberEdit(ChannelConfigurationEdit.NumberField.KEEP_STOCK, menu.getKeepStock())).value();
        if (!priorityDirty && !priority.isFocused()) {
            priority.setValue(Long.toString(pendingPriority == null ? menu.getPriority() : pendingPriority));
            priority.moveCursorToStart(false);
            priorityDirty = false;
        }
        if (!stockDirty && !stock.isFocused()) {
            stock.setValue(Long.toString(pendingStock == null ? menu.getKeepStock() : pendingStock));
            stock.moveCursorToStart(false);
            stockDirty = false;
        }
    }

    public static Component channelName(LogisticsNode node) {
        if (node.isConnectionFace()) return tr("channel_empty");
        var topology = ClientLinkData.INSTANCE.getTopology(node);
        return topology != null && !topology.channelName().isBlank() ? Component.literal(topology.channelName()) : tr("channel").copy().append(" " + (node.channel() + 1));
    }

    private int rowY(int y) {
        return menu.showsInputSettings() && !menu.showsOutputSettings() && y >= 20 && y < 100 ? y + (y / 20) * 4 : y;
    }

    public int resourceRowY() {
        return rowY(RESOURCE_ROW_Y);
    }

    private void stepper(GuiGraphics g, EditBox box, int mx, int my) {
        int x = box.getX() + box.getWidth(), y = box.getY();
        for (int i = 0; i < 2; i++) {
            int sy = y + i * STEPPER_BUTTON_HEIGHT;
            ConfiguratorStyle.button(g, x, sy, STEPPER_WIDTH, STEPPER_BUTTON_HEIGHT, false, mx >= x && mx < x + STEPPER_WIDTH && my >= sy && my < sy + STEPPER_BUTTON_HEIGHT);
            if (i == 0) SLGuiTextures.ARROW_DOWN.upsideDown(g, x + STEPPER_ICON_X, sy + STEPPER_ICON_Y);
            else SLGuiTextures.ARROW_DOWN.draw(g, x + STEPPER_ICON_X, sy + STEPPER_ICON_Y);
        }
    }

    private void submitNumber(boolean isPriority, long value) {
        if (isPriority) priorityDirty = false;
        else stockDirty = false;
        send(new ChannelConfigurationEdit.NumberEdit(isPriority
            ? ChannelConfigurationEdit.NumberField.PRIORITY : ChannelConfigurationEdit.NumberField.KEEP_STOCK, value));
    }

    private static long adjustmentStep() {
        boolean ten = SLKeyMappings.isKeyDown(SLKeyMappings.PRIORITY_X10);
        boolean five = SLKeyMappings.isKeyDown(SLKeyMappings.PRIORITY_X5);
        return ten && five ? 64 : ten ? 10 : five ? 5 : 1;
    }

    public boolean numberArrowClicked(double mx, double my, int button) {
        return button == 0 && menu.hasTarget() && !menu.getTargetNode().isConnectionFace()
            && (stepNumber(priority, true, mx, my) || stepNumber(stock, false, mx, my));
    }

    private boolean stepNumber(EditBox box, boolean isPriority, double mx, double my) {
        if (!box.visible || mx < box.getX() + box.getWidth() || mx >= box.getX() + box.getWidth() + STEPPER_WIDTH
            || my < box.getY() || my >= box.getY() + box.getHeight()) return false;
        try {
            long value = CompactNumberInput.parse(box.getValue());
            boolean increase = my < box.getY() + box.getHeight() / 2;
            long minimum = isPriority ? Long.MIN_VALUE : 0;
            if (value < minimum) throw new IllegalArgumentException();
            long step = adjustmentStep();
            value = increase ? (value > Long.MAX_VALUE - step ? Long.MAX_VALUE : value + step)
                : (value < minimum + step ? minimum : value - step);
            box.setValue(Long.toString(value));
            box.moveCursorToStart(false);
            submitNumber(isPriority, value);
            playClick.run();
        } catch (IllegalArgumentException exception) {
            error = tr("number_invalid").getString();
        }
        return true;
    }

    private static Component tr(String key) {
        return Component.translatable("gui.staticlogistics.compact." + key);
    }

    private void text(GuiGraphics g, Component text, int x, int y, int color) {
        g.drawString(font, text, left + x, top + Y + rowY(y), color, false);
    }

    private void button(GuiGraphics g, Component label, int x, int y, int width, boolean selected, boolean active) {
        y = rowY(y);
        ConfiguratorStyle.button(g, left + x, top + Y + y, width, CONTROL_HEIGHT, selected, false);
        Component fitted = Component.literal(font.plainSubstrByWidth(label.getString(), width - 6));
        g.drawString(font, fitted, left + x + (width - font.width(fitted)) / 2, top + Y + y + BUTTON_TEXT_Y, active ? ConfiguratorStyle.TEXT : 0xFF60606A, false);
    }

    private void dropdownButton(GuiGraphics g, Component label, int x, int y, int width, boolean selected, boolean active, boolean expanded) {
        int drawY = top + Y + rowY(y);
        ConfiguratorStyle.button(g, left + x, drawY, width, CONTROL_HEIGHT, selected, false, active);
        String text = font.plainSubstrByWidth(label.getString(), width - 18);
        g.drawString(font, text, left + x + (width - DROPDOWN_ARROW_TEXT_SPACE - font.width(text)) / 2, drawY + DROPDOWN_TEXT_Y,
            active ? ConfiguratorStyle.TEXT : ConfiguratorStyle.MUTED_TEXT, false);
        if (expanded) SLGuiTextures.ARROW_DOWN.upsideDown(g, left + x + width - 12, drawY + 6);
        else SLGuiTextures.ARROW_DOWN.draw(g, left + x + width - 12, drawY + 6);
    }

    public void render(GuiGraphics g, int mx, int my) {
        Component header = Component.translatable("gui.staticlogistics.node_configuration");
        if (menu.hasTarget())
            header = Component.translatable("gui.staticlogistics.node.configuration_target", header, menu.getPos().toShortString(), NodeDisplayText.direction(menu.getFace()));
        if (menu.hasTarget() && batchEditing)
            header = Component.translatable("gui.staticlogistics.batch.title", batchSize);
        text(g, header, LABEL_X, HEADER_TEXT_Y, ConfiguratorStyle.ACCENT);
        if (!menu.hasTarget()) {
            text(g, Component.translatable("gui.staticlogistics.blocks.select_face"), LABEL_X, EMPTY_SELECTION_TEXT_Y, ConfiguratorStyle.MUTED_TEXT);
            return;
        }
        text(g, tr("face_channels"), LABEL_X, CHANNEL_LABEL_Y, ConfiguratorStyle.TEXT);
        dropdownButton(g, channelName(menu.getTargetNode()), CHANNEL_LIST_X, CHANNEL_BUTTON_Y, CHANNEL_LIST_WIDTH, true, true, channelDropdownOpen);
        boolean addHovered = hit(mx, my, ADD_CHANNEL_X, ADD_CHANNEL_Y, ADD_CHANNEL_WIDTH, ADD_CHANNEL_HEIGHT);
        (addHovered ? SLGuiTextures.ADD_SELECTED : SLGuiTextures.ADD).draw(g, left + ADD_CHANNEL_X + ADD_CHANNEL_ICON_X, top + Y + rowY(ADD_CHANNEL_Y) + ADD_CHANNEL_ICON_Y);
        if (menu.getTargetNode().isConnectionFace()) {
            text(g, tr("channel_empty_hint"), LABEL_X, EMPTY_CHANNEL_TEXT_Y, ConfiguratorStyle.MUTED_TEXT);
            var sharedSlot = menu.getSlot(1);
            if (sharedSlot.isActive()) {
                text(g, tr("upgrade"), UPGRADE_LABEL_X, UPGRADE_LABEL_Y, ConfiguratorStyle.TEXT);
                ConfiguratorStyle.slot(g, left + sharedSlot.x, top + sharedSlot.y);
            }
            return;
        }
        button(g, Component.translatable("gui.staticlogistics.preset.save"), PRESET_SAVE_X, PRESET_Y, PRESET_WIDTH, false, true);
        button(g, Component.translatable("gui.staticlogistics.preset.apply"), PRESET_APPLY_X, PRESET_Y, PRESET_WIDTH, false, true);
        for (int row : new int[]{39, 59, 79, 99, 119, 139}) {
            if (menu.showsInputSettings() && !menu.showsOutputSettings() && row == 99) continue;
            int separator = rowY(row);
            ConfiguratorStyle.separator(g, left + SEPARATOR_X, top + Y + separator, SEPARATOR_WIDTH);
        }
        text(g, tr("mode"), LABEL_X, MODE_LABEL_Y, ConfiguratorStyle.TEXT);
        NodeRole role = preview(new ChannelConfigurationEdit.RoleEdit(currentRole())).role();
        button(g, Component.translatable("gui.staticlogistics.blocks.extract"), EXTRACT_X, ROLE_Y, ROLE_WIDTH, role == NodeRole.SENDER, true);
        button(g, Component.translatable("gui.staticlogistics.blocks.insert"), INSERT_X, ROLE_Y, ROLE_WIDTH, role == NodeRole.RECEIVER, true);
        button(g, tr("both"), BOTH_X, ROLE_Y, ROLE_WIDTH, role == NodeRole.BOTH, true);
        boolean enabled = preview(new ChannelConfigurationEdit.EnabledEdit(menu.isChannelEnabled())).enabled();
        button(g, Component.translatable(enabled ? "gui.staticlogistics.reference.disable" : "gui.staticlogistics.reference.enable"), ENABLE_X, ROLE_Y, ENABLE_WIDTH, !enabled, true);
        boolean outputResources = menu.showsOutputSettings();
        text(g, Component.translatable(outputResources ? "gui.staticlogistics.reference.resources" : "gui.staticlogistics.resources.receivable"), LABEL_X, RESOURCE_LABEL_Y, ConfiguratorStyle.TEXT);
        ConfiguratorStyle.button(g, left + RESOURCE_BUTTON_X, top + Y + resourceRowY(), RESOURCE_BUTTON_WIDTH, RESOURCE_BUTTON_HEIGHT,
            false, outputResources && hit(mx, my, RESOURCE_BUTTON_X, RESOURCE_ROW_Y, RESOURCE_BUTTON_WIDTH, RESOURCE_BUTTON_HEIGHT));
        List<ResourceLocation> types = resourceTypes();
        int iconX = left + RESOURCE_BUTTON_X + RESOURCE_ICON_INSET_X;
        int drawnTypes = 0;
        int iconRight = left + RESOURCE_BUTTON_X + RESOURCE_BUTTON_WIDTH - (types.size() > RESOURCE_OVERFLOW_THRESHOLD ? RESOURCE_OVERFLOW_RIGHT_INSET - 1 : RESOURCE_ARROW_RIGHT_INSET);
        for (var type : TransferRegistries.getAllActive())
            if (types.contains(type.typeId()) && iconX + RESOURCE_ICON_SIZE < iconRight) {
                g.renderItem(type.getIcon(), iconX, top + Y + resourceRowY() + RESOURCE_ICON_INSET_Y);
                if (types.size() == 1)
                    g.drawString(font, Component.translatable(type.translationKey()), iconX + RESOURCE_TEXT_GAP, top + Y + resourceRowY() + RESOURCE_TEXT_Y, ConfiguratorStyle.TEXT, false);
                iconX += RESOURCE_ICON_STEP;
                drawnTypes++;
            }
        if (drawnTypes < types.size()) g.drawString(font, "+" + (types.size() - drawnTypes),
            left + RESOURCE_BUTTON_X + RESOURCE_BUTTON_WIDTH - RESOURCE_OVERFLOW_RIGHT_INSET, top + Y + resourceRowY() + RESOURCE_TEXT_Y, ConfiguratorStyle.TEXT, false);
        if (outputResources)
            SLGuiTextures.ARROW_DOWN.draw(g, left + RESOURCE_BUTTON_X + RESOURCE_BUTTON_WIDTH - RESOURCE_ARROW_RIGHT_INSET, top + Y + resourceRowY() + RESOURCE_ARROW_Y);
        text(g, tr("redstone"), LABEL_X, REDSTONE_LABEL_Y, ConfiguratorStyle.TEXT);
        button(g, tr("redstone_" + preview(new ChannelConfigurationEdit.RedstoneEdit(menu.getRedstoneMode())).mode().name().toLowerCase(Locale.ROOT)), REDSTONE_X, REDSTONE_Y, REDSTONE_WIDTH, false, true);
        if (menu.showsOutputSettings()) {
            text(g, tr("distribution"), LABEL_X, STRATEGY_LABEL_Y, ConfiguratorStyle.TEXT);
            dropdownButton(g, preview(new ChannelConfigurationEdit.StrategyEdit(menu.getStrategy())).strategy().getDisplayName(), STRATEGY_X, STRATEGY_Y, STRATEGY_WIDTH, true, true, false);
            text(g, tr("extraction"), RIGHT_LABEL_X, STRATEGY_LABEL_Y, ConfiguratorStyle.TEXT);
            dropdownButton(g, preview(new ChannelConfigurationEdit.ExtractionEdit(menu.getExtractionMode())).mode().getDisplayName(), EXTRACTION_X, STRATEGY_Y, EXTRACTION_WIDTH, true, true, false);
        }
        if (menu.showsInputSettings()) {
            text(g, Component.translatable("gui.staticlogistics.label.target_priority"), LABEL_X, INPUT_LABEL_Y, ConfiguratorStyle.TEXT);
            text(g, Component.translatable("gui.staticlogistics.label.keep_stock"), RIGHT_LABEL_X, INPUT_LABEL_Y, ConfiguratorStyle.TEXT);
        } else {
            text(g, Component.translatable("gui.staticlogistics.reference.interval"), LABEL_X, INTERVAL_LABEL_Y, ConfiguratorStyle.TEXT);
            text(g, Component.literal(LogisticsCalculator.calcSpeedInterval(
                SLConfig.getDefaultTickInterval(), menu.getSpeedMultiplier()) + " tick"), INTERVAL_VALUE_X, INTERVAL_LABEL_Y, ConfiguratorStyle.MUTED_TEXT);
        }
        if (priority.visible) stepper(g, priority, mx, my);
        if (stock.visible) stepper(g, stock, mx, my);
        var slot = menu.getSlot(0);
        if (!batchEditing) {
            text(g, Component.translatable("gui.staticlogistics.filter"), LABEL_X, FILTER_LABEL_Y, ConfiguratorStyle.TEXT);
            ConfiguratorStyle.slot(g, left + slot.x, top + slot.y, hit(mx, my, slot.x, slot.y - Y, 16, 16));
        }
        var upgradeSlot = menu.getSlot(1);
        if (upgradeSlot.isActive()) {
            text(g, tr("upgrade"), UPGRADE_LABEL_X, UPGRADE_LABEL_Y, ConfiguratorStyle.TEXT);
            ConfiguratorStyle.slot(g, left + upgradeSlot.x, top + upgradeSlot.y, hit(mx, my, upgradeSlot.x, upgradeSlot.y - Y, 16, 16));
        }
        if (batchEditing) {
            text(g, Component.translatable("gui.staticlogistics.batch.pending", batchSize), LABEL_X, BATCH_LABEL_Y, ConfiguratorStyle.MUTED_TEXT);
            button(g, Component.translatable("gui.staticlogistics.batch.reset"), BATCH_RESET_X, BATCH_BUTTON_Y, BATCH_BUTTON_WIDTH, false, editSession.hasBatchDraft());
            button(g, Component.translatable("gui.staticlogistics.resources.apply"),
                BATCH_APPLY_X, BATCH_BUTTON_Y, BATCH_BUTTON_WIDTH, editSession.hasBatchDraft(), editSession.canEdit() && editSession.hasBatchDraft());
        }
        if (!error.isEmpty()) text(g, Component.literal(error), LABEL_X, ERROR_LABEL_Y, 0xFF9D2525);
    }

    private List<ResourceLocation> resourceTypes() {
        if (menu.showsOutputSettings())
            return preview(new ChannelConfigurationEdit.TypePatchEdit(List.of(), List.of())).apply(menu.getSelectedTypeIds());
        var topology = ClientLinkData.INSTANCE.getTopology(menu.getTargetNode());
        return topology == null ? List.of() : topology.presentTypeIds();
    }

    public void renderTooltips(GuiGraphics g, int mx, int my) {
        if (!menu.hasTarget()) return;
        if (!menu.getTargetNode().isConnectionFace() && (hit(mx, my, PRESET_SAVE_X, PRESET_Y, PRESET_WIDTH, CONTROL_HEIGHT)
            || hit(mx, my, PRESET_APPLY_X, PRESET_Y, PRESET_WIDTH, CONTROL_HEIGHT))) {
            boolean save = hit(mx, my, PRESET_SAVE_X, PRESET_Y, PRESET_WIDTH, CONTROL_HEIGHT);
            g.renderComponentTooltip(font, List.of(Component.translatable(save ? "gui.staticlogistics.preset.save_hint" : "gui.staticlogistics.preset.apply_hint"),
                Component.translatable("gui.staticlogistics.preset.contents_hint")), mx, my);
            return;
        }
        if (hit(mx, my, RESOURCE_BUTTON_X, RESOURCE_ROW_Y, RESOURCE_BUTTON_WIDTH, RESOURCE_BUTTON_HEIGHT)) {
            List<ResourceLocation> selected = resourceTypes();
            List<Component> names = TransferRegistries.getAllActive().stream()
                .filter(type -> selected.contains(type.typeId()))
                .<Component>map(type -> Component.translatable(type.translationKey())).toList();
            if (!names.isEmpty()) g.renderComponentTooltip(font, names, mx, my);
            return;
        }
        for (EditBox box : List.of(priority, stock)) {
            if (box.visible && mx >= box.getX() + box.getWidth() && mx < box.getX() + box.getWidth() + STEPPER_WIDTH
                && my >= box.getY() && my < box.getY() + box.getHeight()) {
                String key = my < box.getY() + box.getHeight() / 2 ? "gui.staticlogistics.compact.number_steps_up" : "gui.staticlogistics.compact.number_steps_down";
                g.renderTooltip(font, Component.translatable(key,
                    SLKeyMappings.PRIORITY_X10.getTranslatedKeyMessage(), SLKeyMappings.PRIORITY_X5.getTranslatedKeyMessage()), mx, my);
                return;
            }
        }
        if (hit(mx, my, ADD_CHANNEL_X, ADD_CHANNEL_Y, ADD_CHANNEL_WIDTH, ADD_CHANNEL_HEIGHT))
            g.renderTooltip(font, tr("channel_add"), mx, my);
        else if (hit(mx, my, CHANNEL_LIST_X, CHANNEL_BUTTON_Y, CHANNEL_LIST_WIDTH, CHANNEL_BUTTON_HEIGHT))
            g.renderTooltip(font, tr("channel_local_hint"), mx, my);
        else {
            var slot = menu.getSlot(0);
            if (hit(mx, my, slot.x, slot.y - Y, 16, 16)) g.renderTooltip(font, tr("filter_help"), mx, my);
        }
    }

    /// 在按钮上方展开，避免选项较多时超出配置面板下边缘。
    private int settingDropdownY() {
        return SETTING_MENU_BOTTOM_Y - Math.min(CHANNEL_ROWS, settingLabels.size()) * CHANNEL_ROW_HEIGHT - SETTING_MENU_BOTTOM_GAP;
    }

    public boolean renderChannelDropdown(GuiGraphics g, int mx, int my) {
        if (!settingLabels.isEmpty()) {
            int x = left + (extractionDropdown ? EXTRACTION_MENU_X : STRATEGY_MENU_X), y = top + Y + settingDropdownY();
            int rows = Math.min(CHANNEL_ROWS, settingLabels.size());
            g.pose().pushPose();
            g.pose().translate(0, 0, 600);
            ConfiguratorStyle.panel(g, x, y, SETTING_MENU_WIDTH, rows * CHANNEL_ROW_HEIGHT + SETTING_MENU_INSET * 2);
            for (int row = 0; row < rows; row++) {
                int rowY = y + SETTING_MENU_INSET + row * CHANNEL_ROW_HEIGHT;
                boolean hover = mx >= x + SETTING_MENU_INSET && mx < x + SETTING_MENU_WIDTH - SETTING_MENU_INSET && my >= rowY && my < rowY + CHANNEL_ROW_HEIGHT;
                ChannelConfigurationEdit edit = settingEdits.get(settingScroll + row);
                boolean selected = edit instanceof ChannelConfigurationEdit.StrategyEdit(
                    DistributionStrategy strategy1
                )
                    ? strategy1.equals(menu.getStrategy())
                    : ((ChannelConfigurationEdit.ExtractionEdit) edit).mode() == menu.getExtractionMode();
                ConfiguratorStyle.row(g, x + SETTING_MENU_INSET, rowY, SETTING_MENU_WIDTH - SETTING_MENU_INSET * 2, CHANNEL_ROW_HEIGHT, selected, hover);
                g.drawString(font, font.plainSubstrByWidth(settingLabels.get(settingScroll + row).getString(), SETTING_MENU_WIDTH - SETTING_MENU_TEXT_X - SETTING_MENU_TEXT_RIGHT_INSET), x + SETTING_MENU_TEXT_X, rowY + SETTING_MENU_TEXT_Y, ConfiguratorStyle.TEXT, false);
            }
            g.pose().popPose();
            return true;
        }
        if (!channelDropdownOpen) return false;
        int rows = Math.min(CHANNEL_ROWS, dropdownOptions.size());
        int x = left + CHANNEL_LIST_X, y = top + Y + rowY(CHANNEL_LIST_Y);
        boolean hasScrollbar = dropdownOptions.size() > rows;
        int rowWidth = CHANNEL_LIST_WIDTH - CHANNEL_LIST_INSET * 2 - (hasScrollbar ? CHANNEL_LIST_SCROLL_WIDTH : 0);
        g.pose().pushPose();
        g.pose().translate(0, 0, 600);
        ConfiguratorStyle.panel(g, x, y, CHANNEL_LIST_WIDTH, rows * CHANNEL_ROW_HEIGHT + CHANNEL_LIST_INSET * 2);
        for (int row = 0; row < rows; row++) {
            LogisticsNode node = dropdownOptions.get(channelScroll + row);
            int rowY = y + CHANNEL_LIST_INSET + row * CHANNEL_ROW_HEIGHT;
            boolean selected = node.equals(menu.getTargetNode());
            boolean hovered = mx >= x + CHANNEL_LIST_INSET && mx < x + CHANNEL_LIST_INSET + rowWidth && my >= rowY && my < rowY + CHANNEL_ROW_HEIGHT;
            ConfiguratorStyle.row(g, x + CHANNEL_LIST_INSET, rowY, rowWidth, CHANNEL_ROW_HEIGHT, selected, hovered);
            Component label = channelName(node);
            g.drawString(font, font.plainSubstrByWidth(label.getString(), rowWidth - CHANNEL_LIST_TEXT_INSET * 2), x + CHANNEL_LIST_INSET + CHANNEL_LIST_TEXT_INSET, rowY + CHANNEL_LIST_TEXT_Y, selected ? ConfiguratorStyle.ACCENT : ConfiguratorStyle.TEXT, false);
        }
        if (hasScrollbar) {
            int track = rows * CHANNEL_ROW_HEIGHT, thumb = SLGuiTextures.Scrollbar.HEIGHT;
            int offset = channelScroll * (track - thumb) / (dropdownOptions.size() - rows);
            ConfiguratorStyle.scrollbar(g, x + CHANNEL_LIST_WIDTH - CHANNEL_LIST_INSET - CHANNEL_LIST_SCROLL_WIDTH, y + CHANNEL_LIST_INSET, track, offset, true);
        }
        g.pose().popPose();
        return true;
    }

    public boolean channelDropdownClicked(double mx, double my, int button) {
        if (!settingLabels.isEmpty()) {
            int x = (extractionDropdown ? EXTRACTION_MENU_X : STRATEGY_MENU_X) + SETTING_MENU_INSET;
            int rows = Math.min(CHANNEL_ROWS, settingLabels.size());
            if (button == 0 && hit(mx, my, x, settingDropdownY() + SETTING_MENU_INSET, SETTING_MENU_WIDTH - SETTING_MENU_INSET * 2, rows * CHANNEL_ROW_HEIGHT)) {
                int row = (int) (my - top - Y - settingDropdownY() - SETTING_MENU_INSET) / CHANNEL_ROW_HEIGHT;
                send(settingEdits.get(settingScroll + row));
                playClick.run();
            }
            settingLabels = List.of();
            settingEdits = List.of();
            return true;
        }
        if (!channelDropdownOpen) return false;
        int rows = Math.min(CHANNEL_ROWS, dropdownOptions.size());
        int listX = left + CHANNEL_LIST_X + CHANNEL_LIST_INSET;
        int listY = top + Y + rowY(CHANNEL_LIST_Y) + CHANNEL_LIST_INSET;
        int rowWidth = CHANNEL_LIST_WIDTH - CHANNEL_LIST_INSET * 2 - (dropdownOptions.size() > rows ? CHANNEL_LIST_SCROLL_WIDTH : 0);
        if ((button == 0 || button == 1) && mx >= listX && mx < listX + rowWidth && my >= listY && my < listY + rows * CHANNEL_ROW_HEIGHT) {
            int row = (int) (my - listY) / CHANNEL_ROW_HEIGHT;
            LogisticsNode node = dropdownOptions.get(channelScroll + row);
            if (button == 1) channelContext.open(node, mx, my);
            else if (!node.equals(menu.getTargetNode())) selectChannel.accept(node);
            playClick.run();
        }
        channelDropdownOpen = false;
        return true;
    }

    public boolean channelDropdownScrolled(double amount) {
        if (!settingLabels.isEmpty()) {
            settingScroll = Math.clamp(settingScroll - (int) Math.signum(amount), 0, Math.max(0, settingLabels.size() - CHANNEL_ROWS));
            return true;
        }
        if (!channelDropdownOpen) return false;
        channelScroll = Math.clamp(channelScroll - (int) Math.signum(amount), 0, Math.max(0, dropdownOptions.size() - CHANNEL_ROWS));
        return true;
    }

    public boolean isChannelDropdownOpen() {
        return channelDropdownOpen || !settingLabels.isEmpty();
    }

    public boolean mouseClicked(double mx, double my, int button) {
        if (!menu.hasTarget()) return false;
        if (!menu.getTargetNode().isConnectionFace() && hit(mx, my, PRESET_SAVE_X, PRESET_Y, PRESET_WIDTH, CONTROL_HEIGHT)) {
            if (button == 0) {
                savePreset.run();
                playClick.run();
            }
            return true;
        }
        if (!menu.getTargetNode().isConnectionFace() && hit(mx, my, PRESET_APPLY_X, PRESET_Y, PRESET_WIDTH, CONTROL_HEIGHT)) {
            if (button == 0) {
                applyPreset.run();
                playClick.run();
            }
            return true;
        }
        if (batchEditing && hit(mx, my, BATCH_RESET_X, BATCH_BUTTON_Y, BATCH_BUTTON_WIDTH, CONTROL_HEIGHT)) {
            if (button == 0 && editSession.canEdit()) {
                unfocusEditors(-1, -1);
                resetBatch.run();
                tick();
                playClick.run();
            }
            return true;
        }
        if (batchEditing && hit(mx, my, BATCH_APPLY_X, BATCH_BUTTON_Y, BATCH_BUTTON_WIDTH, CONTROL_HEIGHT)) {
            if (button == 0 && editSession.canEdit()) {
                applyBatch.run();
                playClick.run();
            }
            return true;
        }
        if (hit(mx, my, ADD_CHANNEL_X, ADD_CHANNEL_Y, ADD_CHANNEL_WIDTH, ADD_CHANNEL_HEIGHT)) {
            if (button == 0) {
                addChannel.run();
                playClick.run();
            }
            return true;
        }
        if (hit(mx, my, CHANNEL_LIST_X, CHANNEL_BUTTON_Y, CHANNEL_LIST_WIDTH, CHANNEL_BUTTON_HEIGHT)) {
            if (button == 0) {
                dropdownOptions = List.copyOf(channelOptions.get());
                channelScroll = Math.clamp(dropdownOptions.indexOf(menu.getTargetNode()), 0, Math.max(0, dropdownOptions.size() - CHANNEL_ROWS));
                channelDropdownOpen = !dropdownOptions.isEmpty();
                priority.setFocused(false);
                stock.setFocused(false);
                playClick.run();
            } else if (button == 1 && !menu.getTargetNode().isConnectionFace())
                channelContext.open(menu.getTargetNode(), mx, my);
            return true;
        }
        if (menu.getTargetNode().isConnectionFace()) return false;
        if (numberArrowClicked(mx, my, button)) return true;
        if (button == 0 && menu.showsOutputSettings()
            && (hit(mx, my, STRATEGY_X, STRATEGY_Y, STRATEGY_WIDTH, CONTROL_HEIGHT) || hit(mx, my, EXTRACTION_X, STRATEGY_Y, EXTRACTION_WIDTH, CONTROL_HEIGHT))) {
            extractionDropdown = hit(mx, my, EXTRACTION_X, STRATEGY_Y, EXTRACTION_WIDTH, CONTROL_HEIGHT);
            if (extractionDropdown) {
                settingLabels = Arrays.stream(ExtractionMode.values()).map(ExtractionMode::getDisplayName).toList();
                settingEdits = Arrays.stream(ExtractionMode.values())
                    .<ChannelConfigurationEdit>map(ChannelConfigurationEdit.ExtractionEdit::new).toList();
            } else {
                var strategies = DistributionStrategyRegistry.getValues();
                settingLabels = strategies.stream().map(strategy -> strategy.getDisplayName()).toList();
                settingEdits = strategies.stream().<ChannelConfigurationEdit>map(ChannelConfigurationEdit.StrategyEdit::new).toList();
            }
            settingScroll = 0;
            channelDropdownOpen = false;
            priority.setFocused(false);
            stock.setFocused(false);
            playClick.run();
            return true;
        }
        if (hit(mx, my, REDSTONE_X, REDSTONE_Y, REDSTONE_WIDTH, CONTROL_HEIGHT)) {
            send(new ChannelConfigurationEdit.RedstoneEdit(preview(new ChannelConfigurationEdit.RedstoneEdit(menu.getRedstoneMode())).mode().next()));
            playClick.run();
            return true;
        }
        if (hit(mx, my, EXTRACT_X, ROLE_Y, ROLE_WIDTH, CONTROL_HEIGHT)) {
            selectRole(NodeRole.SENDER);
            return true;
        }
        if (hit(mx, my, INSERT_X, ROLE_Y, ROLE_WIDTH, CONTROL_HEIGHT)) {
            selectRole(NodeRole.RECEIVER);
            return true;
        }
        if (hit(mx, my, BOTH_X, ROLE_Y, ROLE_WIDTH, CONTROL_HEIGHT)) {
            selectRole(NodeRole.BOTH);
            return true;
        }
        if (hit(mx, my, ENABLE_X, ROLE_Y, ENABLE_WIDTH, CONTROL_HEIGHT)) {
            send(new ChannelConfigurationEdit.EnabledEdit(!preview(new ChannelConfigurationEdit.EnabledEdit(menu.isChannelEnabled())).enabled()));
            playClick.run();
            return true;
        }
        var slot = menu.getSlot(0);
        if (button == 1 && slot.isActive() && !slot.getItem().isEmpty() && hit(mx, my, slot.x, slot.y - Y, 16, 16)) {
            openFilter.run();
            return true;
        }
        return false;
    }

    private NodeRole currentRole() {
        if (menu.isGlobalInputEnabled() && menu.isGlobalOutputEnabled()) return NodeRole.BOTH;
        if (menu.isGlobalInputEnabled()) return NodeRole.RECEIVER;
        return menu.isGlobalOutputEnabled() ? NodeRole.SENDER : NodeRole.NONE;
    }

    private void selectRole(NodeRole role) {
        if (preview(new ChannelConfigurationEdit.RoleEdit(currentRole())).role() == role) return;
        send(new ChannelConfigurationEdit.RoleEdit(role));
        playClick.run();
    }

    private void send(ChannelConfigurationEdit edit) {
        if (submit != null) submit.accept(edit);
    }

    private boolean hit(double mx, double my, int x, int y, int w, int h) {
        return mx >= left + x && mx < left + x + w && my >= top + Y + rowY(y) && my < top + Y + rowY(y) + h;
    }

    public EditBox editorAt(double x, double y) {
        if (priority.visible && priority.isMouseOver(x, y)) return priority;
        return stock.visible && stock.isMouseOver(x, y) ? stock : null;
    }

    public EditBox focusedEditor() {
        if (priority.canConsumeInput()) return priority;
        return stock.canConsumeInput() ? stock : null;
    }

    public boolean keyPressed(int key) {
        if (!settingLabels.isEmpty()) {
            if (key == 256) {
                settingLabels = List.of();
                settingEdits = List.of();
            } else if (key == 264) channelDropdownScrolled(-1);
            else if (key == 265) channelDropdownScrolled(1);
            return true;
        }
        if (channelDropdownOpen) {
            if (key == 256) channelDropdownOpen = false;
            else if (key == 264) channelDropdownScrolled(-1);
            else if (key == 265) channelDropdownScrolled(1);
            return true;
        }
        if (key != 257 && key != 335) return false;
        if (priority.isFocused() || stock.isFocused()) {
            priority.setFocused(false);
            stock.setFocused(false);
            return true;
        }
        return false;
    }

    public void unfocusEditors(double mx, double my) {
        if (!priority.isMouseOver(mx, my)) priority.setFocused(false);
        if (!stock.isMouseOver(mx, my)) stock.setFocused(false);
    }
}
