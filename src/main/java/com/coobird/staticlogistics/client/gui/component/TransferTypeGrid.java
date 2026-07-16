package com.coobird.staticlogistics.client.gui.component;

import com.coobird.staticlogistics.transfer.LogisticsResource;
import com.coobird.staticlogistics.client.render.SLGuiTextures;
import com.coobird.staticlogistics.transfer.TransferRegistries;
import com.coobird.staticlogistics.transfer.TransferTypeSelection;
import com.coobird.staticlogistics.logistics.SLDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
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
        List<LogisticsResource<?>> types = new ArrayList<>(TransferRegistries.getAllActive());
        List<ResourceLocation> selectedTypeIds = getSelectedTypeIds(stack, types);
        int startX = leftPos + START_X_OFFSET;
        int startY = topPos + START_Y_OFFSET;

        for (int i = 0; i < types.size(); i++) {
            LogisticsResource<?> type = types.get(i);
            boolean isSelected = TransferTypeSelection.isSelected(selectedTypeIds, type);
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
    public static LogisticsResource<?> getHoveredType(double mx, double my, ItemStack stack,
                                                      int leftPos, int topPos) {
        List<LogisticsResource<?>> types = new ArrayList<>(TransferRegistries.getAllActive());
        int startX = leftPos + START_X_OFFSET;
        int startY = topPos + START_Y_OFFSET;
        List<ResourceLocation> selectedTypeIds = getSelectedTypeIds(stack, types);

        for (int i = 0; i < types.size(); i++) {
            LogisticsResource<?> type = types.get(i);
            boolean isSelected = TransferTypeSelection.isSelected(selectedTypeIds, type);
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
     * 处理类型按钮点击。返回被点击并已切换状态的 {@code LogisticsResource<?>}，未命中返回 null。
     */
    @Nullable
    public static LogisticsResource<?> handleClick(double mx, double my, ItemStack stack,
                                                   int leftPos, int topPos) {
        List<LogisticsResource<?>> types = new ArrayList<>(TransferRegistries.getAllActive());
        if (types.isEmpty()) return null;

        LogisticsResource<?> clicked = getHoveredType(mx, my, stack, leftPos, topPos);
        if (clicked == null) return null;

        List<ResourceLocation> selectedTypeIds = getSelectedTypeIds(stack, types);
        List<ResourceLocation> newSelection = TransferTypeSelection.toggle(selectedTypeIds, clicked);
        stack.set(SLDataComponents.SELECTED_TYPES.get(), newSelection);
        int oldMask = stack.getOrDefault(SLDataComponents.SELECTED_TYPES_MASK.get(), 0);
        int unresolvedMask = oldMask & ~TransferTypeSelection.activeLegacyMask(types);
        stack.set(SLDataComponents.SELECTED_TYPES_MASK.get(),
            TransferTypeSelection.toMask(newSelection, types) | unresolvedMask);
        return clicked;
    }

    private static List<ResourceLocation> getSelectedTypeIds(ItemStack stack, List<LogisticsResource<?>> activeTypes) {
        List<ResourceLocation> selectedTypeIds = stack.get(SLDataComponents.SELECTED_TYPES.get());
        int legacyMask = stack.getOrDefault(SLDataComponents.SELECTED_TYPES_MASK.get(), 0);
        return TransferTypeSelection.mergeIdsWithMask(
            selectedTypeIds == null ? List.of() : selectedTypeIds, legacyMask, activeTypes);
    }

    public static void renderTooltip(GuiGraphics g, Font font, LogisticsResource<?> type, int mx, int my) {
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
