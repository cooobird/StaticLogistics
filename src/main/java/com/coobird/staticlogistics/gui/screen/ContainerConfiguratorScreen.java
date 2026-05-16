package com.coobird.staticlogistics.gui.screen;

import com.coobird.staticlogistics.api.type.TransferType;
import com.coobird.staticlogistics.config.SLConfig;
import com.coobird.staticlogistics.core.registration.TransferRegistries;
import com.coobird.staticlogistics.gui.menu.ContainerConfiguratorMenu;
import com.coobird.staticlogistics.gui.screen.texture.SLGuiTextures;
import com.coobird.staticlogistics.storage.config.ContainerConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class ContainerConfiguratorScreen extends AbstractConfiguratorScreen<ContainerConfiguratorMenu> {

    public ContainerConfiguratorScreen(ContainerConfiguratorMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected void init() {
        this.imageWidth = SLGuiTextures.Background.WIDTH + SLGuiTextures.Background.BY_GROUP_WIDTH + 2;
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
    protected void renderTypeListItem(GuiGraphics g, TransferType type, int x, int y, boolean isSelected) {
        Component typeName = Component.translatable(type.translationKey());
        String nameStr = typeName.getString();
        long stackMult = menu.getStackMultiplier();
        int baseStackSize = type.getBaseStackSize();
        boolean infinite = false;
        long finalStackSize = 0;
        try {
            finalStackSize = Math.multiplyExact(baseStackSize, stackMult);
            if (finalStackSize >= Integer.MAX_VALUE) {
                infinite = true;
            }
        } catch (ArithmeticException e) {
            infinite = true;
        }
        String valueText;
        int valueColor;
        if (infinite) {
            valueText = Component.translatable("gui.staticlogistics.infinite").getString();
            valueColor = 0x55FF55;
        } else {
            valueText = String.valueOf(finalStackSize);
            valueColor = finalStackSize > baseStackSize ? 0x55FF55 : 0xCCCCCC;
        }
        String displayName = font.plainSubstrByWidth(nameStr, 60);
        String combined = displayName + ": " + valueText;
        g.drawString(this.font, combined, x + 4, y + 2, valueColor, false);
    }

    @Override
    protected void renderHoveredTypeTooltip(GuiGraphics g, int mx, int my) {
        if (hoveredType == null) return;
        long stackMult = menu.getStackMultiplier();
        int baseStackSize = hoveredType.getBaseStackSize();
        boolean infinite = false;
        long finalStackSize = 0;
        try {
            finalStackSize = Math.multiplyExact(baseStackSize, stackMult);
            if (finalStackSize >= Integer.MAX_VALUE) {
                infinite = true;
            }
        } catch (ArithmeticException e) {
            infinite = true;
        }
        List<Component> tooltip = new ArrayList<>();
        tooltip.add(Component.translatable(hoveredType.translationKey()).withStyle(ChatFormatting.WHITE));
        tooltip.add(Component.translatable("gui.staticlogistics.stat.stack")
            .append(Component.translatable(infinite ? "gui.staticlogistics.infinite" : String.valueOf(stackMult)).withStyle(ChatFormatting.AQUA)));
        tooltip.add(Component.translatable("gui.staticlogistics.stat.transfer")
            .append(Component.translatable(infinite ? "gui.staticlogistics.infinite" : String.valueOf(finalStackSize)).withStyle(ChatFormatting.GREEN)));
        g.renderComponentTooltip(this.font, tooltip, mx, my);
    }

    @Override
    protected void onTypeClicked(TransferType type) {
    }

    @Override
    protected void renderCustomContent(GuiGraphics g, int mouseX, int mouseY) {
        renderUpgradeSlots(g);
        renderSlotHints(g);
        renderStats(g);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltips(graphics, mouseX, mouseY);
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
        String[] hintKeys = {"gui.staticlogistics.hint.speed", "gui.staticlogistics.hint.range", "gui.staticlogistics.hint.stack"};
        for (int i = 0; i < 3; i++) {
            Component text = Component.translatable(hintKeys[i]);
            int x = leftPos + 18;
            int y = topPos + 21 + (i * 30) + 18;
            g.pose().pushPose();
            g.pose().scale(0.8f, 0.8f, 0.8f);
            g.drawString(this.font, text, (int) (x / 0.8f), (int) (y / 0.8f), 0x88FFFFFF, false);
            g.pose().popPose();
        }
    }

    private void renderStats(GuiGraphics g) {
        long speedMult = menu.getSpeedMultiplier();
        long rangeMult = menu.getRangeMultiplier();
        long stackMult = menu.getStackMultiplier();
        boolean hasDimension = menu.isDimensionEffective();

        int baseRange = SLConfig.getDefaultRadius();
        boolean isRangeInfinite = hasDimension || rangeMult >= ContainerConfig.INFINITY_MARKER;
        String rangeText = isRangeInfinite
            ? Component.translatable("gui.staticlogistics.infinite").getString()
            : (baseRange * rangeMult) + Component.translatable("gui.staticlogistics.unit.meters").getString();
        int rangeColor = isRangeInfinite ? 0xFF55FF : 0x55FFFF;

        int infoX = leftPos + 75;
        int infoY = topPos + 16;
        int spacing = 15;
        int columnWidth = 66;

        drawStat(g, Component.translatable("gui.staticlogistics.stat.range"), rangeText, infoX, infoY, 0xFFFFFF, rangeColor);

        int baseInterval = SLConfig.getDefaultTickInterval();
        int actualInterval = (int) Math.max(1, baseInterval / Math.sqrt(speedMult));
        String speedText = actualInterval + Component.translatable("gui.staticlogistics.unit.ticks").getString();
        int speedColor = speedMult > 1 ? 0x55FF55 : 0xCCCCCC;
        drawStat(g, Component.translatable("gui.staticlogistics.stat.speed"), speedText, infoX + columnWidth, infoY, 0xFFFFFF, speedColor);

        String dimensionText = hasDimension
            ? Component.translatable("gui.staticlogistics.true").getString()
            : Component.translatable("gui.staticlogistics.false").getString();
        int dimensionColor = hasDimension ? 0x55FF55 : 0xCCCCCC;
        drawStat(g, Component.translatable("gui.staticlogistics.stat.dimension"), dimensionText, infoX, infoY + spacing, 0xFFFFFF, dimensionColor);

        String stackText;
        int stackColor;
        if (stackMult >= ContainerConfig.INFINITY_MARKER) {
            stackText = Component.translatable("gui.staticlogistics.infinite").getString();
            stackColor = 0x55FF55;
        } else {
            stackText = stackMult + Component.translatable("gui.staticlogistics.unit.multiplier").getString();
            stackColor = stackMult > 1 ? 0x55FF55 : 0xCCCCCC;
        }
        drawStat(g, Component.translatable("gui.staticlogistics.stat.stack"), stackText, infoX + columnWidth, infoY + spacing, 0xFFFFFF, stackColor);
    }

    private void renderTooltips(GuiGraphics g, int mouseX, int mouseY) {
        for (int i = 0; i < menu.slots.size(); i++) {
            Slot slot = menu.getSlot(i);
            if (slot == null) continue;
            int slotX = leftPos + slot.x;
            int slotY = topPos + slot.y;
            if (mouseX >= slotX && mouseX < slotX + 16 && mouseY >= slotY && mouseY < slotY + 16) {
                ItemStack stack = slot.getItem();
                if (!stack.isEmpty()) {
                    g.renderTooltip(font, stack, mouseX, mouseY);
                }
            }
        }
    }
}