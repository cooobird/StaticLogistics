package com.coobird.staticlogistics.client.gui.screen;

import com.coobird.staticlogistics.client.gui.component.ConfiguratorStyle;
import com.coobird.staticlogistics.client.gui.component.SoundUtil;
import com.coobird.staticlogistics.client.render.SLGuiTextures;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;

/// 独立配置界面的公共底板。
/// 资源类型选择已经并入连接配置器与节点输出配置区，不再由这里额外拼接侧栏。
public abstract class AbstractConfiguratorScreen<T extends AbstractContainerMenu>
    extends AbstractContainerScreen<T> {

    protected AbstractConfiguratorScreen(T menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = SLGuiTextures.Background.WIDTH;
        this.imageHeight = SLGuiTextures.Background.HEIGHT + SLGuiTextures.Inventory.HEIGHT;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick,
                            int mouseX, int mouseY) {

        ConfiguratorStyle.window(graphics, leftPos, topPos, imageWidth, imageHeight);
        int inventoryX = leftPos + (SLGuiTextures.Background.WIDTH - SLGuiTextures.Inventory.WIDTH) / 2;
        int inventoryY = topPos + SLGuiTextures.Background.HEIGHT;
        ConfiguratorStyle.inventory(graphics, inventoryX, inventoryY);
        renderCustomContent(graphics, mouseX, mouseY);
    }

    protected abstract void renderCustomContent(
        GuiGraphics graphics, int mouseX, int mouseY);

    protected void playClickSound() {
        SoundUtil.playClickSound();
    }

    /// 仅跳过原版半透明背景，容器自身的 atlas 背景仍必须绘制。
    @Override
    public void renderBackground(GuiGraphics graphics) {
        renderBg(graphics, 0, 0, 0);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, playerInventoryTitle,
            (SLGuiTextures.Background.WIDTH - SLGuiTextures.Inventory.WIDTH) / 2,
            SLGuiTextures.Background.HEIGHT - 12, ConfiguratorStyle.TEXT, false);
    }
}
