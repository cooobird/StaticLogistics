package com.coobird.staticlogistics.client.gui.component;

import PortLib.extensions.net.minecraft.world.item.ItemStack.PortItemStackExtension;
import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.api.group.GroupKey;
import com.coobird.staticlogistics.api.group.GroupRef;
import com.coobird.staticlogistics.client.data.ClientConnection;
import com.coobird.staticlogistics.client.data.ClientLinkData;
import com.coobird.staticlogistics.client.data.GroupConnectionTreeModel;
import com.coobird.staticlogistics.client.render.SLGuiTextures;
import com.coobird.staticlogistics.content.SLKeyNames;
import com.coobird.staticlogistics.logistics.SLDataComponents;
import com.coobird.staticlogistics.logistics.node.ConnectionKey;
import com.coobird.staticlogistics.logistics.util.LogisticsConstants;
import com.coobird.staticlogistics.logistics.util.NodeDisplayText;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * 右侧组面板：搜索框 + 分组列表 + 滚动条 + 行内重命名。
 */
public class GroupPanel {
    public static final int SIDE_PANEL_X = SLGuiTextures.LinkConfigurator.GROUP_X;
    public static final int BAR_W = 76, BAR_X = 73, BAR_Y = 9;
    private static final int BAR_TEXT_WIDTH = 62;
    public static final int LIST_OFFSET_X = 4, LIST_OFFSET_Y = 43;
    public static final int SCROLLBAR_X = 133, SCROLLBAR_Y = 40;
    public static final int SELECTION_WIDTH = 126;
    /**
     * 只微调分组折叠/展开箭头，不移动整行内容。
     */
    private static final int GROUP_ARROW_X_OFFSET = 1;
    private static final int GROUP_ARROW_Y_OFFSET = 0;
    /**
     * 只微调连接方向箭头，不移动连接名称。
     */
    private static final int CONNECTION_ARROW_X_OFFSET = 0;
    private static final int CONNECTION_ARROW_Y_OFFSET = 0;
    /**
     * 整个列表框为四行高，添加行跟随内容且最多停靠在第四行。
     */
    private static final int LIST_HEIGHT = SLGuiTextures.List.ITEM_H * 4;
    /**
     * 滚动轨道从自身起点延伸到整个分组面板的真实底边。
     */
    private static final int SCROLLBAR_BOTTOM_OFFSET = -2;
    private static final int SCROLLBAR_TRACK_HEIGHT =
        SLGuiTextures.LinkConfigurator.GROUP_HEIGHT - (SCROLLBAR_Y - SLGuiTextures.LinkConfigurator.GROUP_Y) + SCROLLBAR_BOTTOM_OFFSET;
    private final EditBox searchBox;
    private final EditBox renameBox;
    private final EditBox newGroupBox;

    private float scrollOffset;
    private boolean isScrolling;
    private double scrollGrabOffset;
    private long lastClickTime;
    private GroupKey lastClickedGroup;
    private ConnectionKey lastClickedConnection;
    private GroupRef editingGroup;
    private ClientConnection editingConnection;
    private boolean creatingGroup;
    private String confirmedSearchTerm = "";
    private GroupRef hoveredGroup;
    private ClientConnection hoveredConnection;

    private int lastSeenVersion = -1;
    private List<GroupRef> cachedGroupList = Collections.emptyList();
    private List<GroupConnectionTreeModel.Row> cachedRows = Collections.emptyList();
    private final Set<GroupKey> expandedGroups = new HashSet<>();

    public GroupPanel(Font font, int leftPos, int topPos) {
        int sx = leftPos + SIDE_PANEL_X;
        this.searchBox = new EditBox(font, sx + BAR_X, topPos + BAR_Y, BAR_TEXT_WIDTH, 8, Component.empty());
        this.searchBox.setBordered(false);
        this.searchBox.setMaxLength(20);
        this.searchBox.setTextColor(0xFFFFFF);
        this.searchBox.setHint(Component.translatable("gui.staticlogistics.search_hint").withStyle(ChatFormatting.DARK_GRAY));
        this.searchBox.setResponder(value -> {
            this.confirmedSearchTerm = value.trim();
            this.scrollOffset = 0;
            this.lastSeenVersion = -1;
        });

        this.renameBox = new EditBox(font, 0, 0, SELECTION_WIDTH - 4, 10, Component.empty());
        this.renameBox.setBordered(false);
        this.renameBox.setVisible(false);
        this.renameBox.setTextColor(0xFFFFCC);

        this.newGroupBox = new EditBox(
            font, 0, 0, SELECTION_WIDTH - 12, 8, Component.empty());
        this.newGroupBox.setBordered(false);
        this.newGroupBox.setVisible(false);
        this.newGroupBox.setMaxLength(32);
        this.newGroupBox.setTextColor(0xFFFFCC);
    }

    public EditBox getSearchBox() {
        return searchBox;
    }

    public EditBox getRenameBox() {
        return renameBox;
    }

    public EditBox getNewGroupBox() {
        return newGroupBox;
    }

    @Nullable
    public GroupRef getHoveredGroup() {
        return hoveredGroup;
    }

    @Nullable
    public ClientConnection getHoveredConnection() {
        return hoveredConnection;
    }

