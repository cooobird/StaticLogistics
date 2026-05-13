package com.coobird.staticlogistics.gui.screen;

import com.coobird.staticlogistics.api.type.DistributionStrategy;
import com.coobird.staticlogistics.api.type.TransferType;
import com.coobird.staticlogistics.client.util.RenderConstants;
import com.coobird.staticlogistics.compat.ModIds;
import com.coobird.staticlogistics.core.registration.TransferRegistries;
import com.coobird.staticlogistics.gui.menu.FaceConfiguratorMenu;
import com.coobird.staticlogistics.gui.screen.texture.SLGuiTextures;
import com.coobird.staticlogistics.network.c2s.C2SConfigureFacePayload;
import com.hollingsworth.arsnouveau.setup.registry.ItemsRegistry;
import mekanism.common.registries.MekanismBlocks;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

public class FaceConfiguratorScreen extends AbstractConfiguratorScreen<FaceConfiguratorMenu> {
    private EditBox priorityBox;
    private TransferType hoveredType = null;

    private static final int LEFT_X = 10;
    private static final int IN_BTN_X = LEFT_X, IN_BTN_Y = 20;
    private static final int IN_COLOR_X = LEFT_X + 20, IN_COLOR_Y = 18;
    private static final int PRIORITY_Y = 65;
    private static final int PRIORITY_TEXT_Y = 80;
    private static final int PRIORITY_BOX_X = 10;

    private static final int RIGHT_X = 90;
    private static final int OUT_BTN_X = RIGHT_X, OUT_BTN_Y = 20;
    private static final int OUT_COLOR_X = RIGHT_X + 20, OUT_COLOR_Y = 18;
    private static final int STRAT_X = RIGHT_X, STRAT_Y = 65;

    private static final int TYPE_SECTION_X = 150;
    private static final int TYPE_SECTION_Y = 20;
    private static final int COLUMN_SPACING = 22;
    private static final int ROW_SPACING = 22;

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
        super.init();
        this.titleLabelX = this.imageWidth - this.font.width(this.title) - 8;
        this.titleLabelY = 6;
        this.inventoryLabelY = 1000;

