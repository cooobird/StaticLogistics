package com.coobird.staticlogistics.gui.screen;

import com.coobird.staticlogistics.api.type.UpgradeType;
import com.coobird.staticlogistics.filter.data.FilterData;
import com.coobird.staticlogistics.gui.menu.HandFilterMenu;
import com.coobird.staticlogistics.gui.screen.texture.SLGuiTextures;
import com.coobird.staticlogistics.network.c2s.C2SUpdateFilterOnHandPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.network.PacketDistributor;

public class HandFilterScreen extends BaseFilterScreen<HandFilterMenu> {

    public HandFilterScreen(HandFilterMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
    }

    @Override
    protected int getBlacklistButtonXOffset() {
        return menu.getActiveUpgradeType() == UpgradeType.NBT_FILTER ? 50 : 0;
    }

    @Override
    protected void renderCustomContent(GuiGraphics graphics, int mouseX, int mouseY) {
        renderTitle(graphics);
        UpgradeType type = menu.getActiveUpgradeType();

        if (type == UpgradeType.TAG_FILTER) {
            renderFilterGrid(graphics);
            renderTagBars(graphics, mouseX, mouseY);
            renderBlacklistButton(graphics, mouseX, mouseY);
        } else if (type == UpgradeType.BASIC_FILTER || type == UpgradeType.NBT_FILTER) {
            renderFilterGrid(graphics);
            renderBlacklistButton(graphics, mouseX, mouseY);
        }

        if (type == UpgradeType.NBT_FILTER) {
            renderNbtModeControls(graphics, mouseX, mouseY);
        }
    }

    @Override
    public void containerTick() {
        super.containerTick();
        if (minecraft != null && minecraft.player != null) {
            ItemStack current = minecraft.player.getMainHandItem();
            ItemStack original = menu.getFilterStack();
            if (current.isEmpty() && !original.isEmpty()) {
                this.onClose();
            } else if (!current.isEmpty() && (current.getItem() != original.getItem() || current.getCount() != original.getCount())) {
                this.onClose();
            }
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (menu.getActiveUpgradeType() == UpgradeType.TAG_FILTER) {
            if (handleTagBarClick(mx, my, button)) return true;
        }

        if (handleNbtModeAndIgnoreClick(mx, my)) return true;

        return super.mouseClicked(mx, my, button);
    }

    @Override
    protected ItemStack getFilterItem(int index) {
        return menu.getFilterItem(index);
    }

    @Override
    protected void setFilterItem(int index, ItemStack stack) {
        menu.setFilterItem(index, stack);
    }

    @Override
    protected void removeFilterItem(int index) {
        menu.removeFilterItem(index);
    }

    @Override
    protected Fluid getFluidItem(int index) {
        return menu.getFluidSlot(index);
    }

    @Override
    protected void setFluidSlot(int index, Fluid fluid) {
        menu.setFluidSlot(index, fluid);
    }

    @Override
    protected void removeFluidSlot(int index) {
        menu.removeFluidSlot(index);
    }

    @Override
    protected boolean isBlacklistMode() {
        return menu.isBlacklistMode();
    }

    @Override
    protected void setBlacklistMode(boolean blacklist) {
        menu.setBlacklistMode(blacklist);
    }

    @Override
    protected void sendFilterUpdate() {
    }

    @Override
    public void onClose() {
        FilterData filter = menu.getFilterData();
        PacketDistributor.sendToServer(new C2SUpdateFilterOnHandPayload(filter));
        super.onClose();
    }

    private void renderTitle(GuiGraphics g) {
        String titleKey = "gui.staticlogistics.hand_filter";
        String titleText = Component.translatable(titleKey).getString();
        int tw = 110, tx = leftPos + (SLGuiTextures.Background.WIDTH - tw) / 2, ty = topPos - 8;
        g.blit(SLGuiTextures.GUI_ATLAS, tx + tw - 2, ty,
            SLGuiTextures.Title.U + SLGuiTextures.Button.Small.DISABLED_WIDTH - 2,
            SLGuiTextures.Title.V, 2, SLGuiTextures.Button.Small.DISABLED_HEIGHT,
            SLGuiTextures.GUI_WIDTH, SLGuiTextures.GUI_HEIGHT);
        g.blit(SLGuiTextures.GUI_ATLAS, tx, ty,
            SLGuiTextures.Title.U, SLGuiTextures.Title.V,
            2, SLGuiTextures.Button.Small.DISABLED_HEIGHT,
            SLGuiTextures.GUI_WIDTH, SLGuiTextures.GUI_HEIGHT);
        g.blit(SLGuiTextures.GUI_ATLAS, tx + 2, ty,
            tw - 4, SLGuiTextures.Button.Small.DISABLED_HEIGHT,
            SLGuiTextures.Title.U + 2, SLGuiTextures.Title.V,
            1, SLGuiTextures.Button.Small.DISABLED_HEIGHT,
            SLGuiTextures.GUI_WIDTH, SLGuiTextures.GUI_HEIGHT);
        int textWidth = this.font.width(titleText);
        g.drawString(this.font, titleText, tx + (tw - textWidth) / 2, ty + 4, 0x98FB98, false);
    }
}