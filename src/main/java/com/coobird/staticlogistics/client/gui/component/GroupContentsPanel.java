package com.coobird.staticlogistics.client.gui.component;

import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.api.group.GroupKey;
import com.coobird.staticlogistics.client.data.ClientConnection;
import com.coobird.staticlogistics.client.data.ClientLinkData;
import com.coobird.staticlogistics.client.data.SearchMatcher;
import com.coobird.staticlogistics.client.render.SLGuiTextures;
import com.coobird.staticlogistics.logistics.node.ConnectionKey;
import com.coobird.staticlogistics.logistics.selection.NodeSelectionPolicy;
import com.coobird.staticlogistics.logistics.util.NodeDisplayText;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Nameable;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.coobird.staticlogistics.client.gui.ConfiguratorLayout.GroupContents.*;

/// 容器选择与连接面配置；关联关系仅在世界中显示。
public final class GroupContentsPanel {
    private final List<Hit> hits = new ArrayList<>();
    private final List<GroupHit> groupHits = new ArrayList<>();
    private final Set<GroupId> expandedGroups = new HashSet<>();

    private record GroupId(int role, int channel, LogisticsNode singleton) {
    }

    private record GroupHit(GroupId id, LogisticsNode node, int x, int y, int width, int height) {
        boolean contains(double px, double py) {
            return px >= x && px < x + width && py >= y && py < y + height;
        }
    }

    private int hoverX, hoverY;
    private GroupKey groupKey;
    private Set<ConnectionKey> connectionScopes = Set.of();
    private ConnectionKey referenceConnection;
    private LogisticsNode configurationFocus;
    private List<LogisticsNode> configurationNodes = List.of();

    public void setConnections(Set<ConnectionKey> connections, @Nullable ConnectionKey reference) {
        ConnectionKey nextReference = reference != null && connections.contains(reference)
            ? reference : connections.stream().findFirst().orElse(null);
        if (connectionScopes.equals(connections) && Objects.equals(referenceConnection, nextReference)) return;
        referenceConnection = nextReference;
        connectionScopes = Set.copyOf(connections);
        expandedGroups.clear();
        configurationFocus = null;
        cachedVersion = -1;
        listScroll = 0;
        if (selectedNode != null && !displays(selectedNode)) selectNode(null);
    }

    public GroupKey groupForNode(LogisticsNode node) {
        return scopeRoutes().stream().filter(route -> route.first().connectionFace().equals(node.connectionFace())
            || route.second().connectionFace().equals(node.connectionFace())).map(route -> route.groupKey()).findFirst().orElse(groupKey);
    }

    private List<ClientConnection> scopeRoutes() {
        return connectionScopes.stream().map(key -> key.groupKey()).distinct()
            .flatMap(key -> ClientLinkData.INSTANCE.getConnectionRoutes(key).stream())
            .filter(route -> connectionScopes.contains(route.key())).toList();
    }

    private List<LogisticsNode> scopeNodes() {
        List<LogisticsNode> nodes = scopeRoutes().stream().flatMap(route -> Stream.of(route.first(), route.second())).distinct().toList();
        Set<LogisticsNode> configuredFaces = nodes.stream().filter(node -> !node.isConnectionFace())
            .map(LogisticsNode::connectionFace).collect(Collectors.toSet());
        return nodes.stream().filter(node -> !node.isConnectionFace() || !configuredFaces.contains(node)).toList();
    }

    /// 展示全部选中链接涉及的连接面，公共端点会在列表中去重。
    private List<LogisticsNode> displayedNodes() {
        if (connectionScopes.size() > 1) return scopeNodes();
        if (referenceConnection == null) return List.of();
        return ClientLinkData.INSTANCE.getConnectionRoutes(referenceConnection.groupKey()).stream()
            .filter(route -> route.key().equals(referenceConnection))
            .flatMap(route -> Stream.of(route.first(), route.second())).distinct().toList();
    }

    public boolean displays(LogisticsNode node) {
        return displayedNodes().stream().anyMatch(candidate -> candidate.connectionFace().equals(node.connectionFace()));
    }

