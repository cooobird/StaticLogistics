package com.coobird.staticlogistics.gui.screen;

import com.coobird.staticlogistics.api.type.UpgradeType;
import com.coobird.staticlogistics.filter.data.FilterData;
import com.coobird.staticlogistics.gui.menu.FilterConfiguratorMenu;
import com.coobird.staticlogistics.gui.screen.texture.SLGuiTextures;
import com.coobird.staticlogistics.network.c2s.C2SConfigureFacePayload;
import com.coobird.staticlogistics.network.c2s.C2SUpdateFilterOnItemPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 过滤器配置界面（从容器/面打开）。
 */
public class FilterConfiguratorScreen extends BaseFilterScreen<FilterConfiguratorMenu> {

    public FilterConfiguratorScreen(FilterConfiguratorMenu menu, Inventory inv,
                                    Component title) {
        super(menu, inv, title);
    }

    @Override
    protected int getBlacklistButtonXOffset() {
        return menu.getActiveUpgradeType() == UpgradeType.NBT_FILTER ? 50 : 0;
    }

    @Override
    protected void renderCustomContent(GuiGraphics g, int mx, int my) {
        renderBackButton(g, mx, my);
        renderTitle(g);

        UpgradeType type = menu.getActiveUpgradeType();

        if (type == UpgradeType.TAG_FILTER) {
            renderFilterGrid(g);
            renderTagBars(g, mx, my);
            renderBlacklistButton(g, mx, my);
        } else if (type == UpgradeType.BASIC_FILTER || type == UpgradeType.NBT_FILTER) {
            renderFilterGrid(g);
            renderBlacklistButton(g, mx, my);
        }

        if (type == UpgradeType.NBT_FILTER) {
            renderNbtModeControls(g, mx, my);
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int bwBack = SLGuiTextures.Button.Middle.SELECTED_WIDTH;
        int bhBack = SLGuiTextures.Button.Middle.SELECTED_HEIGHT;
        int bxBack = leftPos - bwBack + 1;
        int byBack = topPos + 9;
        if (mx >= bxBack && mx < bxBack + bwBack
            && my >= byBack && my < byBack + bhBack) {
            CompoundTag tag = new CompoundTag();
            tag.putBoolean("open_face_config", true);
            PacketDistributor.sendToServer(new C2SConfigureFacePayload(
                menu.getPos(), menu.getFace(), tag));
            playClickSound();
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    protected void sendFilterUpdate() {
        FilterData filter = menu.getFilterData();
        PacketDistributor.sendToServer(new C2SUpdateFilterOnItemPayload(
            menu.getPos(), menu.getFace(),
            menu.getTransferType().id(), menu.isInput(), filter));
    }

    @Override
    public void onClose() {
        sendFilterUpdate();
        super.onClose();
    }

    private void renderBackButton(GuiGraphics g, int mx, int my) {
        int by = topPos + 9;
        boolean hover = mx >= leftPos - SLGuiTextures.Button.Middle.SELECTED_WIDTH + 1
            && mx < leftPos + 1
            && my >= by && my < by + SLGuiTextures.Button.Middle.SELECTED_HEIGHT;
        int bw = hover ? SLGuiTextures.Button.Middle.SELECTED_WIDTH
            : SLGuiTextures.Button.Middle.WIDTH;
        int bh = hover ? SLGuiTextures.Button.Middle.SELECTED_HEIGHT
            : SLGuiTextures.Button.Middle.HEIGHT;
        int bgU = hover ? SLGuiTextures.Button.Middle.SELECTED_U
            : SLGuiTextures.Button.Middle.DISABLED_U;
        int bgV = hover ? SLGuiTextures.Button.Middle.SELECTED_V
            : SLGuiTextures.Button.Middle.DISABLED_V;
        int bx = leftPos - bw + 1;
        g.blit(SLGuiTextures.GUI_ATLAS, bx, by, bgU, bgV, bw, bh,
            SLGuiTextures.GUI_WIDTH, SLGuiTextures.GUI_HEIGHT);
        int color = hover ? 0xFFFF55 : 0xAAAAAA;
        g.drawString(this.font, "<", bx + (bw - this.font.width("<")) / 2,
            by + (bh - 8) / 2, color, false);
    }

    private void renderTitle(GuiGraphics g) {
        String key = menu.isInput()
            ? "gui.staticlogistics.input_filter"
            : "gui.staticlogistics.output_filter";
        String text = Component.translatable(key).getString();
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
