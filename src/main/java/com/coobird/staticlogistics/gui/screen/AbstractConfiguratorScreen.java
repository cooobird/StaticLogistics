package com.coobird.staticlogistics.gui.screen;

import com.coobird.staticlogistics.api.type.TransferType;
import com.coobird.staticlogistics.gui.screen.texture.SLGuiTextures;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;

import java.util.List;

public abstract class AbstractConfiguratorScreen<T extends AbstractContainerMenu> extends AbstractContainerScreen<T> {
    protected static final ResourceLocation GUI_TEXTURE = SLGuiTextures.GUI_ATLAS;

    private static final int SIDE_PANEL_X = SLGuiTextures.Background.WIDTH + 2;
    private static final int BAR_W = 52, BAR_X = 11, BAR_Y = 13;
    private static final int LIST_OFFSET_X = 6;
    private static final int LIST_OFFSET_Y = 32;
    private static final int LIST_HEIGHT = SLGuiTextures.List.HEIGHT;
    protected int itemHeight = 18;
    private static final int SELECTION_WIDTH = 78;
    private static final int SCROLLBAR_X = 88;
    private static final int SCROLLBAR_Y = 25;

    protected EditBox typeSearchBox;
    protected float typeScrollOffset = 0;
    protected boolean isScrollingTypes = false;
    protected String typeSearchTerm = "";
    protected TransferType hoveredType = null;

    public AbstractConfiguratorScreen(T menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = SLGuiTextures.Background.WIDTH;
        this.imageHeight = SLGuiTextures.Background.HEIGHT + SLGuiTextures.Inventory.HEIGHT;
        this.itemHeight = getItemHeight();
    }

    @Override
    protected void init() {
        super.init();
        int sx = leftPos + SIDE_PANEL_X;
        this.typeSearchBox = new EditBox(this.font, sx + BAR_X, topPos + BAR_Y + 1, BAR_W - 2, 8, Component.empty());
        this.typeSearchBox.setBordered(false);
        this.typeSearchBox.setMaxLength(20);
        this.typeSearchBox.setTextColor(0xFFFFFF);
        this.typeSearchBox.setHint(Component.translatable(getSearchHintKey()).withStyle(ChatFormatting.DARK_GRAY));
        this.typeSearchBox.setResponder(s -> {
            this.typeSearchTerm = s.trim().toLowerCase();
            this.typeScrollOffset = 0;
        });
        this.addRenderableWidget(this.typeSearchBox);
        updateWidgetVisibility();
    }

    protected int getItemHeight() {
        return itemHeight;
    }

    protected void updateWidgetVisibility() {
        this.typeSearchBox.setVisible(shouldShowTypePanel());
    }

    protected boolean shouldShowTypePanel() {
        return false;
    }

    protected abstract String getSearchHintKey();

    protected abstract List<TransferType> getTypeList();

    protected abstract int getSelectedTypesMask();

    protected abstract void renderTypeListItem(GuiGraphics g, TransferType type, int x, int y, boolean isSelected);

    protected abstract void onTypeClicked(TransferType type);