    @Nullable
    public GroupRef getEditingGroup() {
        return editingGroup;
    }

    @Nullable
    public ClientConnection getEditingConnection() {
        return editingConnection;
    }

    public boolean isRenaming() {
        return editingGroup != null || editingConnection != null;
    }

    public boolean isScrolling() {
        return isScrolling;
    }

    public void setInitialState(ItemStack stack) {
        this.lastClickedGroup = PortItemStackExtension.getData(stack, SLDataComponents.SELECTED_GROUP_KEY.get());
        this.expandedGroups.clear();
        if (this.lastClickedGroup != null) this.expandedGroups.add(this.lastClickedGroup);
        this.lastSeenVersion = -1;
    }

    public void render(GuiGraphics g, Font font, ItemStack stack,
                       Set<ConnectionKey> highlightedConnections,
                       int leftPos, int topPos, int mx, int my, float partialTick,
                       double interfaceScale) {
        this.hoveredGroup = null;
        this.hoveredConnection = null;
        int sx = leftPos + SIDE_PANEL_X;

        this.searchBox.setX(sx + BAR_X + 1);
        this.searchBox.setY(topPos + BAR_Y + 1);
        this.searchBox.render(g, mx, my, partialTick);

        renderGroupList(
            g, font, stack, highlightedConnections, sx, topPos, mx, my, interfaceScale);

        if (this.renameBox.isVisible()) {
            this.renameBox.render(g, mx, my, partialTick);
        }
        if (this.newGroupBox.isVisible()) {
            this.newGroupBox.render(g, mx, my, partialTick);
        }
    }

    private void renderGroupList(GuiGraphics g, Font font, ItemStack stack,
                                 Set<ConnectionKey> highlightedConnections,
                                 int sx, int topPos, int mx, int my,
                                 double interfaceScale) {
        List<GroupConnectionTreeModel.Row> rows = getVisibleRows(stack);
        int maxScroll = getMaxScroll();
        renderScrollBar(g, sx + SCROLLBAR_X, topPos + SCROLLBAR_Y, mx, my, maxScroll);

        int listX = sx + LIST_OFFSET_X;
        int listY = topPos + LIST_OFFSET_Y;
        int addRowY = getAddRowY(listY);
        String currentGroupId = PortItemStackExtension.getDataOrDefault(stack, SLDataComponents.SELECTED_GROUP.get(), "");
        GroupKey currentGroupKey = PortItemStackExtension.getData(stack, SLDataComponents.SELECTED_GROUP_KEY.get());
        renameBox.setVisible(false);
        boolean editingRowVisible = false;
        boolean pinnedGroup = !expandedGroups.isEmpty() && !rows.isEmpty();
        int contentTop = pinnedGroup
            ? listY + SLGuiTextures.List.ITEM_H : listY;
        GuiScissor.enable(g, interfaceScale, listX - 2, contentTop,
            listX + SELECTION_WIDTH + 2, addRowY);

        for (int i = pinnedGroup ? 1 : 0; i < rows.size(); i++) {
            int itemY = getRowY(i, listY);
            if (itemY + SLGuiTextures.List.ITEM_H <= contentTop
                || itemY >= addRowY) continue;
            editingRowVisible |= renderListRow(g, font, rows.get(i), listX,
                itemY, mx, my, currentGroupId, currentGroupKey,
                highlightedConnections);
        }
        g.disableScissor();
        if (pinnedGroup) {
            editingRowVisible |= renderListRow(g, font, rows.get(0), listX,
                listY, mx, my, currentGroupId, currentGroupKey,
                highlightedConnections);
        }
        renderAddGroupRow(g, font, listX, addRowY, mx, my);
        if (isRenaming() && !editingRowVisible) cancelRename();
    }

