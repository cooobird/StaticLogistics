package com.coobird.staticlogistics.client.gui.screen;

import com.coobird.staticlogistics.client.gui.component.ConfiguratorStyle;
import com.coobird.staticlogistics.content.menu.FilterConfiguratorMenu;
import com.coobird.staticlogistics.logistics.filter.FilterData;
import com.coobird.staticlogistics.network.c2s.C2SReturnToLinkConfiguratorPayload;
import com.coobird.staticlogistics.network.c2s.C2SUpdateFilterOnItemPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.mesdag.portlib.network.PortPacketDistributor;

/// 过滤器配置界面（从容器/面打开）。
public class FilterConfiguratorScreen extends BaseFilterScreen<FilterConfiguratorMenu> {
    /// 父界面中的物品图标和 Tooltip 会自行增加 Z 值，因此仅按调用顺序先画父界面
    /// 仍可能穿透过滤器背景。将整张父界面统一下沉后，过滤器才能成为真正的顶层容器。
    private static final float NESTED_PARENT_Z = -1000.0F;

    private static LinkConfiguratorScreen pendingNestedParent;
    private final LinkConfiguratorScreen nestedParent;

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
    protected void renderCustomContent(GuiGraphics g, int mx, int my) {
        renderFilterGrid(g);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (nestedParent != null) {
            graphics.pose().pushPose();
            graphics.pose().translate(0.0F, 0.0F, NESTED_PARENT_Z);
            nestedParent.renderWindow(graphics, -1, -1, partialTick);
            graphics.pose().popPose();
            ConfiguratorStyle.shade(graphics, 0, 0, width, height, 0.4F);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    protected void sendFilterUpdate() {
        FilterData filter = menu.getFilterData();
        PortPacketDistributor.sendToServer(new C2SUpdateFilterOnItemPayload(
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
        PortPacketDistributor.sendToServer(new C2SReturnToLinkConfiguratorPayload(
            menu.getPos(), menu.getFace()));
    }

}