    protected void renderTransferTypePanel(GuiGraphics g, int mx, int my) {
        List<TransferType> allTypes = getTypeList();
        if (allTypes.isEmpty()) return;

        List<TransferType> types = allTypes.stream()
            .filter(t -> this.typeSearchTerm.isEmpty() ||
                Component.translatable(t.translationKey()).getString().toLowerCase().contains(this.typeSearchTerm))
            .toList();

        int selectedMask = getSelectedTypesMask();
        int maxScroll = Math.max(0, types.size() * this.itemHeight - LIST_HEIGHT);
        int sx = leftPos + SIDE_PANEL_X;

        g.blit(GUI_TEXTURE, sx, topPos, 0, 144,
            SLGuiTextures.Background.BY_GROUP_WIDTH,
            SLGuiTextures.Background.BY_GROUP_HEIGHT,
            SLGuiTextures.GUI_WIDTH, SLGuiTextures.GUI_HEIGHT);

        this.typeSearchBox.render(g, mx, my, 0);

        int listX = sx + LIST_OFFSET_X;
        int listY = topPos + LIST_OFFSET_Y;
        g.enableScissor(listX - 2, listY, listX + SELECTION_WIDTH + 2, listY + LIST_HEIGHT);

        for (int i = 0; i < types.size(); i++) {
            TransferType type = types.get(i);
            boolean isSelected = (selectedMask & type.getFlag()) != 0;
            int itemY = listY + (i * this.itemHeight) - (int) typeScrollOffset;

            if (itemY + this.itemHeight < listY || itemY > listY + LIST_HEIGHT) continue;

            boolean isHovered = mx >= listX && mx <= listX + SELECTION_WIDTH &&
                my >= itemY && my < itemY + this.itemHeight;

            if (isSelected) {
                g.fill(listX, itemY, listX + SELECTION_WIDTH, itemY + this.itemHeight, 0x4498FB98);
            } else if (isHovered) {
                g.fill(listX, itemY, listX + SELECTION_WIDTH, itemY + this.itemHeight, 0x22FFFFFF);
            }

            renderTypeListItem(g, type, listX, itemY, isSelected);

            if (isHovered) {
                this.hoveredType = type;
            }
        }

        g.disableScissor();

        renderTypeScrollBar(g, sx + SCROLLBAR_X, topPos + SCROLLBAR_Y, mx, my, maxScroll);
    }

