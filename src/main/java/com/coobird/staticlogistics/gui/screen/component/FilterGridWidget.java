package com.coobird.staticlogistics.gui.screen.component;

import com.coobird.staticlogistics.api.type.UpgradeType;
import com.coobird.staticlogistics.gui.screen.texture.SLGuiTextures;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

import java.util.ArrayList;
import java.util.List;

/**
 * 过滤网格组件：9×4（或标签模式 1×4）的过滤槽位渲染与交互。
 */
public class FilterGridWidget {

    public static final int GRID_COLS = 9;
    public static final int GRID_ROWS = 4;
    public static final int START_X = 23;
    public static final int START_Y = 20;
    public static final int SLOT_SIZE = 18;

    /**
     * 获取指定行列的槽位索引
     */
    public static int getSlotIndex(int row, int col, boolean tagMode) {
        return tagMode ? row : row * GRID_COLS + col;
    }

    public static int getSlotIndex(int row, int col, UpgradeType type) {
        return getSlotIndex(row, col, type == UpgradeType.TAG_FILTER);
    }

    /**
     * 返回网格区域（用于 JEI/REI 等兼容）
     */
    public static Rect2i getArea(int leftPos, int topPos, boolean tagMode) {
        int cols = tagMode ? 1 : GRID_COLS;
        int rows = tagMode ? 4 : GRID_ROWS;
        return new Rect2i(
            leftPos + START_X, topPos + START_Y,
            cols * SLOT_SIZE, rows * SLOT_SIZE);
    }

    public static Rect2i getArea(int leftPos, int topPos, UpgradeType type) {
        return getArea(leftPos, topPos, type == UpgradeType.TAG_FILTER);
    }

    public interface FilterSlotProvider {
        ItemStack getFilterItem(int index);

        Fluid getFluidItem(int index);
    }