    private boolean renderListRow(
        GuiGraphics graphics,
        Font font,
        GroupConnectionTreeModel.Row row,
        int listX,
        int itemY,
        int mouseX,
        int mouseY,
        String currentGroupId,
        @Nullable GroupKey currentGroupKey,
        Set<ConnectionKey> highlightedConnections
    ) {
        GroupRef group = row.group();
        boolean groupRow = row.connection() == null;
        boolean selectedGroup = groupRow && (currentGroupKey != null
            ? currentGroupKey.equals(group.key())
            : Objects.equals(currentGroupId, group.displayName()));
        boolean selectedConnection = !groupRow
            && highlightedConnections.contains(row.connection().key());
        boolean hovered = mouseX >= listX
            && mouseX <= listX + SELECTION_WIDTH
            && mouseY >= itemY
            && mouseY < itemY + SLGuiTextures.List.ITEM_H;
        if (hovered) {
            if (groupRow) hoveredGroup = group;
            else hoveredConnection = row.connection();
        }
        if (selectedGroup || selectedConnection) {
            graphics.fill(listX, itemY, listX + SELECTION_WIDTH,
                itemY + SLGuiTextures.List.ITEM_H,
                selectedConnection ? 0x55FFD45A : 0x4498FB98);
        } else if (hovered) {
            graphics.fill(listX, itemY, listX + SELECTION_WIDTH,
                itemY + SLGuiTextures.List.ITEM_H, 0x22FFFFFF);
        }

        boolean editing = groupRow
            ? editingGroup != null && editingGroup.key().equals(group.key())
            : editingConnection != null
                && editingConnection.key().equals(row.connection().key());
        if (editing) {
            renameBox.setX(listX);
            renameBox.setY(itemY + 1);
            renameBox.setVisible(true);
            return true;
        }

        int color = selectedConnection ? 0xFFFFD45A
            : selectedGroup ? 0xFF98FB98 : 0xFFCCCCCC;
        int textX = listX;
        if (groupRow) {
            renderChevron(graphics, textX, itemY,
                expandedGroups.contains(group.key()));
            textX += 8;
        } else {
            renderConnectionIcon(graphics, row.connection(), textX, itemY);
            textX += 10;
        }
        UUID ownerUUID = group.key().ownerId();
        if (groupRow && ownerUUID != null) {
            int headSize = 10;
            PlayerAvatarRenderer.render(
                graphics, ownerUUID, textX + 2, itemY + 1, headSize);
            textX += headSize + 3;
            if (mouseX >= textX - headSize - 3 && mouseX < textX - 3
                && mouseY >= itemY + 1 && mouseY < itemY + 1 + headSize) {
                hoveredGroup = group;
            }
        }
        int availableWidth = listX + SELECTION_WIDTH - textX;
        String display;
        if (groupRow) {
            display = "#" + group.displayName();
        } else {
            String customName = row.connection().displayName();
            display = customName.isEmpty()
                ? Component.translatable("gui.staticlogistics.connection")
                .append(" " + row.connectionIndex()).getString()
                : customName;
        }
        graphics.drawString(font,
            font.plainSubstrByWidth(display, availableWidth),
            textX, itemY + 2, color, false);
        return false;
    }

    private void renderAddGroupRow(
        GuiGraphics graphics,
        Font font,
        int listX,
        int itemY,
        int mouseX,
        int mouseY
    ) {
        if (creatingGroup) {
            newGroupBox.setX(listX + 10);
            newGroupBox.setY(itemY + 1);
            newGroupBox.setWidth(SELECTION_WIDTH - 12);
            newGroupBox.setVisible(true);
            return;
        }
        newGroupBox.setVisible(false);
        boolean hovered = mouseX >= listX
            && mouseX <= listX + SELECTION_WIDTH
            && mouseY >= itemY
            && mouseY < itemY + SLGuiTextures.List.ITEM_H;
        if (hovered) {
            graphics.fill(listX, itemY,
                listX + SELECTION_WIDTH,
                itemY + SLGuiTextures.List.ITEM_H, 0x22FFFFFF);
        }
        graphics.drawString(font, "+", listX + 1, itemY + 1,
            0xFF98FB98, false);
        String label = Component.translatable(
            "gui.staticlogistics.add_group").getString();
        graphics.drawString(font,
            font.plainSubstrByWidth(label, SELECTION_WIDTH - 12),
            listX + 10, itemY + 1,
            hovered ? 0xFF98FB98 : 0xFF777777, false);
    }

    private void renderScrollBar(GuiGraphics g, int x, int y, int mx, int my, int maxScroll) {
        boolean showActive = maxScroll > 0
            && ((mx >= x && mx <= x + SLGuiTextures.Scrollbar.WIDTH
            && my >= y && my <= y + SCROLLBAR_TRACK_HEIGHT) || this.isScrolling);
        int knobY = scrollKnobOffset(maxScroll);
        g.blit(SLGuiTextures.GUI_ATLAS, x, y + knobY,
            showActive ? SLGuiTextures.Scrollbar.ENABLED_U : SLGuiTextures.Scrollbar.DISABLED_U,
            SLGuiTextures.Scrollbar.ENABLED_V,
            SLGuiTextures.Scrollbar.WIDTH, SLGuiTextures.Scrollbar.HEIGHT,
            SLGuiTextures.GUI_WIDTH, SLGuiTextures.GUI_HEIGHT);
    }

    public boolean isSearchTriggerHit(double mx, double my, int leftPos, int topPos) {
        int sx = leftPos + SIDE_PANEL_X;
        int iconX = sx + BAR_X + BAR_W - SLGuiTextures.SEARCH_ICON_WIDTH;
        return mx >= iconX
            && mx <= iconX + SLGuiTextures.SEARCH_ICON_WIDTH
            && my >= topPos + BAR_Y
            && my <= topPos + BAR_Y + SLGuiTextures.SEARCH_ICON_HEIGHT;
    }

    public void triggerSearch() {
        this.confirmedSearchTerm = this.searchBox.getValue().trim();
        this.scrollOffset = 0;
        this.lastSeenVersion = -1;
        playClickSound();
    }

    public boolean searchBoxMouseClicked(double mx, double my, int button) {
        return this.searchBox.mouseClicked(mx, my, button);
    }

    public boolean isSearchBoxFocused() {
        return this.searchBox.isFocused();
    }

    public boolean renameBoxMouseClicked(double mx, double my, int button) {
        if (this.renameBox.isVisible()) {
            return this.renameBox.mouseClicked(mx, my, button);
        }
        return false;
    }

    public boolean newGroupBoxMouseClicked(
        double mouseX,
        double mouseY,
        int button
    ) {
        return creatingGroup
            && newGroupBox.mouseClicked(mouseX, mouseY, button);
    }

    public boolean isCreatingGroup() {
        return creatingGroup;
    }

