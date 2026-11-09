package com.coobird.staticlogistics.client.gui.screen;

import com.coobird.staticlogistics.api.group.GroupKey;
import com.coobird.staticlogistics.client.data.ClientConnection;
import com.coobird.staticlogistics.client.data.ClientLinkData;
import com.coobird.staticlogistics.client.data.ConnectionDirectoryModel;
import com.coobird.staticlogistics.client.data.ConnectionDirectoryModel.Row;
import com.coobird.staticlogistics.client.data.SelectedWorldLinks;
import com.coobird.staticlogistics.client.gui.component.*;
import com.coobird.staticlogistics.client.render.SLGuiTextures;
import com.coobird.staticlogistics.content.item.BlueprintModeHandler;
import com.coobird.staticlogistics.logistics.SLDataComponents;
import com.coobird.staticlogistics.logistics.blueprint.BlueprintData;
import com.coobird.staticlogistics.logistics.group.GroupHierarchy;
import com.coobird.staticlogistics.logistics.node.ConnectionKey;
import com.coobird.staticlogistics.network.c2s.C2SBeginBlueprintPlacementPayload;
import com.coobird.staticlogistics.network.c2s.C2SCopyBlueprintSelectionPayload;
import com.coobird.staticlogistics.network.c2s.C2SGroupHierarchyPayload;
import com.coobird.staticlogistics.network.c2s.C2SSelectBlueprintGroupPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.coobird.staticlogistics.client.gui.ConfiguratorLayout.Blueprint.*;

/// 配置器内嵌蓝图页，复用 Screen 的文本输入支持，但不单独打开或替换容器菜单。
public final class BlueprintPage extends Screen {
    private final ItemStack tool;
    private final LinkConfiguratorScreen host;
    private final int toolSlot;
    private final BlueprintBlockViewport viewport = new BlueprintBlockViewport();
    private final Set<String> collapsed = new HashSet<>();
    private List<Row> rows = List.of();
    private CompoundTag hierarchy = new CompoundTag();
    private EditBox search;
    private AtlasButton scopeButton, createButton, selectAllButton, resetViewButton;
    private boolean scopeOpen;
    private int scopePage;
    private long seenScopeRevision = -1;
    private GroupKey selectedGroup;
    private List<BlockPos> blocks = List.of();
    private int left, top, panelWidth, panelHeight, split, scroll, version = -1, ticks;
    private UUID pendingCopy;
    private boolean placeAfterCopy;
    private int pendingTicks;
    private long helpSince;
    private int helpX, helpY;
    private boolean initialized, directoryDragging;
    private double scrollGrab;
    private ResourceKey<Level> dimension;
    private String error = "";
    private double interfaceScale = 1;
    private List<ClientConnection> allConnections = List.of();
    private final Set<ConnectionKey> selectedConnections = new LinkedHashSet<>();
    private ConnectionKey rangeAnchor;
    private String selectedDirectory = "";

    public record ViewState(GroupKey group, Set<ConnectionKey> connections, Set<String> collapsed, String query,
                            String directory, int scroll, BlueprintBlockViewport.ViewState viewport) {
    }

    public ViewState saveView() {
        return new ViewState(selectedGroup, Set.copyOf(selectedConnections), Set.copyOf(collapsed),
            search == null ? "" : search.getValue(), selectedDirectory, scroll, viewport.saveView());
    }

    public void restoreView(ViewState view) {
        selectedGroup = view.group();
        selectedConnections.clear();
        view.connections().stream().filter(key -> ClientLinkData.INSTANCE.findConnection(key) != null)
            .forEach(selectedConnections::add);
        collapsed.clear();
        collapsed.addAll(view.collapsed());
        selectedDirectory = view.directory();
        search.setValue(view.query());
        rebuildRows();
        scroll = Math.clamp(view.scroll(), 0, maxScroll());
        refreshBlocks(true);
        viewport.restoreView(view.viewport());
    }

    public BlueprintPage(ItemStack stack, LinkConfiguratorScreen host) {
        super(Component.translatable("mode.staticlogistics.blueprint"));
        tool = stack;
        this.host = host;
        var player = Minecraft.getInstance().player;
        toolSlot = player == null ? -1 : stack == player.getOffhandItem() ? 40 : player.getInventory().selected;
        selectedGroup = stack.get(SLDataComponents.BLUEPRINT_GROUP_KEY.get());
        SelectedWorldLinks.blueprint().stream().filter(key -> Objects.equals(key.groupKey(), selectedGroup))
            .filter(key -> ClientLinkData.INSTANCE.findConnection(key) != null).forEach(selectedConnections::add);
    }

