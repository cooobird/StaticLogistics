package com.coobird.staticlogistics.client.gui.component;

import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.client.render.SLGuiTextures;
import com.coobird.staticlogistics.config.SLConfig;
import com.coobird.staticlogistics.logistics.node.ChannelConfigurationEdit;
import com.coobird.staticlogistics.logistics.node.ConnectionKey;
import com.coobird.staticlogistics.logistics.util.NodeDisplayText;
import com.coobird.staticlogistics.transfer.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;

import java.util.*;
import java.util.function.*;

import static com.coobird.staticlogistics.client.gui.ConfiguratorLayout.ResourceDialog.*;

/// 世界建链和节点配置共用的资源选择面板，不替换正在使用的容器菜单。
public final class ResourceTypeDialog {
    private final List<LogisticsResource<?>> types = new ArrayList<>(TransferRegistries.getAllActive());
    private final Map<ResourceLocation, Integer> states = new LinkedHashMap<>();
    private final Map<ResourceLocation, Integer> originalStates = new LinkedHashMap<>();
    private final Map<ResourceLocation, Boolean> changes = new LinkedHashMap<>();
    private final List<Button> buttons = new ArrayList<>();
    private Button saveButton;
    private IntSupplier stackMultiplier, speedMultiplier;
    private boolean sourceDerived;
    private Function<ResourceLocation, LiveTransferRates.Rate> rates;
    private Supplier<String> diagnostic;
    private Supplier<Map<LogisticsNode, Map<ResourceLocation, LiveTransferRates.Rate>>> peerRates;
    private BooleanSupplier peersTruncated;
    private BooleanSupplier peerRatesReady;
    private Supplier<Set<LogisticsNode>> unavailablePeers;
    private Consumer<LogisticsNode> locatePeer;
    private int detailType, detailPage;
    private boolean detailTypesOpen;
    private final List<AtlasButton> detailButtons = new ArrayList<>();
    private AtlasButton detailPrevious, detailNext;

    public void showConnectionRates(Supplier<Map<LogisticsNode, Map<ResourceLocation, LiveTransferRates.Rate>>> rates,
                                    BooleanSupplier truncated, BooleanSupplier ready, Supplier<Set<LogisticsNode>> unavailable, Consumer<LogisticsNode> locate) {
        peerRates = rates;
        peersTruncated = truncated;
        peerRatesReady = ready;
        unavailablePeers = unavailable;
        locatePeer = locate;
    }

    private List<LogisticsResource<?>> detailTypes() {
        var observed = new HashSet<ResourceLocation>();
        peerRates.get().values().forEach(rates -> observed.addAll(rates.keySet()));
        return types.stream().filter(type -> observed.contains(type.typeId())).toList();
    }

    private List<LogisticsNode> detailPeers() {
        return peerRates.get().keySet().stream().sorted(ConnectionKey.NODE_ORDER).toList();
    }

    public void showLiveRates(Function<ResourceLocation, LiveTransferRates.Rate> rates) {
        this.rates = rates;
    }

    public void showTransferValues(IntSupplier stack, IntSupplier speed, boolean sourceDerived) {
        stackMultiplier = stack;
        speedMultiplier = speed;
        this.sourceDerived = sourceDerived;
    }

    private static Component text(String key) {
        return Component.translatable("gui.staticlogistics.resources." + key);
    }

    private final Component context;
    private final boolean batch;
    private final boolean readOnly;
    private final Consumer<ChannelConfigurationEdit.TypePatchEdit> apply;
    private final Runnable close;
    private final WindowDrag drag = new WindowDrag();
    private int viewportWidth, viewportHeight;
    private int viewportOriginX, viewportOriginY;

    public void setViewportOrigin(int x, int y) {
        viewportOriginX = x;
        viewportOriginY = y;
        int nextLeft = WindowDrag.constrain(left, panelWidth, x, viewportWidth);
        int panelHeight = peerRates != null ? detailHeight() : rows * GRID_ROW_HEIGHT + PANEL_HEIGHT_EXTRA;
        int nextTop = WindowDrag.constrain(top, panelHeight, y, viewportHeight);
        if (nextLeft == left && nextTop == top) return;
        left = nextLeft;
        top = nextTop;
        if (peerRates != null) rebuildDetailButtons();
        else rebuild();
    }