    public boolean isCreateRowHit(
        double mouseX,
        double mouseY,
        int leftPos,
        int topPos
    ) {
        int listX = leftPos + SIDE_PANEL_X + LIST_OFFSET_X;
        int listY = topPos + LIST_OFFSET_Y;
        int itemY = getAddRowY(listY);
        return mouseX >= listX
            && mouseX <= listX + SELECTION_WIDTH
            && mouseY >= itemY
            && mouseY < itemY + SLGuiTextures.List.ITEM_H;
    }

    public void beginCreateGroup() {
        creatingGroup = true;
        newGroupBox.setValue("");
        newGroupBox.setVisible(true);
        newGroupBox.setFocused(true);
    }

    public String confirmCreateGroup() {
        String name = newGroupBox.getValue().trim();
        cancelCreateGroup();
        return name;
    }

    public void cancelCreateGroup() {
        creatingGroup = false;
        newGroupBox.setFocused(false);
        newGroupBox.setVisible(false);
        newGroupBox.setValue("");
    }

    public boolean isRenameBoxVisible() {
        return this.renameBox.isVisible();
    }

    public void startRename(GroupRef group, int leftPos, int topPos) {
        int sx = leftPos + SIDE_PANEL_X;
        this.renameBox.setX(sx + LIST_OFFSET_X);
        this.renameBox.setY(topPos + LIST_OFFSET_Y);
        this.editingGroup = group;
        this.editingConnection = null;
        this.renameBox.setValue(group.displayName());
        this.renameBox.setVisible(true);
        this.renameBox.setFocused(true);
    }

    public void startRename(ClientConnection connection, int leftPos, int topPos) {
        int sx = leftPos + SIDE_PANEL_X;
        this.renameBox.setX(sx + LIST_OFFSET_X);
        this.renameBox.setY(topPos + LIST_OFFSET_Y);
        this.editingGroup = null;
        this.editingConnection = Objects.requireNonNull(connection);
        this.renameBox.setValue(connection.displayName());
        this.renameBox.setVisible(true);
        this.renameBox.setFocused(true);
    }

    /**
     * 将 renameBox 定位到指定绝对坐标
     */
    public String confirmRename() {
        String newId = renameBox.getValue().trim();
        cancelRename();
        return newId;
    }

    public void cancelRename() {
        this.editingGroup = null;
        this.editingConnection = null;
        this.renameBox.setVisible(false);
    }

    /**
     * 处理分组列表区域的鼠标点击。
     *
     * @return 点击结果：null=未命中，ClickResult=命中
     */
    @Nullable
    public ClickResult handleListClick(double mx, double my, int button, int leftPos, int topPos,
                                       ItemStack stack, boolean shiftDown) {
        int sx = leftPos + SIDE_PANEL_X;
        List<GroupConnectionTreeModel.Row> rows = getVisibleRows(stack);
        int listX = sx + LIST_OFFSET_X;
        int listY = topPos + LIST_OFFSET_Y;
        int addRowY = getAddRowY(listY);
        if (!(mx >= listX && mx <= listX + SELECTION_WIDTH
            && my >= listY && my < addRowY))
            return null;

        for (int i = 0; i < rows.size(); i++) {
            GroupConnectionTreeModel.Row row = rows.get(i);
            int itemY = getRowY(i, listY);
            if (my >= itemY
                && my < itemY + SLGuiTextures.List.ITEM_H) {
                GroupRef group = row.group();
                if (row.connection() != null) {
                    ClientConnection connection = row.connection();
                    if (button == 1) return ClickResult.deleteConnection(group, connection);
                    if (button != 0) return ClickResult.consumed(group);
                    long now = Util.getMillis();
                    boolean isDoubleClick = Objects.equals(
                        lastClickedConnection, connection.key())
                        && now - lastClickTime < LogisticsConstants.UI.DOUBLE_CLICK_THRESHOLD_MS;
                    lastClickedConnection = connection.key();
                    lastClickedGroup = null;
                    lastClickTime = now;
                    return isDoubleClick
                        ? ClickResult.renameConnection(group, connection)
                        : ClickResult.open(group, connection);
                }
                if (button == 1) {
                    return ClickResult.delete(group);
                }
                if (shiftDown) {
                    return ClickResult.export(group);
                }
                if (button == 0 && mx < listX + 8) {
                    boolean collapse = expandedGroups.contains(group.key());
                    expandedGroups.clear();
                    if (!collapse) expandedGroups.add(group.key());
                    scrollOffset = 0;
                    rebuildRows();
                    clampScroll();
                    return ClickResult.consumed(group);
                }
                if (button == 0 && expandedGroups.isEmpty()) {
                    expandedGroups.add(group.key());
                    scrollOffset = 0;
                    rebuildRows();
                    lastClickedGroup = group.key();
                    lastClickedConnection = null;
                    lastClickTime = Util.getMillis();
                    return ClickResult.select(group);
                }
                long now = Util.getMillis();
                boolean isDoubleClick = Objects.equals(lastClickedGroup, group.key())
                    && now - lastClickTime < LogisticsConstants.UI.DOUBLE_CLICK_THRESHOLD_MS;
                lastClickedGroup = group.key();
                lastClickedConnection = null;
                lastClickTime = now;
                return isDoubleClick ? ClickResult.rename(group) : ClickResult.select(group);
            }
        }
        return null;
    }

