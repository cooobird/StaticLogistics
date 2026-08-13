package com.coobird.staticlogistics.client.gui.screen;

import PortLib.extensions.net.minecraft.world.item.ItemStack.PortItemStackExtension;
import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.api.group.GroupKey;
import com.coobird.staticlogistics.api.group.GroupRef;
import com.coobird.staticlogistics.client.data.ClientConnection;
import com.coobird.staticlogistics.client.data.ClientLinkData;
import com.coobird.staticlogistics.client.data.GroupConnectionTreeModel;
import com.coobird.staticlogistics.client.data.SelectionContext;
import com.coobird.staticlogistics.client.gui.component.PlayerAvatarRenderer;
import com.coobird.staticlogistics.client.gui.component.SoundUtil;
import com.coobird.staticlogistics.client.key.SLKeyMappings;
import com.coobird.staticlogistics.client.render.SLGuiTextures;
import com.coobird.staticlogistics.logistics.SLDataComponents;
import com.coobird.staticlogistics.logistics.node.ConnectionKey;
import com.coobird.staticlogistics.logistics.util.NodeDisplayText;
import com.coobird.staticlogistics.network.SLNetwork;
import com.coobird.staticlogistics.network.c2s.C2SUpdateToolConnectionPayload;
import com.coobird.staticlogistics.network.c2s.C2SUpdateToolGroupPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.*;

/**
 * 蓝图分组预览界面。
 *
 * <p>第一次点击分组只切换世界中的链接预览；再次点击同一分组才提交选择并关闭界面。
 * 搜索、列表边框和滚动区域均直接使用蓝图专用 atlas 背景。
 */
public class BlueprintGroupScreen extends Screen {
    private static final int PANEL_W = SLGuiTextures.BlueprintGroup.WIDTH;
    private static final int PANEL_H = SLGuiTextures.BlueprintGroup.HEIGHT;
    private static final int BAR_X = SLGuiTextures.BlueprintGroup.SEARCH_X;
    private static final int BAR_Y = SLGuiTextures.BlueprintGroup.SEARCH_Y;
    private static final int BAR_W = SLGuiTextures.BlueprintGroup.SEARCH_WIDTH;
    private static final int LIST_OFFSET_X = SLGuiTextures.BlueprintGroup.LIST_X;
    private static final int LIST_OFFSET_Y = SLGuiTextures.BlueprintGroup.LIST_Y;
    private static final int LIST_HEIGHT = SLGuiTextures.BlueprintGroup.LIST_HEIGHT;
    private static final int SCROLLBAR_X = SLGuiTextures.BlueprintGroup.SCROLLBAR_X;
    private static final int SCROLLBAR_Y = SLGuiTextures.BlueprintGroup.SCROLLBAR_Y;
    private static final int SCROLL_TRACK_HEIGHT = SLGuiTextures.BlueprintGroup.SCROLL_TRACK_HEIGHT;
    private static final int SELECTION_WIDTH = SLGuiTextures.BlueprintGroup.LIST_WIDTH;

    private final ItemStack stack;
    private int leftPos, topPos;
    private EditBox searchBox;
    private float scrollOffset;
    private boolean isScrolling;
    private double scrollGrabOffset;
    private String confirmedSearchTerm = "";
    private int lastSeenVersion = -1;
    private List<GroupRef> cachedGroupList = Collections.emptyList();
    private List<GroupConnectionTreeModel.Row> cachedRows = Collections.emptyList();
    private final Set<GroupKey> expandedGroups = new HashSet<>();
    private GroupRef hoveredGroup;
    private ClientConnection hoveredConnection;
    private GroupKey pendingConfirmationKey;

    public BlueprintGroupScreen(ItemStack stack) {
        super(Component.translatable("item.staticlogistics.blueprint"));
        this.stack = stack;
    }