    private int left, top, panelWidth, rows, page, pageSize, columns, cell, focus = -1;

    public ResourceTypeDialog(Component context, List<ResourceLocation> selected, boolean batch, boolean readOnly, Consumer<ChannelConfigurationEdit.TypePatchEdit> apply, Runnable close) {
        this.context = context;
        this.batch = batch;
        this.readOnly = readOnly;
        this.apply = apply;
        this.close = close;
        for (var type : types) states.put(type.typeId(), selected.contains(type.typeId()) ? 1 : 0);
        originalStates.putAll(states);
    }

    public void layout(int width, int height) {
        viewportWidth = width;
        viewportHeight = height;
        if (peerRates != null) {
            panelWidth = Math.min(350, width - 16);
            rows = DETAIL_ROWS;
            left = (width - panelWidth) / 2;
            top = (height - (DETAIL_GRID_Y + rows * DETAIL_ROW_HEIGHT + 40)) / 2;
            rebuildDetailButtons();
            return;
        }
        int availableWidth = Math.min(MAX_PANEL_WIDTH, width - VIEWPORT_HORIZONTAL_MARGIN);
        cell = CELL_MIN_WIDTH;
        for (var type : types) {
            Component name = Component.translatable(type.translationKey());
            int labelWidth = Minecraft.getInstance().font.width(name);
            cell = Math.max(cell, labelWidth + CELL_LABEL_EXTRA_WIDTH);
        }
        cell = Math.min(cell, availableWidth - GRID_SIDE_INSETS);
        columns = Math.clamp((availableWidth - GRID_GAP) / (cell + GRID_GAP), 1, MAX_COLUMNS);
        columns = Math.min(columns, Math.max(1, types.size()));
        rows = Math.clamp(Math.min((height - HEIGHT_RESERVE) / GRID_ROW_HEIGHT, (types.size() + columns - 1) / columns), 1, MAX_ROWS);
        if (stackMultiplier != null) {
            availableWidth = Math.min(MIN_PANEL_WIDTH, availableWidth);
            columns = 1;
            cell = availableWidth - GRID_SIDE_INSETS;
            rows = Math.clamp(Math.min((height - HEIGHT_RESERVE) / GRID_ROW_HEIGHT, types.size()), 1, MAX_ROWS);
        }
        pageSize = rows * columns;
        page = Math.min(page, Math.max(0, (types.size() - 1) / pageSize));
        panelWidth = Math.clamp((long) columns * (cell + GRID_GAP) + GRID_GAP, Math.min(MIN_PANEL_WIDTH, availableWidth), availableWidth);
        left = (width - panelWidth) / 2;
        top = (height - (rows * GRID_ROW_HEIGHT + PANEL_HEIGHT_EXTRA)) / 2;
        rebuild();
    }

