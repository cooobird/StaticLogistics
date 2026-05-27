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
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 手持过滤器界面 — 从手持物品打开。
 */
public class HandFilterScreen extends BaseFilterScreen<HandFilterMenu> {

    public HandFilterScreen(HandFilterMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
    }

    @Override
    protected int getBlacklistButtonXOffset() {
        return menu.getActiveUpgradeType() == UpgradeType.NBT_FILTER ? 50 : 0;
    }

    @Override
    protected void renderCustomContent(GuiGraphics g, int mx, int my) {
        renderTitle(g);

        UpgradeType type = menu.getActiveUpgradeType();
        if (type == UpgradeType.TAG_FILTER) {
            renderFilterGrid(g);
            renderTagBars(g, mx, my);
            renderBlacklistButton(g, mx, my);
        } else if (type == UpgradeType.BASIC_FILTER
            || type == UpgradeType.NBT_FILTER) {
            renderFilterGrid(g);
            renderBlacklistButton(g, mx, my);
        }
        if (type == UpgradeType.NBT_FILTER) {
            renderNbtModeControls(g, mx, my);
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
            } else if (!current.isEmpty()
                && (current.getItem() != original.getItem()
                || current.getCount() != original.getCount())) {
                this.onClose();
            }
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        return super.mouseClicked(mx, my, button);
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
        String text = Component.translatable("gui.staticlogistics.hand_filter").getString();
        int tw = 110, tx = leftPos + (SLGuiTextures.Background.WIDTH - tw) / 2,
            ty = topPos - 8;
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
        g.drawString(this.font, text,
            tx + (tw - this.font.width(text)) / 2, ty + 4, 0x98FB98, false);
    }
}
