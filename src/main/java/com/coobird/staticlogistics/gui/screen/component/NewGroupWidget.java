package com.coobird.staticlogistics.gui.screen.component;

import com.coobird.staticlogistics.gui.screen.texture.SLGuiTextures;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

/**
 * 右侧最下方新建分组区域。
 * 玩家输入名称，点击加号或 Enter 提交。
 */
public class NewGroupWidget {
    public static final int NEW_GROUP_X = 12, NEW_GROUP_Y = 130, NEW_GROUP_W = 75;
    private static final int SX_OFFSET = 199;
    private static final int LIST_OFFSET_X = GroupPanel.LIST_OFFSET_X;

    private final EditBox editBox;
    private boolean isEditing;

    public NewGroupWidget(Font font) {
        this.editBox = new EditBox(font, NEW_GROUP_X, NEW_GROUP_Y, NEW_GROUP_W - 4, 8, Component.empty());
        this.editBox.setBordered(false);
        this.editBox.setVisible(false);
        this.editBox.setMaxLength(32);
        this.editBox.setTextColor(0xFFFFCC);
        this.isEditing = false;
    }

    public EditBox getEditBox() {
        return editBox;
    }

    public boolean isEditing() {
        return isEditing;
    }

    private static int sx(int leftPos) {
        return leftPos + SX_OFFSET;
    }

    public void render(GuiGraphics g, Font font, int leftPos, int topPos, int mx, int my, float partialTick) {
        int sx = sx(leftPos);
        int ngY = topPos + NEW_GROUP_Y;

        int texX = sx + LIST_OFFSET_X;
        int texY = ngY - 2;
        g.blit(SLGuiTextures.GUI_ATLAS, texX, texY,
            SLGuiTextures.NewGroupEditBox.U, SLGuiTextures.NewGroupEditBox.V,
            SLGuiTextures.NewGroupEditBox.WIDTH, SLGuiTextures.NewGroupEditBox.HEIGHT,
            SLGuiTextures.GUI_WIDTH, SLGuiTextures.GUI_HEIGHT);

        int ngX = sx + NEW_GROUP_X;
        int ngW = NEW_GROUP_W;
        this.editBox.setX(ngX + 3);
        this.editBox.setY(ngY + 8);
        this.editBox.setWidth(ngW - 4);
        if (this.isEditing) {
            this.editBox.render(g, mx, my, partialTick);
        } else {
            String hint = Component.translatable("gui.staticlogistics.add_group").getString();
            g.drawString(font, hint, ngX + 3, ngY + 8, 0x666666, false);
        }
    }

    public void beginEdit() {
        this.isEditing = true;
        this.editBox.setVisible(true);
        this.editBox.setValue("");
        this.editBox.setFocused(true);
    }

    public String confirmInput() {
        String text = this.editBox.getValue().trim();
        this.isEditing = false;
        this.editBox.setVisible(false);
        this.editBox.setValue("");
        return text;
    }

    public void cancelEdit() {
        this.isEditing = false;
        this.editBox.setVisible(false);
        this.editBox.setValue("");
    }

    public boolean isAddButtonHit(double mx, double my, int leftPos, int topPos) {
        int sx = sx(leftPos);
        int texX = sx + LIST_OFFSET_X;
        int btnRX = texX + SLGuiTextures.NewGroupEditBox.WIDTH - 11;
        int texY = topPos + NEW_GROUP_Y - 2;
        return mx >= btnRX && mx < btnRX + 11
            && my >= texY && my < texY + SLGuiTextures.NewGroupEditBox.HEIGHT;
    }

    public boolean isTextBoxHit(double mx, double my, int leftPos, int topPos) {
        int sx = sx(leftPos);
        int texX = sx + LIST_OFFSET_X;
        int texY = topPos + NEW_GROUP_Y - 2;
        int texW = SLGuiTextures.NewGroupEditBox.WIDTH;
        int texH = SLGuiTextures.NewGroupEditBox.HEIGHT;
        return mx >= texX && mx < texX + texW
            && my >= texY && my < texY + texH;
    }

    public boolean isEditBoxMouseOver(double mx, double my) {
        return this.editBox.isMouseOver(mx, my);
    }
}
