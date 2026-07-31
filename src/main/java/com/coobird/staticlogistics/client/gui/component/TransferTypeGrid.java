package com.coobird.staticlogistics.client.gui.component;

import PortLib.extensions.net.minecraft.world.item.ItemStack.PortItemStackExtension;
import com.coobird.staticlogistics.client.render.SLGuiTextures;
import com.coobird.staticlogistics.logistics.SLDataComponents;
import com.coobird.staticlogistics.transfer.LogisticsResource;
import com.coobird.staticlogistics.transfer.TransferRegistries;
import com.coobird.staticlogistics.transfer.TransferTypeSelection;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 顶部资源类型视窗。
 *
 * <p>同一排按钮根据当前界面上下文展示配置器默认类型、节点输出类型或节点
 * 实际接收类型；具体数据来源和修改行为由主界面协调，本类只负责绘制与命中。
 */
public final class TransferTypeGrid {
    private static final int VISIBLE_TYPES = 5;
    private static final int BTN_WIDTH = 20;
    private static final int SPACING = 2;
    private static final int START_X_OFFSET = 182;
    private static final int START_Y_OFFSET = 6;
    private static final int PREVIOUS_X_OFFSET = 159;
    private static final int NEXT_X_OFFSET = 293;
    private static final int NAVIGATION_ARROW_X_OFFSET = 0;
    private static final int NAVIGATION_ARROW_Y_OFFSET = -2;
    private static final float ICON_SCALE = 0.7F;
    private static final float ICON_X_OFFSET = 3.7F;
    private static final float ICON_Y_OFFSET = 1.5F;
    private static int currentPage;

    public enum Context {
        TOOL_DEFAULT("gui.staticlogistics.tooltip.type_context.tool_default"),
        NODE_OUTPUT("gui.staticlogistics.tooltip.type_context.node_output"),
        NODE_INPUT("gui.staticlogistics.tooltip.type_context.node_input");

        private final String descriptionKey;

        Context(String descriptionKey) {
            this.descriptionKey = descriptionKey;
        }
    }

    public record View(
        List<ResourceLocation> selectedTypeIds,
        Context context,
        boolean editable
    ) {
        public View {
            selectedTypeIds = List.copyOf(selectedTypeIds);
        }
    }

    public static void render(
        GuiGraphics g,
        View view,
        int leftPos,
        int topPos,
        int mx,
        int my
    ) {
        List<LogisticsResource<?>> types = new ArrayList<>(TransferRegistries.getAllActive());
        int startX = leftPos + START_X_OFFSET;
        int baseY = topPos + START_Y_OFFSET;

        clampCurrentPage(types.size());
        int firstVisible = firstVisibleIndex();
        int pageCount = pageCount(types.size());
        int end = Math.min(types.size(), firstVisible + VISIBLE_TYPES);
        renderNavigationButton(g, leftPos + PREVIOUS_X_OFFSET, topPos + START_Y_OFFSET,
            false, currentPage > 0);
        renderNavigationButton(g, leftPos + NEXT_X_OFFSET, topPos + START_Y_OFFSET,
            true, currentPage + 1 < pageCount);

        for (int i = firstVisible; i < end; i++) {
            LogisticsResource<?> type = types.get(i);
            boolean isSelected = TransferTypeSelection.isSelected(
                view.selectedTypeIds(), type);
            int col = i - firstVisible;
            int baseX = startX + col * (BTN_WIDTH + SPACING);

            int bw = isSelected ? SLGuiTextures.Button.Big.SELECTED_WIDTH : SLGuiTextures.Button.Big.DISABLED_WIDTH;
            int bh = isSelected ? SLGuiTextures.Button.Big.SELECTED_HEIGHT : SLGuiTextures.Button.Big.DISABLED_HEIGHT;
            int u = isSelected ? SLGuiTextures.Button.Big.SELECTED_U : SLGuiTextures.Button.Big.DISABLED_U;
            int v = isSelected ? SLGuiTextures.Button.Big.SELECTED_V : SLGuiTextures.Button.Big.DISABLED_V;
            int drawX = isSelected ? baseX - 1 : baseX;
            int drawY = isSelected ? baseY - 1 : baseY;

            g.blit(SLGuiTextures.GUI_ATLAS, drawX, drawY, u, v, bw, bh, SLGuiTextures.GUI_WIDTH, SLGuiTextures.GUI_HEIGHT);

            ItemStack iconStack = type.getIcon();
            g.pose().pushPose();
            float iconX = (baseX + ICON_X_OFFSET) / ICON_SCALE;
            float iconY = (baseY + ICON_Y_OFFSET) / ICON_SCALE;
            g.pose().scale(ICON_SCALE, ICON_SCALE, 1.0F);
            g.renderFakeItem(iconStack, Math.round(iconX), Math.round(iconY));
            g.pose().popPose();
        }
        g.drawString(Minecraft.getInstance().font, types.isEmpty() ? "0 / 0" : (currentPage + 1) + " / " + pageCount, leftPos + 316, topPos + 12, 0xFF888888, false);
    }