        this.priorityBox = new EditBox(this.font,
            leftPos + PRIORITY_BOX_X, topPos + PRIORITY_Y,
            36, 12, Component.translatable("gui.staticlogistics.label.priority"));
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
        updateWidgetVisibility();
    }

    private void updateWidgetVisibility() {
        if (this.priorityBox != null) {
            this.priorityBox.setVisible(menu.isInputEnabled());
        }
    }

    @Override
    public void containerTick() {
        super.containerTick();
        updateWidgetVisibility();
        if (this.priorityBox != null && this.priorityBox.isVisible() && !this.priorityBox.isFocused()) {
            String currentVal = String.valueOf(menu.getPriority());
            if (!this.priorityBox.getValue().equals(currentVal)) {
                this.priorityBox.setValue(currentVal);
            }
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.hoveredType = null;
        super.render(graphics, mouseX, mouseY, partialTick);
        if (this.hoveredType != null) {
            renderTypeTooltip(graphics, this.hoveredType, mouseX, mouseY);
        }
        this.renderTooltip(graphics, mouseX, mouseY);
        renderCustomTooltips(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderCustomContent(GuiGraphics graphics, int mouseX, int mouseY) {
        renderFilterSlots(graphics);
        renderFilterHints(graphics);

        renderToggleButton(graphics, IN_BTN_X, IN_BTN_Y, menu.isInputEnabled());
        renderColorButton(graphics, IN_COLOR_X, IN_COLOR_Y, menu.getInputChannel());
        if (menu.isInputEnabled()) {
            Component priorityLabel = Component.translatable("gui.staticlogistics.label.priority");
            graphics.drawString(this.font, priorityLabel,
                leftPos + PRIORITY_BOX_X, topPos + PRIORITY_TEXT_Y,
                0xFFFFFFFF, false);
        }

        renderToggleButton(graphics, OUT_BTN_X, OUT_BTN_Y, menu.isOutputEnabled());
        renderColorButton(graphics, OUT_COLOR_X, OUT_COLOR_Y, menu.getOutputChannel());
        if (menu.isOutputEnabled()) {
            renderStrategyButton(graphics, mouseX, mouseY);
            renderTransferTypeSection(graphics, mouseX, mouseY);
        }

        if (menu.isInputEnabled() && !menu.getSlot(0).getItem().isEmpty()) {
            renderFilterConfigButtonAt(graphics, mouseX, mouseY, INPUT_FILTER_X, INPUT_FILTER_Y, true);
        }
        if (menu.isOutputEnabled() && !menu.getSlot(1).getItem().isEmpty()) {
            renderFilterConfigButtonAt(graphics, mouseX, mouseY, OUTPUT_FILTER_X, OUTPUT_FILTER_Y, false);
        }
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

    private void renderTransferTypeSection(GuiGraphics g, int mx, int my) {
        List<TransferType> types = new ArrayList<>(TransferRegistries.getAllActive());
        int selectedMask = menu.getSelectedTypesMask();

        for (int i = 0; i < types.size(); i++) {
            TransferType type = types.get(i);
            boolean isSelected = (selectedMask & type.getFlag()) != 0;
            int col = i % 2;
            int row = i / 2;
            int baseX = leftPos + TYPE_SECTION_X + (col * COLUMN_SPACING);
            int baseY = topPos + TYPE_SECTION_Y + (row * ROW_SPACING);

            int bw = isSelected ? SLGuiTextures.Button.Big.SELECTED_WIDTH : SLGuiTextures.Button.Big.DISABLED_WIDTH;
            int bh = isSelected ? SLGuiTextures.Button.Big.SELECTED_HEIGHT : SLGuiTextures.Button.Big.DISABLED_HEIGHT;
            int u = isSelected ? SLGuiTextures.Button.Big.SELECTED_U : SLGuiTextures.Button.Big.DISABLED_U;
            int v = isSelected ? SLGuiTextures.Button.Big.SELECTED_V : SLGuiTextures.Button.Big.DISABLED_V;

            int drawX = isSelected ? baseX - 1 : baseX;
            int drawY = isSelected ? baseY - 1 : baseY;
            g.blit(SLGuiTextures.GUI_ATLAS, drawX, drawY, u, v, bw, bh, SLGuiTextures.GUI_WIDTH, SLGuiTextures.GUI_HEIGHT);

            ItemStack iconStack = getIconForType(type);
            float scale = 0.8f;
            g.pose().pushPose();
            g.pose().translate(baseX + 3.5f, baseY + 1.5f, 0);
            g.pose().scale(scale, scale, 1.0f);
            g.renderFakeItem(iconStack, 0, 0);
            g.pose().popPose();

            if (mx >= drawX && mx < drawX + bw && my >= drawY && my < drawY + bh) {
                this.hoveredType = type;
            }
        }
    }

    private ItemStack getIconForType(TransferType type) {
        String path = type.id().getPath();
        return switch (path) {
            case "item" -> new ItemStack(Items.IRON_INGOT);
            case "fluid" -> new ItemStack(Items.WATER_BUCKET);
            case "energy" -> new ItemStack(Items.REDSTONE);
            case "mek_chemicals" -> ModList.get().isLoaded(ModIds.MEKANISM)
                ? new ItemStack(MekanismBlocks.BASIC_CHEMICAL_TANK.get()) : new ItemStack(Items.BARRIER);
            case "ars_source" -> ModList.get().isLoaded(ModIds.ARS_NOUVEAU)
                ? new ItemStack(ItemsRegistry.SOURCE_GEM) : new ItemStack(Items.BARRIER);
            default -> new ItemStack(Items.PAPER);
        };
    }

    private void renderTypeTooltip(GuiGraphics g, TransferType type, int mx, int my) {
        List<Component> tooltip = new ArrayList<>();
        int safeColor = type.color() | 0xFF000000;
        tooltip.add(Component.translatable(type.translationKey()).withStyle(style -> style.withColor(safeColor)));
        tooltip.add(Component.translatable(type.translationKey() + ".desc").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.empty());
        tooltip.add(Component.translatable("gui.staticlogistics.tooltip.toggle_type").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        g.renderComponentTooltip(this.font, tooltip, mx, my);
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
        if (menu.isInputEnabled() && !menu.getSlot(0).getItem().isEmpty() && isFilterBtnHover(mx, my, INPUT_FILTER_X, INPUT_FILTER_Y)) {
            CompoundTag tag = new CompoundTag();
            tag.putBoolean("open_filter", true);
            tag.putBoolean("is_input", true);
            PacketDistributor.sendToServer(new C2SConfigureFacePayload(menu.getPos(), menu.getFace(), menu.getTransferType().id(), tag));
            playClickSound();
            return true;
        }
        if (menu.isOutputEnabled() && !menu.getSlot(1).getItem().isEmpty() && isFilterBtnHover(mx, my, OUTPUT_FILTER_X, OUTPUT_FILTER_Y)) {
            CompoundTag tag = new CompoundTag();
            tag.putBoolean("open_filter", true);
            tag.putBoolean("is_input", false);
            PacketDistributor.sendToServer(new C2SConfigureFacePayload(menu.getPos(), menu.getFace(), menu.getTransferType().id(), tag));
            playClickSound();
            return true;
        }

        if (isMouseOver(mx, my, IN_BTN_X, IN_BTN_Y, SLGuiTextures.Button.Push.WIDTH, SLGuiTextures.Button.Push.HEIGHT)) {
            sendConfigUpdate("inputEnabled", !menu.isInputEnabled());
            playClickSound();
            return true;
        }
        if (isMouseOver(mx, my, OUT_BTN_X, OUT_BTN_Y, SLGuiTextures.Button.Push.WIDTH, SLGuiTextures.Button.Push.HEIGHT)) {
            sendConfigUpdate("outputEnabled", !menu.isOutputEnabled());
            playClickSound();
            return true;
        }
        if (isMouseOver(mx, my, IN_COLOR_X, IN_COLOR_Y, 14, 14)) {
            int nextChannel = (menu.getInputChannel() + (button == 1 ? -1 : 1) + 16) % 16;
            sendConfigUpdate("inputChannel", nextChannel);
            playClickSound();
            return true;
        }
        if (isMouseOver(mx, my, OUT_COLOR_X, OUT_COLOR_Y, 14, 14)) {
            int nextChannel = (menu.getOutputChannel() + (button == 1 ? -1 : 1) + 16) % 16;
            sendConfigUpdate("outputChannel", nextChannel);
            playClickSound();
            return true;
        }
        if (menu.isOutputEnabled() && isMouseOver(mx, my, STRAT_X, STRAT_Y, getStrategyButtonWidth(), SLGuiTextures.Button.Middle.HEIGHT)) {
            int nextOrd = (menu.getStrategy().ordinal() + 1) % DistributionStrategy.values().length;
            sendConfigUpdate("strategy", DistributionStrategy.values()[nextOrd].getSerializedName());
            playClickSound();
            return true;
        }
        List<TransferType> types = new ArrayList<>(TransferRegistries.getAllActive());
        int selectedMask = menu.getSelectedTypesMask();
        for (int i = 0; i < types.size(); i++) {
            int col = i % 2;
            int row = i / 2;
            int baseX = leftPos + TYPE_SECTION_X + (col * COLUMN_SPACING);
            int baseY = topPos + TYPE_SECTION_Y + (row * ROW_SPACING);

            TransferType type = types.get(i);
            boolean isSelected = (selectedMask & type.getFlag()) != 0;
            int bw = isSelected ? SLGuiTextures.Button.Big.SELECTED_WIDTH : SLGuiTextures.Button.Big.DISABLED_WIDTH;
            int bh = isSelected ? SLGuiTextures.Button.Big.SELECTED_HEIGHT : SLGuiTextures.Button.Big.DISABLED_HEIGHT;
            int drawX = isSelected ? baseX - 1 : baseX;
            int drawY = isSelected ? baseY - 1 : baseY;

            if (mx >= drawX && mx < drawX + bw && my >= drawY && my < drawY + bh) {
                TransferType clicked = types.get(i);
                menu.toggleTypeSelection(clicked);
                syncTypeSelection();
                playClickSound();
                return true;
            }
        }

        return super.mouseClicked(mx, my, button);
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
        if (menu.isOutputEnabled() && isMouseOver(mx, my, STRAT_X, STRAT_Y, getStrategyButtonWidth(), SLGuiTextures.Button.Middle.HEIGHT)) {
            g.renderComponentTooltip(this.font, List.of(
                Component.translatable("gui.staticlogistics.strategy"),
                menu.getStrategy().getDisplayName().copy().withStyle(ChatFormatting.AQUA)), mx, my);
        }

        int inputSlotX = leftPos + INPUT_FILTER_X;
        int inputSlotY = topPos + INPUT_FILTER_Y;
        if (menu.isInputEnabled() && mx >= inputSlotX && mx < inputSlotX + 16 && my >= inputSlotY && my < inputSlotY + 16) {
            ItemStack stack = menu.getSlot(0).getItem();
            if (!stack.isEmpty()) g.renderTooltip(font, stack, mx, my);
        }
        int outputSlotX = leftPos + OUTPUT_FILTER_X;
        int outputSlotY = topPos + OUTPUT_FILTER_Y;
        if (menu.isOutputEnabled() && mx >= outputSlotX && mx < outputSlotX + 16 && my >= outputSlotY && my < outputSlotY + 16) {
            ItemStack stack = menu.getSlot(1).getItem();
            if (!stack.isEmpty()) g.renderTooltip(font, stack, mx, my);
        }

        if (menu.isInputEnabled() && !menu.getSlot(0).getItem().isEmpty() && isFilterBtnHover(mx, my, INPUT_FILTER_X, INPUT_FILTER_Y)) {
            g.renderTooltip(this.font, Component.translatable("gui.staticlogistics.open_filter"), mx, my);
        }
        if (menu.isOutputEnabled() && !menu.getSlot(1).getItem().isEmpty() && isFilterBtnHover(mx, my, OUTPUT_FILTER_X, OUTPUT_FILTER_Y)) {
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
        PacketDistributor.sendToServer(new C2SConfigureFacePayload(menu.getPos(), menu.getFace(), menu.getTransferType().id(), tag));
    }

    private void syncTypeSelection() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("selected_types_mask", menu.getSelectedTypesMask());
        PacketDistributor.sendToServer(new C2SConfigureFacePayload(menu.getPos(), menu.getFace(), menu.getTransferType().id(), tag));
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