package com.coobird.staticlogistics.client.gui.screen;

import com.coobird.staticlogistics.client.gui.component.TitleBar;
import com.coobird.staticlogistics.client.render.SLGuiTextures;
import com.coobird.staticlogistics.content.menu.FilterConfiguratorMenu;
import com.coobird.staticlogistics.logistics.filter.FilterData;
import com.coobird.staticlogistics.network.SLNetwork;
import com.coobird.staticlogistics.network.c2s.C2SReturnToLinkConfiguratorPayload;
import com.coobird.staticlogistics.network.c2s.C2SUpdateFilterOnItemPayload;
import com.coobird.staticlogistics.transfer.UpgradeType;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;

/**
 * 过滤器配置界面（从容器/面打开）。
 */
public class FilterConfiguratorScreen extends BaseFilterScreen<FilterConfiguratorMenu> {
    /**
     * 父界面中的物品图标和 Tooltip 会自行增加 Z 值，因此仅按调用顺序先画父界面
     * 仍可能穿透过滤器背景。将整张父界面统一下沉后，过滤器才能成为真正的顶层容器。
     */
    private static final float NESTED_PARENT_Z = -1000.0F;

    private static LinkConfiguratorScreen pendingNestedParent;
    private final LinkConfiguratorScreen nestedParent;
    private boolean dragging;
    private double dragOffsetX;
    private double dragOffsetY;

    public FilterConfiguratorScreen(FilterConfiguratorMenu menu, Inventory inv,
                                    Component title) {
        super(menu, inv, title);
        this.nestedParent = takeNestedParent();
    }

    public static void prepareNestedOpen(LinkConfiguratorScreen parent) {
        pendingNestedParent = parent;
    }

    private static LinkConfiguratorScreen takeNestedParent() {
        LinkConfiguratorScreen parent = pendingNestedParent;
        pendingNestedParent = null;
        return parent;
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
        } else if (type == UpgradeType.BASIC_FILTER || type == UpgradeType.NBT_FILTER) {
            renderFilterGrid(g);
            renderBlacklistButton(g, mx, my);
        }

        if (type == UpgradeType.NBT_FILTER) {
            renderNbtModeControls(g, mx, my);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (nestedParent != null) {
            graphics.pose().pushPose();
            graphics.pose().translate(0.0F, 0.0F, NESTED_PARENT_Z);
            nestedParent.render(graphics, mouseX, mouseY, partialTick);
            graphics.pose().popPose();
            graphics.fill(0, 0, width, height, 0x66000000);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (nestedParent != null && button == 0
            && TitleBar.contains(mx, my, leftPos, topPos,
            SLGuiTextures.Background.WIDTH)) {
            dragging = true;
            dragOffsetX = mx - leftPos;
            dragOffsetY = my - topPos;
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button,
                                double dragX, double dragY) {
        if (dragging && button == 0) {
            leftPos = Mth.clamp(
                (int) Math.round(mouseX - dragOffsetX),
                0,
                Math.max(0, width - imageWidth));
            topPos = Mth.clamp(
                (int) Math.round(mouseY - dragOffsetY),
                -TitleBar.Y_OFFSET,
                Math.max(-TitleBar.Y_OFFSET,
                    height - imageHeight));
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        dragging = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    protected void sendFilterUpdate() {
        FilterData filter = menu.getFilterData();
        SLNetwork.HANDLER.sendToServer(new C2SUpdateFilterOnItemPayload(
            menu.getPos(), menu.getFace(),
            menu.getTransferType().typeId(), menu.isInput(),
            menu.getActiveUpgradeType(), filter));
    }

    @Override
    public void onClose() {
        sendFilterUpdate();
        if (nestedParent == null) {
            super.onClose();
            return;
        }
        SLNetwork.HANDLER.sendToServer(new C2SReturnToLinkConfiguratorPayload(
            menu.getPos(), menu.getFace()));
    }

    private void renderTitle(GuiGraphics g) {
        String key = menu.isInput()
            ? "gui.staticlogistics.input_filter"
            : "gui.staticlogistics.output_filter";
        String text = Component.translatable(key).getString();
        TitleBar.render(g, font, leftPos, topPos,
            SLGuiTextures.Background.WIDTH, text);
    }
}
