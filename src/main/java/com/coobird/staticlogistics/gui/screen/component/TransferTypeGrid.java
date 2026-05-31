package com.coobird.staticlogistics.gui.screen.component;

import com.coobird.staticlogistics.api.type.TransferType;
import com.coobird.staticlogistics.core.registration.TransferRegistries;
import com.coobird.staticlogistics.gui.screen.texture.SLGuiTextures;
import com.coobird.staticlogistics.registry.SLDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 中间区域的传输类型按钮网格组件：渲染类型切换按钮及其物品图标。
 */
public class TransferTypeGrid {

    private static final int PER_ROW = 8;
    private static final int BTN_WIDTH = 19;
    private static final int SPACING = 4;
    private static final int ROW_SPACING = 22;
    private static final int START_X_OFFSET = 15;
    private static final int START_Y_OFFSET = 18;

    public static void render(GuiGraphics g, ItemStack stack, int leftPos, int topPos, int mx, int my) {
        int mask = stack.getOrDefault(SLDataComponents.SELECTED_TYPES_MASK.get(), 0);
        List<TransferType> types = new ArrayList<>(TransferRegistries.getAllActive());
        int startX = leftPos + START_X_OFFSET;
        int startY = topPos + START_Y_OFFSET;

        for (int i = 0; i < types.size(); i++) {
            TransferType type = types.get(i);
            boolean isSelected = (mask & type.getFlag()) != 0;
            int row = i / PER_ROW;
            int col = i % PER_ROW;
            int baseX = startX + col * (BTN_WIDTH + SPACING);
            int baseY = startY + row * ROW_SPACING;

            int bw = isSelected ? SLGuiTextures.Button.Big.SELECTED_WIDTH : SLGuiTextures.Button.Big.DISABLED_WIDTH;
            int bh = isSelected ? SLGuiTextures.Button.Big.SELECTED_HEIGHT : SLGuiTextures.Button.Big.DISABLED_HEIGHT;
            int u = isSelected ? SLGuiTextures.Button.Big.SELECTED_U : SLGuiTextures.Button.Big.DISABLED_U;
            int v = isSelected ? SLGuiTextures.Button.Big.SELECTED_V : SLGuiTextures.Button.Big.DISABLED_V;
            int drawX = isSelected ? baseX - 1 : baseX;
            int drawY = isSelected ? baseY - 1 : baseY;

            g.blit(SLGuiTextures.GUI_ATLAS, drawX, drawY, u, v, bw, bh, SLGuiTextures.GUI_WIDTH, SLGuiTextures.GUI_HEIGHT);

            ItemStack iconStack = type.getIcon();
            float scale = 0.8f;
            g.pose().pushPose();
            float iconX = (baseX + 3.5f) / scale;
            float iconY = (baseY + 1.5f) / scale;
            g.pose().scale(scale, scale, 1.0f);
            g.renderFakeItem(iconStack, (int) iconX, (int) iconY);
            g.pose().popPose();
        }
    }

    @Nullable
    public static TransferType getHoveredType(double mx, double my, ItemStack stack,
                                              int leftPos, int topPos) {
        List<TransferType> types = new ArrayList<>(TransferRegistries.getAllActive());
        int startX = leftPos + START_X_OFFSET;
        int startY = topPos + START_Y_OFFSET;
        int mask = stack.getOrDefault(SLDataComponents.SELECTED_TYPES_MASK.get(), 0);

        for (int i = 0; i < types.size(); i++) {
            TransferType type = types.get(i);
            boolean isSelected = (mask & type.getFlag()) != 0;
            int row = i / PER_ROW;
            int col = i % PER_ROW;
            int baseX = startX + col * (BTN_WIDTH + SPACING);
            int baseY = startY + row * ROW_SPACING;
            int bw = isSelected ? SLGuiTextures.Button.Big.SELECTED_WIDTH : SLGuiTextures.Button.Big.DISABLED_WIDTH;
            int bh = isSelected ? SLGuiTextures.Button.Big.SELECTED_HEIGHT : SLGuiTextures.Button.Big.DISABLED_HEIGHT;
            int drawX = isSelected ? baseX - 1 : baseX;
            int drawY = isSelected ? baseY - 1 : baseY;

            if (mx >= drawX && mx < drawX + bw && my >= drawY && my < drawY + bh) {
                return type;
            }
        }
        return null;
    }

    /**
     * 处理类型按钮点击。返回被点击的 TransferType（已 toggle），未命中返回 null。
     */
    @Nullable
    public static TransferType handleClick(double mx, double my, ItemStack stack,
                                           int leftPos, int topPos) {
        List<TransferType> types = new ArrayList<>(TransferRegistries.getAllActive());
        if (types.isEmpty()) return null;

        int startX = leftPos + START_X_OFFSET;
        int startY = topPos + START_Y_OFFSET;
        int rows = (types.size() + PER_ROW - 1) / PER_ROW;

        if (mx < startX || mx >= startX + PER_ROW * (BTN_WIDTH + SPACING)
            || my < startY || my >= startY + rows * ROW_SPACING)
            return null;

        int col = (int) ((mx - startX) / (BTN_WIDTH + SPACING));
        int row = (int) ((my - startY) / ROW_SPACING);
        int idx = row * PER_ROW + col;
        if (idx < 0 || idx >= types.size()) return null;

        TransferType clicked = types.get(idx);
        int mask = stack.getOrDefault(SLDataComponents.SELECTED_TYPES_MASK.get(), 0);
        int newMask = mask ^ clicked.getFlag();
        stack.set(SLDataComponents.SELECTED_TYPES_MASK.get(), newMask);
        return clicked;
    }

    public static void renderTooltip(GuiGraphics g, Font font, TransferType type, int mx, int my) {
        List<Component> tooltip = new ArrayList<>();
        int safeColor = type.color() & 0xFFFFFF;
        tooltip.add(Component.translatable(type.translationKey())
            .withStyle(style -> style.withColor(
                net.minecraft.network.chat.TextColor.fromRgb(safeColor))));
        tooltip.add(Component.translatable(type.translationKey() + ".desc")
            .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.empty());
        tooltip.add(Component.translatable("gui.staticlogistics.tooltip.toggle_type")
            .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        g.renderComponentTooltip(font, tooltip, mx, my);
    }
}