    public boolean hasMultipleConnections() {
        return connectionScopes.size() > 1;
    }

    public List<LogisticsNode> configurationTargets(LogisticsNode focus) {
        if (connectionScopes.size() <= 1) return List.of(focus);
        if (!focus.equals(configurationFocus)) {
            configurationNodes = NodeSelectionPolicy.configurationTargets(scopeNodes(), focus, node -> {
                var topology = ClientLinkData.INSTANCE.getTopology(node);
                return topology == null ? null : topology.role();
            });
            configurationFocus = focus;
        }
        return configurationNodes;
    }

    public Map<LogisticsNode, GroupKey> configurationGroups(LogisticsNode focus) {
        Set<LogisticsNode> selected = Set.copyOf(configurationTargets(focus));
        Map<LogisticsNode, GroupKey> groups = new LinkedHashMap<>();
        for (var route : scopeRoutes()) {
            if (selected.contains(route.first())) groups.putIfAbsent(route.first(), route.groupKey());
            if (selected.contains(route.second())) groups.putIfAbsent(route.second(), route.groupKey());
        }
        return groups;
    }

    private LogisticsNode selectedNode;
    private EditBox searchBox;
    private int listScroll, listMaximum;

    public record ViewState(String search, int scroll) {
    }

    public ViewState saveView() {
        return new ViewState(searchBox.getValue(), listScroll);
    }

    public void restoreView(ViewState state) {
        searchBox.setValue(state.search());
        listScroll = state.scroll();
        cachedVersion = -1;
    }

    private boolean listDragging;
    private int listTop, listHeight, scrollbarX, scrollbarY, scrollbarHeight;
    private final Map<LogisticsNode, Integer> blockRoles = new HashMap<>();
    private final Map<LogisticsNode, Set<Direction>> blockFaces = new HashMap<>();
    private int cachedVersion = -1;
    private long cachedSecond = -1;
    private String cachedQuery = "";
    private List<LogisticsNode> cachedBlocks = List.of();

    private record Hit(LogisticsNode node, int x, int y, int width, int height) {
        boolean contains(double px, double py) {
            return px >= x && px < x + width && py >= y && py < y + height;
        }
    }

    public void init(Font font, int x, int y, int width) {
        searchBox = new AtlasEditBox(font, x + SEARCH_X, y + SEARCH_Y, Math.max(1, width - SEARCH_WIDTH_INSET + SEARCH_WIDTH_ADJUST), SEARCH_HEIGHT, Component.empty());
        searchBox.setBordered(false);
        searchBox.setMaxLength(64);
        searchBox.setHint(Component.translatable("gui.staticlogistics.directory.search"));
        searchBox.setResponder(value -> {
            listScroll = 0;
        });
        searchBox.setVisible(true);
    }

    public void setGroup(@Nullable GroupKey key) {
        if (Objects.equals(groupKey, key)) return;
        groupKey = key;
        configurationFocus = null;
        connectionScopes = Set.of();
        referenceConnection = null;
        listScroll = 0;
        cachedVersion = -1;
        selectNode(null);
        if (searchBox != null) searchBox.setValue("");
    }

    @Nullable
    public LogisticsNode getSelectedNode() {
        return selectedNode;
    }

    public EditBox getSearchBox() {
        return searchBox;
    }

    public boolean isSearchBoxFocused() {
        return searchBox.isFocused();
    }

    public boolean searchBoxMouseClicked(double x, double y, int button) {
        return searchBox.mouseClicked(x, y, button);
    }

    public Component title() {
        var group = groupKey == null ? null : ClientLinkData.INSTANCE.findGroupRef(groupKey);
        return group == null ? tr("title") : Component.literal(group.displayName()).append(selectedNode == null ? "" : " / " + name(selectedNode));
    }

    public void selectNode(@Nullable LogisticsNode node) {
        selectedNode = node;
        hits.clear();
        listDragging = false;
        if (searchBox != null) {
            searchBox.setVisible(true);
            searchBox.setFocused(false);
        }
    }