    private static Component tr(String key) {
        return Component.translatable("gui.staticlogistics.blueprint." + key);
    }

    /// 内嵌页只初始化自己的控件，不发布独立 Screen 的初始化事件。
    public void layout(Minecraft client, int width, int height) {
        this.minecraft = client;
        this.font = client.font;
        this.width = width;
        this.height = height;
        clearWidgets();
        setFocused(null);
        setDragging(false);
        init();
    }

    @Override
    protected void init() {
        directoryDragging = false;
        String query = search == null ? "" : search.getValue();
        interfaceScale = host.interfaceScale();
        panelWidth = SLGuiTextures.LinkConfigurator.WIDTH;
        panelHeight = SLGuiTextures.LinkConfigurator.HEIGHT - SLGuiTextures.LinkConfigurator.CONTENT_INSET;
        left = host.contentLeft();
        top = host.contentTop();
        split = left + SPLIT_X;
        scopeOpen = false;
        scopeButton = addRenderableWidget(AtlasButton.atlasBuilder(Component.empty(), b -> scopeOpen = !scopeOpen)
            .bounds(split + SCOPE_X, top + SCOPE_Y, SCOPE_WIDTH, BUTTON_HEIGHT).build());
        scopeButton.setTooltip(Tooltip.create(Component.translatable("gui.staticlogistics.directory.scope_hint")));
        int footerY = top + panelHeight - FOOTER_BOTTOM;
        selectAllButton = addRenderableWidget(AtlasButton.atlasBuilder(tr("select_all"), b -> viewport.selectAll())
            .bounds(left + SELECT_ALL_X, footerY, SELECT_ALL_WIDTH, BUTTON_HEIGHT).build());
        resetViewButton = addRenderableWidget(AtlasButton.atlasBuilder(tr("reset_view"), b -> viewport.resetView())
            .bounds(left + RESET_VIEW_X, footerY, RESET_VIEW_WIDTH, BUTTON_HEIGHT).build());
        createButton = addRenderableWidget(AtlasButton.atlasBuilder(tr("create"), b -> {
            copy(true);
        }).bounds(split - CREATE_RIGHT_INSET, footerY, CREATE_WIDTH, BUTTON_HEIGHT).build().selection(() -> true));
        search = new AtlasEditBox(font, split + SEARCH_X, top + SEARCH_Y, left + panelWidth - split - SEARCH_WIDTH_INSET, SEARCH_HEIGHT, Component.translatable("gui.staticlogistics.directory.search"));
        ((AtlasEditBox) search).setRightInset(12);
        search.setMaxLength(64);
        search.setHint(Component.translatable("gui.staticlogistics.directory.search"));
        search.setValue(query);
        search.setResponder(value -> {
            scroll = 0;
            rebuildRows();
        });
        addRenderableWidget(search);
        PacketDistributor.sendToServer(new C2SGroupHierarchyPayload("query", "", "", ""));
        refreshBlocks(!initialized);
        initialized = true;
        rebuildRows();
    }

    public void acceptHierarchy(CompoundTag data) {
        hierarchy = data == null ? new CompoundTag() : data.copy();
        rebuildRows();
    }

    private void selectScope(UUID owner) {
        if (Objects.equals(ConnectionDirectoryModel.selectedOwner(), owner)) return;
        ConnectionDirectoryModel.setOwnerScope(owner);
        selectedDirectory = "";
        selectedGroup = null;
        selectedConnections.clear();
        rangeAnchor = null;
        scroll = 0;
        refreshBlocks(true);
        rebuildRows();
    }

    private void rebuildRows() {
        if (seenScopeRevision >= 0 && seenScopeRevision != ConnectionDirectoryModel.scopeRevision()) {
            selectedDirectory = "";
            selectedGroup = null;
            selectedConnections.clear();
            rangeAnchor = null;
            scroll = 0;
            refreshBlocks(true);
        }
        seenScopeRevision = ConnectionDirectoryModel.scopeRevision();
        allConnections = ConnectionDirectoryModel.connections();
        var names = ConnectionDirectoryModel.connectionNames(allConnections);
        rows = ConnectionDirectoryModel.rows(hierarchy, allConnections, collapsed, search == null ? "" : search.getValue(), connection -> names.get(connection.key()));
    }