    @Nullable
    public static LogisticsResource<?> getHoveredType(
        double mx,
        double my,
        View view,
        int leftPos,
        int topPos
    ) {
        List<LogisticsResource<?>> types = new ArrayList<>(TransferRegistries.getAllActive());
        int startX = leftPos + START_X_OFFSET;
        int startY = topPos + START_Y_OFFSET;

        clampCurrentPage(types.size());
        int firstVisible = firstVisibleIndex();
        int end = Math.min(types.size(), firstVisible + VISIBLE_TYPES);
        for (int i = firstVisible; i < end; i++) {
            LogisticsResource<?> type = types.get(i);
            boolean isSelected = TransferTypeSelection.isSelected(
                view.selectedTypeIds(), type);
            int col = i - firstVisible;
            int baseX = startX + col * (BTN_WIDTH + SPACING);
            int baseY = startY;
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

    public static boolean handleNavigationClick(double mx, double my,
                                                int leftPos, int topPos) {
        List<LogisticsResource<?>> types = new ArrayList<>(TransferRegistries.getAllActive());
        clampCurrentPage(types.size());
        int pageCount = pageCount(types.size());
        if (isNavigationHit(mx, my, leftPos + PREVIOUS_X_OFFSET,
            topPos + START_Y_OFFSET) && currentPage > 0) {
            currentPage--;
            return true;
        }
        if (isNavigationHit(mx, my, leftPos + NEXT_X_OFFSET,
            topPos + START_Y_OFFSET)
            && currentPage + 1 < pageCount) {
            currentPage++;
            return true;
        }
        return false;
    }

    public static boolean mouseScrolled(double mx, double my, double delta,
                                        int leftPos, int topPos) {
        int x = leftPos + PREVIOUS_X_OFFSET;
        int y = topPos + START_Y_OFFSET;
        if (mx < x || mx >= leftPos + NEXT_X_OFFSET + BTN_WIDTH
            || my < y || my >= y + SLGuiTextures.Button.Big.SELECTED_HEIGHT) {
            return false;
        }
        List<LogisticsResource<?>> types = new ArrayList<>(TransferRegistries.getAllActive());
        clampCurrentPage(types.size());
        int maximumPage = Math.max(0, pageCount(types.size()) - 1);
        currentPage = Math.max(0, Math.min(
            currentPage + (delta < 0 ? 1 : -1), maximumPage));
        return true;
    }

    private static void renderNavigationButton(GuiGraphics graphics, int x, int y,
                                               boolean next, boolean enabled) {
        int u = enabled ? SLGuiTextures.Button.Big.NORMAL_U
            : SLGuiTextures.Button.Big.DISABLED_U;
        int v = enabled ? SLGuiTextures.Button.Big.NORMAL_V
            : SLGuiTextures.Button.Big.DISABLED_V;
        graphics.blit(SLGuiTextures.GUI_ATLAS, x, y, u, v,
            SLGuiTextures.Button.Big.WIDTH, SLGuiTextures.Button.Big.HEIGHT,
            SLGuiTextures.GUI_WIDTH, SLGuiTextures.GUI_HEIGHT);

        int iconU = next
            ? SLGuiTextures.Direction.RIGHT_U
            : SLGuiTextures.Direction.LEFT_U;
        int iconV = enabled
            ? SLGuiTextures.Direction.ENABLED_V
            : SLGuiTextures.Direction.DISABLED_V;
        int iconX = x + (SLGuiTextures.Button.Big.WIDTH
            - SLGuiTextures.Direction.HORIZONTAL_WIDTH) / 2
            + NAVIGATION_ARROW_X_OFFSET;
        int iconY = y + (SLGuiTextures.Button.Big.HEIGHT
            - SLGuiTextures.Direction.HORIZONTAL_HEIGHT) / 2
            + NAVIGATION_ARROW_Y_OFFSET;
        graphics.blit(SLGuiTextures.GUI_ATLAS, iconX, iconY, iconU, iconV,
            SLGuiTextures.Direction.HORIZONTAL_WIDTH,
            SLGuiTextures.Direction.HORIZONTAL_HEIGHT,
            SLGuiTextures.GUI_WIDTH, SLGuiTextures.GUI_HEIGHT);
    }

    private static boolean isNavigationHit(double mx, double my, int x, int y) {
        return mx >= x && mx < x + BTN_WIDTH
            && my >= y && my < y + SLGuiTextures.Button.Big.HEIGHT;
    }

    private static int firstVisibleIndex() {
        return currentPage * VISIBLE_TYPES;
    }

    private static int pageCount(int typeCount) {
        return typeCount == 0
            ? 0
            : (typeCount + VISIBLE_TYPES - 1) / VISIBLE_TYPES;
    }

    private static void clampCurrentPage(int typeCount) {
        currentPage = Math.min(currentPage,
            Math.max(0, pageCount(typeCount) - 1));
    }

    public static List<ResourceLocation> getToolSelectedTypeIds(ItemStack stack) {
        List<LogisticsResource<?>> activeTypes = new ArrayList<>(TransferRegistries.getAllActive());
        List<ResourceLocation> selectedTypeIds = PortItemStackExtension.getData(stack, SLDataComponents.SELECTED_TYPES.get());
        int legacyMask = PortItemStackExtension.getDataOrDefault(stack, SLDataComponents.SELECTED_TYPES_MASK.get(), 0);
        return TransferTypeSelection.mergeIdsWithMask(
            selectedTypeIds == null ? List.of() : selectedTypeIds, legacyMask, activeTypes);
    }

    public static void toggleToolType(ItemStack stack, LogisticsResource<?> type) {
        List<LogisticsResource<?>> activeTypes = new ArrayList<>(TransferRegistries.getAllActive());
        List<ResourceLocation> newSelection = TransferTypeSelection.toggle(
            getToolSelectedTypeIds(stack), type);
        PortItemStackExtension.setData(stack, SLDataComponents.SELECTED_TYPES.get(), newSelection);
        int oldMask = PortItemStackExtension.getDataOrDefault(stack, SLDataComponents.SELECTED_TYPES_MASK.get(), 0);
        int unresolvedMask = oldMask & ~TransferTypeSelection.activeLegacyMask(activeTypes);
        PortItemStackExtension.setData(stack, SLDataComponents.SELECTED_TYPES_MASK.get(), TransferTypeSelection.toMask(newSelection, activeTypes) | unresolvedMask);
    }

    public static void renderTooltip(
        GuiGraphics g,
        Font font,
        LogisticsResource<?> type,
        View view,
        int mx,
        int my
    ) {
        List<Component> tooltip = new ArrayList<>();
        int safeColor = type.color() & 0xFFFFFF;
        tooltip.add(Component.translatable(type.translationKey())
            .withStyle(style -> style.withColor(
                TextColor.fromRgb(safeColor))));
        tooltip.add(Component.translatable(type.translationKey() + ".desc")
            .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.empty());
        tooltip.add(Component.translatable(view.context().descriptionKey)
            .withStyle(ChatFormatting.GRAY));
        if (view.editable()) {
            tooltip.add(Component.translatable("gui.staticlogistics.tooltip.toggle_type")
                .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        } else {
            tooltip.add(Component.translatable(
                    view.context() == Context.NODE_INPUT
                        ? "gui.staticlogistics.tooltip.input_types_read_only"
                        : "gui.staticlogistics.tooltip.enable_output_to_edit_types")
                .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        }
        g.renderComponentTooltip(font, tooltip, mx, my);
    }
}