    public void render(GuiGraphics g, Font font, int x, int y, int width, int height, int mouseX, int mouseY, double scale) {
        hits.clear();
        groupHits.clear();
        hoverX = mouseX;
        hoverY = mouseY;
        searchBox.setX(x + SEARCH_X);
        searchBox.setY(y + SEARCH_Y);
        searchBox.setWidth(Math.max(1, width - SEARCH_WIDTH_INSET + SEARCH_WIDTH_ADJUST));
        searchBox.render(g, mouseX, mouseY, 0);
        List<LogisticsNode> nodes = blocks();
        int listX = x + LIST_X_OFFSET;
        int listY = y + 18 + LIST_Y_OFFSET;
        int listWidth = Math.max(1, width + LIST_WIDTH_ADJUST);
        int listHeight = Math.max(17, height - 18 + LIST_HEIGHT_ADJUST);
        if (hasMultipleConnections()) {
            renderGroups(g, font, listX, listY, listWidth, listHeight, scale);
            return;
        }
        renderList(g, font, nodes, listX, listY, listWidth, listHeight, scale);
    }

    private List<LogisticsNode> blocks() {
        String query = searchBox.getValue().trim().toLowerCase(Locale.ROOT);
        int version = ClientLinkData.INSTANCE.getDataVersion();
        var level = Minecraft.getInstance().level;
        long second = level == null ? 0 : level.getGameTime() / 20;
        if (version == cachedVersion && second == cachedSecond && query.equals(cachedQuery)) return cachedBlocks;
        Map<LogisticsNode, LogisticsNode> blocks = new LinkedHashMap<>();
        blockRoles.clear();
        blockFaces.clear();
        for (LogisticsNode node : displayedNodes()) {
            var topology = ClientLinkData.INSTANCE.getTopology(node);
            int role = topology == null ? 0 : (topology.role().canSend() ? 1 : 0) | (topology.role().canReceive() ? 2 : 0);
            blockRoles.merge(node.connectionFace(), role, (a, b) -> a | b);
            blockFaces.computeIfAbsent(node.connectionFace(), ignored -> EnumSet.noneOf(Direction.class)).add(node.face());
            if (matchesSearch(node, query))
                blocks.merge(node.connectionFace(), node, (a, b) -> selectedNode != null && selectedNode.connectionFace().equals(a.connectionFace()) ? selectedNode : a.isConnectionFace() != b.isConnectionFace() ? (a.isConnectionFace() ? b : a) : a.channel() <= b.channel() ? a : b);
        }
        cachedBlocks = blocks.values().stream().sorted(nodeOrder()).toList();
        cachedVersion = version;
        cachedSecond = second;
        cachedQuery = query;
        return cachedBlocks;
    }

    private static Comparator<LogisticsNode> nodeOrder() {
        return Comparator.comparing((LogisticsNode n) -> n.gPos().dimension().location().toString()).thenComparing(n -> n.gPos().pos()).thenComparing(LogisticsNode::face);
    }


    private GroupId groupId(LogisticsNode node) {
        if (node.isConnectionFace()) return new GroupId(0, node.channel(), node);
        var topology = ClientLinkData.INSTANCE.getTopology(node);
        if (topology == null) return new GroupId(0, node.channel(), node);
        int role = (topology.role().canSend() ? 1 : 0) | (topology.role().canReceive() ? 2 : 0);
        return new GroupId(role, node.channel(), null);
    }

    private boolean matchesSearch(LogisticsNode node, String query) {
        return SearchMatcher.matches(name(node) + " " + coordinates(node) + " "
            + node.gPos().dimension().location(), query);
    }