    @Override
    protected void init() {
        this.leftPos = Math.min(12, Math.max(0, this.width - PANEL_W));
        this.topPos = Math.max(8, (this.height - PANEL_H) / 2);

        this.searchBox = new EditBox(this.font,
            leftPos + BAR_X, topPos + BAR_Y + 1, BAR_W, 8, Component.empty());
        this.searchBox.setBordered(false);
        this.searchBox.setMaxLength(20);
        this.searchBox.setTextColor(0xFFFFFF);
        this.searchBox.setHint(Component.translatable("gui.staticlogistics.search_hint")
            .withStyle(ChatFormatting.DARK_GRAY));
        this.searchBox.setResponder(value -> {
            this.confirmedSearchTerm = value.trim();
            this.scrollOffset = 0;
            this.lastSeenVersion = -1;
            this.pendingConfirmationKey = null;
            SelectionContext.clearPreview();
        });
        this.addRenderableWidget(this.searchBox);
        this.lastSeenVersion = -1;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        this.hoveredGroup = null;
        this.hoveredConnection = null;

        g.blit(SLGuiTextures.GUI_ATLAS, leftPos, topPos,
            SLGuiTextures.BlueprintGroup.U, SLGuiTextures.BlueprintGroup.V,
            PANEL_W, PANEL_H,
            SLGuiTextures.GUI_WIDTH, SLGuiTextures.GUI_HEIGHT);

        renderGroupList(g, mx, my);
        super.render(g, mx, my, pt);

        if (this.hoveredGroup != null) {
            g.pose().pushPose();
            g.pose().translate(0, 0, 500);
            renderGroupTooltip(g, mx, my, this.hoveredGroup,
                SLKeyMappings.isKeyDown(SLKeyMappings.GROUP_DETAILS_AND_EXPORT));
            g.pose().popPose();
        } else if (this.hoveredConnection != null) {
            g.pose().pushPose();
            g.pose().translate(0, 0, 500);
            renderConnectionTooltip(g, mx, my, this.hoveredConnection);
            g.pose().popPose();
        }
    }

    private void renderGroupList(GuiGraphics g, int mx, int my) {
        List<GroupConnectionTreeModel.Row> rows = getVisibleRows();
        int maxScroll = getMaxScroll();
        int listX = leftPos + LIST_OFFSET_X;
        int listY = topPos + LIST_OFFSET_Y;

        // 滚动条只覆盖列表区域，避免截断搜索框或显示不完整的列表项。
        boolean showActive = maxScroll > 0
            && ((mx >= leftPos + SCROLLBAR_X
            && mx <= leftPos + SCROLLBAR_X + SLGuiTextures.Scrollbar.WIDTH
            && my >= topPos + SCROLLBAR_Y
            && my <= topPos + SCROLLBAR_Y + SCROLL_TRACK_HEIGHT)
            || this.isScrolling);
        int knobY = maxScroll > 0
            ? (int) (scrollOffset / maxScroll
            * (SCROLL_TRACK_HEIGHT - SLGuiTextures.Scrollbar.HEIGHT))
            : 0;
        g.blit(SLGuiTextures.GUI_ATLAS, leftPos + SCROLLBAR_X,
            topPos + SCROLLBAR_Y + knobY,
            showActive ? SLGuiTextures.Scrollbar.ENABLED_U
                : SLGuiTextures.Scrollbar.DISABLED_U,
            SLGuiTextures.Scrollbar.DISABLED_V,
            SLGuiTextures.Scrollbar.WIDTH,
            SLGuiTextures.Scrollbar.HEIGHT,
            SLGuiTextures.GUI_WIDTH, SLGuiTextures.GUI_HEIGHT);

        if (cachedGroupList.isEmpty()) {
            String emptyText = Component.translatable(
                "gui.staticlogistics.blueprint.empty").getString();
            emptyText = this.font.plainSubstrByWidth(emptyText, SELECTION_WIDTH - 4);
            g.drawCenteredString(this.font, emptyText,
                listX + SELECTION_WIDTH / 2, listY + LIST_HEIGHT / 2 - 4, 0x777777);
            return;
        }

        g.enableScissor(listX - 2, listY,
            listX + SELECTION_WIDTH + 2, listY + LIST_HEIGHT);

        String current = PortItemStackExtension.getDataOrDefault(stack, SLDataComponents.SELECTED_GROUP.get(), "");
        GroupKey currentKey = PortItemStackExtension.getData(stack, SLDataComponents.SELECTED_GROUP_KEY.get());

        ConnectionKey focusedConnection = SelectionContext.getFocusedConnectionKey();
        for (int i = 0; i < rows.size(); i++) {
            GroupConnectionTreeModel.Row row = rows.get(i);
            GroupRef group = row.group();
            ClientConnection connection = row.connection();
            String gn = group.displayName();
            int itemY = listY + (i * SLGuiTextures.List.ITEM_H) - (int) scrollOffset;
            if (itemY + SLGuiTextures.List.ITEM_H < listY
                || itemY > listY + LIST_HEIGHT) continue;

            boolean groupRow = connection == null;
            boolean selectedGroup = groupRow && (pendingConfirmationKey != null
                ? pendingConfirmationKey.equals(group.key())
                : currentKey != null ? currentKey.equals(group.key())
                : Objects.equals(current, gn));
            boolean selectedConnection = connection != null
                && connection.key().equals(focusedConnection);
            boolean sel = selectedGroup || selectedConnection;
            boolean hover = mx >= listX
                && mx <= listX + SELECTION_WIDTH
                && my >= itemY && my < itemY + SLGuiTextures.List.ITEM_H;
            if (hover) {
                if (groupRow) this.hoveredGroup = group;
                else this.hoveredConnection = connection;
            }

            if (sel) g.fill(listX, itemY,
                listX + SELECTION_WIDTH,
                itemY + SLGuiTextures.List.ITEM_H, 0x4498FB98);
            else if (hover) g.fill(listX, itemY,
                listX + SELECTION_WIDTH,
                itemY + SLGuiTextures.List.ITEM_H, 0x22FFFFFF);

            int textX = listX + 2;
            if (groupRow) {
                renderChevron(g, textX, itemY,
                    expandedGroups.contains(group.key()));
                textX += 8;
            } else {
                renderConnectionIcon(g, connection, textX, itemY);
                textX += 10;
            }
            UUID ownerUUID = group.key().ownerId();
            if (groupRow && ownerUUID != null) {
                int headSize = 10;
                PlayerAvatarRenderer.render(
                    g, ownerUUID, textX, itemY + 1, headSize);
                textX += headSize + 3;
            }

            int nameWidth = Math.max(0,
                listX + SELECTION_WIDTH - textX - 2);
            String rowName;
            if (groupRow) {
                rowName = "#" + gn;
            } else if (!connection.displayName().isEmpty()) {
                rowName = connection.displayName();
            } else {
                rowName = Component.translatable(
                    "gui.staticlogistics.connection").getString()
                    + " " + row.connectionIndex();
            }
            String displayName = this.font.plainSubstrByWidth(rowName, nameWidth);
            int color = selectedConnection
                ? 0xFFFFD45A : sel ? 0xFF98FB98 : 0xFFCCCCCC;
            g.drawString(this.font, displayName, textX, itemY + 2, color, false);
        }
        g.disableScissor();
    }

