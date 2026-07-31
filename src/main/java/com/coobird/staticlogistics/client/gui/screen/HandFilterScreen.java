package com.coobird.staticlogistics.client.gui.screen;

import com.coobird.staticlogistics.client.gui.component.TitleBar;
import com.coobird.staticlogistics.client.render.SLGuiTextures;
import com.coobird.staticlogistics.content.menu.HandFilterMenu;
import com.coobird.staticlogistics.network.SLNetwork;
import com.coobird.staticlogistics.network.c2s.C2SUpdateFilterOnHandPayload;
import com.coobird.staticlogistics.transfer.UpgradeType;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

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
        if (minecraft != null && minecraft.player != null
            && (!menu.stillValid(minecraft.player) || !menu.isBoundSlotSelected())) {
            this.onClose();
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        return super.mouseClicked(mx, my, button);
    }

    @Override
    protected void sendFilterUpdate() {
        SLNetwork.HANDLER.sendToServer(new C2SUpdateFilterOnHandPayload(menu.getFilterData()));
    }

    @Override
    public void onClose() {
        sendFilterUpdate();
        super.onClose();
    }

    private void renderTitle(GuiGraphics g) {
        String text = Component.translatable("gui.staticlogistics.hand_filter").getString();
        TitleBar.render(g, font, leftPos, topPos,
            SLGuiTextures.Background.WIDTH, text);
    }
}
