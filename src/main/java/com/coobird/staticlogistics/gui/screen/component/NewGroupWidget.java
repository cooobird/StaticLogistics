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

    public static final int ADD_BTN_SIZE = 12;
    public static final int ADD_BTN_X = 75, ADD_BTN_Y = 129;
    public static final int NEW_GROUP_X = 12, NEW_GROUP_Y = 130, NEW_GROUP_W = 77;
    private static final int SX_OFFSET = 202;
    private static final int LIST_OFFSET_X = GroupPanel.LIST_OFFSET_X;

    private final EditBox editBox;
    private boolean isEditing;

    public NewGroupWidget(Font font) {
        this.editBox = new EditBox(font, NEW_GROUP_X, NEW_GROUP_Y, NEW_GROUP_W - 4, 8, Component.empty());
        this.editBox.setBordered(false);
        this.editBox.setVisible(false);
        this.editBox.setMaxLength(32);
        this.editBox.setTextColor(0xFFFFCC);
        this.editBox.setHint(Component.translatable("gui.staticlogistics.add_group"));
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

        int listX = sx + LIST_OFFSET_X;
        int btnRight = sx + ADD_BTN_X + 20;
        int btnW = btnRight - listX;

        int btnH = SLGuiTextures.Button.Big.DISABLED_HEIGHT;
        int btnU = SLGuiTextures.Button.Big.DISABLED_U;
        int btnV = SLGuiTextures.Button.Big.DISABLED_V;
        int btnSrcW = SLGuiTextures.Button.Big.DISABLED_WIDTH;
        int btnSrcH = SLGuiTextures.Button.Big.DISABLED_HEIGHT;
        int btnCorner = 2;
        int btnMidW = btnSrcW - 2 * btnCorner;
        int btnY = ngY - 2;

        g.blit(SLGuiTextures.GUI_ATLAS, listX, btnY, btnU, btnV,
            btnCorner, btnSrcH, SLGuiTextures.GUI_WIDTH, SLGuiTextures.GUI_HEIGHT);
        g.blit(SLGuiTextures.GUI_ATLAS, listX + btnCorner, btnY,
            btnW - 2 * btnCorner, btnH,
            btnU + btnCorner, btnV, btnMidW, btnSrcH,
            SLGuiTextures.GUI_WIDTH, SLGuiTextures.GUI_HEIGHT);
        g.blit(SLGuiTextures.GUI_ATLAS, listX + btnW - btnCorner, btnY,
            btnU + btnSrcW - btnCorner, btnV, btnCorner, btnSrcH,
            SLGuiTextures.GUI_WIDTH, SLGuiTextures.GUI_HEIGHT);

        int ngX = sx + NEW_GROUP_X;
        int ngW = NEW_GROUP_W + 4;
        int ngH = SLGuiTextures.EditBox.HEIGHT;
        int ngU = SLGuiTextures.EditBox.DEFAULT_U;
        int ngV = SLGuiTextures.EditBox.DEFAULT_V;
        int ngSrcW = SLGuiTextures.EditBox.WIDTH;
        int ngSrcH = SLGuiTextures.EditBox.HEIGHT;
        int corner = 4;
        int midW = ngSrcW - 2 * corner;

        g.blit(SLGuiTextures.GUI_ATLAS, ngX, ngY, ngU, ngV,
            corner, ngSrcH, SLGuiTextures.GUI_WIDTH, SLGuiTextures.GUI_HEIGHT);
        g.blit(SLGuiTextures.GUI_ATLAS, ngX + corner, ngY, ngW - 2 * corner, ngH,
            ngU + corner, ngV, midW, ngSrcH,
            SLGuiTextures.GUI_WIDTH, SLGuiTextures.GUI_HEIGHT);
        g.blit(SLGuiTextures.GUI_ATLAS, ngX + ngW - corner, ngY,
            ngU + ngSrcW - corner, ngV, corner, ngSrcH,
            SLGuiTextures.GUI_WIDTH, SLGuiTextures.GUI_HEIGHT);

        this.editBox.setX(ngX + 2);
        this.editBox.setY(ngY + 1);
        this.editBox.setWidth(ngW - 4);
        if (this.isEditing) {
            this.editBox.render(g, mx, my, partialTick);
        } else {
            String hint = Component.translatable("gui.staticlogistics.add_group").getString();
            g.drawString(font, hint, ngX + 3, ngY + 2, 0x666666, false);
        }

        int addX = sx + ADD_BTN_X + 21;
        int addY = topPos + ADD_BTN_Y;

        g.blit(SLGuiTextures.GUI_ATLAS, addX, addY, ADD_BTN_SIZE, ADD_BTN_SIZE,
            SLGuiTextures.Button.Big.DISABLED_U,
            SLGuiTextures.Button.Big.DISABLED_V,
            SLGuiTextures.Button.Big.DISABLED_WIDTH,
            SLGuiTextures.Button.Big.DISABLED_HEIGHT,
            SLGuiTextures.GUI_WIDTH, SLGuiTextures.GUI_HEIGHT);
        g.blit(SLGuiTextures.GUI_ATLAS, addX, addY,
            SLGuiTextures.Operator.ADD_U, SLGuiTextures.Operator.ADD_V,
            ADD_BTN_SIZE, ADD_BTN_SIZE,
            SLGuiTextures.GUI_WIDTH, SLGuiTextures.GUI_HEIGHT);
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
        int addX = sx + ADD_BTN_X + 21;
        int addY = topPos + ADD_BTN_Y;
        return mx >= addX && mx < addX + ADD_BTN_SIZE
            && my >= addY && my < addY + ADD_BTN_SIZE;
    }

    public boolean isTextBoxHit(double mx, double my, int leftPos, int topPos) {
        int sx = sx(leftPos);
        int ngY = topPos + NEW_GROUP_Y;
        int btnY = ngY - 2;
        int totalH = SLGuiTextures.EditBox.HEIGHT
            + SLGuiTextures.Button.Big.DISABLED_HEIGHT;
        int listX = sx + LIST_OFFSET_X;
        int btnRight = sx + ADD_BTN_X + 20;
        return mx >= listX && mx < btnRight + ADD_BTN_SIZE
            && my >= btnY && my < btnY + totalH;
    }

    public boolean isEditBoxMouseOver(double mx, double my) {
        return this.editBox.isMouseOver(mx, my);
    }
}
