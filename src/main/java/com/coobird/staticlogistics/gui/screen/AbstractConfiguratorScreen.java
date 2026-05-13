package com.coobird.staticlogistics.gui.screen;

import com.coobird.staticlogistics.gui.screen.texture.SLGuiTextures;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;

public abstract class AbstractConfiguratorScreen<T extends AbstractContainerMenu> extends AbstractContainerScreen<T> {
    protected static final ResourceLocation GUI_TEXTURE = SLGuiTextures.GUI_ATLAS;

    public AbstractConfiguratorScreen(T menu, Inventory inventory, net.minecraft.network.chat.Component title) {
        super(menu, inventory, title);
        this.imageWidth = SLGuiTextures.Background.WIDTH;
        this.imageHeight = SLGuiTextures.Background.HEIGHT + SLGuiTextures.Inventory.HEIGHT;
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        int startX = 1;
        int startY = SLGuiTextures.Background.HEIGHT - 4;
        graphics.drawString(this.font, this.playerInventoryTitle, startX, startY, 0xFFFFFFFF, false);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(GUI_TEXTURE, leftPos, topPos, SLGuiTextures.Background.U, SLGuiTextures.Background.V, SLGuiTextures.Background.WIDTH, SLGuiTextures.Background.HEIGHT, SLGuiTextures.GUI_WIDTH, SLGuiTextures.GUI_HEIGHT);
        int invX = leftPos + (SLGuiTextures.Background.WIDTH - SLGuiTextures.Inventory.WIDTH) / 2;
        graphics.blit(GUI_TEXTURE, invX, topPos + SLGuiTextures.Background.HEIGHT, SLGuiTextures.Inventory.U, SLGuiTextures.Inventory.V, SLGuiTextures.Inventory.WIDTH, SLGuiTextures.Inventory.HEIGHT, SLGuiTextures.GUI_WIDTH, SLGuiTextures.GUI_HEIGHT);

        renderCustomContent(graphics, mouseX, mouseY);
    }

    protected abstract void renderCustomContent(GuiGraphics graphics, int mouseX, int mouseY);

    protected void drawStat(GuiGraphics g, net.minecraft.network.chat.Component label, String value, int x, int y, int labelColor, int valueColor) {
        g.drawString(this.font, label, x, y, labelColor, false);
        int labelWidth = this.font.width(label);
        g.drawString(this.font, value, x + labelWidth + 4, y, valueColor, false);
    }

    protected boolean isMouseOver(double mx, double my, int x, int y, int w, int h) {
        return mx >= leftPos + x && mx < leftPos + x + w && my >= topPos + y && my < topPos + y + h;
    }

    protected void playClickSound() {
        if (this.minecraft != null) {
            this.minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
        }
    }
}