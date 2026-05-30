package com.coobird.staticlogistics.gui.screen;

import com.coobird.staticlogistics.api.type.DistributionStrategy;
import com.coobird.staticlogistics.api.type.ExtractionMode;
import com.coobird.staticlogistics.api.type.TransferType;
import com.coobird.staticlogistics.config.SLConfig;
import com.coobird.staticlogistics.core.registration.TransferRegistries;
import com.coobird.staticlogistics.gui.menu.NodeConfiguratorMenu;
import com.coobird.staticlogistics.gui.screen.component.NodeConfigControls;
import com.coobird.staticlogistics.gui.screen.texture.SLGuiTextures;
import com.coobird.staticlogistics.network.c2s.C2SConfigureFacePayload;
import com.coobird.staticlogistics.storage.config.ContainerConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class NodeConfiguratorScreen extends AbstractConfiguratorScreen<NodeConfiguratorMenu> {

    static final int BTN = NodeConfigControls.BTN, EDIT_BOX_W = 36;

    static final int INPUT_TOGGLE_X = 30, OUTPUT_TOGGLE_X = 116, ROW1_Y = 12;
    static final int CHANNEL_Y = 24, INPUT_CHANNEL_X = 32, OUTPUT_CHANNEL_X = 118;
    static final int ROW3_Y = 52, RARITY_BOX_X = 10, RARITY_OP_X = 48, STRATEGY_X = 96, STRATEGY_Y = ROW3_Y - 2;
    static final int ROW4_Y = 80, STOCK_BOX_X = 10, STOCK_OP_X = 48, EXTRACTION_X = 96, EXTRACTION_Y = ROW4_Y - 2;

    private EditBox priorityBox, keepStockBox;

    public NodeConfiguratorScreen(NodeConfiguratorMenu menu, Inventory inventory, Component title) {
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
        priorityBox = makeBox(RARITY_BOX_X, ROW3_Y, "priority", menu.getPriority(), "-?[0-9]*", 10);
        keepStockBox = makeBox(STOCK_BOX_X, ROW4_Y, "keep_stock", menu.getKeepStock(), "[0-9]*", 6);
        addRenderableWidget(priorityBox);
        addRenderableWidget(keepStockBox);
        priorityBox.setVisible(false);
        keepStockBox.setVisible(false);
    }

    private EditBox makeBox(int x, int y, String key, int init, String pattern, int maxLen) {
        EditBox b = new EditBox(font, leftPos + x, topPos + y, EDIT_BOX_W, BTN, Component.translatable("gui.staticlogistics.label." + key));
        b.setBordered(true);
        b.setMaxLength(maxLen);
        b.setFilter(s -> s.isEmpty() || s.matches(pattern));
        b.setValue(String.valueOf(init));
        b.setResponder(s -> {
            try {
                int v = s.isEmpty() ? 0 : Integer.parseInt(s);
                if (v != (key.equals("priority") ? menu.getPriority() : menu.getKeepStock())) send(key, v);
            } catch (NumberFormatException ignored) {
            }
        });
        return b;
    }

    @Override
    protected void updateWidgetVisibility() {
        super.updateWidgetVisibility();
        if (priorityBox != null) priorityBox.setVisible(menu.isGlobalInputEnabled());
        if (keepStockBox != null) keepStockBox.setVisible(menu.isGlobalInputEnabled());
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
    protected void renderTypeListItem(GuiGraphics g, TransferType t, int x, int y, boolean sel) {
        g.pose().pushPose();
        g.pose().translate(x + 4, y + 2, 0);
        g.pose().scale(0.75f, 0.75f, 1.0f);
        g.renderFakeItem(t.getIcon(), 0, 0);
        g.pose().popPose();
        g.drawString(font, font.plainSubstrByWidth(Component.translatable(t.translationKey()).getString(), 55), x + 18, y + 5, sel ? 0x98FB98 : 0xAAAAAA, false);
    }

    @Override
    protected void renderHoveredTypeTooltip(GuiGraphics g, int mx, int my) {
        if (hoveredType == null) return;
        long sm = menu.getStackMultiplier();
        int base = hoveredType.getBaseStackSize();
        boolean inf;
        long fs;
        try {
            fs = Math.multiplyExact(base, sm);
            inf = fs >= Integer.MAX_VALUE;
        } catch (ArithmeticException e) {
            inf = true;
            fs = 0;
        }
        List<Component> t = new ArrayList<>();
        t.add(Component.translatable(hoveredType.translationKey()).withStyle(ChatFormatting.WHITE));
        t.add(Component.translatable("gui.staticlogistics.stat.stack").withStyle(ChatFormatting.GRAY).append(Component.translatable(inf ? "gui.staticlogistics.infinite" : "x" + sm).withStyle(ChatFormatting.AQUA)));
        t.add(Component.translatable("gui.staticlogistics.stat.transfer").withStyle(ChatFormatting.GRAY).append(Component.translatable(inf ? "gui.staticlogistics.infinite" : String.valueOf(fs)).withStyle(ChatFormatting.GREEN)));
        t.add(Component.empty());
        t.add(Component.translatable("gui.staticlogistics.tooltip.toggle_type").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        g.renderComponentTooltip(font, t, mx, my);
    }

    @Override
    protected void onTypeClicked(TransferType t) {
        menu.toggleTypeSelection(t);
        syncType();
        playClickSound();
    }

    @Override
    public void containerTick() {
        super.containerTick();
        updateWidgetVisibility();
        syncBox(priorityBox, menu.getPriority());
        syncBox(keepStockBox, menu.getKeepStock());
    }

    private void syncBox(EditBox b, int v) {
        if (b != null && b.isVisible() && !b.isFocused()) {
            String s = String.valueOf(v);
            if (!Objects.equals(b.getValue(), s)) b.setValue(s);
        }
    }

    @Override
    protected void renderCustomContent(GuiGraphics g, int mx, int my) {
        boolean in = menu.isGlobalInputEnabled(), out = menu.isGlobalOutputEnabled();
        int lx = leftPos, ty = topPos;

        // 行1 — 开关 + 过滤槽
        NodeConfigControls.renderToggle(g, lx + INPUT_TOGGLE_X, ty + ROW1_Y, in);
        NodeConfigControls.renderToggle(g, lx + OUTPUT_TOGGLE_X, ty + ROW1_Y, out);
        if (in)
            NodeConfigControls.drawSlotBg(g, lx + NodeConfiguratorMenu.INPUT_FILTER_X, ty + NodeConfiguratorMenu.INPUT_FILTER_Y);
        if (out)
            NodeConfigControls.drawSlotBg(g, lx + NodeConfiguratorMenu.OUTPUT_FILTER_X, ty + NodeConfiguratorMenu.OUTPUT_FILTER_Y);
        Slot is = menu.getSlot(0), os = menu.getSlot(1);
        int inputSlotX = lx + NodeConfiguratorMenu.INPUT_FILTER_X, inputSlotY = ty + NodeConfiguratorMenu.INPUT_FILTER_Y;
        int outputSlotX = lx + NodeConfiguratorMenu.OUTPUT_FILTER_X, outputSlotY = ty + NodeConfiguratorMenu.OUTPUT_FILTER_Y;
        if (in && is != null && !is.getItem().isEmpty())
            NodeConfigControls.renderFilterCfgBtn(g, inputSlotX, inputSlotY, NodeConfigControls.hitFilterCfgBtn(mx, my, inputSlotX, inputSlotY));
        if (out && os != null && !os.getItem().isEmpty())
            NodeConfigControls.renderFilterCfgBtn(g, outputSlotX, outputSlotY, NodeConfigControls.hitFilterCfgBtn(mx, my, outputSlotX, outputSlotY));

        // 行2 — 频道
        NodeConfigControls.renderChannel(g, lx + INPUT_CHANNEL_X, ty + CHANNEL_Y, menu.getInputChannel());
        NodeConfigControls.renderChannel(g, lx + OUTPUT_CHANNEL_X, ty + CHANNEL_Y, menu.getOutputChannel());

        // 行3 — 稀有度 + 分发策略
        if (in) {
            g.drawString(font, Component.translatable("gui.staticlogistics.label.priority"), lx + RARITY_BOX_X, ty + ROW3_Y - 10, 0xAAAAAA, false);
            int bx = lx + RARITY_OP_X, by = ty + ROW3_Y;
            NodeConfigControls.renderOpBtn(g, bx, by, true, NodeConfigControls.hitOpBtn(mx, my, bx, by));
            NodeConfigControls.renderOpBtn(g, bx + BTN + 2, by, false, NodeConfigControls.hitOpBtn(mx, my, bx + BTN + 2, by));
        }
        if (out) renderCycleBtn(g, mx, my, lx + STRATEGY_X, ty + STRATEGY_Y, menu.getStrategy().getDisplayName());

        // 行4 — 维持库存 + 提取策略
        if (in) {
            g.drawString(font, Component.translatable("gui.staticlogistics.label.keep_stock"), lx + STOCK_BOX_X, ty + ROW4_Y - 10, 0xAAAAAA, false);
            int bx = lx + STOCK_OP_X, by = ty + ROW4_Y;
            NodeConfigControls.renderOpBtn(g, bx, by, true, NodeConfigControls.hitOpBtn(mx, my, bx, by));
            NodeConfigControls.renderOpBtn(g, bx + BTN + 2, by, false, NodeConfigControls.hitOpBtn(mx, my, bx + BTN + 2, by));
        }
        if (out)
            renderCycleBtn(g, mx, my, lx + EXTRACTION_X, ty + EXTRACTION_Y, menu.getExtractionMode().getDisplayName());

        // 行5 — 升级槽
        if (out) {
            for (int i = 2; i < 5; i++) {
                Slot s = menu.getSlot(i);
                if (s != null) NodeConfigControls.drawSlotBg(g, lx + s.x, ty + s.y);
            }
        }

    }

    private void renderOverlayTooltips(GuiGraphics g, int mx, int my) {
        boolean in = menu.isGlobalInputEnabled(), out = menu.isGlobalOutputEnabled();
        int lx = leftPos, ty = topPos;

        for (int i = 0; i < 5; i++) {
            Slot s = menu.getSlot(i);
            if (s == null || !s.isActive()) continue;
            int sx = lx + s.x, sy = ty + s.y;
            if (mx < sx || mx >= sx + 16 || my < sy || my >= sy + 16) continue;
            if (!s.getItem().isEmpty()) {
                if (i >= 2 && out) {
                    renderUpgradeSlotTooltip(g, s, i, mx, my);
                } else {
                    g.renderTooltip(font, s.getItem(), mx, my);
                }
            } else switch (i) {
                case 0 ->
                    g.renderTooltip(font, Component.translatable("gui.staticlogistics.hint.input_filter"), mx, my);
                case 1 ->
                    g.renderTooltip(font, Component.translatable("gui.staticlogistics.hint.output_filter"), mx, my);
                case 2 -> g.renderTooltip(font, Component.translatable("gui.staticlogistics.hint.speed"), mx, my);
                case 3 -> g.renderTooltip(font, Component.translatable("gui.staticlogistics.hint.range"), mx, my);
                case 4 -> g.renderTooltip(font, Component.translatable("gui.staticlogistics.hint.stack"), mx, my);
            }
        }

        // 编辑框悬停提示
        if (keepStockBox != null && keepStockBox.isVisible() && keepStockBox.isMouseOver(mx, my))
            g.renderTooltip(font, Component.translatable("gui.staticlogistics.keep_stock.tooltip"), mx, my);

        // 加减按钮悬停提示
        if (in) {
            int bx = lx + RARITY_OP_X, by = ty + ROW3_Y;
            if (NodeConfigControls.hitOpBtn(mx, my, bx, by) || NodeConfigControls.hitOpBtn(mx, my, bx + BTN + 2, by))
                g.renderTooltip(font, Component.translatable("gui.staticlogistics.priority.tooltip"), mx, my);
        }

        // 分发策略 / 提取策略 按钮悬停提示
        if (out) {
            if (NodeConfigControls.hitCycleBtn(mx, my, lx + STRATEGY_X, ty + STRATEGY_Y, menu.getStrategy().getDisplayName(), font))
                g.renderComponentTooltip(font, List.of(
                    Component.translatable("gui.staticlogistics.strategy").withStyle(ChatFormatting.BLUE),
                    menu.getStrategy().getDisplayName()
                ), mx, my);
            if (NodeConfigControls.hitCycleBtn(mx, my, lx + EXTRACTION_X, ty + EXTRACTION_Y, menu.getExtractionMode().getDisplayName(), font))
                g.renderComponentTooltip(font, List.of(
                    Component.translatable("gui.staticlogistics.extraction_mode").withStyle(ChatFormatting.BLUE),
                    menu.getExtractionMode().getDisplayName()
                ), mx, my);
        }
    }

    private void renderUpgradeSlotTooltip(GuiGraphics g, Slot slot, int idx, int mx, int my) {
        var minecraft = this.minecraft;
        if (minecraft == null) return;
        List<Component> lines = new ArrayList<>(
            Screen.getTooltipFromItem(minecraft, slot.getItem()));
        var stats = buildUpgradeStatLines(idx);
        if (!stats.isEmpty()) {
            lines.add(Component.empty());
            lines.addAll(stats);
        }
        g.renderComponentTooltip(font, lines, mx, my);
    }

    private List<Component> buildUpgradeStatLines(int slotIdx) {
        List<Component> lines = new ArrayList<>();
        int baseInterval = SLConfig.getDefaultTickInterval();
        int baseRadius = SLConfig.getDefaultRadius();
        long INF = ContainerConfig.INFINITY_MARKER;
        switch (slotIdx) {
            case 2 -> { // 速度
                long sm = menu.getSpeedMultiplier();
                int cur = (int) Math.max(1, baseInterval / Math.sqrt(sm));
                lines.add(Component.translatable("gui.staticlogistics.stat.speed").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(" ")).append(statValue(cur, sm > 1))
                    .append(statUnit("gui.staticlogistics.unit.ticks", sm > 1))
                    .append(Component.literal("  (").withStyle(ChatFormatting.DARK_GRAY))
                    .append(Component.literal(String.valueOf(baseInterval)).withStyle(ChatFormatting.DARK_GRAY))
                    .append(Component.translatable("gui.staticlogistics.unit.ticks").withStyle(ChatFormatting.DARK_GRAY))
                    .append(Component.literal(")").withStyle(ChatFormatting.DARK_GRAY)));
            }
            case 3 -> { // 范围 + 跨维度
                long rm = menu.getRangeMultiplier();
                boolean hasDim = menu.isDimensionEffective();
                boolean inf = hasDim || rm >= INF;
                lines.add(Component.translatable("gui.staticlogistics.stat.range").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(" "))
                    .append(inf
                        ? Component.translatable("gui.staticlogistics.infinite").withStyle(ChatFormatting.LIGHT_PURPLE)
                        : statValue(baseRadius * rm, rm > 1))
                    .append(inf ? Component.empty() : statUnit("gui.staticlogistics.unit.meters", rm > 1))
                    .append(Component.literal("  (").withStyle(ChatFormatting.DARK_GRAY))
                    .append(Component.literal(String.valueOf(baseRadius)).withStyle(ChatFormatting.DARK_GRAY))
                    .append(Component.translatable("gui.staticlogistics.unit.meters").withStyle(ChatFormatting.DARK_GRAY))
                    .append(Component.literal(")").withStyle(ChatFormatting.DARK_GRAY)));
                lines.add(Component.translatable("gui.staticlogistics.stat.dimension").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(" "))
                    .append(hasDim
                        ? Component.translatable("gui.staticlogistics.true").withStyle(ChatFormatting.GREEN)
                        : Component.translatable("gui.staticlogistics.false").withStyle(ChatFormatting.GRAY)));
            }
            case 4 -> { // 堆叠
                long stm = menu.getStackMultiplier();
                boolean inf = stm >= INF;
                lines.add(Component.translatable("gui.staticlogistics.stat.stack").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(" "))
                    .append(inf
                        ? Component.translatable("gui.staticlogistics.infinite").withStyle(ChatFormatting.GREEN)
                        : Component.translatable("gui.staticlogistics.unit.multiplier").withStyle(stm > 1 ? ChatFormatting.GREEN : ChatFormatting.GRAY)
                        .append(statValue(stm, stm > 1)))
                    .append(Component.literal("  (").withStyle(ChatFormatting.DARK_GRAY))
                    .append(Component.translatable("gui.staticlogistics.unit.multiplier").withStyle(ChatFormatting.DARK_GRAY))
                    .append(Component.literal("1").withStyle(ChatFormatting.DARK_GRAY))
                    .append(Component.literal(")").withStyle(ChatFormatting.DARK_GRAY)));
            }
        }
        return lines;
    }

    private static Component statValue(long val, boolean boosted) {
        return Component.literal(String.valueOf(val))
            .withStyle(boosted ? ChatFormatting.GREEN : ChatFormatting.GRAY);
    }

    private static Component statUnit(String key, boolean boosted) {
        return Component.translatable(key)
            .withStyle(boosted ? ChatFormatting.GREEN : ChatFormatting.GRAY);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        this.hoveredType = null;
        super.render(g, mx, my, pt);
        renderOverlayTooltips(g, mx, my);
        if (this.hoveredType != null) {
            renderHoveredTypeTooltip(g, mx, my);
        }
    }

    private void renderCycleBtn(GuiGraphics g, int mx, int my, int ax, int ay, Component label) {
        boolean hover = NodeConfigControls.hitCycleBtn(mx, my, ax, ay, label, font);
        NodeConfigControls.renderCycleBtn(g, font, ax, ay, label, hover);
    }


    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int lx = leftPos, ty = topPos;
        if (NodeConfigControls.hitToggle(mx, my, lx + INPUT_TOGGLE_X, ty + ROW1_Y)) {
            send("globalInput", !menu.isGlobalInputEnabled());
            playClickSound();
            return true;
        }
        if (NodeConfigControls.hitToggle(mx, my, lx + OUTPUT_TOGGLE_X, ty + ROW1_Y)) {
            send("globalOutput", !menu.isGlobalOutputEnabled());
            playClickSound();
            return true;
        }
        if (NodeConfigControls.hitChannel(mx, my, lx + INPUT_CHANNEL_X, ty + CHANNEL_Y)) {
            cycleCh("inputChannel", menu.getInputChannel(), button);
            return true;
        }
        if (NodeConfigControls.hitChannel(mx, my, lx + OUTPUT_CHANNEL_X, ty + CHANNEL_Y)) {
            cycleCh("outputChannel", menu.getOutputChannel(), button);
            return true;
        }

        if (menu.isGlobalInputEnabled()) {
            int bx = lx + RARITY_OP_X, by = ty + ROW3_Y;
            if (NodeConfigControls.hitOpBtn(mx, my, bx, by)) {
                adjPriority(1);
                return true;
            }
            if (NodeConfigControls.hitOpBtn(mx, my, bx + BTN + 2, by)) {
                adjPriority(-1);
                return true;
            }
            int bx2 = lx + STOCK_OP_X, by2 = ty + ROW4_Y;
            if (NodeConfigControls.hitOpBtn(mx, my, bx2, by2)) {
                adjKeepStock(1);
                return true;
            }
            if (NodeConfigControls.hitOpBtn(mx, my, bx2 + BTN + 2, by2)) {
                adjKeepStock(-1);
                return true;
            }
        }
        if (menu.isGlobalOutputEnabled()) {
            if (NodeConfigControls.hitCycleBtn(mx, my, lx + STRATEGY_X, ty + STRATEGY_Y, menu.getStrategy().getDisplayName(), font)) {
                DistributionStrategy[] vs = DistributionStrategy.values();
                int n = button == 1 ? (menu.getStrategy().ordinal() - 1 + vs.length) % vs.length : (menu.getStrategy().ordinal() + 1) % vs.length;
                send("strategy", vs[n].getSerializedName());
                playClickSound();
                return true;
            }
            if (NodeConfigControls.hitCycleBtn(mx, my, lx + EXTRACTION_X, ty + EXTRACTION_Y, menu.getExtractionMode().getDisplayName(), font)) {
                ExtractionMode[] vs = ExtractionMode.values();
                int n = button == 1 ? (menu.getExtractionMode().ordinal() - 1 + vs.length) % vs.length : (menu.getExtractionMode().ordinal() + 1) % vs.length;
                send("extractionMode", vs[n].getSerializedName());
                playClickSound();
                return true;
            }
        }
        if (menu.isGlobalInputEnabled())
            openFilter(mx, my, 0, lx + NodeConfiguratorMenu.INPUT_FILTER_X, ty + NodeConfiguratorMenu.INPUT_FILTER_Y);
        if (menu.isGlobalOutputEnabled())
            openFilter(mx, my, 1, lx + NodeConfiguratorMenu.OUTPUT_FILTER_X, ty + NodeConfiguratorMenu.OUTPUT_FILTER_Y);

        boolean h = super.mouseClicked(mx, my, button);
        unfocus(priorityBox, mx, my);
        unfocus(keepStockBox, mx, my);
        return h;
    }

    private void openFilter(double mx, double my, int si, int sx, int sy) {
        Slot s = menu.getSlot(si);
        if (s == null || !s.isActive() || s.getItem().isEmpty()) return;
        if (NodeConfigControls.hitFilterCfgBtn(mx, my, sx, sy)) {
            CompoundTag t = new CompoundTag();
            t.putBoolean("open_filter", true);
            t.putBoolean("is_input", si == 0);
            PacketDistributor.sendToServer(new C2SConfigureFacePayload(menu.getPos(), menu.getFace(), t));
        }
    }

    @Override
    public boolean keyPressed(int k, int sc, int m) {
        if ((k == 257 || k == 335) && priorityBox.isFocused()) {
            priorityBox.setFocused(false);
            return true;
        }
        if ((k == 257 || k == 335) && keepStockBox != null && keepStockBox.isFocused()) {
            keepStockBox.setFocused(false);
            return true;
        }
        return super.keyPressed(k, sc, m);
    }

    private void cycleCh(String key, int cur, int btn) {
        int n = btn == 1 ? (cur - 1 < 1 ? 16 : cur - 1) : (cur + 1 > 16 ? 1 : cur + 1);
        send(key, n);
        playClickSound();
    }

    private void adjPriority(int d) {
        if (d == 0) return;
        if (hasShiftDown() && hasControlDown()) d *= 64;
        else if (hasShiftDown()) d *= 10;
        else if (hasControlDown()) d *= 5;
        send("priority", menu.getPriority() + d);
    }

    private void adjKeepStock(int d) {
        if (d == 0) return;
        if (hasShiftDown() && hasControlDown()) d *= 64;
        else if (hasShiftDown()) d *= 10;
        else if (hasControlDown()) d *= 5;
        send("keep_stock", menu.getKeepStock() + d);
    }

    private void unfocus(EditBox b, double mx, double my) {
        if (b != null && !b.isMouseOver(mx, my) && b.isFocused()) b.setFocused(false);
    }

    private void send(String key, Object val) {
        CompoundTag t = new CompoundTag();
        switch (val) {
            case String s -> t.putString(key, s);
            case Integer i -> t.putInt(key, i);
            case Boolean b -> t.putBoolean(key, b);
            default -> {
            }
        }
        PacketDistributor.sendToServer(new C2SConfigureFacePayload(menu.getPos(), menu.getFace(), t));
    }

    private void syncType() {
        CompoundTag t = new CompoundTag();
        t.putInt("selected_types_mask", menu.getSelectedTypesMask());
        PacketDistributor.sendToServer(new C2SConfigureFacePayload(menu.getPos(), menu.getFace(), t));
    }
}