    private void renderGroups(GuiGraphics g, Font font, int x, int y, int width, int height, double scale) {
        ConfiguratorStyle.row(g, x, y, width, HEADER_HEIGHT, false, false);
        g.drawString(font, tr("column_name"), x + HEADER_NAME_X, y + HEADER_TEXT_Y, ConfiguratorStyle.MUTED_TEXT, false);
        g.drawString(font, tr("column_position"), x + HEADER_COORDINATE_X, y + HEADER_TEXT_Y, ConfiguratorStyle.MUTED_TEXT, false);
        g.drawString(font, tr("column_role"), x + HEADER_ROLE_X, y + HEADER_TEXT_Y, ConfiguratorStyle.MUTED_TEXT, false);
        g.drawString(font, tr("column_direction"), x + width - HEADER_FACING_RIGHT, y + HEADER_TEXT_Y, ConfiguratorStyle.MUTED_TEXT, false);
        String query = searchBox.getValue().trim().toLowerCase(Locale.ROOT);
        Map<GroupId, List<LogisticsNode>> grouped = new LinkedHashMap<>();
        scopeNodes().stream().sorted(nodeOrder()).forEach(node ->
            grouped.computeIfAbsent(groupId(node), ignored -> new ArrayList<>()).add(node));
        List<Map.Entry<GroupId, List<LogisticsNode>>> groups = grouped.entrySet().stream()
            .filter(entry -> entry.getValue().stream().anyMatch(node -> matchesSearch(node, query)))
            .sorted(Comparator.comparingInt((Map.Entry<GroupId, List<LogisticsNode>> entry) ->
                    switch (entry.getKey().role()) {
                        case 1 -> 0;
                        case 2 -> 1;
                        case 3 -> 2;
                        default -> 3;
                    })
                .thenComparingInt(entry -> entry.getKey().channel())).toList();
        int rowCount = 0;
        for (var entry : groups) {
            rowCount++;
            if (expandedGroups.contains(entry.getKey()))
                rowCount += (int) entry.getValue().stream().filter(node -> matchesSearch(node, query)).count();
        }
        int top = y + 16, bottom = Math.max(top, y + height - LIST_BOTTOM_INSET);
        listTop = top;
        listHeight = bottom - top;
        updateScrollbar(x, width, top, bottom);
        listMaximum = Math.max(0, rowCount * NODE_ROW_STEP - listHeight);
        int rowWidth = Math.max(1, width - (listMaximum > 0 ? LIST_RIGHT_INSET : 0) + LIST_ROW_WIDTH_ADJUST);
        listScroll = Math.max(0, Math.min(listScroll, listMaximum));
        GuiScissor.enable(g, scale, x, top, x + width, bottom);
        int rowY = top - listScroll;
        for (var entry : groups) {
            GroupId id = entry.getKey();
            List<LogisticsNode> members = entry.getValue();
            if (rowY + NODE_ROW_STEP > top && rowY < bottom) {
                GroupHit hit = new GroupHit(id, members.get(0), x, Math.max(top, rowY), rowWidth,
                    Math.min(bottom, rowY + NODE_ROW_STEP) - Math.max(top, rowY));
                boolean selected = selectedNode != null && groupId(selectedNode).equals(id);
                ConfiguratorStyle.row(g, x, rowY, rowWidth, NODE_ROW_HEIGHT, selected, hit.contains(hoverX, hoverY));
                g.drawString(font, expandedGroups.contains(id) ? "▼" : "▶", x + GROUP_ARROW_X, rowY + NODE_TEXT_Y, ConfiguratorStyle.TEXT, false);
                String role = tr(id.channel() < 0 ? "face_no_channel" : id.role() == 1 ? "extract" : id.role() == 2 ? "insert" : id.role() == 3 ? "both" : id.singleton() == null ? "disabled" : "unknown").getString();
                String channel = id.channel() < 0 ? "" : "  " + Component.translatable("gui.staticlogistics.compact.channel").getString() + " " + (id.channel() + 1);
                g.drawString(font, font.plainSubstrByWidth(role + channel, GROUP_NAME_WIDTH), x + GROUP_NAME_X, rowY + NODE_TEXT_Y, ConfiguratorStyle.TEXT, false);
                g.drawString(font, Integer.toString(members.size()), x + ROLE_COLUMN_X, rowY + NODE_TEXT_Y, ConfiguratorStyle.TEXT, false);
                String directions = members.stream().map(LogisticsNode::face).distinct()
                    .map(d -> NodeDisplayText.direction(d).getString()).collect(Collectors.joining(" "));
                g.drawString(font, font.plainSubstrByWidth(directions, DIRECTION_TEXT_WIDTH), x + width - FACING_COLUMN_RIGHT, rowY + NODE_TEXT_Y, ConfiguratorStyle.TEXT, false);
                groupHits.add(hit);
            }
            rowY += NODE_ROW_STEP;
            if (!expandedGroups.contains(id)) continue;
            for (LogisticsNode node : members) {
                if (!matchesSearch(node, query)) continue;
                if (rowY + NODE_ROW_STEP > top && rowY < bottom) {
                    Hit hit = new Hit(node, x, Math.max(top, rowY), rowWidth,
                        Math.min(bottom, rowY + NODE_ROW_STEP) - Math.max(top, rowY));
                    ConfiguratorStyle.row(g, x, rowY, rowWidth, NODE_ROW_HEIGHT,
                        selectedNode != null && selectedNode.equals(node), hit.contains(hoverX, hoverY));
                    var level = Minecraft.getInstance().level;
                    if (level != null && level.dimension().equals(node.gPos().dimension())
                        && level.getChunkSource().hasChunk(node.gPos().pos().getX() >> 4, node.gPos().pos().getZ() >> 4))
                        g.renderItem(new ItemStack(level.getBlockState(node.gPos().pos()).getBlock().asItem()), x + NODE_ICON_X + 8, rowY + NODE_ICON_Y);
                    g.drawString(font, font.plainSubstrByWidth(name(node), CHILD_NAME_WIDTH), x + NODE_TEXT_X + CHILD_NAME_INDENT, rowY + NODE_TEXT_Y, ConfiguratorStyle.TEXT, false);
                    g.drawString(font, font.plainSubstrByWidth(coordinates(node), COORDINATE_TEXT_WIDTH), x + COORDINATE_COLUMN_X, rowY + NODE_TEXT_Y, ConfiguratorStyle.MUTED_TEXT, false);
                    g.drawString(font, NodeDisplayText.direction(node.face()), x + width - FACING_COLUMN_RIGHT, rowY + NODE_TEXT_Y, ConfiguratorStyle.TEXT, false);
                    hits.add(hit);
                }
                rowY += NODE_ROW_STEP;
            }
        }
        g.flush();
        g.disableScissor();
        if (groups.isEmpty())
            centered(g, font, tr("empty").getString(), x, top + EMPTY_TEXT_Y, width, ConfiguratorStyle.MUTED_TEXT);
        if (listMaximum > 0) {
            int track = scrollbarHeight, knob = SLGuiTextures.Scrollbar.HEIGHT;
            int offset = SCROLLBAR_THUMB_VERTICAL_INSET + listScroll * Math.max(0, track - knob - 2 * SCROLLBAR_THUMB_VERTICAL_INSET) / listMaximum;
            ConfiguratorStyle.scrollbar(g, scrollbarX, scrollbarY, SCROLLBAR_WIDTH, track, offset, SCROLLBAR_THUMB_X_OFFSET, true);
        }
    }


