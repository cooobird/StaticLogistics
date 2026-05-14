package com.coobird.staticlogistics.gui.screen;

import com.coobird.staticlogistics.api.type.NbtMatchMode;
import com.coobird.staticlogistics.api.type.TransferType;
import com.coobird.staticlogistics.api.type.UpgradeType;
import com.coobird.staticlogistics.gui.menu.AbstractFilterMenu;
import com.coobird.staticlogistics.gui.screen.texture.SLGuiTextures;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Inventory;
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

import java.util.*;
import java.util.stream.Collectors;

public abstract class BaseFilterScreen<T extends AbstractFilterMenu> extends AbstractConfiguratorScreen<T> {

    protected static final int GRID_COLS = 9;
    protected static final int GRID_ROWS = 4;
    protected static final int GRID_START_X = 23;
    protected static final int GRID_START_Y = 20;
    protected static final int SLOT_SIZE = 18;

    protected static final int BLACKLIST_BTN_WIDTH = 60;
    protected static final int BLACKLIST_BTN_HEIGHT = SLGuiTextures.Button.Big.DISABLED_HEIGHT;

    private static final int TAG_BAR_X_OFFSET = GRID_START_X + SLOT_SIZE + 4;
    private static final int TAG_BAR_WIDTH = 130;
    private static final int TAG_BAR_HEIGHT = 18;

    private NbtMatchMode hoveredNbtMode = null;

    private int hoveredTagBarRow = -1;
    private final int[] selectedTagIndices = new int[4];
    @SuppressWarnings("unchecked")
    private final List<EnhancedTagOption>[] tagOptionsCache = new List[4];

    private enum TagType {ITEM, BLOCK, FLUID}

    private record EnhancedTagOption(TagKey<?> rawTag, TagType type) {
        TagKey<Item> toItemTag() {
            return TagKey.create(Registries.ITEM, rawTag.location());
        }
    }

    public BaseFilterScreen(T menu, Inventory inv, Component title) {
        super(menu, inv, title);
        for (int i = 0; i < 4; i++) {
            selectedTagIndices[i] = -1;
            tagOptionsCache[i] = List.of();
        }
    }

    @Override
    protected boolean shouldShowTypePanel() {
        return false;
    }

    @Override
    protected String getSearchHintKey() {
        return "";
    }

    @Override
    protected List<TransferType> getTypeList() {
        return List.of();
    }

    @Override
    protected int getSelectedTypesMask() {
        return 0;
    }

    @Override
    protected void renderTypeListItem(GuiGraphics g, TransferType type, int x, int y, boolean isSelected) {
    }

    @Override
    protected void onTypeClicked(TransferType type) {
    }