    public boolean handleScrollbarClick(double mx, double my, int leftPos, int topPos) {
        int sx = leftPos + SIDE_PANEL_X;
        int scrollX = sx + SCROLLBAR_X;
        int scrollY = topPos + SCROLLBAR_Y;
        if (mx >= scrollX && mx <= scrollX + SLGuiTextures.Scrollbar.WIDTH
            && my >= scrollY && my <= scrollY + SCROLLBAR_TRACK_HEIGHT) {
            if (getMaxScroll() > 0) {
                int knobY = scrollY + scrollKnobOffset(getMaxScroll());
                this.scrollGrabOffset = my >= knobY
                    && my <= knobY + SLGuiTextures.Scrollbar.HEIGHT
                    ? my - knobY
                    : SLGuiTextures.Scrollbar.HEIGHT / 2.0D;
                this.isScrolling = true;
                updateScrollFromMouse(my, topPos);
            }
            return true;
        }
        return false;
    }

    public void updateScrollFromMouse(double mouseY, int topPos) {
        int maxScroll = getMaxScroll();
        if (maxScroll > 0) {
            int travel = scrollbarTravel();
            float relativePos = travel == 0 ? 0.0F : (float) (
                (mouseY - (topPos + SCROLLBAR_Y) - scrollGrabOffset) / travel);
            this.scrollOffset = Mth.clamp(relativePos * maxScroll, 0, maxScroll);
        }
    }

    public boolean mouseScrolled(double mx, double my, double dy, int leftPos, int topPos) {
        int sx = leftPos + SIDE_PANEL_X;
        int listX = sx + LIST_OFFSET_X;
        int listY = topPos + LIST_OFFSET_Y;
        int scrollbarX = sx + SCROLLBAR_X;
        int scrollbarY = topPos + SCROLLBAR_Y;
        boolean overList = mx >= listX && mx < listX + SELECTION_WIDTH
            && my >= listY && my < listY + LIST_HEIGHT;
        boolean overScrollbar = mx >= scrollbarX
            && mx < scrollbarX + SLGuiTextures.Scrollbar.WIDTH
            && my >= scrollbarY
            && my < scrollbarY + SCROLLBAR_TRACK_HEIGHT;
        if (!overList && !overScrollbar) return false;

        int maxScroll = getMaxScroll();
        if (maxScroll > 0) {
            this.scrollOffset = Mth.clamp(
                this.scrollOffset - (float) dy * SLGuiTextures.List.ITEM_H, 0, maxScroll);
            return true;
        }
        return false;
    }

    public boolean mouseDragged(double my, int topPos) {
        if (this.isScrolling) {
            updateScrollFromMouse(my, topPos);
            return true;
        }
        return false;
    }

    public void mouseReleased() {
        this.isScrolling = false;
    }

    private int scrollKnobOffset(int maxScroll) {
        if (maxScroll <= 0) return 0;
        return Math.round(scrollOffset / maxScroll * scrollbarTravel());
    }

    private static int scrollbarTravel() {
        return Math.max(0,
            SCROLLBAR_TRACK_HEIGHT - SLGuiTextures.Scrollbar.HEIGHT);
    }

    private int getMaxScroll() {
        int fixedRows = expandedGroups.isEmpty() ? 1 : 2;
        int scrollingRows = expandedGroups.isEmpty()
            ? cachedRows.size() : Math.max(0, cachedRows.size() - 1);
        return Math.max(0, scrollingRows * SLGuiTextures.List.ITEM_H
            - (LIST_HEIGHT - fixedRows * SLGuiTextures.List.ITEM_H));
    }

    /**
     * 添加分组行固定在列表底部，不参与连接滚动。
     */
    private int getAddRowY(int listY) {
        return listY + LIST_HEIGHT - SLGuiTextures.List.ITEM_H;
    }

    /**
     * 展开分组时固定首行标题，仅让后续连接行参与滚动。
     */
    private int getRowY(int rowIndex, int listY) {
        if (!expandedGroups.isEmpty() && rowIndex == 0) return listY;
        return listY + rowIndex * SLGuiTextures.List.ITEM_H
            - (int) scrollOffset;
    }

    private List<GroupRef> getFilteredGroups(ItemStack stack) {
        int version = ClientLinkData.INSTANCE.getDataVersion();
        if (version == lastSeenVersion) return cachedGroupList;

        Player p = Minecraft.getInstance().player;
        if (p == null) return Collections.emptyList();

        this.cachedGroupList = GroupConnectionTreeModel.filterAndSort(
            ClientLinkData.INSTANCE.getAccessibleGroupRefs(), "");

        this.lastSeenVersion = version;
        rebuildRows();
        return cachedGroupList;
    }

    private List<GroupConnectionTreeModel.Row> getVisibleRows(ItemStack stack) {
        getFilteredGroups(stack);
        return cachedRows;
    }