    private void renderList(GuiGraphics g, Font font, List<LogisticsNode> nodes, int x, int y, int width, int height, double scale) {
        int coordinateX = x + COORDINATE_COLUMN_X, roleX = x + ROLE_COLUMN_X, facesX = x + width - FACING_COLUMN_RIGHT;
        ConfiguratorStyle.row(g, x, y, width, HEADER_HEIGHT, false, false);
        g.drawString(font, tr("column_name"), x + HEADER_NAME_X, y + HEADER_TEXT_Y, ConfiguratorStyle.MUTED_TEXT, false);
        g.drawString(font, tr("column_position"), x + HEADER_COORDINATE_X, y + HEADER_TEXT_Y, ConfiguratorStyle.MUTED_TEXT, false);
        g.drawString(font, tr("column_role"), x + HEADER_ROLE_X, y + HEADER_TEXT_Y, ConfiguratorStyle.MUTED_TEXT, false);
        g.drawString(font, tr("column_direction"), x + width - HEADER_FACING_RIGHT, y + HEADER_TEXT_Y, ConfiguratorStyle.MUTED_TEXT, false);
        int top = y + 16, bottom = Math.max(top, y + height - LIST_BOTTOM_INSET);
        listTop = top;
        listHeight = bottom - top;
        updateScrollbar(x, width, top, bottom);
        listMaximum = Math.max(0, nodes.size() * NODE_ROW_STEP - (bottom - top));
        int rowWidth = Math.max(1, width - (listMaximum > 0 ? LIST_RIGHT_INSET : 0) + LIST_ROW_WIDTH_ADJUST);
        listScroll = Math.clamp(listScroll, 0, listMaximum);
        GuiScissor.enable(g, scale, x, top, x + width, bottom);
        int rowY = top - listScroll;
        for (int role : new int[]{1, 2, 3, 0}) {
            List<LogisticsNode> members = nodes.stream().filter(n -> blockRoles.getOrDefault(n.connectionFace(), 0) == role).toList();
            if (members.isEmpty()) continue;
            int color = role == 1 ? 0xFF823F0B : role == 2 ? 0xFF075866 : role == 3 ? 0xFF574117 : ConfiguratorStyle.MUTED_TEXT;
            for (LogisticsNode node : members) {
                if (rowY + NODE_ROW_STEP > top && rowY < bottom) {
                    Hit hit = new Hit(node, x, Math.max(top, rowY), rowWidth, Math.min(bottom, rowY + NODE_ROW_STEP) - Math.max(top, rowY));
                    ConfiguratorStyle.row(g, x, rowY, rowWidth, NODE_ROW_HEIGHT,
                        selectedNode != null && selectedNode.connectionFace().equals(node.connectionFace()), hit.contains(hoverX, hoverY));
                    var level = Minecraft.getInstance().level;
                    if (level != null && level.dimension().equals(node.gPos().dimension())
                        && level.getChunkSource().hasChunk(node.gPos().pos().getX() >> 4, node.gPos().pos().getZ() >> 4)) {
                        g.renderItem(new ItemStack(level.getBlockState(node.gPos().pos()).getBlock().asItem()), x + NODE_ICON_X, rowY + NODE_ICON_Y);
                    }
                    g.drawString(font, font.plainSubstrByWidth(name(node), NODE_NAME_WIDTH), x + NODE_TEXT_X, rowY + NODE_TEXT_Y, ConfiguratorStyle.TEXT, false);
                    g.drawString(font, font.plainSubstrByWidth(coordinates(node), COORDINATE_TEXT_WIDTH), coordinateX, rowY + NODE_TEXT_Y, ConfiguratorStyle.MUTED_TEXT, false);
                    g.drawString(font, font.plainSubstrByWidth(tr(role == 1 ? "extract" : role == 2 ? "insert" : role == 3 ? "both" : "unknown").getString(), facesX - roleX - ROLE_TEXT_RIGHT_GAP), roleX, rowY + NODE_TEXT_Y, color, false);
                    String directions = blockFaces.getOrDefault(node.connectionFace(), Set.of()).stream().map(d -> NodeDisplayText.direction(d).getString()).collect(Collectors.joining(" "));
                    g.drawString(font, font.plainSubstrByWidth(directions, DIRECTION_TEXT_WIDTH), facesX, rowY + NODE_TEXT_Y, ConfiguratorStyle.TEXT, false);
                    hits.add(hit);
                }
                rowY += NODE_ROW_STEP;

            }
        }
        g.flush();
        g.disableScissor();
        if (nodes.isEmpty())
            centered(g, font, tr("empty").getString(), x, top + EMPTY_TEXT_Y, width, ConfiguratorStyle.MUTED_TEXT);
        if (listMaximum > 0) {
            int track = scrollbarHeight, knob = SLGuiTextures.Scrollbar.HEIGHT;
            int offset = SCROLLBAR_THUMB_VERTICAL_INSET + listScroll * Math.max(0, track - knob - 2 * SCROLLBAR_THUMB_VERTICAL_INSET) / listMaximum;
            ConfiguratorStyle.scrollbar(g, scrollbarX, scrollbarY, SCROLLBAR_WIDTH, track, offset, SCROLLBAR_THUMB_X_OFFSET, true);
        }
    }