    public static void render(GuiGraphics g, int leftPos, int topPos,
                              FilterSlotProvider provider, boolean tagMode) {
        int startX = leftPos + START_X;
        int startY = topPos + START_Y;
        Minecraft mc = Minecraft.getInstance();
        int rows = tagMode ? 4 : GRID_ROWS;
        int cols = tagMode ? 1 : GRID_COLS;

        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                int index = getSlotIndex(row, col, tagMode);
                int x = startX + col * SLOT_SIZE;
                int y = startY + row * SLOT_SIZE;

                g.blit(SLGuiTextures.GUI_ATLAS, x, y,
                    SLGuiTextures.Inventory.SLOT_U, SLGuiTextures.Inventory.SLOT_V,
                    SLGuiTextures.Inventory.SLOT_WIDTH, SLGuiTextures.Inventory.SLOT_HEIGHT,
                    SLGuiTextures.GUI_WIDTH, SLGuiTextures.GUI_HEIGHT);

                Fluid fluid = provider.getFluidItem(index);
                if (fluid != null) {
                    IClientFluidTypeExtensions ext = IClientFluidTypeExtensions.of(fluid);
                    ResourceLocation still = ext.getStillTexture();
                    TextureAtlasSprite sprite = mc.getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(still);
                    int tint = ext.getTintColor();
                    float r = ((tint >> 16) & 0xFF) / 255.0f;
                    float gco = ((tint >> 8) & 0xFF) / 255.0f;
                    float b = (tint & 0xFF) / 255.0f;
                    float a = ((tint >> 24) & 0xFF) / 255.0f;
                    g.setColor(r, gco, b, a);
                    RenderSystem.setShaderTexture(0, InventoryMenu.BLOCK_ATLAS);
                    g.blit(x + 1, y + 1, 0, 16, 16, sprite);
                    g.setColor(1.0f, 1.0f, 1.0f, 1.0f);
                    RenderSystem.enableBlend();
                    RenderSystem.defaultBlendFunc();
                } else {
                    ItemStack stack = provider.getFilterItem(index);
                    if (!stack.isEmpty()) {
                        g.renderFakeItem(stack, x + 1, y + 1);
                    }
                }
            }
        }
    }


    public static void renderTooltips(GuiGraphics g, Font font, int leftPos, int topPos,
                                      int mx, int my, FilterSlotProvider provider,
                                      boolean tagMode, ItemStack carried) {
        int startX = leftPos + START_X;
        int startY = topPos + START_Y;
        int rows = tagMode ? 4 : GRID_ROWS;
        int cols = tagMode ? 1 : GRID_COLS;

        Minecraft mc = Minecraft.getInstance();
        Component carriedItemName = null;
        Component carriedFluidName = null;
        if (!carried.isEmpty()) {
            carriedItemName = carried.getHoverName();
            IFluidHandler fluidCap = carried.getCapability(Capabilities.FluidHandler.ITEM);
            if (fluidCap != null) {
                FluidStack stored = fluidCap.drain(Integer.MAX_VALUE, IFluidHandler.FluidAction.SIMULATE);
                if (!stored.isEmpty()) {
                    carriedFluidName = stored.getHoverName();
                }
            }
        }
        boolean hasCarriedFluid = carriedFluidName != null;

        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                int x = startX + col * SLOT_SIZE;
                int y = startY + row * SLOT_SIZE;
                if (mx >= x && mx < x + 16 && my >= y && my < y + 16) {
                    int index = getSlotIndex(row, col, tagMode);

                    Fluid fluid = provider.getFluidItem(index);
                    if (fluid != null) {
                        FluidStack fs = new FluidStack(fluid, 1000);
                        List<Component> tooltip = new ArrayList<>();
                        tooltip.add(fs.getHoverName());
                        if (mc != null && mc.options.advancedItemTooltips) {
                            tooltip.add(Component.literal(
                                    BuiltInRegistries.FLUID.getKey(fluid).toString())
                                .withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
                        }
                        g.renderComponentTooltip(font, tooltip, mx, my);
                        return;
                    }

                    ItemStack stack = provider.getFilterItem(index);
                    if (!stack.isEmpty()) {
                        TooltipFlag flag = mc.options.advancedItemTooltips
                            ? TooltipFlag.ADVANCED : TooltipFlag.NORMAL;
                        List<Component> tooltip = stack.getTooltipLines(
                            Item.TooltipContext.of(mc.level), mc.player, flag);
                        if (hasCarriedFluid) {
                            tooltip.add(Component.empty());
                            tooltip.add(Component.translatable(
                                    "gui.staticlogistics.filter.left_click_item", carriedItemName)
                                .withStyle(net.minecraft.ChatFormatting.GRAY));
                            tooltip.add(Component.translatable(
                                    "gui.staticlogistics.filter.right_click_fluid", carriedFluidName)
                                .withStyle(net.minecraft.ChatFormatting.GRAY));
                        }
                        g.renderComponentTooltip(font, tooltip, mx, my);
                        return;
                    }

                    List<Component> emptyTooltip = new ArrayList<>();
                    if (!carried.isEmpty()) {
                        emptyTooltip.add(Component.translatable(
                                "gui.staticlogistics.filter.left_click_item", carriedItemName)
                            .withStyle(net.minecraft.ChatFormatting.GRAY));
                    }
                    if (hasCarriedFluid) {
                        emptyTooltip.add(Component.translatable(
                                "gui.staticlogistics.filter.right_click_fluid", carriedFluidName)
                            .withStyle(net.minecraft.ChatFormatting.GRAY));
                    }
                    if (!emptyTooltip.isEmpty()) {
                        g.renderComponentTooltip(font, emptyTooltip, mx, my);
                    }
                    return;
                }
            }
        }
    }

    // ---- 悬停检测 ----

    /**
     * 返回鼠标悬停的槽位索引，-1 表示未命中
     */
    public static int getHoveredSlot(int leftPos, int topPos, double mx, double my,
                                     boolean tagMode) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return -1;
        double sx = mx * mc.getWindow().getGuiScaledWidth() / mc.getWindow().getScreenWidth();
        double sy = my * mc.getWindow().getGuiScaledHeight() / mc.getWindow().getScreenHeight();
        int startX = leftPos + START_X;
        int startY = topPos + START_Y;
        int rows = tagMode ? 4 : GRID_ROWS;
        int cols = tagMode ? 1 : GRID_COLS;
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                int x = startX + col * SLOT_SIZE;
                int y = startY + row * SLOT_SIZE;
                if (sx >= x && sx < x + 16 && sy >= y && sy < y + 16) {
                    return getSlotIndex(row, col, tagMode);
                }
            }
        }
        return -1;
    }

    public enum ClickAction {
        SET_ITEM,     // 左键放置物品
        SET_FLUID,    // 右键设置流体
        CLEAR,        // 右键清空
        NONE
    }

    public record GridClickResult(int slotIndex, ClickAction action) {
        public static GridClickResult setItem(int idx) {
            return new GridClickResult(idx, ClickAction.SET_ITEM);
        }

        public static GridClickResult setFluid(int idx) {
            return new GridClickResult(idx, ClickAction.SET_FLUID);
        }

        public static GridClickResult clear(int idx) {
            return new GridClickResult(idx, ClickAction.CLEAR);
        }
    }

    /**
     * 处理网格区域鼠标点击，返回点击结果或 null
     */
    public static GridClickResult handleClick(int leftPos, int topPos, double mx, double my,
                                              int button, FilterSlotProvider provider,
                                              boolean tagMode, ItemStack carried) {
        int startX = leftPos + START_X;
        int startY = topPos + START_Y;
        int rows = tagMode ? 4 : GRID_ROWS;
        int cols = tagMode ? 1 : GRID_COLS;

        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                int x = startX + col * SLOT_SIZE;
                int y = startY + row * SLOT_SIZE;
                if (mx >= x && mx < x + 16 && my >= y && my < y + 16) {
                    int index = getSlotIndex(row, col, tagMode);
                    if (button == 0) { // 左键
                        if (!carried.isEmpty()) {
                            if (tagMode && collectEnhancedTags(carried).isEmpty()) {
                                return new GridClickResult(index, ClickAction.NONE);
                            }
                            return GridClickResult.setItem(index);
                        }
                    } else { // 右键
                        if (!carried.isEmpty()) {
                            var fluidHandler = carried.getCapability(Capabilities.FluidHandler.ITEM);
                            if (fluidHandler != null) {
                                FluidStack drained = fluidHandler.drain(1000,
                                    IFluidHandler.FluidAction.SIMULATE);
                                if (!drained.isEmpty()) {
                                    return GridClickResult.setFluid(index);
                                }
                            }
                        }
                        if (!provider.getFilterItem(index).isEmpty()
                            || provider.getFluidItem(index) != null) {
                            return GridClickResult.clear(index);
                        }
                    }
                }
            }
        }
        return null;
    }

    /**
     * 在 shift+右键 时，从库存槽位添加物品到过滤网格
     */
    public static int findFirstEmpty(int leftPos, int topPos, FilterSlotProvider provider,
                                     boolean tagMode, ItemStack carried) {
        if (carried.isEmpty()) return -1;
        if (tagMode && collectEnhancedTags(carried).isEmpty()) return -1;
        int rows = tagMode ? 4 : GRID_ROWS;
        int cols = tagMode ? 1 : GRID_COLS;
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                int index = getSlotIndex(row, col, tagMode);
                if (provider.getFilterItem(index).isEmpty()) return index;
            }
        }
        return -1;
    }

    public static List<TagKey<?>> collectEnhancedTags(ItemStack stack) {
        List<TagKey<?>> all = new ArrayList<>();
        if (stack.isEmpty()) return all;
        stack.getTags().forEach(tag -> all.add(tag));
        if (stack.getItem() instanceof BlockItem blockItem) {
            blockItem.getBlock().defaultBlockState().getTags().forEach(all::add);
        }
        var fluidHandler = stack.getCapability(Capabilities.FluidHandler.ITEM);
        if (fluidHandler != null) {
            for (int i = 0; i < fluidHandler.getTanks(); i++) {
                FluidStack fs = fluidHandler.getFluidInTank(i);
                if (!fs.isEmpty()) {
                    BuiltInRegistries.FLUID.wrapAsHolder(fs.getFluid()).tags().forEach(all::add);
                }
            }
        }
        // 去重
        return all.stream().distinct()
            .sorted(java.util.Comparator.comparing(a -> a.location().toString()))
            .toList();
    }
}
