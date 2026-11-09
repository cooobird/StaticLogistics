package com.coobird.staticlogistics.client.gui.screen;

import com.coobird.staticlogistics.client.gui.ConfiguratorLayout.Tooltip;
import com.coobird.staticlogistics.client.gui.component.ConfiguratorStyle;
import com.coobird.staticlogistics.client.gui.component.FilterGridWidget;
import com.coobird.staticlogistics.client.gui.component.FilterNumberDialog;
import com.coobird.staticlogistics.client.gui.component.WindowDrag;
import com.coobird.staticlogistics.client.render.SLGuiTextures;
import com.coobird.staticlogistics.content.menu.AbstractFilterMenu;
import com.coobird.staticlogistics.logistics.filter.FilterData;
import com.coobird.staticlogistics.logistics.filter.FilterRule;
import com.coobird.staticlogistics.logistics.filter.FilterTagRegistry;
import com.coobird.staticlogistics.logistics.filter.NbtRuleValues;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import org.lwjgl.glfw.GLFW;

import java.util.*;

import static com.coobird.staticlogistics.client.gui.ConfiguratorLayout.FilterScreen.*;

public abstract class BaseFilterScreen<T extends AbstractFilterMenu>
    extends AbstractConfiguratorScreen<T> implements FilterGridWidget.FilterSlotProvider {
    private final WindowDrag windowDrag = new WindowDrag();
    private final List<FilterRule> draft = new ArrayList<>();
    private final List<FilterRule> originalRules = new ArrayList<>();
    private final List<String> partialComponentKeys = new ArrayList<>();
    private final Set<Integer> selectedSlots = new LinkedHashSet<>();
    private Boolean pendingBlacklist;
    private int selectionAnchor = -1;
    private int selectedSlot = -1;
    private int selectedRule;
    private int keyIndex;
    private FilterRule.Kind kind = FilterRule.Kind.NORMAL;
    private FilterRule.Mode mode = FilterRule.Mode.MATCH;
    private FilterData cachedData;
    private Map<String, ItemStack> cachedItems = Map.of();
    private boolean dropdownOpen;
    private int dropdownOffset;
    private FilterRule.Kind expandedKind;
    private FilterNumberDialog numberDialog;

    private record RuleOption(FilterRule.Kind kind, String key) {
    }

    public BaseFilterScreen(T menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageHeight += 5;
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
    }

    @Override
    public ItemStack getFilterItem(int index) {
        FilterData data = menu.getFilterData();
        if (data != cachedData) {
            cachedData = data;
            cachedItems = data.items();
        }
        return cachedItems.getOrDefault(Integer.toString(index), ItemStack.EMPTY);
    }

    @Override
    public Fluid getFluidItem(int index) {
        return menu.getFluidSlot(index);
    }

    protected void renderFilterGrid(GuiGraphics graphics) {
        FilterGridWidget.render(graphics, leftPos, topPos, this, false);
        for (int slot : selectedSlots) {
            int x = leftPos + FilterGridWidget.START_X + slot % FilterGridWidget.GRID_COLS * FilterGridWidget.SLOT_SIZE;
            int y = topPos + FilterGridWidget.START_Y + slot / FilterGridWidget.GRID_COLS * FilterGridWidget.SLOT_SIZE;
            graphics.renderOutline(x, y, FilterGridWidget.SLOT_SIZE, FilterGridWidget.SLOT_SIZE, slot == selectedSlot ? 0xFFFFFF55 : 0xFF66DDFF);
        }
        renderControls(graphics);
    }

    private void drawButton(GuiGraphics graphics, int x, int y, int width, String text) {
        ConfiguratorStyle.button(graphics, leftPos + x, topPos + y, width, RULE_BUTTON_HEIGHT, false, false);
        String visible = font.plainSubstrByWidth(text, width - RULE_BUTTON_TEXT_WIDTH_INSET);
        graphics.drawString(font, visible, leftPos + x + (width - font.width(visible)) / 2,
            topPos + y + RULE_BUTTON_TEXT_Y, ConfiguratorStyle.TEXT, false);
    }

    private void renderControls(GuiGraphics graphics) {
        int x = leftPos + SAMPLE_X;
        int y = topPos + SAMPLE_Y;
        SLGuiTextures.FILTER_SLOT.draw(graphics, x, y);
        if (selectedSlot >= 0) {
            ItemStack sample = menu.getFilterItem(selectedSlot);
            if (!sample.isEmpty()) graphics.renderFakeItem(sample, x + SAMPLE_ICON_X, y + SAMPLE_ICON_Y);
            Fluid fluid = menu.getFluidSlot(selectedSlot);
            if (fluid != null) {
                var extension = IClientFluidTypeExtensions.of(fluid);
                var sprite = minecraft.getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(extension.getStillTexture());
                int color = extension.getTintColor();
                graphics.setColor(((color >> 16) & 255) / 255F, ((color >> 8) & 255) / 255F,
                    (color & 255) / 255F, ((color >>> 24) & 255) / 255F);
                graphics.blit(x + SAMPLE_ICON_X, y + SAMPLE_ICON_Y, 0, 16, 16, sprite);
                graphics.setColor(1, 1, 1, 1);
            }
        }
        ConfiguratorStyle.button(graphics, leftPos + FILTER_BUTTON_X, topPos + FILTER_BUTTON_Y, FILTER_BUTTON_SIZE, FILTER_BUTTON_SIZE, false, false);
        Boolean displayBlacklist = displayedBlacklist();
        (Boolean.TRUE.equals(displayBlacklist) ? SLGuiTextures.FILTER_BLACKLIST : SLGuiTextures.FILTER_WHITELIST)
            .draw(graphics, leftPos + FILTER_BUTTON_X + FILTER_ICON_X_OFFSET, topPos + FILTER_BUTTON_Y + FILTER_ICON_Y_OFFSET);
        if (displayBlacklist == null)
            graphics.drawString(font, "?", leftPos + MIXED_STATE_X, topPos + STATUS_TEXT_Y, 0xFFB02020, false);
        if (selectedSlots.size() > 1)
            graphics.drawString(font, Integer.toString(selectedSlots.size()), leftPos + SELECTED_COUNT_X, topPos + STATUS_TEXT_Y, 0xFFFFFF55, false);
        String label = Component.translatable("gui.staticlogistics.filter.rule." + kind.name().toLowerCase()).getString();
        if ((kind == FilterRule.Kind.DURABILITY || kind == FilterRule.Kind.NUMERIC) && !draft.isEmpty() && selectedRule < draft.size())
            label = durabilityLabel(draft.get(selectedRule));
        else if (kind == FilterRule.Kind.COMPONENT && mode != FilterRule.Mode.MATCH) {
            long selectedComponents = draft.stream().filter(rule -> rule.kind() == FilterRule.Kind.COMPONENT && !rule.key().isEmpty()).count();
            label = Component.translatable("gui.staticlogistics.filter.rule.selected_components", selectedComponents).getString();
        } else if (kind == FilterRule.Kind.TAG) {
            List<String> keys = availableKeys();
            if (!keys.isEmpty()) label += ": " + displayKey(kind, keys.get(keyIndex % keys.size()));
        }
        drawButton(graphics, RULE_BUTTON_X, RULE_BUTTON_Y, RULE_BUTTON_WIDTH, label);
        SLGuiTextures.ARROW_DOWN.draw(graphics, leftPos + RULE_BUTTON_X + RULE_BUTTON_WIDTH - RULE_ARROW_RIGHT_INSET, topPos + RULE_BUTTON_Y + RULE_ARROW_Y_OFFSET);
        switch (mode) {
            case MATCH -> SLGuiTextures.FILTER_MATCH.draw(graphics, leftPos + MODE_X, topPos + MODE_Y);
            case FUZZY -> SLGuiTextures.FILTER_FUZZY.draw(graphics, leftPos + MODE_X, topPos + MODE_Y);
            case INVERT -> SLGuiTextures.FILTER_INVERT.draw(graphics, leftPos + MODE_X, topPos + MODE_Y);
        }
        SLGuiTextures.ADD.draw(graphics, leftPos + ADD_X, topPos + ACTION_Y);
        SLGuiTextures.REDUCE.draw(graphics, leftPos + REDUCE_X, topPos + ACTION_Y);
        SLGuiTextures.CLOSE.draw(graphics, leftPos + CANCEL_X, topPos + ACTION_Y + ACTION_ICON_Y_OFFSET);
        SLGuiTextures.CONFIRM.draw(graphics, leftPos + CONFIRM_X, topPos + ACTION_Y + ACTION_ICON_Y_OFFSET);
    }

    private List<String> availableKeys() {
        return availableKeys(kind);
    }

    private List<String> availableKeys(FilterRule.Kind optionKind) {
        if (selectedSlot < 0) return List.of();
        ItemStack sample = menu.getFilterItem(selectedSlot);
        Fluid fluid = menu.getFluidSlot(selectedSlot);
        List<String> keys = new ArrayList<>();
        if ((optionKind == FilterRule.Kind.COMPONENT || optionKind == FilterRule.Kind.NUMERIC) && !sample.isEmpty()) {
            keys.addAll(NbtRuleValues.keys(sample, optionKind == FilterRule.Kind.NUMERIC));
        } else if (optionKind == FilterRule.Kind.TAG) {
            return FilterTagRegistry.options(sample, fluid);
        }
        keys.sort(String::compareTo);
        return keys;
    }

    private String displayKey(FilterRule.Kind optionKind, String key) {
        return optionKind == FilterRule.Kind.TAG ? FilterTagRegistry.display(key) : key;
    }

    private String tagKey(String key) {
        return FilterTagRegistry.canonicalKey(key, menu.getFluidSlot(selectedSlot) == null
            ? Registries.ITEM.location() : Registries.FLUID.location());
    }

    private String optionText(RuleOption option) {
        if (option.key() == null)
            return Component.translatable("gui.staticlogistics.filter.rule.no_values").getString();
        if (option.kind() == FilterRule.Kind.DURABILITY)
            return Component.translatable("gui.staticlogistics.filter.rule.durability").getString();
        if (option.kind() == FilterRule.Kind.NUMERIC && !option.key().isEmpty())
            return Component.translatable("gui.staticlogistics.filter.rule.numeric").getString() + ": " + option.key();
        if (option.key().isEmpty()) {
            String prefix = option.kind() == FilterRule.Kind.NORMAL || option.kind() == FilterRule.Kind.DURABILITY
                ? "" : expandedKind == option.kind() ? "▾ " : "▸ ";
            return prefix + Component.translatable("gui.staticlogistics.filter.rule." + option.kind().name().toLowerCase()).getString();
        }
        return displayKey(option.kind(), option.key());
    }

    private int dropdownWidth(List<RuleOption> options) {
        int required = DROPDOWN_WIDTH;
        for (RuleOption option : options)
            required = Math.max(required, font.width(optionText(option))
                + (option.kind() == FilterRule.Kind.COMPONENT && option.key() != null && !option.key().isEmpty() ? DROPDOWN_CHECK_EXTRA_WIDTH : DROPDOWN_NORMAL_EXTRA_WIDTH));
        return Math.min(required, width - DROPDOWN_SCREEN_MARGIN * 2);
    }

    private int dropdownLeft(int listWidth) {
        return Mth.clamp(leftPos + DROPDOWN_X, 2, width - listWidth - 2);
    }

    private boolean hitDropdown(double mx, double my, int y, int height, List<RuleOption> options) {
        int listWidth = dropdownWidth(options);
        int x = dropdownLeft(listWidth);
        return mx >= x && mx < x + listWidth
            && my >= topPos + y && my < topPos + y + height;
    }

    private List<RuleOption> dropdownOptions() {
        List<RuleOption> options = new ArrayList<>();
        options.add(new RuleOption(FilterRule.Kind.NORMAL, ""));
        for (FilterRule.Kind optionKind : List.of(FilterRule.Kind.COMPONENT, FilterRule.Kind.TAG)) {
            options.add(new RuleOption(optionKind, ""));
            if (expandedKind == optionKind) {
                if (optionKind == FilterRule.Kind.COMPONENT) {
                    if (mode == FilterRule.Mode.MATCH) continue;
                }
                List<String> keys = availableKeys(optionKind);
                Set<String> selectedComponents = new LinkedHashSet<>();
                Set<String> numericComponents = Set.of();
                if (optionKind == FilterRule.Kind.COMPONENT) {
                    for (FilterRule rule : draft)
                        if (rule.kind() == FilterRule.Kind.COMPONENT) selectedComponents.add(rule.key());
                    if (!selectedComponents.isEmpty())
                        numericComponents = new LinkedHashSet<>(availableKeys(FilterRule.Kind.NUMERIC));
                }
                if (keys.isEmpty()) options.add(new RuleOption(optionKind, null));
                else for (String key : keys) {
                    options.add(new RuleOption(optionKind, key));
                    if (optionKind == FilterRule.Kind.COMPONENT && selectedComponents.contains(key)) {
                        if (numericComponents.contains(key))
                            options.add(new RuleOption(FilterRule.Kind.NUMERIC, key));
                        if (key.equals("Damage") && menu.getFilterItem(selectedSlot).isDamageableItem())
                            options.add(new RuleOption(FilterRule.Kind.DURABILITY, key));
                    }
                }
            }
        }
        return options;
    }

    private String durabilityLabel(FilterRule rule) {
        if (rule.kind() == FilterRule.Kind.NUMERIC) {
            String[] numeric = rule.key().split("\\|", -1);
            String symbol = numeric[1].equals("gt") ? ">" : numeric[1].equals("lt") ? "<" : "=";
            return numeric[0] + " " + symbol + " " + numeric[2];
        }
        if (rule.kind() != FilterRule.Kind.DURABILITY)
            return Component.translatable("gui.staticlogistics.filter.rule.durability").getString();
        String[] parts = rule.key().split("\\|", -1);
        String symbol = parts[0].equals("gt") ? ">" : parts[0].equals("lt") ? "<" : "=";
        return Component.translatable("gui.staticlogistics.filter.rule.durability").getString() + " " + symbol + " " + parts[2]
            + (parts[1].equals("percent") ? "%" : "");
    }

    private void openDurabilityEditor() {
        if (!hasSelectedSample() || !menu.getFilterItem(selectedSlot).isDamageableItem()) return;
        openNumberEditor(null);
    }

    private void openNumericEditor(String component) {
        if (!hasSelectedSample()) return;
        openNumberEditor(component);
    }

    private void openNumberEditor(String component) {
        FilterRule current = null;
        for (FilterRule rule : draft) {
            if (component == null ? rule.kind() == FilterRule.Kind.DURABILITY
                : rule.kind() == FilterRule.Kind.NUMERIC && rule.key().startsWith(component + "|")) {
                current = rule;
                break;
            }
        }
        dropdownOpen = false;
        numberDialog = new FilterNumberDialog(font, leftPos + NUMBER_DIALOG_X, topPos + NUMBER_DIALOG_Y, component, current,
            rule -> {
                if (component == null) {
                    draft.removeIf(existing -> existing.kind() == FilterRule.Kind.DURABILITY
                        || existing.kind() == FilterRule.Kind.NUMERIC && existing.key().startsWith("Damage|"));
                } else {
                    draft.removeIf(existing -> existing.kind() == FilterRule.Kind.NUMERIC && existing.key().startsWith(component + "|")
                        || component.equals("Damage") && existing.kind() == FilterRule.Kind.DURABILITY);
                }
                if (draft.size() < FilterData.MAX_RULES_PER_SLOT) {
                    draft.add(rule);
                    selectedRule = draft.size() - 1;
                    kind = rule.kind();
                }
            }, () -> numberDialog = null);
    }

    private void renderDropdown(GuiGraphics graphics, int mx, int my) {
        List<RuleOption> options = dropdownOptions();
        int rows = Math.min(DROPDOWN_VISIBLE_ROWS, options.size() - dropdownOffset);
        int listWidth = dropdownWidth(options);
        int x = dropdownLeft(listWidth);
        int y = topPos + DROPDOWN_Y;
        String overflowText = null;
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 500);
        ConfiguratorStyle.panel(graphics, x, y, listWidth, rows * DROPDOWN_ROW_HEIGHT + DROPDOWN_INSET * 2);
        for (int row = 0; row < rows; row++) {
            RuleOption option = options.get(dropdownOffset + row);
            int rowY = y + DROPDOWN_INSET + row * DROPDOWN_ROW_HEIGHT;
            boolean hovered = mx >= x + DROPDOWN_INSET && mx < x + listWidth - DROPDOWN_INSET && my >= rowY && my < rowY + DROPDOWN_ROW_HEIGHT;
            boolean selected = option.key() != null && (option.kind() == FilterRule.Kind.NORMAL
                ? option.kind() == kind && option.key().isEmpty()
                : draft.stream().anyMatch(rule -> rule.kind() == option.kind()
                && (option.kind() == FilterRule.Kind.DURABILITY || rule.key().equals(option.key())
                || option.kind() == FilterRule.Kind.TAG && tagKey(rule.key()).equals(option.key())
                || option.kind() == FilterRule.Kind.NUMERIC && rule.key().startsWith(option.key() + "|"))
                && (!option.key().isEmpty() || rule.mode() == FilterRule.Mode.MATCH)));
            ConfiguratorStyle.row(graphics, x + DROPDOWN_INSET, rowY, listWidth - DROPDOWN_INSET * 2, DROPDOWN_ROW_HEIGHT, selected, hovered);
            String text = optionText(option);
            boolean componentOption = option.kind() == FilterRule.Kind.COMPONENT && option.key() != null && !option.key().isEmpty();
            int textWidth = listWidth - (componentOption ? DROPDOWN_CHECK_TEXT_INSET : DROPDOWN_TEXT_INSET);
            if (hovered && font.width(text) > textWidth) overflowText = text;
            graphics.drawString(font, font.plainSubstrByWidth(text, textWidth), x + DROPDOWN_TEXT_X, rowY + DROPDOWN_TEXT_Y, ConfiguratorStyle.TEXT, false);
            if (componentOption) {
                int checkboxX = x + listWidth - DROPDOWN_CHECK_RIGHT_INSET;
                int checkboxY = rowY + DROPDOWN_CHECK_Y;
                if (selected) SLGuiTextures.CONFIRM.draw(graphics, checkboxX, checkboxY);
                else
                    SLGuiTextures.BUTTON.frame(graphics, checkboxX, checkboxY, DROPDOWN_CHECK_SIZE, DROPDOWN_CHECK_SIZE, 3);
            }
        }
        if (overflowText != null) {
            renderFullText(graphics, overflowText, mx, my);
        }
        graphics.pose().popPose();
    }

    private void renderFullText(GuiGraphics graphics, String text, int mx, int my) {
        int tipWidth = Math.min(width - Tooltip.SCREEN_WIDTH_INSET, Math.max(Tooltip.MIN_WIDTH, font.width(text) + Tooltip.TEXT_WIDTH_INSET));
        var lines = font.split(Component.literal(text), tipWidth - Tooltip.TEXT_WIDTH_INSET);
        int tipHeight = lines.size() * font.lineHeight + Tooltip.TEXT_HEIGHT_INSET;
        int tipX = Mth.clamp(mx + Tooltip.MOUSE_X_OFFSET, Tooltip.SCREEN_MARGIN, width - tipWidth - Tooltip.SCREEN_MARGIN);
        int tipY = Mth.clamp(my - tipHeight - Tooltip.MOUSE_Y_GAP, Tooltip.SCREEN_MARGIN, height - tipHeight - Tooltip.SCREEN_MARGIN);
        ConfiguratorStyle.panel(graphics, tipX, tipY, tipWidth, tipHeight);
        for (int i = 0; i < lines.size(); i++)
            graphics.drawString(font, lines.get(i), tipX + Tooltip.TEXT_X, tipY + Tooltip.TEXT_Y + i * font.lineHeight, ConfiguratorStyle.TEXT, false);
    }

    private boolean hasSelectedSample() {
        return selectedSlot >= 0 && (!menu.getFilterItem(selectedSlot).isEmpty() || menu.getFluidSlot(selectedSlot) != null);
    }

    private Boolean displayedBlacklist() {
        if (pendingBlacklist != null) return pendingBlacklist;
        Boolean value = null;
        for (int slot : selectedSlots) {
            boolean next = menu.isBlacklistMode(slot);
            if (value != null && value != next) return null;
            value = next;
        }
        return (value != null || !selectedSlots.isEmpty()) && value;
    }

    private boolean hasPendingChanges() {
        return pendingBlacklist != null || !draft.equals(originalRules);
    }

    private boolean canChangeSelection() {
        return !hasPendingChanges();
    }

    private boolean isOccupied(int slot) {
        return !menu.getFilterItem(slot).isEmpty() || menu.getFluidSlot(slot) != null;
    }

    private void selectSlot(int slot) {
        selectedSlots.clear();
        if (isOccupied(slot)) selectedSlots.add(slot);
        selectionAnchor = slot;
        loadSelection(slot);
    }

    private void loadSelection(int slot) {
        dropdownOpen = false;
        selectedSlot = slot;
        draft.clear();
        draft.addAll(menu.getFilterData().rules().getOrDefault(Integer.toString(slot), List.of()));
        originalRules.clear();
        originalRules.addAll(draft);
        partialComponentKeys.clear();
        for (FilterRule rule : draft)
            if (rule.kind() == FilterRule.Kind.COMPONENT && rule.mode() != FilterRule.Mode.MATCH)
                partialComponentKeys.add(rule.key());
        pendingBlacklist = null;
        selectedRule = 0;
        loadRule();
    }

    private void loadRule() {
        keyIndex = 0;
        if (draft.isEmpty()) {
            kind = FilterRule.Kind.NORMAL;
            mode = FilterRule.Mode.MATCH;
            return;
        }
        FilterRule rule = draft.get(selectedRule);
        kind = rule.kind();
        mode = rule.mode();
        String key = rule.key();
        if (kind == FilterRule.Kind.TAG) key = tagKey(key);
        int index = availableKeys().indexOf(key);
        if (index >= 0) keyIndex = index;
    }

    private void saveDraftRule() {
        if (draft.isEmpty()) return;
        List<String> keys = availableKeys();
        if (kind != FilterRule.Kind.NORMAL && !(kind == FilterRule.Kind.COMPONENT && mode == FilterRule.Mode.MATCH) && keys.isEmpty())
            return;
        String key = kind == FilterRule.Kind.NORMAL || kind == FilterRule.Kind.COMPONENT && mode == FilterRule.Mode.MATCH
            ? "" : keys.get(keyIndex % keys.size());
        draft.set(selectedRule, new FilterRule(kind, mode, key));
    }

    private boolean hit(double x, double y, int bx, int by, int w, int h) {
        return x >= leftPos + bx && x < leftPos + bx + w && y >= topPos + by && y < topPos + by + h;
    }

    @Override
    public void render(GuiGraphics graphics, int mx, int my, float partialTick) {
        super.render(graphics, mx, my, partialTick);
        if (numberDialog != null) {
            numberDialog.render(graphics, mx, my, width, height);
            return;
        }
        if (dropdownOpen) {
            renderDropdown(graphics, mx, my);
            return;
        }
        FilterGridWidget.renderTooltips(graphics, font, leftPos, topPos, mx, my, this, false, menu.getCarried());
        renderTooltip(graphics, mx, my);
        List<Component> hints = new ArrayList<>();
        if (hit(mx, my, FILTER_BUTTON_X, FILTER_BUTTON_Y, FILTER_BUTTON_SIZE, FILTER_BUTTON_SIZE)) {
            hints.add(Component.translatable("gui.staticlogistics.filter.rule.blacklist_hint"));
        } else if (hit(mx, my, RULE_BUTTON_X, RULE_BUTTON_Y, RULE_BUTTON_WIDTH, RULE_BUTTON_HEIGHT)) {
            List<String> keys = availableKeys();
            if (kind == FilterRule.Kind.NUMERIC && selectedRule < draft.size())
                hints.add(Component.literal(durabilityLabel(draft.get(selectedRule))));
            else if (kind != FilterRule.Kind.NORMAL && !(kind == FilterRule.Kind.COMPONENT && mode == FilterRule.Mode.MATCH) && !keys.isEmpty())
                hints.add(Component.literal(displayKey(kind, keys.get(keyIndex % keys.size()))));
            hints.add(Component.translatable("gui.staticlogistics.filter.rule.selector_hint"));
        } else if (hit(mx, my, MODE_X, MODE_Y, MODE_WIDTH, MODE_HEIGHT)) {
            hints.add(Component.translatable(kind == FilterRule.Kind.DURABILITY ? "gui.staticlogistics.filter.rule.durability_hint" : "gui.staticlogistics.filter.rule." + mode.name().toLowerCase() + "_hint"));
        } else if (hit(mx, my, ADD_X, ACTION_Y, ACTION_SIZE, ACTION_SIZE))
            hints.add(Component.translatable("gui.staticlogistics.filter.rule.add_hint"));
        else if (hit(mx, my, REDUCE_X, ACTION_Y, ACTION_SIZE, ACTION_SIZE))
            hints.add(Component.translatable("gui.staticlogistics.filter.rule.remove_hint"));
        else if (hit(mx, my, CANCEL_X, ACTION_Y, ACTION_SIZE, ACTION_HEIGHT))
            hints.add(Component.translatable("gui.staticlogistics.filter.rule.cancel_hint"));
        else if (hit(mx, my, CONFIRM_X, ACTION_Y, ACTION_SIZE, ACTION_HEIGHT))
            hints.add(Component.translatable("gui.staticlogistics.filter.rule.apply_hint"));
        if (!hints.isEmpty()) graphics.renderComponentTooltip(font, hints, mx, my);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (numberDialog != null) {
            numberDialog.mouseClicked(mx, my, button);
            return true;
        }
        if (dropdownOpen) {
            List<RuleOption> options = dropdownOptions();
            int rows = Math.min(DROPDOWN_VISIBLE_ROWS, options.size() - dropdownOffset);
            if (button == 0 && hitDropdown(mx, my, DROPDOWN_Y + DROPDOWN_INSET, rows * DROPDOWN_ROW_HEIGHT, options)) {
                int index = dropdownOffset + ((int) my - topPos - DROPDOWN_Y - DROPDOWN_INSET) / DROPDOWN_ROW_HEIGHT;
                RuleOption option = options.get(index);
                if (option.key() == null) return true;
                if (option.kind() == FilterRule.Kind.COMPONENT && option.key().isEmpty() && mode == FilterRule.Mode.MATCH) {
                    if (!hasSelectedSample()) return true;
                    partialComponentKeys.clear();
                    for (FilterRule rule : draft)
                        if (rule.kind() == FilterRule.Kind.COMPONENT && !rule.key().isEmpty())
                            partialComponentKeys.add(rule.key());
                    draft.removeIf(rule -> rule.kind() == FilterRule.Kind.COMPONENT);
                    if (draft.size() < FilterData.MAX_RULES_PER_SLOT) {
                        draft.add(new FilterRule(FilterRule.Kind.COMPONENT, FilterRule.Mode.MATCH, ""));
                        selectedRule = draft.size() - 1;
                    }
                    kind = FilterRule.Kind.COMPONENT;
                    mode = FilterRule.Mode.MATCH;
                    dropdownOpen = false;
                    return true;
                }
                if (option.kind() == FilterRule.Kind.DURABILITY) {
                    openDurabilityEditor();
                    return true;
                }
                if (option.kind() == FilterRule.Kind.NUMERIC && !option.key().isEmpty()) {
                    openNumericEditor(option.key());
                    return true;
                }
                if (option.kind() != FilterRule.Kind.NORMAL && option.key().isEmpty()) {
                    expandedKind = expandedKind == option.kind() ? null : option.kind();
                    int maxOffset = Math.max(0, dropdownOptions().size() - DROPDOWN_VISIBLE_ROWS);
                    dropdownOffset = Math.min(dropdownOffset, maxOffset);
                    return true;
                }
                if (option.kind() == FilterRule.Kind.COMPONENT) {
                    int existing = -1;
                    for (int i = 0; i < draft.size(); i++) {
                        FilterRule rule = draft.get(i);
                        if (rule.kind() == FilterRule.Kind.COMPONENT && rule.key().equals(option.key())) {
                            existing = i;
                            break;
                        }
                    }
                    if (existing >= 0) {
                        draft.remove(existing);
                        draft.removeIf(rule -> rule.kind() == FilterRule.Kind.NUMERIC && rule.key().startsWith(option.key() + "|")
                            || option.key().equals("Damage") && rule.kind() == FilterRule.Kind.DURABILITY);
                        selectedRule = draft.isEmpty() ? 0 : Math.min(existing, draft.size() - 1);
                    } else if (draft.size() < FilterData.MAX_RULES_PER_SLOT) {
                        draft.add(new FilterRule(FilterRule.Kind.COMPONENT, mode, option.key()));
                        selectedRule = draft.size() - 1;
                    }
                    kind = FilterRule.Kind.COMPONENT;
                    keyIndex = availableKeys().indexOf(option.key());
                    dropdownOffset = Math.min(dropdownOffset, Math.max(0, dropdownOptions().size() - DROPDOWN_VISIBLE_ROWS));
                    return true;
                }
                if (option.kind() == FilterRule.Kind.TAG) {
                    draft.removeIf(rule -> rule.kind() == FilterRule.Kind.TAG);
                    if (draft.size() < FilterData.MAX_RULES_PER_SLOT) {
                        draft.add(new FilterRule(FilterRule.Kind.TAG, mode, option.key()));
                        selectedRule = draft.size() - 1;
                    }
                    kind = FilterRule.Kind.TAG;
                    keyIndex = availableKeys().indexOf(option.key());
                } else {
                    kind = FilterRule.Kind.NORMAL;
                    keyIndex = 0;
                }
                dropdownOpen = false;
                return true;
            }
            dropdownOpen = false;
            return true;
        }
        if (windowDrag.begin(mx, my, button, leftPos, topPos,
            leftPos, topPos, imageWidth, FilterGridWidget.START_Y)) return true;
        if (button == 0 && hasShiftDown()) {
            var slot = getSlotUnderMouse();
            if (slot != null && !slot.getItem().isEmpty()) {
                if (!canChangeSelection()) return true;
                for (int index = 0; index < 36; index++) {
                    if (menu.getFilterItem(index).isEmpty() && menu.getFluidSlot(index) == null) {
                        menu.setFilterItem(index, slot.getItem());
                        selectSlot(index);
                        sendFilterUpdate();
                        break;
                    }
                }
                return true;
            }
        }
        if (button == 0 && hit(mx, my, FILTER_BUTTON_X, FILTER_BUTTON_Y, FILTER_BUTTON_SIZE, FILTER_BUTTON_SIZE)) {
            if (!hasSelectedSample()) return true;
            pendingBlacklist = !Boolean.TRUE.equals(displayedBlacklist());
            return true;
        }
        if (button == 0) {
            if (hit(mx, my, RULE_BUTTON_X, RULE_BUTTON_Y, RULE_BUTTON_WIDTH, RULE_BUTTON_HEIGHT)) {
                dropdownOffset = 0;
                expandedKind = null;
                dropdownOpen = true;
                return true;
            }
            if (hit(mx, my, MODE_X, MODE_Y, MODE_WIDTH, MODE_HEIGHT)) {
                if (kind == FilterRule.Kind.DURABILITY) {
                    openDurabilityEditor();
                    return true;
                }
                if (kind == FilterRule.Kind.NUMERIC && !draft.isEmpty()) {
                    openNumericEditor(draft.get(selectedRule).key().split("\\|", -1)[0]);
                    return true;
                }
                int part = (int) (mx - leftPos - MODE_X) * 3 / MODE_WIDTH;
                FilterRule.Mode previousMode = mode;
                mode = FilterRule.Mode.values()[part];
                if (kind == FilterRule.Kind.COMPONENT) {
                    if (mode == FilterRule.Mode.MATCH && previousMode != FilterRule.Mode.MATCH) {
                        partialComponentKeys.clear();
                        for (FilterRule rule : draft)
                            if (rule.kind() == FilterRule.Kind.COMPONENT && !rule.key().isEmpty())
                                partialComponentKeys.add(rule.key());
                        draft.removeIf(rule -> rule.kind() == FilterRule.Kind.COMPONENT);
                        if (hasSelectedSample() && draft.size() < FilterData.MAX_RULES_PER_SLOT) {
                            draft.add(new FilterRule(FilterRule.Kind.COMPONENT, mode, ""));
                            selectedRule = draft.size() - 1;
                        }
                    } else if (previousMode == FilterRule.Mode.MATCH && mode != FilterRule.Mode.MATCH) {
                        draft.removeIf(rule -> rule.kind() == FilterRule.Kind.COMPONENT);
                        for (String key : partialComponentKeys) {
                            if (draft.size() >= FilterData.MAX_RULES_PER_SLOT) break;
                            draft.add(new FilterRule(FilterRule.Kind.COMPONENT, mode, key));
                        }
                        selectedRule = draft.isEmpty() ? 0 : draft.size() - 1;
                    } else {
                        for (int i = 0; i < draft.size(); i++) {
                            FilterRule rule = draft.get(i);
                            if (rule.kind() == FilterRule.Kind.COMPONENT)
                                draft.set(i, new FilterRule(rule.kind(), mode, rule.key()));
                        }
                    }
                } else saveDraftRule();
                return true;
            }
            if (hit(mx, my, ADD_X, ACTION_Y, ACTION_SIZE, ACTION_SIZE) && draft.size() < 64) {
                if (!hasSelectedSample()) return true;
                if (kind == FilterRule.Kind.DURABILITY) {
                    openDurabilityEditor();
                    return true;
                }
                if (kind == FilterRule.Kind.NUMERIC && !draft.isEmpty()) {
                    openNumericEditor(draft.get(selectedRule).key().split("\\|", -1)[0]);
                    return true;
                }
                List<String> keys = availableKeys();
                if (kind != FilterRule.Kind.NORMAL && !(kind == FilterRule.Kind.COMPONENT && mode == FilterRule.Mode.MATCH) && keys.isEmpty())
                    return true;
                String key = kind == FilterRule.Kind.NORMAL || kind == FilterRule.Kind.COMPONENT && mode == FilterRule.Mode.MATCH
                    ? "" : keys.get(keyIndex % keys.size());
                if (kind == FilterRule.Kind.TAG) draft.removeIf(rule -> rule.kind() == FilterRule.Kind.TAG);
                if (kind == FilterRule.Kind.COMPONENT && draft.stream().anyMatch(rule ->
                    rule.kind() == FilterRule.Kind.COMPONENT && rule.key().equals(key))) return true;
                draft.add(new FilterRule(kind, mode, key));
                selectedRule = draft.size() - 1;
                loadRule();
                return true;
            }
            if (hit(mx, my, REDUCE_X, ACTION_Y, ACTION_SIZE, ACTION_SIZE) && !draft.isEmpty()) {
                draft.remove(selectedRule);
                selectedRule = draft.isEmpty() ? 0 : Math.min(selectedRule, draft.size() - 1);
                loadRule();
                return true;
            }
            if (hit(mx, my, REDUCE_X, ACTION_Y, ACTION_SIZE, ACTION_SIZE)) return true;
            if (hit(mx, my, CANCEL_X, ACTION_Y, ACTION_SIZE, ACTION_HEIGHT)) {
                if (hasSelectedSample()) loadSelection(selectedSlot);
                return true;
            }
            if (hit(mx, my, CONFIRM_X, ACTION_Y, ACTION_SIZE, ACTION_HEIGHT)) {
                if (!hasSelectedSample()) return true;
                if (!menu.applySelection(selectedSlots, pendingBlacklist, originalRules, draft)) {
                    return true;
                }
                sendFilterUpdate();
                loadSelection(selectedSlot);
                return true;
            }
        }
        int col = (int) (mx - leftPos - FilterGridWidget.START_X) / FilterGridWidget.SLOT_SIZE;
        int row = (int) (my - topPos - FilterGridWidget.START_Y) / FilterGridWidget.SLOT_SIZE;
        if (hit(mx, my, FilterGridWidget.START_X, FilterGridWidget.START_Y, FilterGridWidget.GRID_COLS * FilterGridWidget.SLOT_SIZE, FilterGridWidget.GRID_ROWS * FilterGridWidget.SLOT_SIZE)) {
            int slot = row * FilterGridWidget.GRID_COLS + col;
            ItemStack carried = menu.getCarried();
            if (!canChangeSelection()) return true;
            if (button == 0 && carried.isEmpty() && (hasControlDown() || hasShiftDown())) {
                if (hasShiftDown() && selectionAnchor >= 0) {
                    if (!hasControlDown()) selectedSlots.clear();
                    for (int index = Math.min(selectionAnchor, slot); index <= Math.max(selectionAnchor, slot); index++)
                        if (isOccupied(index)) selectedSlots.add(index);
                } else if (isOccupied(slot)) {
                    if (!selectedSlots.add(slot)) selectedSlots.remove(slot);
                    selectionAnchor = slot;
                }
                int active = selectedSlots.contains(slot) ? slot
                    : selectedSlots.stream().findFirst().orElse(-1);
                loadSelection(active);
                return true;
            }
            if (button == 0 && !carried.isEmpty()) {
                menu.setFilterItem(slot, carried);
                sendFilterUpdate();
            } else if (button == 1 && !carried.isEmpty()) {
                var handler = carried.getCapability(ForgeCapabilities.FLUID_HANDLER_ITEM).orElse(null);
                if (handler != null) {
                    FluidStack fluid = handler.drain(1000, IFluidHandler.FluidAction.SIMULATE);
                    if (!fluid.isEmpty()) {
                        menu.setFluidSlot(slot, fluid.getFluid());
                        sendFilterUpdate();
                    }
                }
            } else if (button == 1) {
                menu.clearSlot(slot);
                sendFilterUpdate();
            }
            selectSlot(slot);
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollY) {
        if (numberDialog != null) return true;
        if (dropdownOpen) {
            List<RuleOption> options = dropdownOptions();
            if (hitDropdown(mx, my, DROPDOWN_Y, DROPDOWN_VISIBLE_ROWS * DROPDOWN_ROW_HEIGHT + DROPDOWN_INSET * 2, options)) {
                int limit = Math.max(0, options.size() - DROPDOWN_VISIBLE_ROWS);
                dropdownOffset = Mth.clamp(dropdownOffset + (scrollY > 0 ? -1 : 1), 0, limit);
                return true;
            }
        }
        if (hit(mx, my, MODE_X, MODE_Y, MODE_WIDTH, MODE_HEIGHT) && !draft.isEmpty()) {
            selectedRule = Math.floorMod(selectedRule + (scrollY > 0 ? -1 : 1), draft.size());
            loadRule();
            return true;
        }
        return super.mouseScrolled(mx, my, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (numberDialog != null) {
            numberDialog.keyPressed(keyCode);
            return true;
        }
        if (dropdownOpen && keyCode == GLFW.GLFW_KEY_ESCAPE) {
            dropdownOpen = false;
            return true;
        }
        if (!dropdownOpen && keyCode == GLFW.GLFW_KEY_A && hasControlDown()) {
            if (!canChangeSelection()) return true;
            selectedSlots.clear();
            for (int slot = 0; slot < 36; slot++) if (isOccupied(slot)) selectedSlots.add(slot);
            int active = selectedSlots.stream().findFirst().orElse(-1);
            selectionAnchor = active;
            loadSelection(active);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (numberDialog != null) {
            numberDialog.charTyped(codePoint);
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (numberDialog != null) {
            numberDialog.mouseDragged(mx, my, button, width, height);
            return true;
        }
        if (windowDrag.active(button)) {
            leftPos = windowDrag.x(mx, imageWidth, width);
            topPos = windowDrag.y(my, imageHeight, height, 0);
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (numberDialog != null) {
            numberDialog.mouseReleased();
            return true;
        }
        return windowDrag.release() || super.mouseReleased(mx, my, button);
    }

    public Rect2i getFilterSlotArea(int slot) {
        return new Rect2i(leftPos + FilterGridWidget.START_X + slot % FilterGridWidget.GRID_COLS * FilterGridWidget.SLOT_SIZE,
            topPos + FilterGridWidget.START_Y + slot / FilterGridWidget.GRID_COLS * FilterGridWidget.SLOT_SIZE, 16, 16);
    }

    public void acceptGhostIngredient(int slot, ItemStack stack) {
        if (!canChangeSelection()) return;
        menu.setFilterItem(slot, stack);
        selectSlot(slot);
        sendFilterUpdate();
    }

    public void acceptGhostIngredient(int slot, FluidStack fluid) {
        if (!canChangeSelection()) return;
        menu.setFluidSlot(slot, fluid.getFluid());
        selectSlot(slot);
        sendFilterUpdate();
    }

    protected abstract void sendFilterUpdate();
}
