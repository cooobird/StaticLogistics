package com.coobird.staticlogistics.gui.screen;

import com.coobird.staticlogistics.api.type.TransferType;
import com.coobird.staticlogistics.api.type.UpgradeTier;
import com.coobird.staticlogistics.api.type.UpgradeType;
import com.coobird.staticlogistics.config.SLConfig;
import com.coobird.staticlogistics.core.registration.TransferRegistries;
import com.coobird.staticlogistics.gui.menu.ContainerConfiguratorMenu;
import com.coobird.staticlogistics.gui.screen.texture.SLGuiTextures;
import com.coobird.staticlogistics.item.UpgradeItem;
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

            g.blit(
                GUI_TEXTURE,
                x, y,
                16, 16,
                SLGuiTextures.Upgrade.U, SLGuiTextures.Upgrade.V,
                SLGuiTextures.Upgrade.WIDTH, SLGuiTextures.Upgrade.HEIGHT,
                SLGuiTextures.GUI_WIDTH, SLGuiTextures.GUI_HEIGHT
            );
        }
    }

    private void renderSlotHints(GuiGraphics g) {
        String[] hintKeys = {
            "gui.staticlogistics.hint.speed",
            "gui.staticlogistics.hint.range",
            "gui.staticlogistics.hint.stack"
        };
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
        int baseRange = SLConfig.getDefaultRadius();
        int rangeBonus = 0;
        boolean hasDimension = false;
        int speedMult = 1;
        int stackMult = 1;

        for (int i = 0; i < 3; i++) {
            ItemStack stack = menu.getSlot(i).getItem();
            if (!stack.isEmpty() && stack.getItem() instanceof UpgradeItem upgrade) {
                UpgradeTier tier = upgrade.getTier();
                int count = stack.getCount();
                if (tier != null) {
                    int multiplier = tier.getMultiplier();
                    switch (i) {
                        case 0 -> speedMult += multiplier * count;
                        case 1 -> rangeBonus += multiplier * count;
                        case 2 -> stackMult += multiplier * count;
                    }
                } else {
                    if (upgrade.getType() == UpgradeType.DIMENSION) {
                        hasDimension = true;
                    }
                }
            }
        }

        int infoX = leftPos + 75;
        int infoY = topPos + 16;
        int spacing = 15;
        int columnWidth = 66;

        boolean isRangeInfinite = hasDimension || (baseRange + rangeBonus) < 0 || rangeBonus < 0;
        String rangeText = isRangeInfinite
            ? Component.translatable("gui.staticlogistics.infinite").getString()
            : (baseRange + rangeBonus) + Component.translatable("gui.staticlogistics.unit.meters").getString();
        int rangeColor = isRangeInfinite ? 0xFF55FF : 0x55FFFF;
        drawStat(g, Component.translatable("gui.staticlogistics.stat.range"), rangeText, infoX, infoY, 0xFFFFFF, rangeColor);

        int baseInterval = SLConfig.getDefaultTickInterval();
        int actualInterval = (int) Math.max(1, baseInterval / Math.sqrt(speedMult));
        String speedText = actualInterval + Component.translatable("gui.staticlogistics.unit.ticks").getString();
        int speedColor = speedMult > 1 ? 0x55FF55 : 0x555555;
        drawStat(g, Component.translatable("gui.staticlogistics.stat.speed"), speedText, infoX + columnWidth, infoY, 0xFFFFFF, speedColor);

        String dimensionText = hasDimension
            ? Component.translatable("gui.staticlogistics.true").getString()
            : Component.translatable("gui.staticlogistics.false").getString();
        int dimensionColor = hasDimension ? 0x55FF55 : 0x555555;
        drawStat(g, Component.translatable("gui.staticlogistics.stat.dimension"), dimensionText, infoX, infoY + spacing, 0xFFFFFF, dimensionColor);

        String stackText = stackMult + Component.translatable("gui.staticlogistics.unit.multiplier").getString();
        int stackColor = stackMult > 1 ? 0x55FF55 : 0x555555;
        drawStat(g, Component.translatable("gui.staticlogistics.stat.stack"), stackText, infoX + columnWidth, infoY + spacing, 0xFFFFFF, stackColor);

        int transferTypeY = infoY + spacing * 2 + 5;
        int typeSpacing = 14;
        int typeOffsetX = 0;

        List<TransferType> types = new ArrayList<>(TransferRegistries.getAllActive());
        int midIndex = (types.size() + 1) / 2;

        for (int i = 0; i < types.size(); i++) {
            TransferType type = types.get(i);
            int column = i < midIndex ? 0 : 1;
            int row = i < midIndex ? i : i - midIndex;
            int x = infoX + typeOffsetX + column * columnWidth;
            int y = transferTypeY + row * typeSpacing;

            int baseStackSize = type.baseStackSize();
            int finalStackSize = (int) Math.min((long) baseStackSize * stackMult, Integer.MAX_VALUE);
            String baseText = baseStackSize + "";
            String finalText = finalStackSize + "";
            int finalColor = finalStackSize > baseStackSize ? 0x55FF55 : 0x555555;
            drawTransferTypeStat(g, Component.translatable(type.translationKey()), finalText, x, y, 0xFFFFFF, finalColor);
        }
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

    private void drawTransferTypeStat(GuiGraphics g, Component label, String finalValue, int x, int y, int labelColor, int valueColor) {
        g.drawString(this.font, label, x, y, labelColor, false);
        int labelWidth = this.font.width(label);
        g.drawString(this.font, finalValue, x + labelWidth + 4, y, valueColor, false);
    }
}