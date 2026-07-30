package com.coobird.staticlogistics.client.gui.screen;

import com.coobird.staticlogistics.client.gui.component.SoundUtil;
import com.coobird.staticlogistics.client.render.SLGuiTextures;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;

/**
 * 独立配置界面的公共底板。
 *
 * <p>资源类型选择已经并入连接配置器与节点输出配置区，不再由这里额外拼接侧栏。
 */
public abstract class AbstractConfiguratorScreen<T extends AbstractContainerMenu>
    extends AbstractContainerScreen<T> {

    protected static final ResourceLocation GUI_TEXTURE = SLGuiTextures.GUI_ATLAS;

    protected AbstractConfiguratorScreen(T menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = SLGuiTextures.Background.WIDTH;
        this.imageHeight = SLGuiTextures.Background.HEIGHT + SLGuiTextures.Inventory.HEIGHT;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick,
                            int mouseX, int mouseY) {
        graphics.blit(GUI_TEXTURE, leftPos, topPos,
            SLGuiTextures.Background.U, SLGuiTextures.Background.V,
            SLGuiTextures.Background.WIDTH, SLGuiTextures.Background.HEIGHT,
            SLGuiTextures.GUI_WIDTH, SLGuiTextures.GUI_HEIGHT);

        int inventoryX = leftPos
            + (SLGuiTextures.Background.WIDTH - SLGuiTextures.Inventory.WIDTH) / 2;
        graphics.blit(GUI_TEXTURE, inventoryX,
            topPos + SLGuiTextures.Background.HEIGHT,
            SLGuiTextures.Inventory.U, SLGuiTextures.Inventory.V,
            SLGuiTextures.Inventory.WIDTH, SLGuiTextures.Inventory.HEIGHT,
            SLGuiTextures.GUI_WIDTH, SLGuiTextures.GUI_HEIGHT);

        renderCustomContent(graphics, mouseX, mouseY);
    }

    protected abstract void renderCustomContent(
        GuiGraphics graphics, int mouseX, int mouseY);

    protected void playClickSound() {
        SoundUtil.playClickSound();
    }

    /**
     * 仅跳过原版半透明背景，容器自身的 atlas 背景仍必须绘制。
     */
    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX,
                                 int mouseY, float partialTick) {
        renderBg(graphics, partialTick, mouseX, mouseY);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, playerInventoryTitle, 1,
            SLGuiTextures.Background.HEIGHT - 4, 0xFFFFFFFF, false);
    }
}