    private void rebuild() {
        buttons.clear();
        focus = -1;
        int gridLeft = left + (panelWidth - (columns * (cell + GRID_GAP) - GRID_GAP)) / 2;
        for (int i = page * pageSize; i < Math.min(types.size(), (page + 1) * pageSize); i++) {
            var type = types.get(i);
            int index = i - page * pageSize;
            AtlasButton button = AtlasButton.atlasBuilder(stackMultiplier == null ? label(type) : Component.empty(), clicked -> {
                int state = states.get(type.typeId());
                int next = 1 - state;
                states.put(type.typeId(), next);
                if (next == originalStates.get(type.typeId())) changes.remove(type.typeId());
                else changes.put(type.typeId(), next == 1);
                if (stackMultiplier == null) clicked.setMessage(label(type));
                saveButton.active = batch || !changes.isEmpty();
            }).bounds(gridLeft + (index % columns) * (cell + GRID_GAP), top + GRID_TOP + (index / columns) * GRID_ROW_HEIGHT,
                cell, GRID_BUTTON_HEIGHT).build();
            button.selection(() -> states.get(type.typeId()) == 1).textInset(21);
            button.active = !readOnly;
            buttons.add(button);
        }
        int y = top + FOOTER_TOP + rows * GRID_ROW_HEIGHT;
        Button previous = AtlasButton.atlasBuilder(Component.literal("<"), ignored -> {
                page--;
                rebuild();
            })
            .bounds(left + PAGE_PREVIOUS_X, y, PAGE_BUTTON_WIDTH, FOOTER_BUTTON_HEIGHT).build().icon(SLGuiTextures.ARROW_LEFT);
        previous.active = page > 0;
        previous.visible = types.size() > pageSize;
        buttons.add(previous);
        Button next = AtlasButton.atlasBuilder(Component.literal(">"), ignored -> {
                page++;
                rebuild();
            })
            .bounds(left + PAGE_NEXT_X, y, PAGE_BUTTON_WIDTH, FOOTER_BUTTON_HEIGHT).build().icon(SLGuiTextures.ARROW_RIGHT);
        next.active = (page + 1) * pageSize < types.size();
        next.visible = types.size() > pageSize;
        buttons.add(next);
        buttons.add(AtlasButton.atlasBuilder(Component.translatable(readOnly ? "gui.done" : "gui.cancel"), ignored -> close.run())
            .bounds(left + panelWidth - (readOnly ? DONE_RIGHT_INSET : CANCEL_RIGHT_INSET), y, ACTION_BUTTON_WIDTH, FOOTER_BUTTON_HEIGHT).build());
        Button save = AtlasButton.atlasBuilder(Component.translatable(batch ? "gui.staticlogistics.batch.stage" : "gui.staticlogistics.resources.apply"), ignored -> {
            List<ResourceLocation> added = new ArrayList<>(), removed = new ArrayList<>();
            Map<ResourceLocation, Boolean> edits = batch ? new LinkedHashMap<>() : changes;
            if (batch) states.forEach((id, state) -> edits.put(id, state == 1));
            edits.forEach((id, enabled) -> {
                if (enabled) added.add(id);
                else removed.add(id);
            });
            apply.accept(new ChannelConfigurationEdit.TypePatchEdit(added, removed));
            close.run();
        }).bounds(left + panelWidth - APPLY_RIGHT_INSET, y, ACTION_BUTTON_WIDTH, FOOTER_BUTTON_HEIGHT).build();
        saveButton = save;
        save.active = !readOnly && (batch || !changes.isEmpty());
        save.visible = !readOnly;
        buttons.add(save);
        buttons.add(AtlasButton.atlasBuilder(Component.literal("×"), ignored -> close.run())
            .bounds(left + panelWidth - CLOSE_RIGHT_INSET, top + CLOSE_TOP, CLOSE_WIDTH, CLOSE_HEIGHT).build().icon(SLGuiTextures.CLOSE).iconOnly());
    }

    private Component label(LogisticsResource<?> type) {
        return Component.translatable(type.translationKey());
    }

    public void showDiagnostic(Supplier<String> diagnostic) {
        this.diagnostic = diagnostic;
    }

