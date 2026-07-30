package com.coobird.staticlogistics.client.gui.component;

import com.coobird.staticlogistics.client.render.SLGuiTextures;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 标签栏组件：过滤网格旁的标签下拉选择器。
 */
public class TagBarWidget {

    public static final int X_OFFSET = FilterGridWidget.START_X + FilterGridWidget.SLOT_SIZE + 4;
    public static final int WIDTH = 130;
    public static final int HEIGHT = 18;

    public enum TagType {ITEM, BLOCK, FLUID}

    public record EnhancedTagOption(TagKey<?> rawTag, TagType type) {
        public TagKey<Item> toItemTag() {
            return TagKey.create(Registries.ITEM, rawTag.location());
        }
    }

    /**
     * 一次渲染中下拉框的权威布局，点击与滚轮必须复用同一份坐标。
     */
    public record DropdownLayout(
        int row,
        int x,
        int y,
        int width,
        int height,
        int titleHeight,
        int lineHeight,
        int scrollOffset,
        int visibleCount
    ) {
        boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        }

        int optionAt(double mouseY) {
            int relativeY = (int) mouseY - y - titleHeight;
            if (relativeY < 0 || relativeY >= visibleCount * lineHeight) return -1;
            return scrollOffset + relativeY / lineHeight;
        }
    }


    public static class State {
        public final int[] selectedTagIndices = new int[4];
        @SuppressWarnings("unchecked")
        public final List<EnhancedTagOption>[] tagOptionsCache = new List[4];
        public int hoveredTagBarRow = -1;
        public DropdownLayout dropdownLayout;

        public State() {
            for (int i = 0; i < 4; i++) {
                selectedTagIndices[i] = -1;
                tagOptionsCache[i] = List.of();
            }
        }

        public void resetRow(int row) {
            selectedTagIndices[row] = -1;
            tagOptionsCache[row] = List.of();
            if (dropdownLayout != null && dropdownLayout.row() == row) dropdownLayout = null;
        }
    }

    public interface TagSlotAccess {
        Set<TagKey<Item>> getSlotTags(int row);

        Set<TagKey<Fluid>> getSlotFluidTags(int row);

        Set<TagKey<Item>> getExcludedTags(int row);

        Set<TagKey<Fluid>> getExcludedFluidTags(int row);

        void addSlotTag(int row, TagKey<Item> tag);

        void removeSlotTag(int row, TagKey<Item> tag);

        void addExcludedTag(int row, TagKey<Item> tag);

        void removeExcludedTag(int row, TagKey<Item> tag);

        void addSlotFluidTag(int row, TagKey<Fluid> tag);

        void removeSlotFluidTag(int row, TagKey<Fluid> tag);

        void addExcludedFluidTag(int row, TagKey<Fluid> tag);

        void removeExcludedFluidTag(int row, TagKey<Fluid> tag);

        void clearSlotTags(int row);

        void clearSlotFluidTags(int row);
    }

    public static void render(GuiGraphics g, Font font, int leftPos, int topPos,
                              int mx, int my, State state, TagSlotAccess access,
                              FilterGridWidget.FilterSlotProvider slotProvider, boolean tagMode) {
        int startX = leftPos + X_OFFSET;
        int startY = topPos + FilterGridWidget.START_Y;
        state.hoveredTagBarRow = -1;
        int hoveredDelButtonRow = -1;
        boolean overOpenDropdown = state.dropdownLayout != null
            && state.dropdownLayout.contains(mx, my);

        int btnW = SLGuiTextures.Button.Big.DISABLED_WIDTH;
        int btnU = SLGuiTextures.Button.Big.DISABLED_U;
        int btnV = SLGuiTextures.Button.Big.DISABLED_V;
        int barWidth = WIDTH;
        int barHeight = HEIGHT;

        for (int row = 0; row < 4; row++) {
            int y = startY + row * FilterGridWidget.SLOT_SIZE;

            g.blit(SLGuiTextures.GUI_ATLAS, startX, y, btnU, btnV,
                2, barHeight, SLGuiTextures.GUI_WIDTH, SLGuiTextures.GUI_HEIGHT);
            g.blit(SLGuiTextures.GUI_ATLAS, startX + barWidth - 2, y,
                btnU + btnW - 2, btnV, 2, barHeight,
                SLGuiTextures.GUI_WIDTH, SLGuiTextures.GUI_HEIGHT);
            g.blit(SLGuiTextures.GUI_ATLAS, startX + 2, y,
                barWidth - 4, barHeight, btnU + 2, btnV, 1, barHeight,
                SLGuiTextures.GUI_WIDTH, SLGuiTextures.GUI_HEIGHT);

            Set<TagKey<Item>> itemTags = access.getSlotTags(row);
            Set<TagKey<Fluid>> fluidTags = access.getSlotFluidTags(row);
            String displayText = "...";
            int maxTextWidth = barWidth - 4;
            if (!itemTags.isEmpty()) {
                String tagStr = itemTags.stream()
                    .map(t -> t.location().toString())
                    .collect(Collectors.joining("; "));
                displayText = font.plainSubstrByWidth(tagStr, maxTextWidth);
            } else if (!fluidTags.isEmpty()) {
                String tagStr = fluidTags.stream()
                    .map(t -> t.location().toString())
                    .collect(Collectors.joining("; "));
                displayText = font.plainSubstrByWidth(tagStr, maxTextWidth);
            }
            g.drawString(font, displayText, startX + 4, y + 4, 0xCCCCCC, false);

            // 删除按钮
            int delBtnX = startX + barWidth + 3;
            g.blit(SLGuiTextures.GUI_ATLAS, delBtnX, y,
                SLGuiTextures.Button.Middle.DISABLED_U, SLGuiTextures.Button.Middle.DISABLED_V,
                SLGuiTextures.Icon.WIDTH, SLGuiTextures.Button.Middle.HEIGHT,
                SLGuiTextures.GUI_WIDTH, SLGuiTextures.GUI_HEIGHT);
            g.blit(SLGuiTextures.GUI_ATLAS, delBtnX, y,
                SLGuiTextures.DeleteTag.U, SLGuiTextures.DeleteTag.V,
                SLGuiTextures.DeleteTag.WIDTH, SLGuiTextures.DeleteTag.HEIGHT,
                SLGuiTextures.GUI_WIDTH, SLGuiTextures.GUI_HEIGHT);

            if (!overOpenDropdown && mx >= startX && mx < startX + barWidth
                && my >= y && my < y + barHeight)
                state.hoveredTagBarRow = row;
            if (!overOpenDropdown
                && mx >= delBtnX && mx < delBtnX + SLGuiTextures.DeleteTag.WIDTH
                && my >= y && my < y + SLGuiTextures.Button.Middle.HEIGHT)
                hoveredDelButtonRow = row;
        }

        // 渲染下拉
        int activeRow = overOpenDropdown ? state.dropdownLayout.row() : state.hoveredTagBarRow;
        if (activeRow >= 0) {
            state.hoveredTagBarRow = activeRow;
            if (state.selectedTagIndices[activeRow] < 0) state.selectedTagIndices[activeRow] = 0;
            renderDropdown(g, font, leftPos, topPos, mx, my, state, access,
                slotProvider, tagMode, activeRow);
        } else {
            state.dropdownLayout = null;
        }

        if (hoveredDelButtonRow >= 0) {
            g.renderTooltip(font, Component.translatable("gui.staticlogistics.clear_tags"), mx, my);
        }
    }

    private static void renderDropdown(GuiGraphics g, Font font, int leftPos, int topPos,
                                       int mx, int my, State state, TagSlotAccess access,
                                       FilterGridWidget.FilterSlotProvider slotProvider,
                                       boolean tagMode, int row) {
        int index = FilterGridWidget.getSlotIndex(row, 0, tagMode);
        ItemStack ref = slotProvider.getFilterItem(index);
        Fluid fluid = null;
        List<EnhancedTagOption> options;
        String displayName;

        if (!ref.isEmpty()) {
            options = collectEnhancedTagOptions(ref);
            displayName = ref.getHoverName().getString();
        } else {
            fluid = slotProvider.getFluidItem(index);
            if (fluid == null) {
                state.dropdownLayout = null;
                return;
            }
            options = collectEnhancedTagOptionsForFluid(fluid);
            displayName = new FluidStack(fluid, 1000).getHoverName().getString();
        }
        state.tagOptionsCache[row] = options;
        if (options.isEmpty()) {
            state.dropdownLayout = null;
            return;
        }

        Set<TagKey<Item>> activeItemTags = access.getSlotTags(row);
        Set<TagKey<Item>> excludedItemTags = access.getExcludedTags(row);
        Set<TagKey<Fluid>> activeFluidTags = access.getSlotFluidTags(row);
        Set<TagKey<Fluid>> excludedFluidTags = access.getExcludedFluidTags(row);

        int maxVisible = 5;
        int lineHeight = 10;
        int titleHeight = 15;

        int scrollOffset;
        if (state.selectedTagIndices[row] >= 0) {
            int idealOffset = state.selectedTagIndices[row] - maxVisible / 2;
            scrollOffset = Math.max(0, Math.min(idealOffset, options.size() - maxVisible));
        } else {
            scrollOffset = 0;
        }

        List<String> visibleLines = new ArrayList<>();
        List<EnhancedTagOption> visibleOpts = new ArrayList<>();
        int maxLineWidth = 0;
        Minecraft mc = Minecraft.getInstance();
        int maxAllowedWidth = Math.max(92,
            Math.min(240, mc.getWindow().getGuiScaledWidth() - 16));

        for (int i = 0; i < maxVisible; i++) {
            int optIdx = scrollOffset + i;
            if (optIdx >= options.size()) break;
            EnhancedTagOption opt = options.get(optIdx);
            String typeStr = switch (opt.type) {
                case ITEM -> Component.translatable("tag_type.staticlogistics.item").getString();
                case BLOCK -> Component.translatable("tag_type.staticlogistics.block").getString();
                case FLUID -> Component.translatable("tag_type.staticlogistics.fluid").getString();
            };
            String tagName = getTagDisplayName(opt);
            boolean isActive, isExcluded;
            if (opt.type == TagType.ITEM || opt.type == TagType.BLOCK) {
                TagKey<Item> itemTag = opt.toItemTag();
                isActive = activeItemTags.contains(itemTag);
                isExcluded = excludedItemTags.contains(itemTag);
            } else {
                @SuppressWarnings("unchecked")
                TagKey<Fluid> fluidTag = (TagKey<Fluid>) opt.rawTag;
                isActive = activeFluidTags.contains(fluidTag);
                isExcluded = excludedFluidTags.contains(fluidTag);
            }
            String prefix;
            if (isActive) {
                prefix = Component.translatable("gui.staticlogistics.tag.active").getString() + " ";
            } else if (isExcluded) {
                prefix = Component.translatable("gui.staticlogistics.tag.excluded").getString() + " ";
            } else {
                prefix = "  ";
            }
            String line = prefix + typeStr + " " + tagName;
            int width = font.width(line);
            if (width > maxAllowedWidth) {
                line = font.plainSubstrByWidth(line, maxAllowedWidth - 4);
                width = font.width(line);
            }
            if (width > maxLineWidth) maxLineWidth = width;
            visibleLines.add(line);
            visibleOpts.add(opt);
        }

        List<Component> helpComponents = List.of(
            Component.translatable("gui.staticlogistics.tag_dropdown.help")
                .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC),
            Component.translatable("gui.staticlogistics.tag_dropdown.help2")
                .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC),
            Component.translatable("gui.staticlogistics.tag_dropdown.help3")
                .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        int maxHelpWidth = helpComponents.stream().mapToInt(font::width).max().orElse(0);
        int listWidth = calculateDropdownWidth(maxLineWidth + 20, font.width(displayName),
            maxHelpWidth, maxAllowedWidth);
        int contentWidth = listWidth - 8;
        List<FormattedCharSequence> helpLines = new ArrayList<>();
        helpComponents.forEach(component -> helpLines.addAll(font.split(component, contentWidth)));
        int hintHeight = helpLines.size() * 12 + 2;
        int listHeight = titleHeight + Math.min(options.size(), maxVisible) * lineHeight + hintHeight;

        // 定位下拉框
        int barX = leftPos + X_OFFSET;
        int barY = topPos + FilterGridWidget.START_Y + row * FilterGridWidget.SLOT_SIZE;
        int startX = barX;
        int startY = barY + HEIGHT;
        if (startX + listWidth > mc.getWindow().getGuiScaledWidth()) {
            startX = mc.getWindow().getGuiScaledWidth() - listWidth - 4;
        }
        if (startY + listHeight > mc.getWindow().getGuiScaledHeight()) startY = barY - listHeight;
        if (startX < 0) startX = 4;
        if (startY < 0) startY = 4;
        state.dropdownLayout = new DropdownLayout(row, startX, startY, listWidth, listHeight,
            titleHeight, lineHeight, scrollOffset, visibleLines.size());

        g.pose().pushPose();
        g.pose().translate(0, 0, 500);
        g.fill(startX, startY, startX + listWidth, startY + listHeight, 0xCC000000);
        g.renderOutline(startX, startY, listWidth, listHeight, 0xFFFFFFFF);
        g.drawString(font, font.plainSubstrByWidth(displayName, contentWidth),
            startX + 4, startY + 2, 0xFFD700, false);

        int yOffset = startY + titleHeight;
        for (int i = 0; i < visibleLines.size(); i++) {
            String line = visibleLines.get(i);
            int optIdx = scrollOffset + i;
            boolean highlighted = optIdx == state.selectedTagIndices[row];
            EnhancedTagOption opt = visibleOpts.get(i);
            boolean isActive, isExcluded;
            if (opt.type == TagType.ITEM || opt.type == TagType.BLOCK) {
                TagKey<Item> itemTag = opt.toItemTag();
                isActive = activeItemTags.contains(itemTag);
                isExcluded = excludedItemTags.contains(itemTag);
            } else {
                @SuppressWarnings("unchecked")
                TagKey<Fluid> fluidTag = (TagKey<Fluid>) opt.rawTag;
                isActive = activeFluidTags.contains(fluidTag);
                isExcluded = excludedFluidTags.contains(fluidTag);
            }
            int color;
            if (highlighted) color = 0xFFFF55;
            else if (isActive) color = 0x55FF55;
            else if (isExcluded) color = 0xFF5555;
            else color = 0xCCCCCC;
            g.drawString(font, line, startX + 4, yOffset + i * lineHeight, color, false);
        }

        int hintY = startY + titleHeight + Math.min(options.size(), maxVisible) * lineHeight;
        for (int i = 0; i < helpLines.size(); i++) {
            g.drawString(font, helpLines.get(i), startX + 4, hintY + i * 12, 0xAAAAAA, false);
        }
        g.pose().popPose();
    }

    static int calculateDropdownWidth(int optionWidth, int titleWidth,
                                      int helpWidth, int maxContentWidth) {
        int requiredContentWidth = Math.max(optionWidth, Math.max(titleWidth, helpWidth));
        return Math.min(maxContentWidth + 8, Math.max(100, requiredContentWidth + 8));
    }

    // 点击处理

    public static boolean handleClick(double mx, double my, int button,
                                      int leftPos, int topPos, State state,
                                      TagSlotAccess access,
                                      FilterGridWidget.FilterSlotProvider slotProvider,
                                      boolean tagMode, Runnable onChanged) {
        if (button != 0 && button != 1) return false;
        int startX = leftPos + X_OFFSET;
        int startY = topPos + FilterGridWidget.START_Y;

        DropdownLayout dropdown = state.dropdownLayout;
        if (dropdown != null && dropdown.contains(mx, my)) {
            List<EnhancedTagOption> options = state.tagOptionsCache[dropdown.row()];
            int optionIndex = dropdown.optionAt(my);
            if (optionIndex >= 0 && optionIndex < options.size()) {
                state.selectedTagIndices[dropdown.row()] = optionIndex;
                applyTagClick(options.get(optionIndex), dropdown.row(), button, access);
                onChanged.run();
            }
            return true;
        }

        for (int row = 0; row < 4; row++) {
            int y = startY + row * FilterGridWidget.SLOT_SIZE;
            int delBtnX = startX + WIDTH + 3;

            // 删除按钮
            if (mx >= delBtnX
                && mx < delBtnX + SLGuiTextures.DeleteTag.WIDTH
                && my >= y && my < y + SLGuiTextures.Button.Middle.HEIGHT) {
                if (button == 0) {
                    access.clearSlotTags(row);
                    access.clearSlotFluidTags(row);
                    state.resetRow(row);
                    onChanged.run();
                    return true;
                }
            }

            // 标签栏本身点击（使用选中项）
            if (mx >= startX && mx < startX + WIDTH
                && my >= y && my < y + HEIGHT) {
                List<EnhancedTagOption> optionsBar = state.tagOptionsCache[row];
                if (optionsBar == null || optionsBar.isEmpty()) {
                    int index = FilterGridWidget.getSlotIndex(row, 0, tagMode);
                    ItemStack ref = slotProvider.getFilterItem(index);
                    if (!ref.isEmpty()) {
                        optionsBar = collectEnhancedTagOptions(ref);
                        state.tagOptionsCache[row] = optionsBar;
                    } else {
                        Fluid fluid = slotProvider.getFluidItem(index);
                        if (fluid != null) {
                            optionsBar = collectEnhancedTagOptionsForFluid(fluid);
                            state.tagOptionsCache[row] = optionsBar;
                        }
                    }
                }
                if (optionsBar != null && !optionsBar.isEmpty()
                    && state.selectedTagIndices[row] >= 0
                    && state.selectedTagIndices[row] < optionsBar.size()) {
                    EnhancedTagOption opt = optionsBar.get(state.selectedTagIndices[row]);
                    applyTagClick(opt, row, button, access);
                    onChanged.run();
                    return true;
                }
                return true;
            }
        }
        return false;
    }

    private static void applyTagClick(EnhancedTagOption opt, int row, int button,
                                      TagSlotAccess access) {
        if (opt.type == TagType.ITEM || opt.type == TagType.BLOCK) {
            TagKey<Item> itemTag = opt.toItemTag();
            if (button == 0) {
                if (access.getSlotTags(row).contains(itemTag)) {
                    access.removeSlotTag(row, itemTag);
                } else {
                    access.addSlotTag(row, itemTag);
                    access.removeExcludedTag(row, itemTag);
                }
            } else {
                if (access.getExcludedTags(row).contains(itemTag)) {
                    access.removeExcludedTag(row, itemTag);
                } else {
                    access.addExcludedTag(row, itemTag);
                    access.removeSlotTag(row, itemTag);
                }
            }
        } else {
            @SuppressWarnings("unchecked")
            TagKey<Fluid> fluidTag = (TagKey<Fluid>) opt.rawTag;
            if (button == 0) {
                if (access.getSlotFluidTags(row).contains(fluidTag)) {
                    access.removeSlotFluidTag(row, fluidTag);
                } else {
                    access.addSlotFluidTag(row, fluidTag);
                    access.removeExcludedFluidTag(row, fluidTag);
                }
            } else {
                if (access.getExcludedFluidTags(row).contains(fluidTag)) {
                    access.removeExcludedFluidTag(row, fluidTag);
                } else {
                    access.addExcludedFluidTag(row, fluidTag);
                    access.removeSlotFluidTag(row, fluidTag);
                }
            }
        }
    }

    // 滚动
    public static boolean handleScroll(double scrollY, State state, int row) {
        List<EnhancedTagOption> options = state.tagOptionsCache[row];
        if (options != null && !options.isEmpty()) {
            int size = options.size();
            int prev = state.selectedTagIndices[row];
            int delta = (int) Math.signum(scrollY);
            int newIdx = prev - delta;
            if (newIdx < 0) newIdx = -1;
            if (newIdx >= size) newIdx = size - 1;
            state.selectedTagIndices[row] = newIdx;
            return true;
        }
        return false;
    }

    // 标签名称
    public static String getTagDisplayName(EnhancedTagOption opt) {
        String prefix = switch (opt.type) {
            case ITEM -> "tags.item";
            case BLOCK -> "tags.block";
            case FLUID -> "tags.fluid";
        };
        String key = opt.rawTag.location().toLanguageKey(prefix);
        Component translated = Component.translatable(key);
        String text = translated.getString();
        return key.equals(text) ? opt.rawTag.location().toString() : text;
    }

    // 收集标签选项
    public static List<EnhancedTagOption> collectEnhancedTagOptions(ItemStack stack) {
        if (stack.isEmpty()) return List.of();
        List<EnhancedTagOption> all = new ArrayList<>();
        stack.getTags().forEach(tag -> all.add(new EnhancedTagOption(tag, TagType.ITEM)));
        if (stack.getItem() instanceof BlockItem blockItem) {
            blockItem.getBlock().defaultBlockState().getTags()
                .forEach(tag -> all.add(new EnhancedTagOption(tag, TagType.BLOCK)));
        }
        var fluidHandler = stack.getCapability(
            Capabilities.FluidHandler.ITEM);
        if (fluidHandler != null) {
            for (int i = 0; i < fluidHandler.getTanks(); i++) {
                FluidStack fs = fluidHandler.getFluidInTank(i);
                if (!fs.isEmpty()) {
                    BuiltInRegistries.FLUID.wrapAsHolder(fs.getFluid()).tags()
                        .forEach(tag -> all.add(new EnhancedTagOption(tag, TagType.FLUID)));
                }
            }
        }
        Map<ResourceLocation, EnhancedTagOption> unique = new LinkedHashMap<>();
        for (EnhancedTagOption opt : all) {
            unique.putIfAbsent(opt.rawTag.location(), opt);
        }
        List<EnhancedTagOption> result = new ArrayList<>(unique.values());
        result.sort(Comparator.comparing(a -> a.rawTag.location().toString()));
        return result;
    }

    public static List<EnhancedTagOption> collectEnhancedTagOptionsForFluid(Fluid fluid) {
        if (fluid == null) return List.of();
        List<EnhancedTagOption> all = new ArrayList<>();
        BuiltInRegistries.FLUID.wrapAsHolder(fluid).tags()
            .forEach(tag -> all.add(new EnhancedTagOption(tag, TagType.FLUID)));
        Map<ResourceLocation, EnhancedTagOption> unique = new LinkedHashMap<>();
        for (EnhancedTagOption opt : all) {
            unique.putIfAbsent(opt.rawTag.location(), opt);
        }
        List<EnhancedTagOption> result = new ArrayList<>(unique.values());
        result.sort(Comparator.comparing(a -> a.rawTag.location().toString()));
        return result;
    }

    /**
     * 检查是否有已选标签，若槽位为空则清空标签
     */
    public static void clearOrphanedSlotTags(TagSlotAccess access,
                                             FilterGridWidget.FilterSlotProvider provider,
                                             boolean tagMode, Runnable onChanged) {
        for (int row = 0; row < 4; row++) {
            int index = FilterGridWidget.getSlotIndex(row, 0, tagMode);
            boolean hasItem = !provider.getFilterItem(index).isEmpty();
            boolean hasFluid = provider.getFluidItem(index) != null;
            if (!hasItem && !hasFluid) {
                if (!access.getSlotTags(row).isEmpty()
                    || !access.getExcludedTags(row).isEmpty()
                    || !access.getSlotFluidTags(row).isEmpty()
                    || !access.getExcludedFluidTags(row).isEmpty()) {
                    access.clearSlotTags(row);
                    access.clearSlotFluidTags(row);
                    onChanged.run();
                }
            }
        }
    }
}