    private int getSlotIndex(int row, int col) {
        if (menu.getActiveUpgradeType() == UpgradeType.TAG_FILTER) {
            return row;
        } else {
            return row * GRID_COLS + col;
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mx, int my, float pt) {
        this.hoveredNbtMode = null;
        super.render(graphics, mx, my, pt);
        renderGridTooltips(graphics, mx, my);
        if (this.hoveredNbtMode != null) {
            graphics.renderTooltip(this.font,
                Component.translatable("tooltip.staticlogistics.nbt_mode." + this.hoveredNbtMode.name().toLowerCase()), mx, my);
        }
        this.renderTooltip(graphics, mx, my);
    }

    private void renderGridTooltips(GuiGraphics g, int mx, int my) {
        int startX = leftPos + GRID_START_X;
        int startY = topPos + GRID_START_Y;
        boolean tagMode = menu.getActiveUpgradeType() == UpgradeType.TAG_FILTER;
        int rows = tagMode ? 4 : GRID_ROWS;
        int cols = tagMode ? 1 : GRID_COLS;

        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                int x = startX + col * SLOT_SIZE;
                int y = startY + row * SLOT_SIZE;
                if (mx >= x && mx < x + 16 && my >= y && my < y + 16) {
                    int index = getSlotIndex(row, col);

                    Fluid fluid = getFluidItem(index);
                    if (fluid != null) {
                        FluidStack fluidStack = new FluidStack(fluid, 1000);
                        Component name = fluidStack.getHoverName();
                        List<Component> tooltip = new ArrayList<>();
                        tooltip.add(name);
                        if (minecraft != null && minecraft.options.advancedItemTooltips) {
                            tooltip.add(Component.literal(BuiltInRegistries.FLUID.getKey(fluid).toString()).withStyle(ChatFormatting.DARK_GRAY));
                        }
                        g.renderComponentTooltip(this.font, tooltip, mx, my);
                        return;
                    }

                    ItemStack stack = getFilterItem(index);
                    if (!stack.isEmpty()) {
                        TooltipFlag flag = minecraft.options.advancedItemTooltips ? TooltipFlag.ADVANCED : TooltipFlag.NORMAL;
                        List<Component> tooltip = stack.getTooltipLines(Item.TooltipContext.of(minecraft.level), minecraft.player, flag);
                        g.renderComponentTooltip(this.font, tooltip, mx, my);
                        return;
                    }
                }
            }
        }
    }

    protected void renderFilterGrid(GuiGraphics g) {
        int startX = leftPos + GRID_START_X;
        int startY = topPos + GRID_START_Y;
        Minecraft mc = Minecraft.getInstance();
        boolean tagMode = menu.getActiveUpgradeType() == UpgradeType.TAG_FILTER;
        int rows = tagMode ? 4 : GRID_ROWS;
        int cols = tagMode ? 1 : GRID_COLS;

        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                int index = getSlotIndex(row, col);
                int x = startX + col * SLOT_SIZE;
                int y = startY + row * SLOT_SIZE;

                g.blit(SLGuiTextures.GUI_ATLAS, x, y,
                    SLGuiTextures.Inventory.SLOT_U, SLGuiTextures.Inventory.SLOT_V,
                    SLGuiTextures.Inventory.SLOT_WIDTH, SLGuiTextures.Inventory.SLOT_HEIGHT,
                    SLGuiTextures.GUI_WIDTH, SLGuiTextures.GUI_HEIGHT);

                Fluid fluid = getFluidItem(index);
                if (fluid != null) {
                    IClientFluidTypeExtensions clientExtensions = IClientFluidTypeExtensions.of(fluid);
                    ResourceLocation stillTexture = clientExtensions.getStillTexture();
                    TextureAtlasSprite sprite = mc.getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(stillTexture);

                    int tintColor = clientExtensions.getTintColor();
                    float r = ((tintColor >> 16) & 0xFF) / 255.0f;
                    float gco = ((tintColor >> 8) & 0xFF) / 255.0f;
                    float b = (tintColor & 0xFF) / 255.0f;
                    float a = ((tintColor >> 24) & 0xFF) / 255.0f;

                    g.setColor(r, gco, b, a);
                    RenderSystem.setShaderTexture(0, InventoryMenu.BLOCK_ATLAS);
                    g.blit(x + 1, y + 1, 0, 16, 16, sprite);
                    g.setColor(1.0f, 1.0f, 1.0f, 1.0f);
                    RenderSystem.enableBlend();
                    RenderSystem.defaultBlendFunc();
                } else {
                    ItemStack stack = getFilterItem(index);
                    if (!stack.isEmpty()) {
                        g.renderFakeItem(stack, x + 1, y + 1);
                    }
                }
            }
        }
    }

    protected int getBlacklistButtonXOffset() {
        return 0;
    }

    protected int getBlacklistButtonX() {
        return leftPos + GRID_START_X + (GRID_COLS * SLOT_SIZE - BLACKLIST_BTN_WIDTH) / 2 + getBlacklistButtonXOffset();
    }

    protected int getBlacklistButtonY() {
        return topPos + GRID_START_Y + GRID_ROWS * SLOT_SIZE + 6;
    }

    protected void renderBlacklistButton(GuiGraphics g, int mx, int my) {
        boolean isBlacklist = isBlacklistMode();
        int btnX = getBlacklistButtonX();
        int btnY = getBlacklistButtonY();

        int u = SLGuiTextures.Button.Big.DISABLED_U;
        int v = SLGuiTextures.Button.Big.DISABLED_V;
        int bw = SLGuiTextures.Button.Big.DISABLED_WIDTH;

        g.blit(SLGuiTextures.GUI_ATLAS,
            btnX, btnY, u, v,
            2, BLACKLIST_BTN_HEIGHT,
            SLGuiTextures.GUI_WIDTH, SLGuiTextures.GUI_HEIGHT);
        g.blit(SLGuiTextures.GUI_ATLAS,
            btnX + BLACKLIST_BTN_WIDTH - 2, btnY,
            u + bw - 2, v,
            2, BLACKLIST_BTN_HEIGHT,
            SLGuiTextures.GUI_WIDTH, SLGuiTextures.GUI_HEIGHT);
        g.blit(SLGuiTextures.GUI_ATLAS,
            btnX + 2, btnY,
            BLACKLIST_BTN_WIDTH - 4, BLACKLIST_BTN_HEIGHT,
            u + 2, v,
            1, BLACKLIST_BTN_HEIGHT,
            SLGuiTextures.GUI_WIDTH, SLGuiTextures.GUI_HEIGHT);

        String textKey = isBlacklist ? "gui.staticlogistics.blacklist_button" : "gui.staticlogistics.whitelist_button";
        String textText = Component.translatable(textKey).getString();
        boolean hover = mx >= btnX && mx < btnX + BLACKLIST_BTN_WIDTH && my >= btnY && my < btnY + BLACKLIST_BTN_HEIGHT;
        int color = hover ? 0xFFFF55 : 0xCCCCCC;
        int textWidth = this.font.width(textText);
        g.drawString(this.font, textText,
            btnX + (BLACKLIST_BTN_WIDTH - textWidth) / 2,
            btnY + (BLACKLIST_BTN_HEIGHT - 12) / 2,
            color, false);
    }

    protected void renderNbtModeButtons(GuiGraphics g, int mx, int my) {
        NbtMatchMode current = menu.getNbtMatchMode();
        int btnX = leftPos + 23;
        int btnY = topPos + 98;

        int iconW = SLGuiTextures.NbtIcon.WIDTH;
        int iconH = SLGuiTextures.NbtIcon.HEIGHT;
        int bgDisabledW = SLGuiTextures.Button.Middle.DISABLED_WIDTH;
        int bgDisabledH = SLGuiTextures.Button.Middle.DISABLED_HEIGHT;
        int bgChosenW = SLGuiTextures.Button.Middle.SELECTED_WIDTH;
        int bgChosenH = SLGuiTextures.Button.Middle.SELECTED_HEIGHT;

        for (int i = 0; i < 2; i++) {
            NbtMatchMode mode = NbtMatchMode.values()[i];
            boolean selected = current == mode;
            boolean hover = mx >= btnX && mx < btnX + bgDisabledW && my >= btnY && my < btnY + bgDisabledH;
            if (hover) this.hoveredNbtMode = mode;

            int bgU = selected ? SLGuiTextures.Button.Middle.SELECTED_U : SLGuiTextures.Button.Middle.DISABLED_U;
            int bgV = selected ? SLGuiTextures.Button.Middle.SELECTED_V : SLGuiTextures.Button.Middle.DISABLED_V;
            int bgW = selected ? bgChosenW : bgDisabledW;
            int bgH = selected ? bgChosenH : bgDisabledH;

            int drawX = selected ? btnX - (bgChosenW - bgDisabledW) / 2 : btnX;
            int drawY = selected ? btnY - (bgChosenH - bgDisabledH) / 2 : btnY;
            g.blit(SLGuiTextures.GUI_ATLAS, drawX, drawY, bgU, bgV, bgW, bgH,
                SLGuiTextures.GUI_WIDTH, SLGuiTextures.GUI_HEIGHT);

            int iconU, iconV;
            if (selected) {
                if (i == 0) {
                    iconU = SLGuiTextures.NbtIcon.PART_MATCH_ENABLED_U;
                    iconV = SLGuiTextures.NbtIcon.PART_MATCH_ENABLED_V;
                } else {
                    iconU = SLGuiTextures.NbtIcon.FULL_MATCH_ENABLED_U;
                    iconV = SLGuiTextures.NbtIcon.FULL_MATCH_ENABLED_V;
                }
            } else {
                if (i == 0) {
                    iconU = SLGuiTextures.NbtIcon.PART_MATCH_DISABLED_U;
                    iconV = SLGuiTextures.NbtIcon.PART_MATCH_DISABLED_V;
                } else {
                    iconU = SLGuiTextures.NbtIcon.FULL_MATCH_DISABLED_U;
                    iconV = SLGuiTextures.NbtIcon.FULL_MATCH_DISABLED_V;
                }
            }

            int iconDrawX = drawX + (bgW - iconW) / 2;
            int iconDrawY = drawY + (bgH - iconH) / 3;
            g.blit(SLGuiTextures.GUI_ATLAS, iconDrawX, iconDrawY, iconU, iconV, iconW, iconH,
                SLGuiTextures.GUI_WIDTH, SLGuiTextures.GUI_HEIGHT);
            btnX += bgDisabledW + 3;
        }
    }

    protected boolean handleNbtModeClick(double mx, double my) {
        if (menu.getActiveUpgradeType() != UpgradeType.NBT_FILTER) return false;

        int btnX = leftPos + 23;
        int btnY = topPos + 98;
        int btnW = SLGuiTextures.Button.Middle.DISABLED_WIDTH;
        int btnH = SLGuiTextures.Button.Middle.DISABLED_HEIGHT;

        for (int i = 0; i < 2; i++) {
            if (mx >= btnX && mx < btnX + btnW && my >= btnY && my < btnY + btnH) {
                NbtMatchMode newMode = NbtMatchMode.values()[i];
                if (menu.getNbtMatchMode() != newMode) {
                    menu.setNbtMatchMode(newMode);
                    sendFilterUpdate();
                }
                return true;
            }
            btnX += btnW + 3;
        }
        return false;
    }

    private void clearOrphanedSlotTags() {
        for (int row = 0; row < 4; row++) {
            int index = getSlotIndex(row, 0);
            if (getFilterItem(index).isEmpty() && (!menu.getSlotTags(row).isEmpty() || !menu.getExcludedTags(row).isEmpty())) {
                menu.clearSlotTags(row);
                sendFilterUpdate();
            }
        }
    }

    protected void renderTagBars(GuiGraphics g, int mouseX, int mouseY) {
        clearOrphanedSlotTags();
        int startX = leftPos + TAG_BAR_X_OFFSET;
        int startY = topPos + GRID_START_Y;
        int btnW = SLGuiTextures.Button.Big.DISABLED_WIDTH;
        int btnU = SLGuiTextures.Button.Big.DISABLED_U;
        int btnV = SLGuiTextures.Button.Big.DISABLED_V;
        int barWidth = TAG_BAR_WIDTH;
        int barHeight = TAG_BAR_HEIGHT;

        hoveredTagBarRow = -1;
        int hoveredDelButtonRow = -1;

        for (int row = 0; row < 4; row++) {
            int y = startY + row * SLOT_SIZE;

            g.blit(SLGuiTextures.GUI_ATLAS, startX, y, btnU, btnV, 2, barHeight, 512, 512);
            g.blit(SLGuiTextures.GUI_ATLAS, startX + barWidth - 2, y, btnU + btnW - 2, btnV, 2, barHeight, 512, 512);
            g.blit(SLGuiTextures.GUI_ATLAS, startX + 2, y, barWidth - 4, barHeight, btnU + 2, btnV, 1, barHeight, 512, 512);

            Set<TagKey<Item>> tags = menu.getSlotTags(row);
            String displayText = "...";
            int maxTextWidth = barWidth - 4;
            if (!tags.isEmpty()) {
                String tagStr = tags.stream().map(t -> t.location().toString()).collect(Collectors.joining("; "));
                displayText = font.plainSubstrByWidth(tagStr, maxTextWidth);
            }
            g.drawString(font, displayText, startX + 4, y + 4, 0xCCCCCC, false);

            int delBtnX = startX + barWidth + 3;
            g.blit(SLGuiTextures.GUI_ATLAS, delBtnX, y,
                SLGuiTextures.Button.Middle.DISABLED_U, SLGuiTextures.Button.Middle.DISABLED_V,
                19, 17, SLGuiTextures.GUI_WIDTH, SLGuiTextures.GUI_HEIGHT);
            g.blit(SLGuiTextures.GUI_ATLAS, delBtnX, y,
                SLGuiTextures.DeleteTag.U, SLGuiTextures.DeleteTag.V,
                19, 15, SLGuiTextures.GUI_WIDTH, SLGuiTextures.GUI_HEIGHT);

            if (mouseX >= startX && mouseX < startX + barWidth && mouseY >= y && mouseY < y + barHeight)
                hoveredTagBarRow = row;
            if (mouseX >= delBtnX && mouseX < delBtnX + 19 && mouseY >= y && mouseY < y + 17)
                hoveredDelButtonRow = row;
        }

        if (hoveredTagBarRow >= 0) {
            if (selectedTagIndices[hoveredTagBarRow] < 0) selectedTagIndices[hoveredTagBarRow] = 0;
            renderTagDropdown(g, mouseX, mouseY, hoveredTagBarRow);
        }

        if (hoveredDelButtonRow >= 0) {
            g.renderTooltip(font, Component.translatable("gui.staticlogistics.clear_tags"), mouseX, mouseY);
        }
    }

    private void resetRowCache(int row) {
        selectedTagIndices[row] = -1;
        tagOptionsCache[row] = List.of();
    }

    private List<EnhancedTagOption> collectEnhancedTags(ItemStack stack) {
        if (stack.isEmpty()) return List.of();
        List<EnhancedTagOption> all = new ArrayList<>();
        stack.getTags().forEach(tag -> all.add(new EnhancedTagOption(tag, TagType.ITEM)));
        if (stack.getItem() instanceof BlockItem blockItem) {
            blockItem.getBlock().defaultBlockState().getTags()
                .forEach(tag -> all.add(new EnhancedTagOption(tag, TagType.BLOCK)));
        }
        var fluidHandler = stack.getCapability(Capabilities.FluidHandler.ITEM);
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

    private List<String> buildTagDisplayLines(List<EnhancedTagOption> options) {
        List<String> lines = new ArrayList<>();
        for (EnhancedTagOption opt : options) {
            String typeStr = switch (opt.type) {
                case ITEM -> Component.translatable("tag_type.staticlogistics.item").getString();
                case BLOCK -> Component.translatable("tag_type.staticlogistics.block").getString();
                case FLUID -> Component.translatable("tag_type.staticlogistics.fluid").getString();
            };
            String tagName = getTagDisplayName(opt.rawTag, opt.type);
            lines.add("-> " + typeStr + " " + tagName);
        }
        return lines;
    }

    private String getTagDisplayName(TagKey<?> tag, TagType type) {
        String prefix = switch (type) {
            case ITEM -> "tags.item";
            case BLOCK -> "tags.block";
            case FLUID -> "tags.fluid";
        };
        String translationKey = tag.location().toLanguageKey(prefix);
        Component translated = Component.translatable(translationKey);
        String text = translated.getString();
        if (text.equals(translationKey)) {
            return tag.location().toString();
        }
        return text;
    }

    private void renderTagDropdown(GuiGraphics g, int mouseX, int mouseY, int row) {
        int index = getSlotIndex(row, 0);
        ItemStack ref = getFilterItem(index);
        if (ref.isEmpty()) return;

        List<EnhancedTagOption> options = collectEnhancedTags(ref);
        tagOptionsCache[row] = options;
        if (options.isEmpty()) return;

        String itemName = ref.getHoverName().getString();
        int maxVisible = 5;
        int lineHeight = 10;
        int titleHeight = 15;
        int hintHeight = 38;

        List<String> displayLines = buildTagDisplayLines(options);
        int maxLineWidth = 0;
        int maxAllowedWidth = 240;
        for (int i = 0; i < displayLines.size(); i++) {
            String line = displayLines.get(i);
            int width = font.width(line);
            if (width > maxAllowedWidth) {
                line = font.plainSubstrByWidth(line, maxAllowedWidth - 12);
                displayLines.set(i, line);
                width = font.width(line);
            }
            if (width > maxLineWidth) maxLineWidth = width;
        }
        int listWidth = Math.max(100, maxLineWidth + 28);
        int listHeight = titleHeight + Math.min(options.size(), maxVisible) * lineHeight + hintHeight;

        int startX = mouseX + 12;
        int startY = mouseY - 13;
        if (startX + listWidth > this.width) startX = mouseX - listWidth - 4;
        if (startY + listHeight > this.height) startY = this.height - listHeight - 5;
        if (startX < 0) startX = 4;
        if (startY < 0) startY = 4;

        g.pose().pushPose();
        g.pose().translate(0, 0, 500);
        g.fill(startX, startY, startX + listWidth, startY + listHeight, 0xCC000000);
        g.renderOutline(startX, startY, listWidth, listHeight, 0xFFFFFFFF);
        g.drawString(font, itemName, startX + 4, startY + 2, 0xFFD700, false);

        int yOffset = startY + titleHeight;
        int scrollOffset = Math.max(0, Math.min(selectedTagIndices[row] - maxVisible + 1, options.size() - maxVisible));
        if (selectedTagIndices[row] < 0) scrollOffset = 0;

        Set<TagKey<Item>> activeTags = menu.getSlotTags(row);
        Set<TagKey<Item>> excludedTags = menu.getExcludedTags(row);
        for (int i = 0; i < maxVisible; i++) {
            int optIdx = scrollOffset + i;
            if (optIdx >= options.size()) break;
            String line = displayLines.get(optIdx);
            boolean highlighted = optIdx == selectedTagIndices[row];
            TagKey<Item> itemTag = options.get(optIdx).toItemTag();
            boolean isActive = activeTags.contains(itemTag);
            boolean isExcluded = excludedTags.contains(itemTag);

            int color;
            if (highlighted) {
                color = 0xFFFF55;
            } else if (isActive) {
                color = 0x55FF55;
            } else if (isExcluded) {
                color = 0xFF5555;
            } else {
                color = 0xCCCCCC;
            }
            g.drawString(font, line, startX + 4, yOffset + i * lineHeight, color, false);
        }

        int hintY = startY + titleHeight + Math.min(options.size(), maxVisible) * lineHeight;
        g.drawString(font, Component.translatable("gui.staticlogistics.tag_dropdown.help")
                .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC),
            startX + 4, hintY, 0xAAAAAA, false);
        g.drawString(font, Component.translatable("gui.staticlogistics.tag_dropdown.help2")
                .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC),
            startX + 4, hintY + 12, 0xAAAAAA, false);
        g.drawString(font, Component.translatable("gui.staticlogistics.tag_dropdown.help3")
                .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC),
            startX + 4, hintY + 24, 0xAAAAAA, false);
        g.pose().popPose();
    }

    protected boolean handleTagBarClick(double mx, double my, int button) {
        if (button != 0 && button != 1) return false;
        int startX = leftPos + TAG_BAR_X_OFFSET;
        int startY = topPos + GRID_START_Y;

        for (int row = 0; row < 4; row++) {
            int y = startY + row * SLOT_SIZE;
            int delBtnX = startX + TAG_BAR_WIDTH + 3;

            if (mx >= delBtnX && mx < delBtnX + 19 && my >= y && my < y + 17) {
                if (button == 0) {
                    menu.clearSlotTags(row);
                    sendFilterUpdate();
                    resetRowCache(row);
                    return true;
                }
            }

            int dropdownStartY = y + TAG_BAR_HEIGHT;
            int maxVisible = 5;
            List<EnhancedTagOption> options = tagOptionsCache[row];
            if (options != null && !options.isEmpty()) {
                int titleHeight = 15;
                int listHeight = titleHeight + Math.min(options.size(), maxVisible) * 10;
                if (mx >= startX && mx < startX + TAG_BAR_WIDTH && my >= dropdownStartY && my < dropdownStartY + listHeight) {
                    int relY = (int) (my - dropdownStartY - titleHeight);
                    if (relY < 0) return true;
                    int itemIdx = relY / 10;
                    int scrollOffset = Math.max(0, Math.min(selectedTagIndices[row] - maxVisible + 1, options.size() - maxVisible));
                    if (selectedTagIndices[row] < 0) scrollOffset = 0;
                    int actualIdx = scrollOffset + itemIdx;
                    if (actualIdx >= 0 && actualIdx < options.size()) {
                        TagKey<Item> itemTag = options.get(actualIdx).toItemTag();
                        if (button == 0) {
                            if (menu.getSlotTags(row).contains(itemTag)) {
                                menu.removeSlotTag(row, itemTag);
                            } else {
                                menu.addSlotTag(row, itemTag);
                                if (menu.getExcludedTags(row).contains(itemTag)) {
                                    menu.removeExcludedTag(row, itemTag);
                                }
                            }
                        } else {
                            if (menu.getExcludedTags(row).contains(itemTag)) {
                                menu.removeExcludedTag(row, itemTag);
                            } else {
                                menu.addExcludedTag(row, itemTag);
                                if (menu.getSlotTags(row).contains(itemTag)) {
                                    menu.removeSlotTag(row, itemTag);
                                }
                            }
                        }
                        sendFilterUpdate();
                        return true;
                    }
                    return true;
                }
            }

            if (mx >= startX && mx < startX + TAG_BAR_WIDTH && my >= y && my < y + TAG_BAR_HEIGHT) {
                List<EnhancedTagOption> optionsBar = tagOptionsCache[row];
                if (optionsBar == null || optionsBar.isEmpty()) {
                    int index = getSlotIndex(row, 0);
                    ItemStack ref = getFilterItem(index);
                    if (!ref.isEmpty()) {
                        optionsBar = collectEnhancedTags(ref);
                        tagOptionsCache[row] = optionsBar;
                    }
                }
                if (optionsBar != null && !optionsBar.isEmpty() && selectedTagIndices[row] >= 0 && selectedTagIndices[row] < optionsBar.size()) {
                    TagKey<Item> itemTag = optionsBar.get(selectedTagIndices[row]).toItemTag();
                    if (button == 0) {
                        if (menu.getSlotTags(row).contains(itemTag)) {
                            menu.removeSlotTag(row, itemTag);
                        } else {
                            menu.addSlotTag(row, itemTag);
                            if (menu.getExcludedTags(row).contains(itemTag)) {
                                menu.removeExcludedTag(row, itemTag);
                            }
                        }
                    } else {
                        if (menu.getExcludedTags(row).contains(itemTag)) {
                            menu.removeExcludedTag(row, itemTag);
                        } else {
                            menu.addExcludedTag(row, itemTag);
                            if (menu.getSlotTags(row).contains(itemTag)) {
                                menu.removeSlotTag(row, itemTag);
                            }
                        }
                    }
                    sendFilterUpdate();
                    return true;
                }
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        if (menu.getActiveUpgradeType() == UpgradeType.TAG_FILTER && hoveredTagBarRow >= 0) {
            List<EnhancedTagOption> options = tagOptionsCache[hoveredTagBarRow];
            if (options != null && !options.isEmpty()) {
                int size = options.size();
                int prev = selectedTagIndices[hoveredTagBarRow];
                int delta = (int) Math.signum(scrollY);
                int newIdx = prev - delta;
                if (newIdx < -1) newIdx = size - 1;
                if (newIdx >= size) newIdx = -1;
                selectedTagIndices[hoveredTagBarRow] = newIdx;
                return true;
            }
        }
        return super.mouseScrolled(mx, my, scrollX, scrollY);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int btnX = getBlacklistButtonX();
        int btnY = getBlacklistButtonY();
        if (mx >= btnX && mx < btnX + BLACKLIST_BTN_WIDTH && my >= btnY && my < btnY + BLACKLIST_BTN_HEIGHT) {
            boolean current = isBlacklistMode();
            setBlacklistMode(!current);
            sendFilterUpdate();
            return true;
        }
        if (menu.getActiveUpgradeType() == UpgradeType.TAG_FILTER) {
            if (handleTagBarClick(mx, my, button)) return true;
        }

        int startX = leftPos + GRID_START_X;
        int startY = topPos + GRID_START_Y;
        int cols = menu.getActiveUpgradeType() == UpgradeType.TAG_FILTER ? 1 : GRID_COLS;
        int rows = menu.getActiveUpgradeType() == UpgradeType.TAG_FILTER ? 4 : GRID_ROWS;

        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                int x = startX + col * SLOT_SIZE;
                int y = startY + row * SLOT_SIZE;
                if (mx >= x && mx < x + 16 && my >= y && my < y + 16) {
                    int index = getSlotIndex(row, col);
                    ItemStack carried = menu.getCarried();
                    if (button == 0) {
                        if (!carried.isEmpty()) {
                            if (menu.getActiveUpgradeType() == UpgradeType.TAG_FILTER && collectEnhancedTags(carried).isEmpty()) {
                                return true;
                            }
                            setFilterItem(index, carried.copy());
                            sendFilterUpdate();
                            resetRowCache(row);
                            return true;
                        }
                    } else {
                        if (!carried.isEmpty()) {
                            var fluidHandler = carried.getCapability(Capabilities.FluidHandler.ITEM);
                            if (fluidHandler != null) {
                                FluidStack drained = fluidHandler.drain(1000, IFluidHandler.FluidAction.SIMULATE);
                                if (!drained.isEmpty()) {
                                    setFluidSlot(index, drained.getFluid());
                                    sendFilterUpdate();
                                    resetRowCache(row);
                                    return true;
                                }
                            }
                        }
                        ItemStack existing = getFilterItem(index);
                        if (!existing.isEmpty()) {
                            removeFilterItem(index);
                            menu.clearSlotTags(row);
                            sendFilterUpdate();
                            resetRowCache(row);
                            return true;
                        }
                        Fluid existingFluid = getFluidItem(index);
                        if (existingFluid != null) {
                            removeFluidSlot(index);
                            sendFilterUpdate();
                            resetRowCache(row);
                            return true;
                        }
                    }
                }
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    protected abstract ItemStack getFilterItem(int index);

    protected abstract void setFilterItem(int index, ItemStack stack);

    protected abstract void removeFilterItem(int index);

    protected abstract boolean isBlacklistMode();

    protected abstract void setBlacklistMode(boolean blacklist);

    protected abstract void sendFilterUpdate();

    protected abstract Fluid getFluidItem(int index);

    protected abstract void setFluidSlot(int index, Fluid fluid);

    protected abstract void removeFluidSlot(int index);
}