    private void updateScrollbar(int x, int width, int top, int bottom) {
        scrollbarX = x + width - SCROLLBAR_RIGHT_INSET + SCROLLBAR_X_OFFSET;
        scrollbarY = top + SCROLLBAR_Y_OFFSET;
        scrollbarHeight = Math.max(SLGuiTextures.Scrollbar.HEIGHT + 2 * SCROLLBAR_THUMB_VERTICAL_INSET,
            bottom - top + SCROLLBAR_HEIGHT_ADJUST);
    }

    private int scrollFromMouse(double mouseY) {
        int travel = Math.max(1, scrollbarHeight - SLGuiTextures.Scrollbar.HEIGHT - 2 * SCROLLBAR_THUMB_VERTICAL_INSET);
        double offset = mouseY - scrollbarY - SCROLLBAR_THUMB_VERTICAL_INSET - SLGuiTextures.Scrollbar.HEIGHT / 2.0;
        return Math.max(0, Math.min(listMaximum, (int) Math.round(offset * listMaximum / travel)));
    }

    public boolean mouseClicked(double mx, double my, int button, int x, int y, int width, int height) {
        if (mx < x || mx >= x + width || my < y || my >= y + height) return false;
        if (button != 0) return true;
        if (mx >= scrollbarX && mx < scrollbarX + SCROLLBAR_WIDTH && my >= scrollbarY && my < scrollbarY + scrollbarHeight && listMaximum > 0) {
            listDragging = true;
            listScroll = scrollFromMouse(my);
            return true;
        }
        for (GroupHit hit : List.copyOf(groupHits)) {
            if (!hit.contains(mx, my)) continue;
            if (mx < hit.x() + 16) {
                if (!expandedGroups.add(hit.id())) expandedGroups.remove(hit.id());
            } else selectNode(hit.node());
            SoundUtil.playClickSound();
            return true;
        }
        for (Hit hit : List.copyOf(hits))
            if (hit.contains(mx, my)) {
                selectNode(hit.node());
                SoundUtil.playClickSound();
                return true;
            }
        if (my >= y + 34 && !Screen.hasControlDown() && !Screen.hasShiftDown()) {
            selectNode(null);
        }
        return true;
    }