    private static void renderChevron(
        GuiGraphics graphics,
        int x,
        int y,
        boolean expanded
    ) {
        if (expanded) {
            graphics.blit(SLGuiTextures.GUI_ATLAS, x,
                y + (SLGuiTextures.List.ITEM_H
                    - SLGuiTextures.Direction.DOWN_HEIGHT) / 2,
                SLGuiTextures.Direction.DOWN_U,
                SLGuiTextures.Direction.ENABLED_V,
                SLGuiTextures.Direction.DOWN_WIDTH,
                SLGuiTextures.Direction.DOWN_HEIGHT,
                SLGuiTextures.GUI_WIDTH, SLGuiTextures.GUI_HEIGHT);
        } else {
            graphics.blit(SLGuiTextures.GUI_ATLAS,
                x + (SLGuiTextures.Direction.DOWN_WIDTH
                    - SLGuiTextures.Direction.HORIZONTAL_WIDTH) / 2,
                y + (SLGuiTextures.List.ITEM_H
                    - SLGuiTextures.Direction.HORIZONTAL_HEIGHT) / 2,
                SLGuiTextures.Direction.RIGHT_U,
                SLGuiTextures.Direction.ENABLED_V,
                SLGuiTextures.Direction.HORIZONTAL_WIDTH,
                SLGuiTextures.Direction.HORIZONTAL_HEIGHT,
                SLGuiTextures.GUI_WIDTH, SLGuiTextures.GUI_HEIGHT);
        }
    }