    private void rebuildRows() {
        expandedGroups.removeIf(key -> cachedGroupList.stream().noneMatch(
            group -> group.key().equals(key)));
        List<GroupRef> displayedGroups = expandedGroups.isEmpty()
            ? GroupConnectionTreeModel.filterAndSort(
            cachedGroupList, confirmedSearchTerm)
            : cachedGroupList.stream()
            .filter(group -> expandedGroups.contains(group.key()))
            .toList();
        List<GroupConnectionTreeModel.Row> rows = GroupConnectionTreeModel.buildRows(
            displayedGroups, expandedGroups);
        if (!expandedGroups.isEmpty() && !confirmedSearchTerm.isBlank()) {
            String query = confirmedSearchTerm.toLowerCase(Locale.ROOT);
            rows = rows.stream()
                .filter(row -> row.connection() == null
                    || connectionMatchesSearch(row.connection(), query))
                .toList();
        }
        this.cachedRows = rows;
        if (editingGroup != null && cachedGroupList.stream().noneMatch(
            group -> group.key().equals(editingGroup.key()))) {
            cancelRename();
        } else if (editingConnection != null && cachedRows.stream().noneMatch(
            row -> row.connection() != null
                && row.connection().key().equals(editingConnection.key()))) {
            cancelRename();
        }
        clampScroll();
    }

    private void clampScroll() {
        this.scrollOffset = Mth.clamp(this.scrollOffset, 0, getMaxScroll());
    }

    /**
     * 展开并滚动到网络预览关联的连接。搜索条件挡住目标分组时自动清空搜索。
     */
    public void revealConnections(Collection<ConnectionKey> connections, ItemStack stack) {
        if (connections == null || connections.isEmpty()) return;
        LinkedHashSet<ConnectionKey> targets = new LinkedHashSet<>(connections);
        GroupKey targetGroup = targets.iterator().next().groupKey();
        getFilteredGroups(stack);
        if (cachedGroupList.stream().noneMatch(group -> group.key().equals(targetGroup))) {
            searchBox.setValue("");
            getFilteredGroups(stack);
        }
        if (cachedGroupList.stream().noneMatch(group -> group.key().equals(targetGroup))) return;
        expandedGroups.clear();
        expandedGroups.add(targetGroup);
        rebuildRows();
        if (cachedRows.stream().noneMatch(row -> row.connection() != null
            && targets.contains(row.connection().key()))) {
            searchBox.setValue("");
            rebuildRows();
        }
        for (int index = 0; index < cachedRows.size(); index++) {
            ClientConnection connection = cachedRows.get(index).connection();
            if (connection == null || !targets.contains(connection.key())) continue;
            scrollRowIntoView(index);
            return;
        }
    }

    private void scrollRowIntoView(int rowIndex) {
        if (!expandedGroups.isEmpty() && rowIndex == 0) return;
        int scrollingIndex = expandedGroups.isEmpty() ? rowIndex : rowIndex - 1;
        int rowTop = scrollingIndex * SLGuiTextures.List.ITEM_H;
        int rowBottom = rowTop + SLGuiTextures.List.ITEM_H;
        int fixedRows = expandedGroups.isEmpty() ? 1 : 2;
        int visibleHeight = LIST_HEIGHT
            - fixedRows * SLGuiTextures.List.ITEM_H;
        if (rowTop < scrollOffset) {
            scrollOffset = rowTop;
        } else if (rowBottom > scrollOffset + visibleHeight) {
            scrollOffset = rowBottom - visibleHeight;
        }
        clampScroll();
    }

    private static boolean connectionMatchesSearch(
        ClientConnection connection, String query
    ) {
        if (connection.displayName().toLowerCase(Locale.ROOT).contains(query)) return true;
        return NodeDisplayText.details(connection.first()).getString()
            .toLowerCase(Locale.ROOT).contains(query)
            || NodeDisplayText.details(connection.second()).getString()
            .toLowerCase(Locale.ROOT).contains(query);
    }

    private static void renderConnectionIcon(GuiGraphics graphics, ClientConnection connection,
                                             int x, int y) {
        int arrowX = x + CONNECTION_ARROW_X_OFFSET;
        int arrowY = y + CONNECTION_ARROW_Y_OFFSET;
        if (connection.isBidirectional()) {
            drawBidirectionalConnectionArrow(graphics, arrowX,
                arrowY + (SLGuiTextures.List.ITEM_H
                    - SLGuiTextures.Direction.CONNECTION_BIDIRECTIONAL_HEIGHT) / 2);
        } else if (connection.transfersFirstToSecond()) {
            drawConnectionArrow(graphics, arrowX,
                arrowY + (SLGuiTextures.List.ITEM_H
                    - SLGuiTextures.Direction.CONNECTION_HEIGHT) / 2, true);
        } else if (connection.transfersSecondToFirst()) {
            drawConnectionArrow(graphics, arrowX,
                arrowY + (SLGuiTextures.List.ITEM_H
                    - SLGuiTextures.Direction.CONNECTION_HEIGHT) / 2, false);
        } else {
            graphics.fill(x + 1, y + 1, x + 2, y + 7, 0xFFFF5555);
            graphics.fill(x + 6, y + 1, x + 7, y + 7, 0xFFFF5555);
            graphics.fill(x + 2, y + 2, x + 6, y + 6, 0xFFFF5555);
        }
    }