    private void renderTypeScrollBar(GuiGraphics g, int x, int y, int mx, int my, int maxScroll) {
        if (maxScroll <= 0) return;

        boolean showActive = mx >= x && mx <= x + SLGuiTextures.Scrollbar.ENABLED_WIDTH &&
            my >= y && my <= y + SLGuiTextures.Scrollbar.TRACK_HEIGHT || this.isScrollingTypes;
        int knobY = (int) (typeScrollOffset / maxScroll * (SLGuiTextures.Scrollbar.TRACK_HEIGHT - SLGuiTextures.Scrollbar.ENABLED_HEIGHT));
        g.blit(SLGuiTextures.GUI_ATLAS, x, y + knobY,
            showActive ? SLGuiTextures.Scrollbar.ENABLED_U : SLGuiTextures.Scrollbar.DISABLED_U,
            SLGuiTextures.Scrollbar.ENABLED_V,
            SLGuiTextures.Scrollbar.ENABLED_WIDTH, SLGuiTextures.Scrollbar.ENABLED_HEIGHT,
            SLGuiTextures.GUI_WIDTH, SLGuiTextures.GUI_HEIGHT);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (shouldShowTypePanel()) {
            int sx = leftPos + SIDE_PANEL_X;
            int maxScroll = Math.max(0, getTypeList().size() * this.itemHeight - LIST_HEIGHT);
            int scrollX = sx + SCROLLBAR_X;
            int scrollY = topPos + SCROLLBAR_Y;
            if (mx >= scrollX && mx <= scrollX + SLGuiTextures.Scrollbar.ENABLED_WIDTH &&
                my >= scrollY && my <= scrollY + SLGuiTextures.Scrollbar.TRACK_HEIGHT) {
                if (maxScroll > 0) {
                    this.isScrollingTypes = true;
                    updateTypeScrollFromMouse(my, maxScroll);
                }
                return true;
            }

            int listX = sx + LIST_OFFSET_X;
            int listY = topPos + LIST_OFFSET_Y;
            if (mx >= listX && mx <= listX + SELECTION_WIDTH &&
                my >= listY && my <= listY + LIST_HEIGHT) {
                List<TransferType> types = getTypeList().stream()
                    .filter(t -> this.typeSearchTerm.isEmpty() ||
                        Component.translatable(t.translationKey()).getString().toLowerCase().contains(this.typeSearchTerm))
                    .toList();
                for (int i = 0; i < types.size(); i++) {
                    int itemY = listY + (i * this.itemHeight) - (int) typeScrollOffset;
                    if (my >= itemY && my < itemY + this.itemHeight) {
                        onTypeClicked(types.get(i));
                        return true;
                    }
                }
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        if (shouldShowTypePanel()) {
            int maxScroll = Math.max(0, getTypeList().size() * this.itemHeight - LIST_HEIGHT);
            int sx = leftPos + SIDE_PANEL_X;
            int listX = sx + LIST_OFFSET_X;
            int listY = topPos + LIST_OFFSET_Y;
            if (maxScroll > 0 && mx >= listX && mx <= listX + SELECTION_WIDTH + SCROLLBAR_X &&
                my >= listY && my <= listY + LIST_HEIGHT) {
                this.typeScrollOffset = Mth.clamp(this.typeScrollOffset - (float) dy * this.itemHeight, 0, maxScroll);
                return true;
            }
        }
        return super.mouseScrolled(mx, my, dx, dy);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (this.isScrollingTypes) {
            int maxScroll = Math.max(0, getTypeList().size() * this.itemHeight - LIST_HEIGHT);
            updateTypeScrollFromMouse(my, maxScroll);
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        this.isScrollingTypes = false;
        return super.mouseReleased(mx, my, button);
    }

    private void updateTypeScrollFromMouse(double mouseY, int maxScroll) {
        if (maxScroll > 0) {
            float relativePos = (float) ((mouseY - (topPos + SCROLLBAR_Y)) / SLGuiTextures.Scrollbar.TRACK_HEIGHT);
            this.typeScrollOffset = Mth.clamp(relativePos * maxScroll, 0, maxScroll);
        }
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(GUI_TEXTURE, leftPos, topPos, SLGuiTextures.Background.U, SLGuiTextures.Background.V,
            SLGuiTextures.Background.WIDTH, SLGuiTextures.Background.HEIGHT,
            SLGuiTextures.GUI_WIDTH, SLGuiTextures.GUI_HEIGHT);
        int invX = leftPos + (SLGuiTextures.Background.WIDTH - SLGuiTextures.Inventory.WIDTH) / 2;
        graphics.blit(GUI_TEXTURE, invX, topPos + SLGuiTextures.Background.HEIGHT,
            SLGuiTextures.Inventory.U, SLGuiTextures.Inventory.V,
            SLGuiTextures.Inventory.WIDTH, SLGuiTextures.Inventory.HEIGHT,
            SLGuiTextures.GUI_WIDTH, SLGuiTextures.GUI_HEIGHT);

        renderCustomContent(graphics, mouseX, mouseY);

        if (shouldShowTypePanel()) {
            renderTransferTypePanel(graphics, mouseX, mouseY);
        }
    }

    protected abstract void renderCustomContent(GuiGraphics graphics, int mouseX, int mouseY);

    protected void drawStat(GuiGraphics g, Component label, String value, int x, int y, int labelColor, int valueColor) {
        g.drawString(this.font, label, x, y, labelColor, false);
        int labelWidth = this.font.width(label);
        g.drawString(this.font, value, x + labelWidth + 4, y, valueColor, false);
    }

    protected boolean isMouseOver(double mx, double my, int x, int y, int w, int h) {
        return mx >= leftPos + x && mx < leftPos + x + w && my >= topPos + y && my < topPos + y + h;
    }

    protected void playClickSound() {
        com.coobird.staticlogistics.util.SoundUtil.playClickSound();
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        int startX = 1;
        int startY = SLGuiTextures.Background.HEIGHT - 4;
        graphics.drawString(this.font, this.playerInventoryTitle, startX, startY, 0xFFFFFFFF, false);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.hoveredType = null;
        super.render(graphics, mouseX, mouseY, partialTick);
        if (this.hoveredType != null) {
            renderHoveredTypeTooltip(graphics, mouseX, mouseY);
        }
    }

    protected void renderHoveredTypeTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
    }
}