    private static void renderConnectionIcon(
        GuiGraphics graphics,
        ClientConnection connection,
        int x,
        int y
    ) {
        int iconY = y + (SLGuiTextures.List.ITEM_H
            - SLGuiTextures.Direction.CONNECTION_HEIGHT) / 2;
        if (connection.isBidirectional()) {
            graphics.blit(SLGuiTextures.GUI_ATLAS, x,
                y + (SLGuiTextures.List.ITEM_H
                    - SLGuiTextures.Direction.CONNECTION_BIDIRECTIONAL_HEIGHT) / 2,
                SLGuiTextures.Direction.CONNECTION_BIDIRECTIONAL_U,
                SLGuiTextures.Direction.CONNECTION_BIDIRECTIONAL_V,
                SLGuiTextures.Direction.CONNECTION_BIDIRECTIONAL_WIDTH,
                SLGuiTextures.Direction.CONNECTION_BIDIRECTIONAL_HEIGHT,
                SLGuiTextures.GUI_WIDTH, SLGuiTextures.GUI_HEIGHT);
            return;
        }
        if (connection.isBlocked()) {
            graphics.fill(x + 1, y + 2, x + 7, y + 7, 0xFFFF5555);
            return;
        }
        graphics.blit(SLGuiTextures.GUI_ATLAS, x, iconY,
            connection.transfersFirstToSecond()
                ? SLGuiTextures.Direction.CONNECTION_RIGHT_U
                : SLGuiTextures.Direction.CONNECTION_LEFT_U,
            SLGuiTextures.Direction.CONNECTION_V,
            SLGuiTextures.Direction.CONNECTION_WIDTH,
            SLGuiTextures.Direction.CONNECTION_HEIGHT,
            SLGuiTextures.GUI_WIDTH, SLGuiTextures.GUI_HEIGHT);
    }

    private void renderGroupTooltip(GuiGraphics g, int mx, int my, GroupRef group, boolean shiftDown) {
        Player player = Minecraft.getInstance().player;
        if (player == null) return;

        String gid = group.displayName();
        List<LogisticsNode> nodes = ClientLinkData.INSTANCE.getNodesForGroup(group.key());

        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("gui.staticlogistics.tooltip.group_id", gid)
            .withStyle(ChatFormatting.GOLD));
        String owner = ClientLinkData.INSTANCE.getOwnerName(group.key().ownerId());
        if (!owner.isEmpty())
            lines.add(Component.translatable("msg.staticlogistics.owner_display", owner)
                .withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("gui.staticlogistics.blueprint.connection_count",
                ClientLinkData.INSTANCE.getConnectionsForGroup(group.key()).size())
            .withStyle(ChatFormatting.GRAY));

        if (!nodes.isEmpty()) {
            int maxShown = shiftDown ? Integer.MAX_VALUE : 5;
            int count = 0;
            for (LogisticsNode node : nodes) {
                if (count >= maxShown) break;
                lines.add(formatNode(node, player));
                count++;
            }
            if (nodes.size() > maxShown) {
                lines.add(Component.translatable("gui.staticlogistics.tooltip.shift_more",
                    SLKeyMappings.GROUP_DETAILS_AND_EXPORT.getTranslatedKeyMessage(),
                    nodes.size() - maxShown).withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
            }
            lines.add(Component.empty());
        }

        boolean awaitingConfirmation = group.key().equals(pendingConfirmationKey);
        lines.add(Component.translatable(awaitingConfirmation
                ? "gui.staticlogistics.blueprint.confirm_hint"
                : "gui.staticlogistics.blueprint.preview_hint")
            .withStyle(awaitingConfirmation ? ChatFormatting.GREEN : ChatFormatting.BLUE));
        g.renderComponentTooltip(this.font, lines, mx, my);
    }

    private void renderConnectionTooltip(
        GuiGraphics graphics,
        int mouseX,
        int mouseY,
        ClientConnection connection
    ) {
        Component title = connection.displayName().isEmpty()
            ? Component.translatable("gui.staticlogistics.connection")
            : Component.literal(connection.displayName());
        List<Component> lines = new ArrayList<>();
        lines.add(title.copy().withStyle(ChatFormatting.GOLD));
        lines.add(NodeDisplayText.details(connection.first())
            .withStyle(ChatFormatting.WHITE));
        lines.add(NodeDisplayText.details(connection.second())
            .withStyle(ChatFormatting.WHITE));
        lines.add(Component.translatable(
                "gui.staticlogistics.blueprint.preview_hint")
            .withStyle(ChatFormatting.BLUE));
        graphics.renderComponentTooltip(
            this.font, lines, mouseX, mouseY);
    }

