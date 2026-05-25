package com.coobird.staticlogistics.gui.screen;

import com.coobird.staticlogistics.api.type.TransferType;
import com.coobird.staticlogistics.core.registration.TransferRegistries;
import com.coobird.staticlogistics.gui.menu.ContainerConfiguratorMenu;
import com.coobird.staticlogistics.gui.screen.component.ContainerStats;
import com.coobird.staticlogistics.gui.screen.component.FaceControls;
import com.coobird.staticlogistics.gui.screen.texture.SLGuiTextures;
import com.coobird.staticlogistics.network.c2s.C2SOpenFaceConfigPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * 容器配置器界面 — 使用 {@link ContainerStats} 和 {@link FaceControls} 组件化。
 */
public class ContainerConfiguratorScreen
    extends AbstractConfiguratorScreen<ContainerConfiguratorMenu> {

    public ContainerConfiguratorScreen(ContainerConfiguratorMenu menu,
                                       Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected void init() {
        this.imageWidth = SLGuiTextures.Background.WIDTH
            + SLGuiTextures.Background.BY_GROUP_WIDTH + 2;
        this.leftPos = (this.width - this.imageWidth) / 2;
        this.topPos = (this.height - this.imageHeight) / 2;
        super.init();
        this.titleLabelX = this.imageWidth - this.font.width(this.title) - 8;
        this.titleLabelY = 6;
        this.inventoryLabelY = 1000;
    }

    @Override
    protected int getItemHeight() {
        return 12;
    }

    @Override
    protected boolean shouldShowTypePanel() {
        return true;
    }

    @Override
    protected String getSearchHintKey() {
        return "gui.staticlogistics.search_for_values_by_type";
    }

    @Override
    protected List<TransferType> getTypeList() {
        return new ArrayList<>(TransferRegistries.getAllActive());
    }

    @Override
    protected int getSelectedTypesMask() {
        return 0;
    }

    @Override
    protected void renderTypeListItem(GuiGraphics g, TransferType type,
                                      int x, int y, boolean isSelected) {
        String name = Component.translatable(type.translationKey()).getString();
        long stackMult = menu.getStackMultiplier();
        int baseStack = type.getBaseStackSize();
        boolean infinite;
        long finalStack;
        try {
            finalStack = Math.multiplyExact(baseStack, stackMult);
            infinite = finalStack >= Integer.MAX_VALUE;
        } catch (ArithmeticException e) {
            infinite = true;
            finalStack = 0;
        }

        String valText;
        int valColor;
        if (infinite) {
            valText = Component.translatable("gui.staticlogistics.infinite").getString();
            valColor = 0x55FF55;
        } else {
            valText = String.valueOf(finalStack);
            valColor = finalStack > baseStack ? 0x55FF55 : 0xCCCCCC;
        }
        String combined = font.plainSubstrByWidth(name, 60) + ": " + valText;
        g.drawString(this.font, combined, x + 4, y + 2, valColor, false);
    }

    @Override
    protected void renderHoveredTypeTooltip(GuiGraphics g, int mx, int my) {
        if (hoveredType == null) return;
        long stackMult = menu.getStackMultiplier();
        int base = hoveredType.getBaseStackSize();
        boolean infinite;
        long finalStack;
        try {
            finalStack = Math.multiplyExact(base, stackMult);
            infinite = finalStack >= Integer.MAX_VALUE;
        } catch (ArithmeticException e) {
            infinite = true;
            finalStack = 0;
        }
        List<Component> t = new ArrayList<>();
        t.add(Component.translatable(hoveredType.translationKey())
            .withStyle(ChatFormatting.WHITE));
        t.add(Component.translatable("gui.staticlogistics.stat.stack")
            .append(Component.translatable(infinite
                ? "gui.staticlogistics.infinite"
                : String.valueOf(stackMult)).withStyle(ChatFormatting.AQUA)));
        t.add(Component.translatable("gui.staticlogistics.stat.transfer")
            .append(Component.translatable(infinite
                ? "gui.staticlogistics.infinite"
                : String.valueOf(finalStack)).withStyle(ChatFormatting.GREEN)));
        g.renderComponentTooltip(this.font, t, mx, my);
    }

    @Override
    protected void onTypeClicked(TransferType type) {
    }

    @Override
    protected void renderCustomContent(GuiGraphics g, int mx, int my) {
        Component faceLabel = Component.translatable("gui.staticlogistics.face_config");
        boolean faceHover = FaceControls.isTextButtonHovered(mx, my,
            leftPos, topPos, -38, 5, faceLabel, this.font);
        FaceControls.renderTextButton(g, this.font, leftPos, topPos,
            -38, 5, faceLabel, faceHover, GUI_TEXTURE);

        renderUpgradeSlots(g);
        renderSlotHints(g);

        ContainerStats.render(g, this.font, leftPos, topPos,
            menu.getSpeedMultiplier(), menu.getRangeMultiplier(),
            menu.getStackMultiplier(), menu.isDimensionEffective());
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        renderSlotTooltips(g, mx, my);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        Component faceLabel = Component.translatable("gui.staticlogistics.face_config");
        if (FaceControls.isTextButtonHovered(mx, my, leftPos, topPos,
            -38, 5, faceLabel, this.font)) {
            PacketDistributor.sendToServer(new C2SOpenFaceConfigPayload(
                menu.getPos(),
                menu.getFace() != null ? menu.getFace()
                    : net.minecraft.core.Direction.UP));
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    private void renderUpgradeSlots(GuiGraphics g) {
        for (int i = 0; i < 3; i++) {
            int x = leftPos + 18;
            int y = topPos + 21 + (i * 30);
            g.blit(GUI_TEXTURE, x, y, 16, 16,
                SLGuiTextures.Upgrade.U, SLGuiTextures.Upgrade.V,
                SLGuiTextures.Upgrade.WIDTH, SLGuiTextures.Upgrade.HEIGHT,
                SLGuiTextures.GUI_WIDTH, SLGuiTextures.GUI_HEIGHT);
        }
    }

    private void renderSlotHints(GuiGraphics g) {
        String[] keys = {"gui.staticlogistics.hint.speed",
            "gui.staticlogistics.hint.range",
            "gui.staticlogistics.hint.stack"};
        for (int i = 0; i < 3; i++) {
            Component text = Component.translatable(keys[i]);
            int x = leftPos + 18;
            int y = topPos + 21 + (i * 30) + 18;
            g.pose().pushPose();
            g.pose().scale(0.8f, 0.8f, 0.8f);
            g.drawString(this.font, text,
                (int) (x / 0.8f), (int) (y / 0.8f), 0x88FFFFFF, false);
            g.pose().popPose();
        }
    }

    private void renderSlotTooltips(GuiGraphics g, int mx, int my) {
        for (int i = 0; i < menu.slots.size(); i++) {
            Slot slot = menu.getSlot(i);
            if (slot == null) continue;
            int sx = leftPos + slot.x, sy = topPos + slot.y;
            if (mx >= sx && mx < sx + 16 && my >= sy && my < sy + 16) {
                ItemStack stack = slot.getItem();
                if (!stack.isEmpty()) g.renderTooltip(font, stack, mx, my);
            }
        }
    }
}