    public void render(GuiGraphics graphics, Font font, int width, int height, int mouseX, int mouseY, float partialTick) {
        if (peerRates != null) {
            renderConnectionRates(graphics, font, mouseX, mouseY);
            return;
        }
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 900);
        ConfiguratorStyle.panel(graphics, left - PANEL_FRAME_OUTSET, top - PANEL_FRAME_OUTSET, panelWidth + PANEL_FRAME_OUTSET * 2, rows * GRID_ROW_HEIGHT + PANEL_HEIGHT_EXTRA);
        Component title = stackMultiplier == null ? Component.translatable("gui.staticlogistics.resources.title") : text("values_title");
        graphics.drawString(font, font.plainSubstrByWidth(title.getString(), panelWidth - TITLE_WIDTH_INSET), left + TITLE_X, top + TITLE_Y, ConfiguratorStyle.ACCENT, false);
        String state = diagnostic == null ? null : diagnostic.get();
        Component detail = diagnosticText(state);
        graphics.drawString(font, font.plainSubstrByWidth(detail.getString(), panelWidth - CONTEXT_WIDTH_INSET), left + TITLE_X, top + CONTEXT_Y, ConfiguratorStyle.MUTED_TEXT, false);
        if (batch && !readOnly && stackMultiplier == null)
            graphics.drawString(font, font.plainSubstrByWidth(Component.translatable("gui.staticlogistics.resources.batch_hint").getString(), panelWidth - CONTEXT_WIDTH_INSET), left + TITLE_X, top + HINT_Y, ConfiguratorStyle.MUTED_TEXT, false);
        if (stackMultiplier != null) {
            graphics.drawString(font, text("sent_rate"), left + VALUE_COLUMN_X, top + COLUMN_Y, ConfiguratorStyle.MUTED_TEXT, false);
            graphics.drawString(font, text("received_rate"), left + panelWidth - LAST_COLUMN_WIDTH, top + COLUMN_Y, ConfiguratorStyle.MUTED_TEXT, false);
        }
        for (Button widget : buttons) widget.render(graphics, mouseX, mouseY, partialTick);
        int visible = Math.min(types.size() - page * pageSize, pageSize);
        for (int i = 0; i < visible; i++) {
            var type = types.get(page * pageSize + i);
            Button widget = buttons.get(i);
            graphics.renderFakeItem(type.getIcon(), widget.getX() + TYPE_ICON_X, widget.getY() + TYPE_ICON_Y);
            if (stackMultiplier != null) {
                graphics.drawString(font, font.plainSubstrByWidth(Component.translatable(type.translationKey()).getString(), VALUE_COLUMN_X - TYPE_COLUMN_X), left + TYPE_COLUMN_X, widget.getY() + RATE_TEXT_Y, ConfiguratorStyle.TEXT, false);
                var rate = rates == null ? LiveTransferRates.Rate.ZERO : rates.apply(type.typeId());
                graphics.drawString(font, TransferTypeDisplay.rate(type, rate.sent()), left + VALUE_COLUMN_X, widget.getY() + RATE_TEXT_Y, ConfiguratorStyle.TEXT, false);
                graphics.drawString(font, TransferTypeDisplay.rate(type, rate.received()), left + panelWidth - LAST_COLUMN_WIDTH, widget.getY() + RATE_TEXT_Y, ConfiguratorStyle.TEXT, false);
                if (widget.isMouseOver(mouseX, mouseY)) {
                    List<Component> hints = new ArrayList<>();
                    hints.add(text("sent_rate").copy().append(": " + TransferTypeDisplay.rate(type, rate.sent())));
                    hints.add(text("received_rate").copy().append(": " + TransferTypeDisplay.rate(type, rate.received())));
                    hints.add(text("rate_hint"));
                    if (batch) hints.add(text("rate_reference"));
                    if (!sourceDerived) hints.add(Component.translatable("gui.staticlogistics.resources.limit_hint",
                        TransferTypeDisplay.transferLimit(type, stackMultiplier.getAsInt()),
                        LogisticsCalculator.calcSpeedInterval(SLConfig.getDefaultTickInterval(), speedMultiplier.getAsInt()) + " tick"));
                    graphics.renderComponentTooltip(font, hints, mouseX, mouseY);
                }
            }
        }
        if (types.size() > pageSize)
            graphics.drawString(font, (page + 1) + " / " + Math.max(1, (types.size() + pageSize - 1) / pageSize), left + PAGE_TEXT_X, top + PAGE_TEXT_Y + rows * GRID_ROW_HEIGHT, ConfiguratorStyle.MUTED_TEXT, false);
        if (diagnostic != null && mouseX >= left + TITLE_X && mouseX < left + panelWidth - CONTEXT_HIT_RIGHT_INSET
            && mouseY >= top + CONTEXT_Y && mouseY < top + CONTEXT_Y + font.lineHeight)
            graphics.renderTooltip(font, detail, mouseX, mouseY);
        graphics.pose().popPose();
    }

    private void rebuildDetailButtons() {
        detailButtons.clear();
        int footer = top + DETAIL_GRID_Y + rows * DETAIL_ROW_HEIGHT + DETAIL_FOOTER_GAP;
        detailPrevious = AtlasButton.atlasBuilder(Component.literal("<"), button -> detailPage = Math.max(0, detailPage - 1))
            .bounds(left + DETAIL_PREVIOUS_X, footer, DETAIL_PAGE_BUTTON_WIDTH, DETAIL_FOOTER_BUTTON_HEIGHT).build().icon(SLGuiTextures.ARROW_LEFT);
        detailNext = AtlasButton.atlasBuilder(Component.literal(">"), button -> detailPage = Math.min(
                Math.max(0, (detailPeers().size() - 1) / DETAIL_ROWS), detailPage + 1))
            .bounds(left + DETAIL_NEXT_X, footer, DETAIL_PAGE_BUTTON_WIDTH, DETAIL_FOOTER_BUTTON_HEIGHT).build().icon(SLGuiTextures.ARROW_RIGHT);
        detailButtons.add(detailPrevious);
        detailButtons.add(detailNext);
        detailButtons.add(AtlasButton.atlasBuilder(Component.translatable("gui.done"), button -> close.run())
            .bounds(left + panelWidth - DETAIL_DONE_RIGHT_INSET, footer, DETAIL_DONE_WIDTH, DETAIL_FOOTER_BUTTON_HEIGHT).build());
        detailButtons.add(AtlasButton.atlasBuilder(Component.translatable("gui.done"), button -> close.run())
            .bounds(left + panelWidth - DETAIL_CLOSE_RIGHT_INSET, top + DETAIL_CLOSE_Y, DETAIL_CLOSE_WIDTH, DETAIL_CLOSE_HEIGHT).build().icon(SLGuiTextures.CLOSE).iconOnly());
    }

    private int detailHeight() {
        return DETAIL_GRID_Y + rows * DETAIL_ROW_HEIGHT + DETAIL_HEIGHT_EXTRA;
    }

    private void renderConnectionRates(GuiGraphics graphics, Font font, int mouseX, int mouseY) {
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 900);
        ConfiguratorStyle.panel(graphics, left - PANEL_FRAME_OUTSET, top - PANEL_FRAME_OUTSET, panelWidth + PANEL_FRAME_OUTSET * 2, detailHeight() + PANEL_FRAME_OUTSET * 2);
        graphics.drawString(font, text("connection_rates"), left + DETAIL_TEXT_X, top + DETAIL_TITLE_Y, ConfiguratorStyle.ACCENT, false);
        graphics.drawString(font, font.plainSubstrByWidth(context.getString(), panelWidth - DETAIL_CONTEXT_WIDTH_INSET), left + DETAIL_TEXT_X, top + DETAIL_CONTEXT_Y, ConfiguratorStyle.TEXT, false);
        if (!peerRatesReady.getAsBoolean()) {
            graphics.drawString(font, Component.translatable("gui.staticlogistics.diagnostic.loading"), left + DETAIL_TEXT_X, top + DETAIL_LOADING_Y, ConfiguratorStyle.MUTED_TEXT, false);
            for (AtlasButton widget : detailButtons) {
                if (widget != detailPrevious && widget != detailNext) widget.render(graphics, mouseX, mouseY, 0);
            }
        } else {
            var availableTypes = detailTypes();
            detailType = Math.max(0, Math.min(detailType, availableTypes.size() - 1));
            var type = availableTypes.isEmpty() ? null : availableTypes.get(detailType);
            if (availableTypes.size() > 1)
                ConfiguratorStyle.button(graphics, left + DETAIL_TYPE_X, top + DETAIL_TYPE_Y, DETAIL_TYPE_WIDTH, DETAIL_TYPE_HEIGHT, false, false);
            graphics.drawString(font, font.plainSubstrByWidth(type == null
                ? Component.translatable("gui.staticlogistics.resources.no_observed_types").getString()
                : Component.translatable(type.translationKey()).getString(), type == null ? panelWidth - DETAIL_CONTEXT_WIDTH_INSET : DETAIL_TYPE_WIDTH - DETAIL_TYPE_TEXT_RIGHT_INSET), left + DETAIL_TYPE_X + DETAIL_TYPE_TEXT_X, top + DETAIL_TYPE_Y + DETAIL_TYPE_TEXT_Y, ConfiguratorStyle.TEXT, false);
            if (availableTypes.size() > 1) {
                if (detailTypesOpen)
                    SLGuiTextures.ARROW_DOWN.upsideDown(graphics, left + DETAIL_TYPE_X + DETAIL_TYPE_WIDTH - DETAIL_TYPE_ARROW_RIGHT_INSET, top + DETAIL_TYPE_Y + DETAIL_TYPE_ARROW_Y);
                else
                    SLGuiTextures.ARROW_DOWN.draw(graphics, left + DETAIL_TYPE_X + DETAIL_TYPE_WIDTH - DETAIL_TYPE_ARROW_RIGHT_INSET, top + DETAIL_TYPE_Y + DETAIL_TYPE_ARROW_Y);
            }
            var entries = peerRates.get();
            double total = 0;
            for (var entry : entries.entrySet()) {
                if (unavailablePeers.get().contains(entry.getKey())) continue;
                var rate = type == null ? LiveTransferRates.Rate.ZERO : entry.getValue().getOrDefault(type.typeId(), LiveTransferRates.Rate.ZERO);
                total += sourceDerived ? rate.received() : rate.sent();
            }
            String totalText = type == null ? "" : Component.translatable((peersTruncated.getAsBoolean() || !unavailablePeers.get().isEmpty())
                ? "gui.staticlogistics.resources.partial_total" : "gui.staticlogistics.resources.total").getString()
                + ": " + TransferTypeDisplay.rate(type, total);
            graphics.drawString(font, font.plainSubstrByWidth(totalText, panelWidth - DETAIL_TOTAL_X - DETAIL_TOTAL_RIGHT_INSET), left + DETAIL_TOTAL_X, top + DETAIL_TOTAL_Y, ConfiguratorStyle.TEXT, false);
            graphics.drawString(font, Component.translatable(sourceDerived ? "gui.staticlogistics.output" : "gui.staticlogistics.input"), left + DETAIL_TEXT_X, top + DETAIL_DIRECTION_Y, ConfiguratorStyle.MUTED_TEXT, false);
            List<LogisticsNode> peers = detailPeers();
            detailPage = Math.min(detailPage, Math.max(0, (peers.size() - 1) / DETAIL_ROWS));
            for (int i = 0; i < DETAIL_ROWS && detailPage * DETAIL_ROWS + i < peers.size(); i++) {
                var peer = peers.get(detailPage * DETAIL_ROWS + i);
                int y = top + DETAIL_GRID_Y + i * DETAIL_ROW_HEIGHT;
                boolean hovered = mouseX >= left + DETAIL_PEER_X && mouseX < left + panelWidth - DETAIL_PEER_RIGHT_INSET && mouseY >= y && mouseY < y + DETAIL_PEER_HEIGHT;
                ConfiguratorStyle.button(graphics, left + DETAIL_PEER_X, y, panelWidth - DETAIL_PEER_X - DETAIL_PEER_RIGHT_INSET, DETAIL_PEER_HEIGHT, false, hovered);
                var rate = type == null ? LiveTransferRates.Rate.ZERO : entries.get(peer).getOrDefault(type.typeId(), LiveTransferRates.Rate.ZERO);
                String amount = unavailablePeers.get().contains(peer)
                    ? Component.translatable("gui.staticlogistics.resources.unloaded").getString()
                    : type == null ? "—" : TransferTypeDisplay.rate(type, sourceDerived ? rate.received() : rate.sent());
                graphics.drawString(font, font.plainSubstrByWidth(peerLabel(peer), panelWidth - DETAIL_PEER_NAME_WIDTH_INSET), left + DETAIL_PEER_X + DETAIL_PEER_TEXT_X, y + DETAIL_PEER_TEXT_Y, ConfiguratorStyle.TEXT, false);
                graphics.drawString(font, amount, left + panelWidth - DETAIL_PEER_RATE_RIGHT_INSET, y + DETAIL_PEER_TEXT_Y, ConfiguratorStyle.TEXT, false);
                if (hovered) graphics.renderTooltip(font, NodeDisplayText.details(peer), mouseX, mouseY);
            }
            int footer = top + DETAIL_GRID_Y + rows * DETAIL_ROW_HEIGHT + DETAIL_FOOTER_GAP;
            detailPrevious.active = detailPage > 0;
            detailNext.active = (detailPage + 1) * DETAIL_ROWS < peers.size();
            for (AtlasButton widget : detailButtons) widget.render(graphics, mouseX, mouseY, 0);
            graphics.drawString(font, (detailPage + 1) + " / " + Math.max(1, (peers.size() + DETAIL_ROWS - 1) / DETAIL_ROWS), left + PAGE_TEXT_X, footer + DETAIL_PAGE_TEXT_Y, ConfiguratorStyle.MUTED_TEXT, false);
            if (detailTypesOpen) {
                int listY = top + DETAIL_MENU_Y;
                ConfiguratorStyle.panel(graphics, left + DETAIL_MENU_X, listY, DETAIL_MENU_WIDTH, availableTypes.size() * DETAIL_OPTION_HEIGHT + DETAIL_MENU_HEIGHT_INSET);
                for (int i = 0; i < availableTypes.size(); i++) {
                    int optionY = listY + DETAIL_OPTION_Y + i * DETAIL_OPTION_HEIGHT;
                    boolean hovered = mouseX >= left + DETAIL_OPTION_X && mouseX < left + DETAIL_OPTION_X + DETAIL_OPTION_WIDTH && mouseY >= optionY && mouseY < optionY + DETAIL_OPTION_HEIGHT;
                    ConfiguratorStyle.button(graphics, left + DETAIL_OPTION_X, optionY, DETAIL_OPTION_WIDTH, DETAIL_OPTION_HEIGHT, i == detailType, hovered);
                    graphics.drawString(font, font.plainSubstrByWidth(Component.translatable(availableTypes.get(i).translationKey()).getString(), DETAIL_OPTION_WIDTH - DETAIL_OPTION_TEXT_WIDTH_INSET),
                        left + DETAIL_OPTION_X + DETAIL_OPTION_TEXT_X, optionY + DETAIL_OPTION_TEXT_Y, ConfiguratorStyle.TEXT, false);
                }
            }
            if (mouseX >= left + DETAIL_TOTAL_X && mouseX < left + panelWidth - DETAIL_TOTAL_HIT_RIGHT_INSET && mouseY >= top + DETAIL_TOTAL_HIT_Y && mouseY < top + DETAIL_TOTAL_HIT_Y + DETAIL_TOTAL_HIT_HEIGHT)
                graphics.renderTooltip(font, Component.translatable(peersTruncated.getAsBoolean()
                    ? "gui.staticlogistics.resources.partial_limit_hint" : "gui.staticlogistics.resources.connection_rate_hint"), mouseX, mouseY);
        }
        graphics.pose().popPose();
    }

    private String peerLabel(LogisticsNode peer) {
        var level = Minecraft.getInstance().level;
        String name = "";
        if (level != null && level.dimension().equals(peer.gPos().dimension())
            && level.getChunkSource().hasChunk(peer.gPos().pos().getX() >> 4, peer.gPos().pos().getZ() >> 4))
            name = level.getBlockState(peer.gPos().pos()).getBlock().getName().getString() + " ";
        return name + NodeDisplayText.compact(peer).getString();
    }

    private Component diagnosticText(String state) {
        if (state == null) return context;
        if (state.isEmpty()) return Component.translatable("gui.staticlogistics.diagnostic.no_record");
        if (state.equals("loading")) return Component.translatable("gui.staticlogistics.diagnostic.loading");
        if (state.startsWith("state:"))
            return Component.translatable("gui.staticlogistics.diagnostic.state." + state.substring(6));
        if (state.equals("success")) return Component.translatable("gui.staticlogistics.diagnostic.last_success");
        Component reason = state.equals("failure")
            ? Component.translatable("gui.staticlogistics.diagnostic.unknown_failure")
            : Component.translatable("failure." + state.replace(':', '.'));
        return Component.translatable("gui.staticlogistics.diagnostic.last_failure", reason);
    }

    public Rect2i bounds() {
        return new Rect2i(left - 1, top - 1, panelWidth + 2, peerRates == null ? rows * GRID_ROW_HEIGHT + PANEL_HEIGHT_EXTRA : detailHeight() + PANEL_FRAME_OUTSET * 2);
    }

    public boolean mouseClicked(double x, double y, int button) {
        if (peerRates != null) {
            if (button != 0) return true;
            if (detailTypesOpen) {
                int option = (int) ((y - top - DETAIL_MENU_Y - DETAIL_OPTION_Y) / DETAIL_OPTION_HEIGHT);
                if (x >= left + DETAIL_OPTION_X && x < left + DETAIL_OPTION_X + DETAIL_OPTION_WIDTH && y >= top + DETAIL_MENU_Y + DETAIL_OPTION_Y && option >= 0 && option < detailTypes().size())
                    detailType = option;
                detailTypesOpen = false;
                return true;
            }
            for (AtlasButton widget : List.copyOf(detailButtons))
                if (widget.mouseClicked(x, y, button)) return true;
            if (x >= left + DETAIL_TYPE_X && x < left + DETAIL_TYPE_X + DETAIL_TYPE_WIDTH && y >= top + DETAIL_TYPE_Y && y < top + DETAIL_TYPE_Y + DETAIL_TYPE_HEIGHT && detailTypes().size() > 1) {
                detailTypesOpen = true;
                return true;
            }
            if (drag.begin(x, y, button, left, top, left, top, panelWidth - CLOSE_RIGHT_INSET, 22)) return true;
            List<LogisticsNode> peers = detailPeers();
            int index = (int) ((y - top - DETAIL_GRID_Y) / DETAIL_ROW_HEIGHT);
            if (x >= left + DETAIL_PEER_X && x < left + panelWidth - DETAIL_PEER_RIGHT_INSET && y >= top + DETAIL_GRID_Y
                && index >= 0 && index < DETAIL_ROWS && y < top + DETAIL_GRID_Y + index * DETAIL_ROW_HEIGHT + DETAIL_PEER_HEIGHT && detailPage * DETAIL_ROWS + index < peers.size()) {
                locatePeer.accept(peers.get(detailPage * DETAIL_ROWS + index));
                return true;
            }
            return true;
        }
        for (Button widget : List.copyOf(buttons)) if (widget.mouseClicked(x, y, button)) return true;
        drag.begin(x, y, button, left, top, left, top, panelWidth - CLOSE_RIGHT_INSET, 22);
        return true;
    }

    public boolean mouseDragged(double x, double y, int button) {
        if (peerRates != null) {
            if (drag.active(button)) {
                left = drag.viewportX(x, panelWidth, viewportOriginX, viewportWidth);
                top = drag.viewportY(y, detailHeight(), viewportOriginY, viewportHeight);
                detailTypesOpen = false;
                rebuildDetailButtons();
            }
            return true;
        }
        if (drag.active(button)) {
            left = drag.viewportX(x, panelWidth, viewportOriginX, viewportWidth);
            top = drag.viewportY(y, rows * GRID_ROW_HEIGHT + PANEL_HEIGHT_EXTRA, viewportOriginY, viewportHeight);
            rebuild();
        }
        return true;
    }

    public boolean mouseReleased() {
        return drag.release();
    }

    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (key == GLFW.GLFW_KEY_ESCAPE && detailTypesOpen) {
            detailTypesOpen = false;
            return true;
        }
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            close.run();
            return true;
        }
        if (peerRates != null) return true;
        if (key == GLFW.GLFW_KEY_TAB) {
            if (focus >= 0) buttons.get(focus).setFocused(false);
            int direction = (modifiers & GLFW.GLFW_MOD_SHIFT) == 0 ? 1 : -1;
            for (int i = 0; i < buttons.size(); i++) {
                focus = Math.floorMod(focus + direction, buttons.size());
                if (buttons.get(focus).active && buttons.get(focus).visible) break;
            }
            buttons.get(focus).setFocused(true);
        } else if (focus >= 0) buttons.get(focus).keyPressed(key, scanCode, modifiers);
        return true;
    }
}