    private static Component formatNode(LogisticsNode node, Player player) {
        BlockPos pos = node.gPos().pos();
        var location = NodeDisplayText.details(node).withStyle(ChatFormatting.WHITE);
        if (!node.isInSameDimension(player.level().dimension())) return location;
        double distance = Math.sqrt(pos.distToCenterSqr(player.position()));
        return location.append(Component.literal("  "))
            .append(NodeDisplayText.distanceFromPlayer(distance).withStyle(ChatFormatting.AQUA));
    }

    private int getMaxScroll() {
        return Math.max(0,
            getVisibleRows().size() * SLGuiTextures.List.ITEM_H
                - LIST_HEIGHT);
    }

    private List<GroupRef> getFilteredGroups() {
        int cv = ClientLinkData.INSTANCE.getDataVersion();
        if (cv == lastSeenVersion) return cachedGroupList;

        Player p = Minecraft.getInstance().player;
        if (p == null) return Collections.emptyList();

        String filter = this.confirmedSearchTerm.toLowerCase(Locale.ROOT);
        this.cachedGroupList = GroupConnectionTreeModel.filterAndSort(
            ClientLinkData.INSTANCE.getAccessibleGroupRefs(), filter);
        if (pendingConfirmationKey != null
            && this.cachedGroupList.stream().noneMatch(
            group -> group.key().equals(pendingConfirmationKey))) {
            pendingConfirmationKey = null;
            SelectionContext.clearPreview();
        }

        this.lastSeenVersion = cv;
        rebuildRows();
        return cachedGroupList;
    }

    private List<GroupConnectionTreeModel.Row> getVisibleRows() {
        getFilteredGroups();
        return cachedRows;
    }

    private void rebuildRows() {
        this.cachedRows = GroupConnectionTreeModel.buildRows(
            cachedGroupList, expandedGroups);
        this.scrollOffset = Mth.clamp(
            this.scrollOffset, 0, getMaxScrollWithoutRefresh());
    }

    private int getMaxScrollWithoutRefresh() {
        return Math.max(0,
            cachedRows.size() * SLGuiTextures.List.ITEM_H - LIST_HEIGHT);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int b) {
        if (this.searchBox.mouseClicked(mx, my, b)) return true;

        int sx = leftPos + SCROLLBAR_X, sy = topPos + SCROLLBAR_Y;
        if (b == 0
            && mx >= sx && mx <= sx + SLGuiTextures.Scrollbar.WIDTH
            && my >= sy && my <= sy + SCROLL_TRACK_HEIGHT) {
            if (getMaxScroll() > 0) {
                this.isScrolling = true;
                int knobY = scrollKnobOffset(getMaxScroll());
                this.scrollGrabOffset = Mth.clamp(
                    my - (sy + knobY), 0, SLGuiTextures.Scrollbar.HEIGHT);
                updateScrollFromMouse(my);
            }
            return true;
        }

        List<GroupConnectionTreeModel.Row> rows = getVisibleRows();
        int listY = topPos + LIST_OFFSET_Y;
        if (b == 0
            && mx >= leftPos + LIST_OFFSET_X
            && mx <= leftPos + LIST_OFFSET_X + SELECTION_WIDTH
            && my >= listY && my < listY + LIST_HEIGHT) {
            for (int i = 0; i < rows.size(); i++) {
                int iy = listY + (i * SLGuiTextures.List.ITEM_H) - (int) scrollOffset;
                if (my >= iy && my < iy + SLGuiTextures.List.ITEM_H) {
                    GroupConnectionTreeModel.Row row = rows.get(i);
                    if (row.connection() != null) {
                        handleConnectionClick(
                            row.group(), row.connection());
                    } else if (mx < leftPos + LIST_OFFSET_X + 10) {
                        if (!expandedGroups.add(row.group().key())) {
                            expandedGroups.remove(row.group().key());
                        }
                        rebuildRows();
                        playClickSound();
                    } else {
                        handleGroupClick(row.group());
                    }
                    return true;
                }
            }
        }
        return super.mouseClicked(mx, my, b);
    }

    private void handleGroupClick(GroupRef group) {
        if (group.key().equals(this.pendingConfirmationKey)
            && SelectionContext.getFocusedConnectionKey() == null) {
            confirmSelection(group);
            return;
        }
        this.pendingConfirmationKey = group.key();
        SelectionContext.preview(group.displayName(), group.key());
        SelectionContext.clearConnectionFocus();
        Player player = Minecraft.getInstance().player;
        if (player != null) {
            player.displayClientMessage(Component.translatable(
                "gui.staticlogistics.blueprint.previewing", group.displayName()
            ).withStyle(ChatFormatting.GREEN), true);
        }
        playClickSound();
    }