    private static void renderChevron(GuiGraphics graphics, int x, int y, boolean expanded) {
        if (expanded) {
            graphics.blit(SLGuiTextures.GUI_ATLAS,
                x + GROUP_ARROW_X_OFFSET,
                y + (SLGuiTextures.List.ITEM_H
                    - SLGuiTextures.Direction.DOWN_HEIGHT) / 2
                    + GROUP_ARROW_Y_OFFSET,
                SLGuiTextures.Direction.DOWN_U,
                SLGuiTextures.Direction.ENABLED_V,
                SLGuiTextures.Direction.DOWN_WIDTH,
                SLGuiTextures.Direction.DOWN_HEIGHT,
                SLGuiTextures.GUI_WIDTH, SLGuiTextures.GUI_HEIGHT);
        } else {
            graphics.blit(SLGuiTextures.GUI_ATLAS,
                x + (SLGuiTextures.Direction.DOWN_WIDTH
                    - SLGuiTextures.Direction.HORIZONTAL_WIDTH) / 2
                    + GROUP_ARROW_X_OFFSET,
                y + (SLGuiTextures.List.ITEM_H
                    - SLGuiTextures.Direction.HORIZONTAL_HEIGHT) / 2
                    + GROUP_ARROW_Y_OFFSET,
                SLGuiTextures.Direction.RIGHT_U,
                SLGuiTextures.Direction.ENABLED_V,
                SLGuiTextures.Direction.HORIZONTAL_WIDTH,
                SLGuiTextures.Direction.HORIZONTAL_HEIGHT,
                SLGuiTextures.GUI_WIDTH, SLGuiTextures.GUI_HEIGHT);
        }
    }

    private static void drawConnectionArrow(GuiGraphics graphics, int x, int y,
                                            boolean right) {
        graphics.blit(SLGuiTextures.GUI_ATLAS, x, y,
            right
                ? SLGuiTextures.Direction.CONNECTION_RIGHT_U
                : SLGuiTextures.Direction.CONNECTION_LEFT_U,
            SLGuiTextures.Direction.CONNECTION_V,
            SLGuiTextures.Direction.CONNECTION_WIDTH,
            SLGuiTextures.Direction.CONNECTION_HEIGHT,
            SLGuiTextures.GUI_WIDTH, SLGuiTextures.GUI_HEIGHT);
    }

    private static void drawBidirectionalConnectionArrow(GuiGraphics graphics,
                                                         int x, int y) {
        graphics.blit(SLGuiTextures.GUI_ATLAS, x, y,
            SLGuiTextures.Direction.CONNECTION_BIDIRECTIONAL_U,
            SLGuiTextures.Direction.CONNECTION_BIDIRECTIONAL_V,
            SLGuiTextures.Direction.CONNECTION_BIDIRECTIONAL_WIDTH,
            SLGuiTextures.Direction.CONNECTION_BIDIRECTIONAL_HEIGHT,
            SLGuiTextures.GUI_WIDTH, SLGuiTextures.GUI_HEIGHT);
    }

    public void renderConnectionTooltip(GuiGraphics g, Font font, int mx, int my, ClientConnection connection) {
        List<Component> lines = new ArrayList<>();
        lines.add((connection.displayName().isEmpty()
            ? Component.translatable("gui.staticlogistics.connection")
            : Component.literal(connection.displayName())).withStyle(ChatFormatting.GOLD));
        lines.add(NodeDisplayText.details(connection.first()).withStyle(ChatFormatting.WHITE));
        lines.add(NodeDisplayText.details(connection.second()).withStyle(ChatFormatting.WHITE));
        Component direction = connection.isBidirectional()
            ? Component.translatable("gui.staticlogistics.connection.bidirectional")
            : connection.transfersFirstToSecond()
            ? Component.translatable("gui.staticlogistics.connection.first_to_second")
            : connection.transfersSecondToFirst()
            ? Component.translatable("gui.staticlogistics.connection.second_to_first")
            : Component.translatable("gui.staticlogistics.connection.blocked");
        lines.add(direction.copy().withStyle(
            connection.isBlocked() ? ChatFormatting.RED : ChatFormatting.GREEN));
        lines.add(Component.translatable("gui.staticlogistics.connection.open_hint")
            .withStyle(ChatFormatting.AQUA));
        lines.add(Component.translatable("gui.staticlogistics.connection.rename_hint")
            .withStyle(ChatFormatting.AQUA));
        lines.add(Component.translatable("gui.staticlogistics.connection.delete_hint")
            .withStyle(ChatFormatting.RED, ChatFormatting.ITALIC));
        g.renderComponentTooltip(font, lines, mx, my);
    }

    public void renderGroupTooltip(GuiGraphics g, Font font, int mx, int my, GroupRef group, boolean shiftDown) {
        Player player = Minecraft.getInstance().player;
        if (player == null) return;

        String gid = group.displayName();
        List<LogisticsNode> nodes = ClientLinkData.INSTANCE.getNodesForGroup(group.key());

        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("gui.staticlogistics.tooltip.group_id", gid).withStyle(ChatFormatting.GOLD));
        String ownerName = ClientLinkData.INSTANCE.getOwnerName(group.key().ownerId());
        if (!ownerName.isEmpty()) {
            lines.add(Component.translatable("msg.staticlogistics.owner_display", ownerName).withStyle(ChatFormatting.GRAY));
        }

