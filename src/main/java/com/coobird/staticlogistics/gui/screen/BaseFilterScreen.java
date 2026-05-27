package com.coobird.staticlogistics.gui.screen;

import com.coobird.staticlogistics.api.type.TransferType;
import com.coobird.staticlogistics.api.type.UpgradeType;
import com.coobird.staticlogistics.gui.menu.AbstractFilterMenu;
import com.coobird.staticlogistics.gui.screen.component.BlacklistButton;
import com.coobird.staticlogistics.gui.screen.component.FilterGridWidget;
import com.coobird.staticlogistics.gui.screen.component.NbtModeControls;
import com.coobird.staticlogistics.gui.screen.component.TagBarWidget;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

import java.util.List;
import java.util.Set;

/**
 * 抽象过滤器界面基类 —— 使用组件化架构拆分各处职责：
 * <p>
 * {@link FilterGridWidget} — 过滤槽位网格
 * {@link TagBarWidget} — 标签下拉栏
 * {@link BlacklistButton} — 黑/白名单切换
 * {@link NbtModeControls} — NBT匹配模式
 *
 */
public abstract class BaseFilterScreen<T extends AbstractFilterMenu>
    extends AbstractConfiguratorScreen<T>
    implements FilterGridWidget.FilterSlotProvider, TagBarWidget.TagSlotAccess {

    private final TagBarWidget.State tagState = new TagBarWidget.State();
    private Component toastMessage;
    private long toastExpiry;

    public BaseFilterScreen(T menu, Inventory inv, Component title) {
        super(menu, inv, title);
    }


    @Override
    public ItemStack getFilterItem(int index) {
        return menu.getFilterItem(index);
    }

    @Override
    public Fluid getFluidItem(int index) {
        return menu.getFluidSlot(index);
    }


    @Override
    public Set<TagKey<Item>> getSlotTags(int row) {
        return menu.getSlotTags(row);
    }

    @Override
    public Set<TagKey<Fluid>> getSlotFluidTags(int row) {
        return menu.getSlotFluidTags(row);
    }

    @Override
    public Set<TagKey<Item>> getExcludedTags(int row) {
        return menu.getExcludedTags(row);
    }

    @Override
    public Set<TagKey<Fluid>> getExcludedFluidTags(int row) {
        return menu.getExcludedFluidTags(row);
    }

    @Override
    public void addSlotTag(int row, TagKey<Item> tag) {
        menu.addSlotTag(row, tag);
    }

    @Override
    public void removeSlotTag(int row, TagKey<Item> tag) {
        menu.removeSlotTag(row, tag);
    }

    @Override
    public void addExcludedTag(int row, TagKey<Item> tag) {
        menu.addExcludedTag(row, tag);
    }

    @Override
    public void removeExcludedTag(int row, TagKey<Item> tag) {
        menu.removeExcludedTag(row, tag);
    }

    @Override
    public void addSlotFluidTag(int row, TagKey<Fluid> tag) {
        menu.addSlotFluidTag(row, tag);
    }

    @Override
    public void removeSlotFluidTag(int row, TagKey<Fluid> tag) {
        menu.removeSlotFluidTag(row, tag);
    }

    @Override
    public void addExcludedFluidTag(int row, TagKey<Fluid> tag) {
        menu.addExcludedFluidTag(row, tag);
    }

    @Override
    public void removeExcludedFluidTag(int row, TagKey<Fluid> tag) {
        menu.removeExcludedFluidTag(row, tag);
    }

    @Override
    public void clearSlotTags(int row) {
        menu.clearSlotTags(row);
    }

    @Override
    public void clearSlotFluidTags(int row) {
        menu.clearSlotFluidTags(row);
    }

    @Override
    public void render(GuiGraphics graphics, int mx, int my, float pt) {
        super.render(graphics, mx, my, pt);
        renderFilterOverlay(graphics, mx, my);
        this.renderTooltip(graphics, mx, my);
        if (toastMessage != null && System.currentTimeMillis() < toastExpiry) {
            int width = this.font.width(toastMessage);
            int x = (this.width - width) / 2;
            int y = this.topPos - 20;
            graphics.fill(x - 4, y - 2, x + width + 4, y + 12, 0xCC000000);
            graphics.drawString(this.font, toastMessage, x, y, 0xFFFF55);
        } else {
            toastMessage = null;
        }
    }

    /**
     * 子类在 renderCustomContent 中调用这些渲染方法
     */
    protected void renderFilterOverlay(GuiGraphics g, int mx, int my) {
        FilterGridWidget.renderTooltips(g, this.font, leftPos, topPos,
            mx, my, this, isTagMode(), menu.getCarried());
    }

    protected void renderFilterGrid(GuiGraphics g) {
        FilterGridWidget.render(g, leftPos, topPos, this, isTagMode());
    }

    protected void renderBlacklistButton(GuiGraphics g, int mx, int my) {
        BlacklistButton.render(g, this.font, leftPos, topPos,
            mx, my, menu.isBlacklistMode(), getBlacklistButtonXOffset());
    }

    protected void renderNbtModeControls(GuiGraphics g, int mx, int my) {
        NbtModeControls.render(g, this.font, leftPos, topPos,
            mx, my, menu.getNbtMatchMode(), menu.isIgnoreDamage());
    }

    protected void renderTagBars(GuiGraphics g, int mx, int my) {
        TagBarWidget.clearOrphanedSlotTags(this, this, isTagMode(),
            this::sendFilterUpdate);
        TagBarWidget.render(g, this.font, leftPos, topPos, mx, my,
            tagState, this, this, isTagMode());
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 1 && Screen.hasShiftDown()) {
            Slot hoveredSlot = getSlotUnderMouse();
            if (hoveredSlot != null && !hoveredSlot.getItem().isEmpty()) {
                if (!FilterGridWidget.getArea(leftPos, topPos, isTagMode())
                    .contains((int) mx, (int) my)) {
                    addItemToFilterSlot(hoveredSlot.getItem());
                    return true;
                }
            }
        }

        if (BlacklistButton.isHovered(mx, my, leftPos, topPos,
            getBlacklistButtonXOffset())) {
            menu.setBlacklistMode(!menu.isBlacklistMode());
            sendFilterUpdate();
            return true;
        }

        if (isTagMode()) {
            if (TagBarWidget.handleClick(mx, my, button, leftPos, topPos,
                tagState, this, this, true, this::sendFilterUpdate))
                return true;
        }

        if (handleNbtModeAndIgnoreClick(mx, my)) return true;

        FilterGridWidget.GridClickResult gridResult =
            FilterGridWidget.handleClick(leftPos, topPos, mx, my, button,
                this, isTagMode(), menu.getCarried());
        if (gridResult != null) {
            switch (gridResult.action()) {
                case SET_ITEM:
                    menu.setFilterItem(gridResult.slotIndex(),
                        menu.getCarried().copy());
                    if (isTagMode()) tagState.resetRow(gridResult.slotIndex());
                    sendFilterUpdate();
                    break;
                case SET_FLUID:
                    var fluidHandler = menu.getCarried()
                        .getCapability(Capabilities.FluidHandler.ITEM);
                    if (fluidHandler != null) {
                        FluidStack drained = fluidHandler.drain(1000,
                            IFluidHandler.FluidAction.SIMULATE);
                        if (!drained.isEmpty()) {
                            menu.setFluidSlot(gridResult.slotIndex(),
                                drained.getFluid());
                            if (isTagMode()) tagState.resetRow(gridResult.slotIndex());
                            sendFilterUpdate();
                        }
                    }
                    break;
                case CLEAR:
                    menu.removeFilterItem(gridResult.slotIndex());
                    menu.removeFluidSlot(gridResult.slotIndex());
                    menu.clearSlotTags(gridResult.slotIndex());
                    menu.clearSlotFluidTags(gridResult.slotIndex());
                    if (isTagMode()) tagState.resetRow(gridResult.slotIndex());
                    sendFilterUpdate();
                    break;
            }
            return true;
        }

        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        if (isTagMode() && tagState.hoveredTagBarRow >= 0) {
            if (TagBarWidget.handleScroll(scrollY, tagState,
                tagState.hoveredTagBarRow)) return true;
        }
        return super.mouseScrolled(mx, my, scrollX, scrollY);
    }

    protected boolean handleNbtModeAndIgnoreClick(double mx, double my) {
        if (menu.getActiveUpgradeType() != UpgradeType.NBT_FILTER) return false;

        if (NbtModeControls.isModeBtnHovered(mx, my, leftPos, topPos)) {
            boolean isPartial = menu.getNbtMatchMode()
                == com.coobird.staticlogistics.api.type.NbtMatchMode.PARTIAL;
            menu.setNbtMatchMode(isPartial
                ? com.coobird.staticlogistics.api.type.NbtMatchMode.FULL
                : com.coobird.staticlogistics.api.type.NbtMatchMode.PARTIAL);
            sendFilterUpdate();
            return true;
        }

        if (NbtModeControls.isIgnoreBtnHovered(mx, my, leftPos, topPos)) {
            menu.setIgnoreDamage(!menu.isIgnoreDamage());
            sendFilterUpdate();
            return true;
        }
        return false;
    }

    protected int getBlacklistButtonXOffset() {
        return 0;
    }

    private boolean isTagMode() {
        return menu.getActiveUpgradeType() == UpgradeType.TAG_FILTER;
    }

    protected void addItemToFilterSlot(ItemStack stack) {
        if (stack.isEmpty()) return;
        if (isTagMode() && FilterGridWidget.collectEnhancedTags(stack).isEmpty()) {
            showToast(Component.translatable("gui.staticlogistics.filter.no_tags"));
            return;
        }
        int emptySlot = FilterGridWidget.findFirstEmpty(leftPos, topPos, this,
            isTagMode(), stack);
        if (emptySlot == -1) {
            showToast(Component.translatable("gui.staticlogistics.filter.full"));
            return;
        }
        menu.setFilterItem(emptySlot, stack.copyWithCount(1));
        sendFilterUpdate();
        playClickSound();
        if (isTagMode()) tagState.resetRow(emptySlot);
    }

    private void showToast(Component message) {
        this.toastMessage = message;
        this.toastExpiry = System.currentTimeMillis() + 2000;
    }

    public Rect2i getFilterGridArea() {
        return FilterGridWidget.getArea(leftPos, topPos, isTagMode());
    }

    public void acceptGhostIngredient(ItemStack stack) {
        if (stack.isEmpty()) return;
        int hovered = FilterGridWidget.getHoveredSlot(leftPos, topPos, 0, 0, isTagMode());
        if (hovered != -1) {
            menu.setFilterItem(hovered, stack.copyWithCount(1));
        } else {
            addItemToFilterSlot(stack);
            return;
        }
        sendFilterUpdate();
    }

    public void acceptGhostIngredient(FluidStack fluid) {
        if (fluid.isEmpty()) return;
        int hovered = FilterGridWidget.getHoveredSlot(leftPos, topPos, 0, 0, isTagMode());
        if (hovered != -1) {
            menu.setFluidSlot(hovered, fluid.getFluid());
        } else {
            int empty = FilterGridWidget.findFirstEmpty(leftPos, topPos, this,
                isTagMode(), ItemStack.EMPTY);
            if (empty != -1) {
                menu.setFluidSlot(empty, fluid.getFluid());
            }
        }
        sendFilterUpdate();
    }

    @Override
    protected int getSelectedTypesMask() {
        return 0;
    }

    @Override
    protected List<TransferType> getTypeList() {
        return List.of();
    }

    @Override
    protected String getSearchHintKey() {
        return "";
    }

    @Override
    protected void renderTypeListItem(GuiGraphics g, TransferType type, int x, int y, boolean isSelected) {
    }

    @Override
    protected void onTypeClicked(TransferType type) {
    }

    protected abstract void sendFilterUpdate();
}