    /**
     * 单条连接只缩小世界预览，不改变蓝图最终捕获的分组。
     */
    private void handleConnectionClick(
        GroupRef group,
        ClientConnection connection
    ) {
        this.pendingConfirmationKey = group.key();
        SelectionContext.preview(group.displayName(), group.key());
        SelectionContext.focusConnection(connection.key());
        playClickSound();
    }

    private void confirmSelection(GroupRef group) {
        String groupId = group.displayName();
        PortItemStackExtension.setData(stack, SLDataComponents.SELECTED_GROUP.get(), groupId);
        PortItemStackExtension.setData(stack, SLDataComponents.SELECTED_GROUP_KEY.get(), group.key());
        PortItemStackExtension.removeData(stack, SLDataComponents.SELECTED_CONNECTION_KEY.get());
        SelectionContext.setGroupSelection(groupId, group.key());
        SelectionContext.clearConnectionFocus();
        SLNetwork.HANDLER.sendToServer(
            new C2SUpdateToolGroupPayload(groupId, group.key()));
        SLNetwork.HANDLER.sendToServer(
            new C2SUpdateToolConnectionPayload(null));
        Player player = Minecraft.getInstance().player;
        if (player != null) {
            player.displayClientMessage(Component.translatable(
                "gui.staticlogistics.blueprint.confirmed", groupId
            ).withStyle(ChatFormatting.GREEN), true);
        }
        playClickSound();
        this.onClose();
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dy) {
        int listX = leftPos + LIST_OFFSET_X;
        int listY = topPos + LIST_OFFSET_Y;
        if (mx < listX || mx > listX + SELECTION_WIDTH
            || my < listY || my >= listY + LIST_HEIGHT) {
            return super.mouseScrolled(mx, my, dy);
        }
        int ms = getMaxScroll();
        if (ms > 0) {
            this.scrollOffset = Mth.clamp(
                this.scrollOffset - (float) dy * SLGuiTextures.List.ITEM_H, 0, ms);
            return true;
        }
        return super.mouseScrolled(mx, my, dy);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int b, double dx, double dy) {
        if (this.isScrolling) {
            updateScrollFromMouse(my);
            return true;
        }
        return super.mouseDragged(mx, my, b, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int b) {
        this.isScrolling = false;
        return super.mouseReleased(mx, my, b);
    }

    private void updateScrollFromMouse(double mouseY) {
        int ms = getMaxScroll();
        if (ms > 0) {
            int travel = Math.max(0,
                SCROLL_TRACK_HEIGHT - SLGuiTextures.Scrollbar.HEIGHT);
            if (travel == 0) {
                this.scrollOffset = 0;
                return;
            }
            float rp = (float) ((mouseY - scrollGrabOffset
                - (topPos + SCROLLBAR_Y)) / travel);
            this.scrollOffset = Mth.clamp(rp * ms, 0, ms);
        }
    }

    private int scrollKnobOffset(int maxScroll) {
        if (maxScroll <= 0) return 0;
        int travel = Math.max(0,
            SCROLL_TRACK_HEIGHT - SLGuiTextures.Scrollbar.HEIGHT);
        return Math.round(scrollOffset / maxScroll * travel);
    }

    private void playClickSound() {
        SoundUtil.playClickSound();
    }

    @Override
    public void renderBackground(GuiGraphics g) {
    }

    @Override
    public void onClose() {
        SelectionContext.clearPreview();
        super.onClose();
    }

    @Override
    public void removed() {
        SelectionContext.clearPreview();
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean keyPressed(int k, int s, int m) {
        if (this.searchBox.canConsumeInput()) {
            if (k == 257 || k == 335) {
                this.confirmedSearchTerm = this.searchBox.getValue().trim();
                this.scrollOffset = 0;
                this.lastSeenVersion = -1;
                return true;
            }
            return super.keyPressed(k, s, m);
        }
        if (Minecraft.getInstance().options.keyInventory.matches(k, s) || k == 256) {
            this.onClose();
            return true;
        }
        return super.keyPressed(k, s, m);
    }
}