        int maxShown = shiftDown ? Integer.MAX_VALUE : 5;
        if (!nodes.isEmpty()) {
            int count = 0;
            for (LogisticsNode node : nodes) {
                if (count >= maxShown) break;
                lines.add(formatNode(node, player));
                count++;
            }
            if (nodes.size() > maxShown) {
                lines.add(Component.translatable("gui.staticlogistics.tooltip.shift_more",
                    Component.keybind(SLKeyNames.GROUP_DETAILS_AND_EXPORT),
                    nodes.size() - maxShown).withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
            }
            lines.add(Component.empty());
        }

        if (editingGroup == null) {
            lines.add(Component.translatable("gui.staticlogistics.tooltip.select_hint")
                .withStyle(ChatFormatting.BLUE));
            lines.add(Component.translatable("gui.staticlogistics.tooltip.right_click_delete")
                .withStyle(ChatFormatting.RED, ChatFormatting.ITALIC));
            lines.add(Component.translatable("gui.staticlogistics.tooltip.rename_hint")
                .withStyle(ChatFormatting.AQUA));
        }
        lines.add(Component.translatable("gui.staticlogistics.tooltip.shift_export",
                Component.keybind(SLKeyNames.GROUP_DETAILS_AND_EXPORT))
            .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        g.renderComponentTooltip(font, lines, mx, my);
    }

    public void exportToChat(GroupRef group) {
        Player player = Minecraft.getInstance().player;
        if (player == null) return;
        String gid = group.displayName();
        List<LogisticsNode> nodes = ClientLinkData.INSTANCE.getNodesForGroup(group.key());
        player.sendSystemMessage(Component.translatable("msg.staticlogistics.export.header", gid)
            .withStyle(ChatFormatting.GOLD));
        if (nodes.isEmpty()) {
            player.sendSystemMessage(Component.literal(" §7> ").append(
                Component.translatable("msg.staticlogistics.no_nodes_stored").withStyle(ChatFormatting.RED)));
        } else {
            for (LogisticsNode node : nodes) {
                BlockPos p = node.gPos().pos();
                String posStr = p.getX() + " " + p.getY() + " " + p.getZ();
                String dimensionId = node.gPos().dimension().location().toString();
                String command = node.isInSameDimension(player.level().dimension())
                    ? "/tp " + posStr
                    : "/execute in " + dimensionId + " run tp @s " + posStr;
                MutableComponent posEntry = Component.literal(" > ")
                    .withStyle(ChatFormatting.DARK_GRAY)
                    .append(NodeDisplayText.details(node).withStyle(ChatFormatting.GREEN))
                    .withStyle(style -> style
                        .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND,
                            command))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                            Component.translatable("msg.staticlogistics.export.tp_hover")
                                .withStyle(ChatFormatting.ITALIC))));
                player.sendSystemMessage(posEntry);
            }
        }
        Minecraft.getInstance().getSoundManager()
            .play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    private static Component formatNode(LogisticsNode node, Player player) {
        BlockPos pos = node.gPos().pos();
        MutableComponent location = NodeDisplayText.details(node).withStyle(ChatFormatting.WHITE);
        if (!node.isInSameDimension(player.level().dimension())) return location;
        double distance = Math.sqrt(pos.distToCenterSqr(player.position()));
        return location.append(Component.literal("  "))
            .append(NodeDisplayText.distanceFromPlayer(distance).withStyle(ChatFormatting.AQUA));
    }

    private void playClickSound() {
        SoundUtil.playClickSound();
    }

    public static class ClickResult {
        public enum Action {
            SELECT,
            RENAME,
            EXPORT,
            DELETE,
            OPEN_CONNECTION,
            RENAME_CONNECTION,
            DELETE_CONNECTION,
            CONSUME
        }

        private final Action action;
        private final GroupRef group;
        private final ClientConnection connection;

        private ClickResult(Action action, GroupRef group, @Nullable ClientConnection connection) {
            this.action = action;
            this.group = group;
            this.connection = connection;
        }

        public static ClickResult select(GroupRef group) {
            return new ClickResult(Action.SELECT, group, null);
        }

        public static ClickResult rename(GroupRef group) {
            return new ClickResult(Action.RENAME, group, null);
        }

        public static ClickResult export(GroupRef group) {
            return new ClickResult(Action.EXPORT, group, null);
        }

        public static ClickResult delete(GroupRef group) {
            return new ClickResult(Action.DELETE, group, null);
        }

        public static ClickResult open(GroupRef group, ClientConnection connection) {
            return new ClickResult(Action.OPEN_CONNECTION, group, connection);
        }

        public static ClickResult renameConnection(
            GroupRef group,
            ClientConnection connection
        ) {
            return new ClickResult(Action.RENAME_CONNECTION, group, connection);
        }

        public static ClickResult deleteConnection(
            GroupRef group,
            ClientConnection connection
        ) {
            return new ClickResult(Action.DELETE_CONNECTION, group, connection);
        }

        public static ClickResult consumed(GroupRef group) {
            return new ClickResult(Action.CONSUME, group, null);
        }

        public Action getAction() {
            return action;
        }

        public String getGroupId() {
            return group.displayName();
        }

        public GroupRef getGroup() {
            return group;
        }

        @Nullable
        public ClientConnection getConnection() {
            return connection;
        }
    }

}