    private List<ClientConnection> selectedRoutes() {
        return selectedConnections.stream().map(key -> key.groupKey()).distinct()
            .flatMap(group -> ClientLinkData.INSTANCE.getConnectionRoutes(group).stream()).filter(route -> selectedConnections.contains(route.key())).toList();
    }

    private void refreshBlocks(boolean reset) {
        SelectedWorldLinks.setBlueprint(selectedConnections);
        var level = minecraft.level;
        var nextDimension = level == null ? null : level.dimension();
        reset |= !Objects.equals(dimension, nextDimension);
        dimension = nextDimension;
        List<ClientConnection> routes = level == null || selectedGroup == null ? List.of() : selectedRoutes();
        List<BlockPos> next = routes.stream().flatMap(route -> Stream.of(route.first(), route.second()))
            .filter(node -> node.isInSameDimension(level.dimension()))
            .map(node -> node.gPos().pos()).distinct()
            .filter(pos -> level.getChunkSource().hasChunk(pos.getX() >> 4, pos.getZ() >> 4)
                && !level.getBlockState(pos).isAir()).sorted().toList();
        if (reset || !blocks.equals(next)) {
            blocks = next;
            viewport.setBlocks(blocks, reset);
        }
        if (reset || version != ClientLinkData.INSTANCE.getDataVersion()) viewport.setConnections(routes.stream()
            .filter(link -> link.first().isInSameDimension(level.dimension()) && link.second().isInSameDimension(level.dimension())).toList());
        version = ClientLinkData.INSTANCE.getDataVersion();
        if (reset) viewport.selectAll();
    }

    @Override
    public void tick() {
        if (minecraft.player == null || BlueprintModeHandler.tool(minecraft.player, toolSlot).isEmpty()) {
            onClose();
            return;
        }
        if (version != ClientLinkData.INSTANCE.getDataVersion() || ++ticks % 20 == 0) {
            refreshBlocks(false);
            rebuildRows();
        }
        if (pendingCopy != null && ++pendingTicks > 100) {
            pendingCopy = null;
            error = tr("copy_failed").getString();
        }
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partialTick) {
        renderInterface(g, mx, my, partialTick);
    }

