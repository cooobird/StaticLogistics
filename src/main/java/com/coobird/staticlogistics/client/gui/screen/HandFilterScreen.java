package com.coobird.staticlogistics.client.gui.screen;

import com.coobird.staticlogistics.content.menu.HandFilterMenu;
import com.coobird.staticlogistics.network.c2s.C2SUpdateFilterOnHandPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

/// 手持过滤器界面 — 从手持物品打开。
public class HandFilterScreen extends BaseFilterScreen<HandFilterMenu> {

    public HandFilterScreen(HandFilterMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
    }


    @Override
    protected void renderCustomContent(GuiGraphics g, int mx, int my) {
        renderFilterGrid(g);
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
    protected void sendFilterUpdate() {
        PacketDistributor.sendToServer(new C2SUpdateFilterOnHandPayload(
            menu.getActiveUpgradeType(), menu.getFilterData()));
    }

    @Override
    public void onClose() {
        sendFilterUpdate();
        super.onClose();
    }

}