    public boolean mouseDragged(double mx, double my, double dx, double dy) {
        if (listDragging) {
            listScroll = scrollFromMouse(my);
            return true;
        }
        return false;
    }

    public boolean mouseReleased() {
        listDragging = false;
        return false;
    }

    public boolean mouseScrolled(double mx, double my, double delta, int x, int y, int width, int height) {
        if (mx < x || mx >= x + width || my < y || my >= y + height) return false;
        listScroll = Math.clamp(listScroll - (int) (delta * 23), 0, listMaximum);
        return true;
    }

    public void renderTooltip(GuiGraphics g, Font font, int mx, int my, int x, int y, int width, int height) {
        for (Hit hit : hits)
            if (hit.contains(mx, my)) {
                g.renderComponentTooltip(font, List.of(Component.literal(name(hit.node())), NodeDisplayText.details(hit.node()), tr("view_hint")), mx, my);
                return;
            }
    }

    private static String name(LogisticsNode node) {
        var level = Minecraft.getInstance().level;
        if (level == null || !level.dimension().equals(node.gPos().dimension())
            || !level.getChunkSource().hasChunk(node.gPos().pos().getX() >> 4,
            node.gPos().pos().getZ() >> 4)) return tr("unloaded").getString();
        if (level.getBlockEntity(node.gPos().pos()) instanceof Nameable named)
            return named.getDisplayName().getString();
        return level.getBlockState(node.gPos().pos()).getBlock().getName().getString();
    }

    private static String coordinates(LogisticsNode node) {
        return node.gPos().pos().toShortString();
    }

    private static Component tr(String key, Object... args) {
        return Component.translatable("gui.staticlogistics.blocks." + key, args);
    }

    private static void centered(GuiGraphics g, Font font, String value, int x, int y, int width, int color) {
        String text = font.plainSubstrByWidth(value, Math.max(1, width - 4));
        g.drawString(font, text, x + (width - font.width(text)) / 2, y, color, false);
    }
}