    private void renderInterface(GuiGraphics g, int mx, int my, float partialTick) {
        ConfiguratorStyle.window(g, left, top, panelWidth, panelHeight);
        ConfiguratorStyle.window(g, left + LEFT_PANEL_X, top + LEFT_PANEL_Y,
            split - left - LEFT_PANEL_WIDTH_INSET, panelHeight - LEFT_PANEL_HEIGHT_INSET);
        ConfiguratorStyle.window(g, split, top + RIGHT_PANEL_Y,
            left + panelWidth - split - RIGHT_PANEL_WIDTH_INSET, panelHeight - RIGHT_PANEL_HEIGHT_INSET);
        g.drawString(font, title, left + TITLE_X, top + TITLE_Y, ConfiguratorStyle.ACCENT, false);
        SLGuiTextures.LIST_SURFACE.frame(g, left + VIEWPORT_FRAME_X, top + VIEWPORT_FRAME_Y, split - left - VIEWPORT_FRAME_WIDTH_INSET, panelHeight - VIEWPORT_FRAME_HEIGHT_INSET, 1);
        viewport.render(g, left + VIEWPORT_X, top + VIEWPORT_Y, split - left - VIEWPORT_WIDTH_INSET, panelHeight - VIEWPORT_HEIGHT_INSET, interfaceScale);
        ConfiguratorStyle.window(g, left + FOOTER_FRAME_X, top + panelHeight - FOOTER_FRAME_BOTTOM, split - left - FOOTER_FRAME_WIDTH_INSET, FOOTER_FRAME_HEIGHT);
        if (blocks.isEmpty())
            g.drawString(font, tr(selectedGroup == null ? "choose_group" : "no_loaded_blocks"), (left + (split - left) / 2) - font.width(tr(selectedGroup == null ? "choose_group" : "no_loaded_blocks")) / 2, top + panelHeight / 2, ConfiguratorStyle.MUTED_TEXT, false);
        if (!error.isEmpty())
            g.drawString(font, font.plainSubstrByWidth(error, split - left - ERROR_WIDTH_INSET), left + ERROR_X, top + panelHeight - ERROR_BOTTOM, 0xFFFFAAAA, false);
        int rowTop = top + DIRECTORY_TOP;
        int listX = split + DIRECTORY_LIST_X;
        int listRight = left + panelWidth - DIRECTORY_SCROLL_X_INSET - DIRECTORY_SCROLL_GAP;
        SLGuiTextures.LIST_SURFACE.frame(g, listX + DIRECTORY_FRAME_X_OFFSET, rowTop + DIRECTORY_FRAME_Y_OFFSET,
            listRight - listX + DIRECTORY_FRAME_WIDTH_ADJUST, panelHeight - DIRECTORY_FRAME_HEIGHT_INSET, 1);
        scroll = Math.clamp(scroll, 0, maxScroll());
        GuiScissor.enable(g, interfaceScale, listX, rowTop, listRight, top + panelHeight - DIRECTORY_BOTTOM_INSET);
        for (int i = scroll / DIRECTORY_ROW_STEP; i < rows.size(); i++) {
            Row row = rows.get(i);
            int ry = rowTop + i * DIRECTORY_ROW_STEP - scroll, rx = split + DIRECTORY_ROW_X + row.depth() * DIRECTORY_DEPTH_INDENT;
            if (ry >= top + panelHeight - DIRECTORY_BOTTOM_INSET) break;
            boolean selected = row.connection() != null ? selectedConnections.contains(row.connection().key()) : row.id().equals(selectedDirectory);
            boolean hovered = mx >= listX && mx < listRight && my >= ry && my < ry + DIRECTORY_ROW_HEIGHT;
            if (selected) ConfiguratorStyle.row(g, listX, ry, listRight - listX, DIRECTORY_ROW_HEIGHT, true, false);
            else {
                g.fill(listX, ry, listRight, ry + DIRECTORY_ROW_HEIGHT, 0xFFC2C2C2);
                g.fill(listX, ry + DIRECTORY_ROW_HEIGHT - DIRECTORY_DIVIDER_HEIGHT, listRight, ry + DIRECTORY_ROW_HEIGHT, 0xFF999999);
            }
            if (row.connection() != null && row.depth() > 0) {
                int lineX = listX + DIRECTORY_TREE_X;
                boolean hasNextChild = i + 1 < rows.size() && rows.get(i + 1).depth() > 0;
                int lineEnd = hasNextChild ? DIRECTORY_ROW_HEIGHT : DIRECTORY_ROW_HEIGHT / 2 + 1;
                for (int dy = 0; dy < lineEnd; dy += DIRECTORY_TREE_DASH_STEP)
                    g.fill(lineX, ry + dy, lineX + DIRECTORY_TREE_LINE_WIDTH, ry + Math.min(dy + DIRECTORY_TREE_DASH_LENGTH, lineEnd), 0xFF444444);
                for (int dx = 0; dx < DIRECTORY_TREE_BRANCH_LENGTH; dx += DIRECTORY_TREE_DASH_STEP)
                    g.fill(lineX + dx, ry + DIRECTORY_ROW_HEIGHT / 2,
                        lineX + Math.min(dx + DIRECTORY_TREE_DASH_LENGTH, DIRECTORY_TREE_BRANCH_LENGTH), ry + DIRECTORY_ROW_HEIGHT / 2 + DIRECTORY_TREE_LINE_WIDTH, 0xFF444444);
            }
            if (row.connection() == null) {
                ConfiguratorStyle.disclosure(g, rx + FOLDER_ARROW_X, ry + FOLDER_ARROW_Y, collapsed.contains(row.id()));
                ConfiguratorStyle.directory(g, rx + FOLDER_ICON_X, ry + FOLDER_ICON_Y, selected);
            } else
                g.renderItem(ConnectionDirectoryModel.connectionIcon(row.connection()), rx + CONNECTION_ICON_X, ry + CONNECTION_ICON_Y);
            int textX = rx + (row.connection() == null ? DIRECTORY_FOLDER_TEXT_X : DIRECTORY_CONNECTION_TEXT_X);
            int textRight = listRight - DIRECTORY_TEXT_RIGHT_INSET;
            g.drawString(font, ConfiguratorStyle.scrollingText(font, row.name(),
                Math.max(0, textRight - textX), hovered && row.connection() != null), textX, ry + DIRECTORY_TEXT_Y, ConfiguratorStyle.TEXT, false);
        }
        g.flush();
        g.disableScissor();
        if (maxScroll() > 0) {
            int track = panelHeight - DIRECTORY_SCROLL_HEIGHT_INSET;
            int knobY = scrollThumbY();
            int scrollbarY = top + DIRECTORY_SCROLL_Y;
            boolean active = directoryDragging || mx >= left + panelWidth - DIRECTORY_SCROLL_X_INSET
                && mx < left + panelWidth - DIRECTORY_SCROLL_RIGHT_INSET && my >= scrollbarY && my < scrollbarY + track;
            ConfiguratorStyle.scrollbar(g, left + panelWidth - DIRECTORY_SCROLL_X_INSET, scrollbarY, track, knobY - scrollbarY, active);
        }
        int titleWidth = Math.max(0, SCOPE_X - DIRECTORY_TITLE_X - DIRECTORY_TITLE_GAP);
        String directoryTitle = font.plainSubstrByWidth(Component.translatable("gui.staticlogistics.directory.title").getString(), titleWidth);
        g.drawString(font, directoryTitle, split + DIRECTORY_TITLE_X, top + DIRECTORY_TITLE_Y, ConfiguratorStyle.TEXT, false);
        scopeButton.setMessage(ConnectionDirectoryModel.scopeLabel());
        super.render(g, mx, my, partialTick);
        (search.isFocused() || isSearchButtonHit(mx, my) ? SLGuiTextures.SEARCH_SELECTED : SLGuiTextures.SEARCH)
            .draw(g, search.getX() + search.getWidth() - SEARCH_ICON_RIGHT_INSET, search.getY() + SEARCH_ICON_Y_OFFSET);
        SLGuiTextures.ARROW_DOWN.draw(g, split + SCOPE_X + SCOPE_WIDTH - SCOPE_ARROW_RIGHT_INSET, top + SCOPE_Y + SCOPE_ARROW_Y_OFFSET);
        if (scopeOpen) {
            var owners = ConnectionDirectoryModel.availableOwners();
            int pages = Math.max(1, (owners.size() + 4) / 5);
            scopePage = Math.max(0, Math.min(scopePage, pages - 1));
            int visible = Math.min(5, owners.size() - scopePage * 5);
            int menuX = split + SCOPE_X - SCOPE_MENU_LEFT_OFFSET;
            Component ownerTip = null;
            g.pose().pushPose();
            g.pose().translate(0, 0, 600);
            for (int i = 0; i < 2 + visible; i++) {
                int rowY = top + SCOPE_Y + BUTTON_HEIGHT + SCOPE_MENU_GAP + i * SCOPE_MENU_ROW_HEIGHT;
                UUID owner = i == 0 ? null : i == 1 ? minecraft.player.getUUID() : owners.get(scopePage * 5 + i - 2);
                Component label;
                if (i == 0) label = Component.translatable("gui.staticlogistics.directory.all");
                else if (i == 1) label = Component.translatable("gui.staticlogistics.directory.own");
                else {
                    int permission = ConnectionDirectoryModel.ownerPermission(owner);
                    String state = Component.translatable(permission == 2 ? "gui.staticlogistics.permissions.manage"
                        : permission == 1 ? "gui.staticlogistics.permissions.view" : "gui.staticlogistics.permissions.no_access").getString();
                    label = Component.literal(font.plainSubstrByWidth(ConnectionDirectoryModel.ownerName(owner), Math.max(0, 90 - font.width(state) - font.width("  "))) + "  " + state);
                }
                ConfiguratorStyle.button(g, menuX, rowY, SCOPE_MENU_WIDTH, SCOPE_MENU_ROW_HEIGHT,
                    Objects.equals(ConnectionDirectoryModel.selectedOwner(), owner),
                    mx >= menuX && mx < menuX + SCOPE_MENU_WIDTH && my >= rowY && my < rowY + SCOPE_MENU_ROW_HEIGHT, true);
                g.drawString(font, font.plainSubstrByWidth(label.getString(), SCOPE_MENU_WIDTH - 2 * SCOPE_MENU_TEXT_X), menuX + SCOPE_MENU_TEXT_X, rowY + SCOPE_MENU_TEXT_Y, ConfiguratorStyle.TEXT, false);
                if (owner != null && i >= 2 && mx >= menuX && mx < menuX + SCOPE_MENU_WIDTH
                    && my >= rowY && my < rowY + SCOPE_MENU_ROW_HEIGHT) {
                    ownerTip = ConnectionDirectoryModel.ownerTeamPermission(owner) > 0
                        ? Component.translatable("gui.staticlogistics.permissions.team_source", ConnectionDirectoryModel.ownerName(owner))
                        : Component.literal(ConnectionDirectoryModel.ownerName(owner));
                }
            }
            if (pages > 1) {
                int rowY = top + SCOPE_Y + BUTTON_HEIGHT + SCOPE_MENU_GAP + (2 + visible) * SCOPE_MENU_ROW_HEIGHT;
                ConfiguratorStyle.button(g, menuX, rowY, SCOPE_MENU_WIDTH, SCOPE_MENU_ROW_HEIGHT, false, false, true);
                Component paging = Component.literal("<   " + (scopePage + 1) + "/" + pages + "   >");
                g.drawString(font, paging, menuX + SCOPE_MENU_WIDTH / 2 - font.width(paging) / 2, rowY + SCOPE_MENU_TEXT_Y, ConfiguratorStyle.TEXT, false);
            }
            g.pose().popPose();
            if (ownerTip != null) g.renderTooltip(font, ownerTip, mx, my);
        }
        boolean showHelp = pendingCopy == null && !directoryDragging && !search.isFocused() && viewport.isIdleEmptySpace(mx, my);
        long now = System.nanoTime();
        if (!showHelp || mx != helpX || my != helpY) {
            helpSince = now;
            helpX = mx;
            helpY = my;
        }
        if (showHelp && now - helpSince >= 500_000_000L) {
            g.renderComponentTooltip(font, List.of(tr("controls_select"), tr("controls_camera"), tr("controls_anchor"), tr("controls_copy"), tr("controls_paste"), tr("controls_links")), mx, my);
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (pendingCopy != null) return true;
        if (scopeOpen) {
            int menuX = split + SCOPE_X - SCOPE_MENU_LEFT_OFFSET;
            int menuY = top + SCOPE_Y + BUTTON_HEIGHT + SCOPE_MENU_GAP;
            var owners = ConnectionDirectoryModel.availableOwners();
            int pages = Math.max(1, (owners.size() + 4) / 5);
            int visible = Math.min(5, owners.size() - scopePage * 5);
            int row = (int) ((my - menuY) / SCOPE_MENU_ROW_HEIGHT);
            if (button == 0 && mx >= menuX && mx < menuX + SCOPE_MENU_WIDTH && my >= menuY) {
                if (row == 2 + visible && pages > 1) {
                    scopePage = Math.max(0, Math.min(pages - 1, scopePage + (mx < menuX + SCOPE_MENU_WIDTH / 2 ? -1 : 1)));
                    return true;
                }
                if (row >= 0 && row < 2 + visible) {
                    UUID owner = row == 0 ? null : row == 1 ? minecraft.player.getUUID() : owners.get(scopePage * 5 + row - 2);
                    scopeOpen = false;
                    selectScope(owner);
                    return true;
                }
            }
            scopeOpen = false;
            return true;
        }
        if (scopeButton.mouseClicked(mx, my, button)) return true;
        if (selectAllButton.mouseClicked(mx, my, button)) return true;
        if (resetViewButton.mouseClicked(mx, my, button)) return true;
        if (createButton.mouseClicked(mx, my, button)) return true;
        if (button == 0 && isSearchButtonHit(mx, my)) {
            scroll = 0;
            rebuildRows();
            search.setFocused(true);
            setFocused(search);
            SoundUtil.playClickSound();
            return true;
        }
        if (search.mouseClicked(mx, my, button)) {
            setFocused(search);
            return true;
        }
        search.setFocused(false);
        setFocused(null);
        if (button == 0 && maxScroll() > 0 && mx >= left + panelWidth - DIRECTORY_SCROLL_X_INSET && mx < left + panelWidth - DIRECTORY_SCROLL_RIGHT_INSET
            && my >= top + DIRECTORY_SCROLL_Y + Math.min(0, DIRECTORY_SCROLL_THUMB_Y_OFFSET)
            && my < top + DIRECTORY_SCROLL_Y + panelHeight - DIRECTORY_SCROLL_HEIGHT_INSET + Math.max(0, DIRECTORY_SCROLL_THUMB_Y_OFFSET)) {
            directoryDragging = true;
            int thumbY = scrollThumbY();
            scrollGrab = my >= thumbY && my < thumbY + scrollThumbHeight() ? my - thumbY : scrollThumbHeight() / 2.0;
            dragDirectory(my);
            return true;
        }
        if (button == 0 && mx >= split + DIRECTORY_LIST_X && mx < left + panelWidth - DIRECTORY_SCROLL_X_INSET - DIRECTORY_SCROLL_GAP && my >= top + DIRECTORY_TOP && my < top + panelHeight - DIRECTORY_BOTTOM_INSET) {
            int index = (scroll + (int) (my - top - DIRECTORY_TOP)) / DIRECTORY_ROW_STEP;
            if (index < rows.size()) {
                Row row = rows.get(index);
                if (row.connection() == null) {
                    if (mx < split + 20 + row.depth() * DIRECTORY_DEPTH_INDENT) {
                        if (!collapsed.add(row.id())) collapsed.remove(row.id());
                        rebuildRows();
                    } else {
                        var entries = hierarchy.getCompound("entries");
                        List<ConnectionKey> directoryConnections = new ArrayList<>();
                        for (var connection : allConnections) {
                            String folder = GroupHierarchy.connectionFolder(hierarchy, connection.key());
                            if (folder.equals(row.id()) || entries.getCompound(folder).getString("parent").equals(row.id())) {
                                directoryConnections.add(connection.key());
                            }
                        }
                        Set<GroupKey> groups = directoryConnections.stream()
                            .map(ConnectionKey::groupKey).collect(Collectors.toSet());
                        GroupKey directoryGroup = selectedGroup != null && groups.contains(selectedGroup)
                            ? selectedGroup : groups.size() == 1 ? groups.iterator().next() : null;
                        if (directoryGroup == null) {
                            selectedDirectory = "";
                            selectedConnections.clear();
                            selectedGroup = null;
                            rangeAnchor = null;
                            refreshBlocks(true);
                            error = tr("mixed_groups").getString();
                            return true;
                        }
                        selectedDirectory = row.id();
                        selectedGroup = directoryGroup;
                        selectedConnections.clear();
                        directoryConnections.stream().filter(key -> key.groupKey().equals(directoryGroup))
                            .forEach(selectedConnections::add);
                        error = "";
                        refreshBlocks(true);
                        PacketDistributor.sendToServer(new C2SSelectBlueprintGroupPayload(toolSlot, selectedGroup));
                    }
                } else {
                    selectedDirectory = "";
                    var key = row.connection().key();
                    if (!key.groupKey().equals(selectedGroup)) {
                        selectedConnections.clear();
                        rangeAnchor = null;
                    }
                    selectedGroup = key.groupKey();
                    var order = rows.stream().filter(r -> r.connection() != null
                            && r.connection().key().groupKey().equals(selectedGroup))
                        .map(r -> r.connection().key()).toList();
                    int start = order.indexOf(rangeAnchor), end = order.indexOf(key);
                    if (hasShiftDown() && start >= 0) {
                        if (!hasControlDown()) selectedConnections.clear();
                        selectedConnections.addAll(order.subList(Math.min(start, end), Math.max(start, end) + 1));
                    } else {
                        if (!hasControlDown()) selectedConnections.clear();
                        if (!selectedConnections.add(key)) selectedConnections.remove(key);
                        rangeAnchor = key;
                    }
                    if (selectedConnections.isEmpty()) selectedGroup = null;
                    error = "";
                    refreshBlocks(true);
                    if (selectedGroup != null)
                        PacketDistributor.sendToServer(new C2SSelectBlueprintGroupPayload(toolSlot, selectedGroup));
                }
            }
            return true;
        }
        return viewport.mouseClicked(mx, my, button) || super.mouseClicked(mx, my, button);
    }

    private boolean isSearchButtonHit(double mx, double my) {
        int x = search.getX() + search.getWidth() - SEARCH_ICON_RIGHT_INSET;
        int y = search.getY() + SEARCH_ICON_Y_OFFSET;
        return mx >= x && mx < x + SLGuiTextures.SEARCH_ICON_WIDTH
            && my >= y && my < y + SLGuiTextures.SEARCH_ICON_HEIGHT;
    }

    private int maxScroll() {
        return Math.max(0, rows.size() * DIRECTORY_ROW_STEP - (panelHeight - DIRECTORY_TOP - DIRECTORY_BOTTOM_INSET));
    }

    private int scrollThumbHeight() {
        return SLGuiTextures.Scrollbar.HEIGHT;
    }

    private int scrollThumbY() {
        return top + DIRECTORY_SCROLL_Y + DIRECTORY_SCROLL_THUMB_Y_OFFSET + scroll * (panelHeight - DIRECTORY_SCROLL_HEIGHT_INSET - scrollThumbHeight() - 2 * DIRECTORY_SCROLL_THUMB_Y_OFFSET) / Math.max(1, maxScroll());
    }

    private void dragDirectory(double mouseY) {
        double fraction = (mouseY - top - DIRECTORY_SCROLL_Y - DIRECTORY_SCROLL_THUMB_Y_OFFSET - scrollGrab) / Math.max(1, panelHeight - DIRECTORY_SCROLL_HEIGHT_INSET - scrollThumbHeight() - 2 * DIRECTORY_SCROLL_THUMB_Y_OFFSET);
        scroll = Math.clamp((int) Math.round(fraction * maxScroll()), 0, maxScroll());
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (pendingCopy != null) return true;
        if (directoryDragging) {
            dragDirectory(my);
            return true;
        }
        return viewport.mouseDragged(mx, my, dx, dy) || super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (directoryDragging && button == 0) {
            directoryDragging = false;
            return true;
        }
        return viewport.mouseReleased(mx, my, button) || super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
        if (scopeOpen) return true;
        if (pendingCopy != null) return true;
        if (mx >= split && mx < left + panelWidth && my >= top + DIRECTORY_TOP && my < top + panelHeight) {
            scroll = Math.clamp(scroll - (int) Math.signum(vertical) * DIRECTORY_ROW_STEP, 0, maxScroll());
            return true;
        }
        return viewport.scroll(mx, my, vertical) || super.mouseScrolled(mx, my, horizontal, vertical);
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (scopeOpen) {
            if (key == 256) scopeOpen = false;
            return true;
        }
        if (search.canConsumeInput()) {
            if (key == 256 || key == 257 || key == 335) {
                search.setFocused(false);
                setFocused(null);
            } else search.keyPressed(key, scan, modifiers);
            return true;
        }
        if (minecraft.options.keyInventory.matches(key, scan)) {
            onClose();
            return true;
        }
        if (pendingCopy != null) return key != 256 || super.keyPressed(key, scan, modifiers);
        if (hasControlDown() && key == 65) {
            viewport.selectAll();
            return true;
        }
        if (hasControlDown() && key == 86) {
            PacketDistributor.sendToServer(new C2SBeginBlueprintPlacementPayload());
            return true;
        }
        if ((hasControlDown() && key == 67) || key == 257 || key == 335) {
            copy(false);
            return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }

    @Override
    public boolean charTyped(char character, int modifiers) {
        if (pendingCopy != null) return true;
        if (search.canConsumeInput()) {
            search.charTyped(character, modifiers);
            return true;
        }
        return super.charTyped(character, modifiers);
    }

    private void copy(boolean place) {
        var selection = viewport.selection();
        if (selectedGroup == null || viewport.anchor() == null || selection.isEmpty() || minecraft.level == null)
            return;
        if (selection.size() > BlueprintData.MAX_BLOCKS
            || selectedConnections.isEmpty()
            || selectedConnections.size() > BlueprintData.MAX_LINKS_PER_FACE) {
            error = tr("selection_limit").getString();
            return;
        }
        pendingCopy = UUID.randomUUID();
        placeAfterCopy = place;
        pendingTicks = 0;
        error = "";
        PacketDistributor.sendToServer(new C2SCopyBlueprintSelectionPayload(pendingCopy, toolSlot, selectedGroup,
            selectedDirectory, List.copyOf(selectedConnections),
            GlobalPos.of(minecraft.level.dimension(), viewport.anchor()), selection.stream().sorted().toList()));
    }

    public void copyResult(UUID request, boolean success) {
        if (!Objects.equals(request, pendingCopy)) return;
        pendingCopy = null;
        if (success && placeAfterCopy) PacketDistributor.sendToServer(new C2SBeginBlueprintPlacementPayload());
        else if (success) onClose();
        else error = tr("copy_failed").getString();
        placeAfterCopy = false;
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public void onClose() {
        host.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
