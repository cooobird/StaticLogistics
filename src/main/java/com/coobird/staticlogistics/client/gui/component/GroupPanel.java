package com.coobird.staticlogistics.client.gui.component;

import com.coobird.staticlogistics.client.gui.ConfiguratorLayout;

import static com.coobird.staticlogistics.client.gui.ConfiguratorLayout.GroupDirectory.*;

import java.util.Arrays;

import net.minecraft.client.renderer.Rect2i;

import com.coobird.staticlogistics.api.LogisticsNode;

import net.minecraft.util.Mth;

import com.coobird.staticlogistics.api.group.GroupRef;
import com.coobird.staticlogistics.client.data.ClientConnection;
import com.coobird.staticlogistics.client.data.ClientLinkData;
import com.coobird.staticlogistics.client.data.ConnectionDirectoryModel;
import com.coobird.staticlogistics.client.data.ConnectionDirectoryModel.Row;
import com.coobird.staticlogistics.client.render.SLGuiTextures;
import com.coobird.staticlogistics.logistics.group.GroupHierarchy;
import com.coobird.staticlogistics.logistics.node.ConnectionKey;
import com.coobird.staticlogistics.logistics.util.NodeDisplayText;
import com.coobird.staticlogistics.network.c2s.C2SGroupHierarchyPayload;
import com.coobird.staticlogistics.network.c2s.C2SManageConnectionsPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import org.mesdag.portlib.network.PortPacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/// 右侧分组目录。
/// 本组件负责目录与连接选择；目录、连接和通道的管理操作统一使用右键弹窗。
public final class GroupPanel {
    // 面板与标题栏。
    // 右侧面板的横向位置。
    public static final int SIDE_PANEL_X = SLGuiTextures.LinkConfigurator.GROUP_X;
    public static final int BAR_W = ConfiguratorLayout.GroupDirectory.BAR_W;
    public static final int BAR_X = ConfiguratorLayout.GroupDirectory.BAR_X;
    public static final int BAR_Y = ConfiguratorLayout.GroupDirectory.BAR_Y;
    public static final int LIST_OFFSET_X = ConfiguratorLayout.GroupDirectory.LIST_OFFSET_X;
    public static final int LIST_OFFSET_Y = ConfiguratorLayout.GroupDirectory.LIST_OFFSET_Y;
    public static final int SCROLLBAR_X = ConfiguratorLayout.GroupDirectory.SCROLLBAR_X;
    public static final int SCROLLBAR_Y = ConfiguratorLayout.GroupDirectory.SCROLLBAR_Y;
    public static final int SELECTION_WIDTH = ConfiguratorLayout.GroupDirectory.SELECTION_WIDTH;
    // 树形横向分支相对连接行顶端的纵向偏移。
    private static final int TREE_BRANCH_Y = ROW_HEIGHT / 2;

    private final EditBox searchBox;
    private float scrollOffset;
    private boolean scrolling;
    private double scrollGrabOffset;
    private String searchTerm = "";
    private int lastSeenVersion = -1;
    private List<GroupRef> cachedGroups = Collections.emptyList();
    private CompoundTag hierarchy = new CompoundTag();
    private boolean hierarchyReceived;
    private int hierarchyRevision, builtHierarchy = -1, builtTopology = -1;
    private Set<String> builtCollapsed = Set.of();
    private String builtSearch = "";
    private final Set<String> collapsed = new HashSet<>();
    private List<Row> directoryRows = List.of();
    private final List<AtlasButton> directoryButtons = new ArrayList<>();
    private final AtlasButton scopeButton;
    private boolean builtTeamScope;
    private String selectedDirectory = "";
    private ClientConnection selectedConnection, hoveredConnection;
    private String hoveredDirectory;
    private List<ClientConnection> monitoredConnections = List.of();
    private final Set<ConnectionKey> selectedConnections = new LinkedHashSet<>();
    private ConnectionKey connectionAnchor;
    private ConnectionKey pendingSingleClick;
    private List<ConnectionKey> dragConnections = List.of();
    private double dragStartX, dragStartY;
    private boolean draggingConnections;

    public boolean hasDirectorySelection() {
        return !selectedDirectory.isEmpty();
    }

    public Set<ConnectionKey> getSelectedConnections() {
        return Set.copyOf(selectedConnections);
    }

    public ConnectionKey getSelectedConnection() {
        return selectedConnection == null ? null : selectedConnection.key();
    }

    public record ViewState(String search, Set<String> collapsed, float scroll, String directory, ConnectionKey primary,
                            Set<ConnectionKey> selected, ConnectionKey anchor) {
    }

    public ViewState saveView() {
        return new ViewState(searchBox.getValue(), Set.copyOf(collapsed), scrollOffset, selectedDirectory, getSelectedConnection(), Set.copyOf(selectedConnections), connectionAnchor);
    }

    public boolean restoreView(ViewState state) {
        if (!hierarchyReceived) return false;
        if (state.primary() != null && !focusConnection(state.primary())) {
            selectedConnection = null;
        }
        searchBox.setValue(state.search());
        collapsed.clear();
        collapsed.addAll(state.collapsed());
        scrollOffset = state.scroll();
        selectedDirectory = state.directory();
        selectedConnections.clear();
        state.selected().stream().filter(key -> ClientLinkData.INSTANCE.findConnection(key) != null).forEach(selectedConnections::add);
        connectionAnchor = state.anchor();
        builtTopology = -1;
        return true;
    }

    public boolean focusConnection(ConnectionKey key) {
        var connection = ClientLinkData.INSTANCE.getConnectionsForGroup(key.groupKey()).stream().filter(c -> c.key().equals(key)).findFirst().orElse(null);
        if (connection == null) return false;
        if (key.equals(getSelectedConnection())) {
            selectedConnection = connection;
            return true;
        }
        selectedDirectory = "";
        selectedConnection = connection;
        selectedConnections.clear();
        selectedConnections.add(key);
        connectionAnchor = key;
        return true;
    }

    private GroupHierarchyDialog dialog;
    private PlayerGrantDialog grantDialog;
    private AtlasButton grantButton;
    private int scopePage;
    private long builtScopeRevision = -1;
    private final List<AtlasButton> contextButtons = new ArrayList<>();
    private Consumer<ClickResult> contextAction = result -> {
    };

    public void setContextAction(Consumer<ClickResult> action) {
        contextAction = action;
    }

    private final Font directoryFont;
    private final int originX, originY;
    private Map<ConnectionKey, String> connectionNames = Map.of();

    private String connectionName(ClientConnection c) {
        return connectionNames.getOrDefault(c.key(), c.displayName());
    }

    public void acceptHierarchy(CompoundTag data) {
        hierarchy = data == null ? new CompoundTag() : data.copy();
        if (grantDialog != null) grantDialog.accept(hierarchy);
        hierarchyReceived = true;
        hierarchyRevision++;
    }

    public boolean hasDialog() {
        return dialog != null || grantDialog != null || !contextButtons.isEmpty();
    }

    public void openPresetName(Consumer<String> save) {
        contextButtons.clear();
        dialog = new GroupHierarchyDialog(directoryFont, originX + DIALOG_X, originY + DIALOG_Y,
            "gui.staticlogistics.preset.name_title", "", true, false, "", List.of(),
            (operation, value) -> save.accept(value.trim()), () -> dialog = null);
    }

    public void openPresetSelection(List<String> names, Consumer<String> select, Consumer<String> delete) {
        contextButtons.clear();
        var entries = names.stream().map(name -> new GroupHierarchyDialog.Destination(name,
            name.isEmpty() ? Component.translatable("gui.staticlogistics.preset.legacy").getString() : name, "", true)).toList();
        dialog = new GroupHierarchyDialog(directoryFont, originX + DIALOG_X, originY + DIALOG_Y,
            "gui.staticlogistics.preset.select_title", "", false, true, "preset_select", entries,
            (operation, value) -> select.accept(value), () -> dialog = null);
        dialog.onPresetDelete(name -> {
            String displayName = name.isEmpty() ? Component.translatable("gui.staticlogistics.preset.legacy").getString() : name;
            dialog = new GroupHierarchyDialog(directoryFont, originX + DIALOG_X, originY + DIALOG_Y,
                "gui.staticlogistics.preset.delete_title", displayName, false, false, "delete", List.of(),
                (operation, ignored) -> delete.accept(name), () -> dialog = null);
        });
    }

    public void openPresetReview(List<String> labels, int targetCount, Consumer<Set<Integer>> apply) {
        openPresetReview(labels, targetCount, apply, null);
    }

    public void openPresetReview(List<String> labels, int targetCount, Consumer<Set<Integer>> apply, Runnable back) {
        var entries = new ArrayList<GroupHierarchyDialog.Destination>();
        for (int i = 0; i < labels.size(); i++)
            entries.add(new GroupHierarchyDialog.Destination(Integer.toString(i), labels.get(i), "", true));
        dialog = new GroupHierarchyDialog(directoryFont, originX + DIALOG_X, originY + DIALOG_Y,
            targetCount > 1 ? "gui.staticlogistics.preset.review_batch_title" : "gui.staticlogistics.preset.review_title",
            Integer.toString(targetCount), false, true, back == null ? "preset_review" : "preset_review_batch", entries,
            (operation, value) -> {
                if (operation.equals("back")) back.run();
                else apply.accept(Arrays.stream(value.split(",")).map(Integer::parseInt).collect(Collectors.toSet()));
            }, () -> dialog = null);
    }

    public void openPresetTargetSelection(List<LogisticsNode> targets, Consumer<LogisticsNode> select) {
        var entries = new ArrayList<GroupHierarchyDialog.Destination>();
        for (int i = 0; i < targets.size(); i++)
            entries.add(new GroupHierarchyDialog.Destination(Integer.toString(i),
                NodeDisplayText.details(targets.get(i)).getString(), "", true));
        dialog = new GroupHierarchyDialog(directoryFont, originX + DIALOG_X, originY + DIALOG_Y,
            "gui.staticlogistics.preset.target_title", "", false, true, "preset_target", entries,
            (operation, value) -> select.accept(targets.get(Integer.parseInt(value))), () -> dialog = null);
    }

    public boolean dragDialog(double x, double y, int button, int width, int height) {
        return dialog != null && dialog.mouseDragged(x, y, button, width, height);
    }

    public boolean dragDialog(double x, double y, int button, int viewportX, int viewportY, int width, int height) {
        if (grantDialog != null) {
            grantDialog.drag(x, y, button, viewportX, viewportY, width, height);
            return true;
        }
        return dialog != null && dialog.mouseDragged(x, y, button, viewportX, viewportY, width, height);
    }

    public List<Rect2i> dialogBounds() {
        List<Rect2i> result = new ArrayList<>();
        if (dialog != null) result.add(dialog.bounds());
        if (grantDialog != null) result.add(grantDialog.bounds());
        for (AtlasButton widget : contextButtons)
            result.add(new Rect2i(widget.getX(), widget.getY(), widget.getWidth(), widget.getHeight()));
        return result;
    }

    public boolean releaseDialog() {
        if (grantDialog != null) {
            grantDialog.release();
            return true;
        }
        return dialog != null && dialog.mouseReleased();
    }

    public boolean scrollDialog(double delta) {
        if (grantDialog != null) {
            grantDialog.scroll(delta);
            return true;
        }
        return dialog != null && dialog.mouseScrolled(delta);
    }

    public void renderDialog(GuiGraphics graphics, int mouseX, int mouseY) {
        if (dialog != null) dialog.render(graphics, directoryFont, mouseX, mouseY);
        if (grantDialog != null) grantDialog.render(graphics, mouseX, mouseY);
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 600);
        for (AtlasButton widget : contextButtons) widget.render(graphics, mouseX, mouseY, 0);
        graphics.pose().popPose();
    }

    public void constrainDialogs(int viewportX, int viewportY, int width, int height) {
        if (dialog != null) dialog.constrainToViewport(viewportX, viewportY, width, height);
        if (grantDialog != null) grantDialog.constrain(viewportX, viewportY, width, height);
        if (contextButtons.isEmpty()) return;
        int left = contextButtons.stream().mapToInt(AtlasButton::getX).min().orElse(0);
        int top = contextButtons.stream().mapToInt(AtlasButton::getY).min().orElse(0);
        int right = contextButtons.stream().mapToInt(b -> b.getX() + b.getWidth()).max().orElse(left);
        int bottom = contextButtons.stream().mapToInt(b -> b.getY() + b.getHeight()).max().orElse(top);
        int dx = WindowDrag.constrain(left, right - left, viewportX, width) - left;
        int dy = WindowDrag.constrain(top, bottom - top, viewportY, height) - top;
        for (var button : contextButtons) {
            button.setX(button.getX() + dx);
            button.setY(button.getY() + dy);
        }
    }

    public boolean dialogKeyPressed(int key, int scan, int modifiers) {
        if (grantDialog != null) return grantDialog.key(key, scan, modifiers);
        if (!contextButtons.isEmpty()) {
            if (key == GLFW.GLFW_KEY_ESCAPE) contextButtons.clear();
            return true;
        }
        return dialog != null && dialog.keyPressed(key, scan, modifiers);
    }

    public boolean dialogCharTyped(char character, int modifiers) {
        if (grantDialog != null) return grantDialog.typed(character, modifiers);
        return dialog != null && dialog.charTyped(character, modifiers);
    }

    public boolean directoryMouseClicked(double x, double y, int button) {
        if (grantDialog != null) return grantDialog.click(x, y, button);
        if (dialog != null) return dialog.mouseClicked(x, y, button);
        if (!contextButtons.isEmpty()) {
            for (AtlasButton widget : List.copyOf(contextButtons)) if (widget.mouseClicked(x, y, button)) return true;
            contextButtons.clear();
            return true;
        }
        if (grantButton.mouseClicked(x, y, button)) return true;
        for (AtlasButton widget : directoryButtons) if (widget.mouseClicked(x, y, button)) return true;
        if (scopeButton.mouseClicked(x, y, button)) return true;
        return false;
    }

    private void request(String action, String id, String parent, String name) {
        PortPacketDistributor.sendToServer(new C2SGroupHierarchyPayload(action, id, parent, name));
    }

    private void openDirectoryDialog(boolean create, boolean root) {
        openDirectoryDialog(create, root, "");
    }

    public void openChannelContext(double mouseX, double mouseY, String name, BiConsumer<String, String> action) {
        contextButtons.clear();
        int x = Mth.clamp((int) mouseX, originX, originX + CONTEXT_MENU_MAX_X);
        int y = Mth.clamp((int) mouseY, originY, originY + CONTEXT_MENU_MAX_Y);
        for (String operation : List.of("rename", "delete")) {
            contextButtons.add(AtlasButton.atlasBuilder(Component.translatable("gui.staticlogistics.directory." + operation), button -> {
                contextButtons.clear();
                dialog = new GroupHierarchyDialog(directoryFont, originX + DIALOG_X, originY + DIALOG_Y,
                    "gui.staticlogistics.compact.channel_" + operation, name, false, false, operation, List.of(), action, () -> dialog = null);
            }).bounds(x, y + contextButtons.size() * CONTEXT_MENU_ROW_HEIGHT, CONTEXT_MENU_WIDTH, CONTEXT_MENU_ROW_HEIGHT).build());
        }
    }

    private void openContextMenu(double mouseX, double mouseY) {
        contextButtons.clear();
        int x = Mth.clamp((int) mouseX, originX, originX + CONTEXT_MENU_MAX_X);
        int y = Mth.clamp((int) mouseY, originY, originY + CONTEXT_MENU_MAX_Y);
        List<String> actions = new ArrayList<>(List.of("rename"));
        if (!hasDirectorySelection() && selectedConnection != null) actions.add("move");
        actions.add("delete");
        for (String action : actions) {
            String key = action.equals("delete") && !hasDirectorySelection() && selectedConnection != null ? "delete_link" : action;
            if (!hasDirectorySelection() && selectedConnections.size() > 1) key = "batch_" + action;
            contextButtons.add(AtlasButton.atlasBuilder(Component.translatable("gui.staticlogistics.directory." + key), b -> {
                contextButtons.clear();
                if (!hasDirectorySelection() && selectedConnection != null) openConnectionDialog(action);
                else openDirectoryDialog(false, false, action);
            }).bounds(x, y + contextButtons.size() * CONTEXT_MENU_ROW_HEIGHT, CONTEXT_MENU_WIDTH, CONTEXT_MENU_ROW_HEIGHT).build());
        }
    }

    private void openConnectionDialog(String operation) {
        Set<String> savedCollapsed = Set.copyOf(collapsed);
        String savedSearch = searchTerm;
        List<ConnectionKey> targets;
        try {
            collapsed.clear();
            searchTerm = "";
            buildRows();
            targets = directoryRows.stream().filter(row -> row.connection() != null && selectedConnections.contains(row.connection().key())).map(row -> row.connection().key()).distinct().toList();
        } finally {
            collapsed.addAll(savedCollapsed);
            searchTerm = savedSearch;
            buildRows();
        }
        if (targets.isEmpty()) return;
        if (targets.size() != selectedConnections.size() || targets.size() > 256) {
            if (Minecraft.getInstance().player != null)
                Minecraft.getInstance().player.displayClientMessage(Component.translatable("gui.staticlogistics.directory.batch_manage_limit"), false);
            return;
        }
        var player = Minecraft.getInstance().player;
        if (player == null) return;
        int menuId = player.containerMenu.containerId;
        List<GroupHierarchyDialog.Destination> destinations = new ArrayList<>();
        destinations.add(new GroupHierarchyDialog.Destination("", Component.translatable("gui.staticlogistics.directory.root").getString(), "", true));
        var entries = hierarchy.getCompound("entries");
        for (String id : entries.getAllKeys()) {
            var entry = entries.getCompound(id);
            destinations.add(new GroupHierarchyDialog.Destination(id, entry.getString("name"), "", true));
        }
        destinations.sort(Comparator.comparing(GroupHierarchyDialog.Destination::name));
        String title = targets.size() > 1 ? "batch_" + operation : operation;
        String initialName = targets.size() == 1 ? connectionName(selectedConnection) : "";
        dialog = new GroupHierarchyDialog(directoryFont, originX + DIALOG_X, originY + DIALOG_Y, "gui.staticlogistics.directory." + title,
            initialName, false, true, operation, destinations,
            (action, value) -> PortPacketDistributor.sendToServer(new C2SManageConnectionsPayload(menuId, targets, action, value)), () -> dialog = null);
    }

    private void openDirectoryDialog(boolean create, boolean root, String operation) {
        CompoundTag entries = hierarchy.getCompound("entries");
        String id = selectedDirectory;
        if (!create && id.isEmpty()) return;
        String titleKey = create ? "new_collection" : "manage";
        String initialName = create ? "" : entries.getCompound(id).getString("name");
        dialog = new GroupHierarchyDialog(directoryFont, originX + DIALOG_X, originY + DIALOG_Y, "gui.staticlogistics.directory." + titleKey,
            initialName, create, false, operation, List.of(), (action, value) -> {
            if (create) request("create", "", "", value);
            else request(action, id, action.equals("move") ? value : "", action.equals("rename") ? value : "");
        }, () -> dialog = null);
    }

    private void buildRows() {
        refreshGroups();
        if (builtScopeRevision >= 0 && builtScopeRevision != ConnectionDirectoryModel.scopeRevision()) {
            selectedDirectory = "";
            selectedConnection = null;
            selectedConnections.clear();
            connectionAnchor = null;
            scrollOffset = 0;
            contextAction.accept(ClickResult.consumed(null));
        }
        int version = ClientLinkData.INSTANCE.getDataVersion();
        if (version == builtTopology && hierarchyRevision == builtHierarchy
            && builtCollapsed.equals(collapsed) && builtSearch.equals(searchTerm)
            && builtTeamScope == ConnectionDirectoryModel.teamScope()
            && builtScopeRevision == ConnectionDirectoryModel.scopeRevision()) return;
        List<ClientConnection> connections = ConnectionDirectoryModel.connections();
        monitoredConnections = connections;
        connectionNames = ConnectionDirectoryModel.connectionNames(connections);
        directoryRows = ConnectionDirectoryModel.rows(hierarchy, connections, collapsed, searchTerm, this::connectionName);
        builtTopology = version;
        builtHierarchy = hierarchyRevision;
        builtTeamScope = ConnectionDirectoryModel.teamScope();
        builtScopeRevision = ConnectionDirectoryModel.scopeRevision();
        builtCollapsed = Set.copyOf(collapsed);
        builtSearch = searchTerm;
    }

    public GroupPanel(Font font, int leftPos, int topPos) {
        directoryFont = font;
        originX = leftPos;
        originY = topPos;
        directoryButtons.add(AtlasButton.atlasBuilder(Component.translatable("gui.staticlogistics.directory.new_collection"), b -> openDirectoryDialog(true, true)).bounds(leftPos + SIDE_PANEL_X + ADD_GROUP_BUTTON_X, topPos + ADD_GROUP_BUTTON_Y, ADD_GROUP_BUTTON_WIDTH, ADD_GROUP_BUTTON_HEIGHT).build().icon(SLGuiTextures.ADD).iconOnly());
        directoryButtons.get(0).setTooltip(Tooltip.create(Component.translatable("gui.staticlogistics.directory.new_collection_hint")));
        grantButton = AtlasButton.atlasBuilder(Component.translatable("gui.staticlogistics.permissions.entry"), b -> {
            grantDialog = new PlayerGrantDialog(directoryFont, originX + DIALOG_X, originY + DIALOG_Y,
                (recipient, permission) -> request(recipient.length() == 36 ? "grant" : "grant_name",
                    recipient, Integer.toString(permission), ""),
                enabled -> request("team_access", "", enabled ? "1" : "0", ""), () -> grantDialog = null);
            grantDialog.accept(hierarchy);
        }).bounds(leftPos + SIDE_PANEL_X + GRANT_BUTTON_X, topPos + GRANT_BUTTON_Y, GRANT_BUTTON_WIDTH, GRANT_BUTTON_HEIGHT).build();
        grantButton.setTooltip(Tooltip.create(Component.translatable("gui.staticlogistics.permissions.title")));
        scopeButton = AtlasButton.atlasBuilder(Component.empty(), b -> openScopeMenu())
            .bounds(leftPos + SIDE_PANEL_X + SCOPE_BUTTON_X, topPos + SCOPE_BUTTON_Y, SCOPE_BUTTON_WIDTH, SCOPE_BUTTON_HEIGHT).build();
        scopeButton.setTooltip(Tooltip.create(Component.translatable("gui.staticlogistics.directory.scope_hint")));
        int panelX = leftPos + SIDE_PANEL_X;
        searchBox = new AtlasEditBox(font, panelX + BAR_X + SEARCH_TEXT_INSET, topPos + BAR_Y + SEARCH_TEXT_INSET,
            BAR_TEXT_WIDTH, SEARCH_TEXT_HEIGHT, Component.empty());
        ((AtlasEditBox) searchBox).setRightInset(SEARCH_RIGHT_INSET);
        searchBox.setBordered(false);
        searchBox.setMaxLength(32);
        searchBox.setTextColor(ConfiguratorStyle.TEXT);
        searchBox.setHint(Component.translatable(
            "gui.staticlogistics.directory.search").withStyle(ChatFormatting.GRAY));
        searchBox.setResponder(value -> {
            searchTerm = value.trim();
            scrollOffset = 0;
            clampScroll();
        });


    }

    public EditBox getSearchBox() {
        return searchBox;
    }

    private void selectScope(UUID owner) {
        if (Objects.equals(ConnectionDirectoryModel.selectedOwner(), owner)) return;
        ConnectionDirectoryModel.setOwnerScope(owner);
        selectedDirectory = "";
        selectedConnection = null;
        selectedConnections.clear();
        connectionAnchor = null;
        scrollOffset = 0;
        builtTopology = -1;
        request("select", "", "", "");
        contextAction.accept(ClickResult.consumed(null));
    }

    private void openScopeMenu() {
        contextButtons.clear();
        int x = originX + SIDE_PANEL_X + SCOPE_BUTTON_X;
        int y = originY + SCOPE_BUTTON_Y + SCOPE_BUTTON_HEIGHT + SCOPE_MENU_GAP;
        var owners = ConnectionDirectoryModel.availableOwners();
        int pageCount = Math.max(1, (owners.size() + 4) / 5);
        scopePage = Math.max(0, Math.min(scopePage, pageCount - 1));
        addScopeOption(x, y, null, Component.translatable("gui.staticlogistics.directory.all"));
        UUID self = Minecraft.getInstance().player == null ? null : Minecraft.getInstance().player.getUUID();
        if (self != null) addScopeOption(x, y, self, Component.translatable("gui.staticlogistics.directory.own"));
        for (int i = scopePage * 5; i < Math.min(owners.size(), scopePage * 5 + 5); i++) {
            UUID owner = owners.get(i);
            int permission = ConnectionDirectoryModel.ownerPermission(owner);
            String state = Component.translatable(permission == 2 ? "gui.staticlogistics.permissions.manage"
                : permission == 1 ? "gui.staticlogistics.permissions.view" : "gui.staticlogistics.permissions.no_access").getString();
            addScopeOption(x, y, owner, Component.literal(directoryFont.plainSubstrByWidth(
                ConnectionDirectoryModel.ownerName(owner), Math.max(0, SCOPE_MENU_WIDTH - 10 - directoryFont.width(state) - directoryFont.width("  "))) + "  " + state));
        }
        if (pageCount > 1) {
            int rowY = y + contextButtons.size() * SCOPE_MENU_ROW_HEIGHT;
            contextButtons.add(AtlasButton.atlasBuilder(Component.literal("<"), button -> {
                scopePage = Math.max(0, scopePage - 1);
                openScopeMenu();
            }).bounds(x, rowY, SCOPE_PREVIOUS_WIDTH, SCOPE_MENU_ROW_HEIGHT).build());
            contextButtons.add(AtlasButton.atlasBuilder(Component.literal((scopePage + 1) + "/" + pageCount), button -> {
                })
                .bounds(x + SCOPE_PREVIOUS_WIDTH, rowY, SCOPE_PAGE_WIDTH, SCOPE_MENU_ROW_HEIGHT).build());
            contextButtons.add(AtlasButton.atlasBuilder(Component.literal(">"), button -> {
                scopePage = Math.min(pageCount - 1, scopePage + 1);
                openScopeMenu();
            }).bounds(x + SCOPE_PREVIOUS_WIDTH + SCOPE_PAGE_WIDTH, rowY, SCOPE_NEXT_WIDTH, SCOPE_MENU_ROW_HEIGHT).build());
        }
    }

    private void addScopeOption(int x, int y, UUID owner, Component text) {
        int rowY = y + contextButtons.size() * SCOPE_MENU_ROW_HEIGHT;
        var button = AtlasButton.atlasBuilder(text, clicked -> {
                contextButtons.clear();
                selectScope(owner);
            }).bounds(x, rowY, SCOPE_MENU_WIDTH, SCOPE_MENU_ROW_HEIGHT).build()
            .selection(() -> Objects.equals(ConnectionDirectoryModel.selectedOwner(), owner));
        if (owner != null) {
            Component tip = ConnectionDirectoryModel.ownerTeamPermission(owner) > 0
                ? Component.translatable("gui.staticlogistics.permissions.team_source", ConnectionDirectoryModel.ownerName(owner))
                : Component.literal(ConnectionDirectoryModel.ownerName(owner));
            button.setTooltip(Tooltip.create(tip));
        }
        contextButtons.add(button);
    }

    @Nullable
    public GroupRef getHoveredGroup() {
        return hoveredConnection == null ? null : ClientLinkData.INSTANCE.findGroupRef(hoveredConnection.groupKey());
    }

    public boolean isSearchBoxFocused() {
        return searchBox.isFocused();
    }


    public void render(GuiGraphics graphics, Font font,
                       int leftPos, int topPos, int mouseX, int mouseY,
                       float partialTick, double interfaceScale) {
        hoveredConnection = null;
        hoveredDirectory = null;
        int panelX = leftPos + SIDE_PANEL_X;
        String title = Component.translatable("gui.staticlogistics.directory.title").getString();
        int addX = Math.max(ADD_GROUP_BUTTON_X, TITLE_X + font.width(title) + TITLE_ADD_GAP);
        addX = Math.min(addX, SCOPE_BUTTON_X - ADD_GROUP_BUTTON_WIDTH - ADD_SCOPE_GAP);
        directoryButtons.get(0).setX(panelX + addX);
        searchBox.setX(panelX + BAR_X + SEARCH_TEXT_INSET);
        searchBox.setY(topPos + BAR_Y + SEARCH_TEXT_INSET);
        searchBox.render(graphics, mouseX, mouseY, partialTick);
        renderGroups(graphics, font, panelX, topPos, mouseX, mouseY, interfaceScale);
    }

    private void renderGroups(GuiGraphics graphics, Font font,
                              int panelX, int topPos, int mouseX, int mouseY,
                              double interfaceScale) {
        int searchIconX = panelX + BAR_X + BAR_W - SEARCH_ICON_RIGHT_INSET;
        int searchIconY = topPos + BAR_Y + SEARCH_ICON_Y_OFFSET;
        boolean searchHovered = inside(mouseX, mouseY, searchIconX, searchIconY, SLGuiTextures.SEARCH_ICON_WIDTH, SLGuiTextures.SEARCH_ICON_HEIGHT);
        (searchHovered || searchBox.isFocused() ? SLGuiTextures.SEARCH_SELECTED : SLGuiTextures.SEARCH)
            .draw(graphics, searchIconX, searchIconY);
        refreshGroups();
        buildRows();
        scrollOffset = Mth.clamp(scrollOffset, 0, maxScroll());
        int maximum = maxScroll();
        int listX = panelX + LIST_OFFSET_X;
        int listY = topPos + LIST_OFFSET_Y;
        int addY = addRowY(listY);
        SLGuiTextures.LIST_SURFACE.frame(graphics, listX - 1, listY - 1, SELECTION_WIDTH + 2, LIST_HEIGHT + 2, 1);
        if (maximum > 0) renderScrollbar(graphics, panelX + SCROLLBAR_X, topPos + SCROLLBAR_Y, mouseX, mouseY, maximum);
        String dropTarget = draggingConnections ? dropTarget(mouseX, mouseY, panelX, topPos) : null;
        GuiScissor.enable(graphics, interfaceScale, listX - 2, listY, listX + SELECTION_WIDTH + 2, addY);
        for (int index = 0; index < directoryRows.size(); index++) {
            int rowY = rowY(index, listY);
            if (rowY + ROW_HEIGHT <= listY || rowY >= addY) continue;
            Row row = directoryRows.get(index);
            if (row.connection() == null && inside(mouseX, mouseY, listX, rowY, SELECTION_WIDTH, ROW_HEIGHT)
                && mouseY >= listY && mouseY < addY) hoveredDirectory = row.id();
            int indent = row.depth() * DIRECTORY_INDENT_STEP;
            boolean active = row.connection() != null ? selectedConnections.contains(row.connection().key()) && selectedDirectory.isEmpty() : row.id().equals(selectedDirectory) || row.id().equals(dropTarget);
            if (active) ConfiguratorStyle.row(graphics, listX, rowY, SELECTION_WIDTH, ROW_HEIGHT, true, false);
            else {
                graphics.fill(listX, rowY, listX + SELECTION_WIDTH, rowY + ROW_HEIGHT, 0xFFC2C2C2);
                graphics.fill(listX, rowY + ROW_HEIGHT - ROW_DIVIDER_HEIGHT, listX + SELECTION_WIDTH, rowY + ROW_HEIGHT, 0xFF999999);
            }
            if (row.connection() != null) {
                if (inside(mouseX, mouseY, listX, rowY, SELECTION_WIDTH, ROW_HEIGHT) && mouseY >= listY && mouseY < addY) {
                    hoveredConnection = row.connection();
                }
                if (row.depth() > 0) {
                    int lineX = listX + TREE_LINE_X;
                    boolean hasNextChild = index + 1 < directoryRows.size() && directoryRows.get(index + 1).depth() > 0;
                    int lineEnd = hasNextChild ? ROW_HEIGHT : ROW_HEIGHT / 2 + 1;
                    for (int dy = 0; dy < lineEnd; dy += TREE_DASH_STEP)
                        graphics.fill(lineX, rowY + TREE_LINE_Y + dy, lineX + TREE_LINE_WIDTH, rowY + TREE_LINE_Y + Math.min(dy + TREE_DASH_LENGTH, lineEnd), 0xFF444444);
                    for (int dx = 0; dx < TREE_BRANCH_LENGTH; dx += TREE_DASH_STEP)
                        graphics.fill(lineX + dx, rowY + TREE_BRANCH_Y, lineX + Math.min(dx + TREE_DASH_LENGTH, TREE_BRANCH_LENGTH), rowY + TREE_BRANCH_Y + TREE_LINE_WIDTH, 0xFF444444);
                }
                graphics.renderItem(ConnectionDirectoryModel.connectionIcon(row.connection()), listX + indent + CONNECTION_ICON_X, rowY + CONNECTION_ICON_Y);
                graphics.drawString(font,
                    ConfiguratorStyle.scrollingText(font, row.name(), SELECTION_WIDTH - indent - CONNECTION_TEXT_WIDTH_INSET,
                        inside(mouseX, mouseY, listX, rowY, SELECTION_WIDTH, ROW_HEIGHT) && mouseY >= listY && mouseY < addY),
                    listX + indent + CONNECTION_TEXT_X, rowY + DIRECTORY_TEXT_Y, ConfiguratorStyle.TEXT, false);
            } else {
                ConfiguratorStyle.disclosure(graphics, listX + indent + FOLDER_ARROW_X, rowY + FOLDER_ARROW_Y, collapsed.contains(row.id()));
                ConfiguratorStyle.directory(graphics, listX + indent + FOLDER_ICON_X, rowY + FOLDER_ICON_Y, active);
                int textX = listX + indent + DIRECTORY_TEXT_X;
                int textRight = listX + SELECTION_WIDTH - DIRECTORY_TEXT_RIGHT_INSET;
                graphics.drawString(font,
                    font.plainSubstrByWidth(row.name(), Math.max(0, textRight - textX)),
                    textX, rowY + DIRECTORY_TEXT_Y, ConfiguratorStyle.TEXT, false);
            }
        }
        graphics.flush();
        graphics.disableScissor();
        if (draggingConnections) {
            if (dropTarget != null && dropTarget.isEmpty()) {
                int blankY = Math.max(listY, rowY(directoryRows.size(), listY));
                if (blankY < addY) graphics.fill(listX, blankY, listX + SELECTION_WIDTH, addY, 0x668899DD);
            }
            Component label = Component.translatable("gui.staticlogistics.directory.drag_count", dragConnections.size());
            int labelX = mouseX + DRAG_LABEL_X_OFFSET, labelY = mouseY + DRAG_LABEL_Y_OFFSET;
            graphics.fill(labelX - DRAG_LABEL_PADDING, labelY - DRAG_LABEL_PADDING, labelX + font.width(label) + DRAG_LABEL_PADDING, labelY + font.lineHeight + DRAG_LABEL_PADDING, 0xDD303030);
            graphics.drawString(font, label, labelX, labelY, 0xFFFFFF, false);
        }
        int titleWidth = Math.max(0, directoryButtons.get(0).getX() - panelX - TITLE_X - TITLE_ADD_GAP);
        String title = font.plainSubstrByWidth(Component.translatable("gui.staticlogistics.directory.title").getString(), titleWidth);
        graphics.drawString(font, title, panelX + TITLE_X, topPos + TITLE_Y, ConfiguratorStyle.TEXT, false);
        for (AtlasButton widget : directoryButtons) widget.render(graphics, mouseX, mouseY, 0);
        grantButton.render(graphics, mouseX, mouseY, 0);
        scopeButton.setMessage(Component.translatable(ConnectionDirectoryModel.teamScope()
            ? "gui.staticlogistics.directory.all" : "gui.staticlogistics.directory.own"));
        scopeButton.render(graphics, mouseX, mouseY, 0);
        SLGuiTextures.ARROW_DOWN.draw(graphics, panelX + SCOPE_BUTTON_X + SCOPE_BUTTON_WIDTH - SCOPE_ARROW_RIGHT_INSET, topPos + SCOPE_BUTTON_Y + SCOPE_ARROW_Y_OFFSET);
    }

    private void renderScrollbar(GuiGraphics graphics, int x, int y,
                                 int mouseX, int mouseY, int maximum) {
        boolean active = maximum > 0 && (scrolling
            || inside(mouseX, mouseY, x, y,
            SLGuiTextures.Scrollbar.WIDTH, SCROLLBAR_TRACK_HEIGHT));
        ConfiguratorStyle.scrollbar(graphics, x, y, SCROLLBAR_TRACK_HEIGHT, scrollKnobOffset(maximum) + SCROLLBAR_THUMB_Y_OFFSET, active);
    }

    public boolean searchBoxMouseClicked(double x, double y, int button) {
        return searchBox.mouseClicked(x, y, button);
    }


    public boolean isSearchTriggerHit(double x, double y, int leftPos, int topPos) {
        int panelX = leftPos + SIDE_PANEL_X;
        int iconX = panelX + BAR_X + BAR_W - SEARCH_ICON_RIGHT_INSET;
        return inside(x, y, iconX, topPos + BAR_Y + SEARCH_ICON_Y_OFFSET, SLGuiTextures.SEARCH_ICON_WIDTH, SLGuiTextures.SEARCH_ICON_HEIGHT);
    }

    public void triggerSearch() {
        searchTerm = searchBox.getValue().trim();
        scrollOffset = 0;
        clampScroll();
        SoundUtil.playClickSound();
    }


    @Nullable
    public ClickResult handleListClick(double mouseX, double mouseY, int button,
                                       int leftPos, int topPos) {
        refreshGroups();
        buildRows();
        int listX = leftPos + SIDE_PANEL_X + LIST_OFFSET_X;
        int listY = topPos + LIST_OFFSET_Y;
        int addY = addRowY(listY);
        if (!inside(mouseX, mouseY, listX, listY,
            SELECTION_WIDTH, addY - listY)) return null;
        for (int index = 0; index < directoryRows.size(); index++) {
            int rowY = rowY(index, listY);
            if (!inside(mouseX, mouseY, listX, rowY,
                SELECTION_WIDTH, ROW_HEIGHT)) continue;
            Row row = directoryRows.get(index);
            if (row.connection() == null) {
                selectedDirectory = row.id();
                selectedConnection = null;
                selectedConnections.clear();
                connectionAnchor = null;
                if (button == 1) {
                    if (hierarchy.getCompound("entries").contains(row.id())) openContextMenu(mouseX, mouseY);
                    return ClickResult.consumed(null);
                }
                request("select", row.id(), "", "");
                if (!collapsed.add(row.id())) collapsed.remove(row.id());
                return ClickResult.consumed(null);
            }
            GroupRef group = ClientLinkData.INSTANCE.findGroupRef(row.connection().groupKey());
            selectedDirectory = "";
            selectedConnection = row.connection();
            ConnectionKey clicked = row.connection().key();
            boolean ctrl = Screen.hasControlDown();
            boolean shift = Screen.hasShiftDown();
            List<ConnectionKey> order = directoryRows.stream().filter(r -> r.connection() != null).map(r -> r.connection().key()).toList();
            int anchor = order.indexOf(connectionAnchor), end = order.indexOf(clicked);
            if (button == 1 && selectedConnections.contains(clicked)) {
                /// 右键已选项保留整批选择。
            } else if (button == 0 && !ctrl && !shift && selectedConnections.size() > 1 && selectedConnections.contains(clicked)) {
                pendingSingleClick = clicked;
            } else if (button == 0 && shift && anchor >= 0) {
                if (!ctrl) selectedConnections.clear();
                selectedConnections.addAll(order.subList(Math.min(anchor, end), Math.max(anchor, end) + 1));
            } else {
                if (!ctrl || button != 0) selectedConnections.clear();
                if (!selectedConnections.add(clicked)) selectedConnections.remove(clicked);
                connectionAnchor = clicked;
            }
            if (selectedConnections.isEmpty()) {
                selectedConnection = null;
                return ClickResult.consumed(null);
            }
            if (!selectedConnections.contains(clicked)) {
                selectedConnection = directoryRows.stream().filter(r -> r.connection() != null && selectedConnections.contains(r.connection().key())).map(Row::connection).findFirst().orElse(null);
                if (selectedConnection == null) {
                    selectedConnections.clear();
                    return ClickResult.consumed(null);
                }
                group = ClientLinkData.INSTANCE.findGroupRef(selectedConnection.groupKey());
            }
            request("select", GroupHierarchy.connectionFolder(hierarchy, row.connection().key()), "", "");
            if (button == 1) {
                openContextMenu(mouseX, mouseY);
                return ClickResult.select(group);
            }
            if (button != 0) return ClickResult.consumed(group);
            if (selectedConnections.contains(clicked)) {
                dragStartX = mouseX;
                dragStartY = mouseY;
                dragConnections = List.copyOf(selectedConnections);
            }
            return ClickResult.select(group);
        }
        return null;
    }

    public boolean handleScrollbarClick(double mouseX, double mouseY,
                                        int leftPos, int topPos) {
        if (maxScroll() <= 0) return false;
        int x = leftPos + SIDE_PANEL_X + SCROLLBAR_X;
        int y = topPos + SCROLLBAR_Y;
        int hitTop = y + Math.min(0, SCROLLBAR_THUMB_Y_OFFSET);
        int hitHeight = SCROLLBAR_TRACK_HEIGHT + Math.abs(SCROLLBAR_THUMB_Y_OFFSET);
        if (!inside(mouseX, mouseY, x, hitTop,
            SLGuiTextures.Scrollbar.WIDTH, hitHeight)) return false;
        if (maxScroll() > 0) {
            int knobY = y + scrollKnobOffset(maxScroll()) + SCROLLBAR_THUMB_Y_OFFSET;
            scrollGrabOffset = mouseY >= knobY
                && mouseY <= knobY + SLGuiTextures.Scrollbar.HEIGHT
                ? mouseY - knobY : SLGuiTextures.Scrollbar.HEIGHT / 2.0D;
            scrolling = true;
            updateScrollFromMouse(mouseY, topPos);
        }
        return true;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double delta,
                                 int leftPos, int topPos) {
        int listX = leftPos + SIDE_PANEL_X + LIST_OFFSET_X;
        int listY = topPos + LIST_OFFSET_Y;
        if (!inside(mouseX, mouseY, listX, listY,
            SELECTION_WIDTH, LIST_HEIGHT) || maxScroll() <= 0) return false;
        scrollOffset = Mth.clamp(scrollOffset
            - (float) delta * ROW_HEIGHT, 0, maxScroll());
        return true;
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button, int topPos) {
        if (scrolling) {
            updateScrollFromMouse(mouseY, topPos);
            return true;
        }
        if (button != 0 || dragConnections.isEmpty()) return false;
        double dx = mouseX - dragStartX, dy = mouseY - dragStartY;
        if (dx * dx + dy * dy >= 16) draggingConnections = true;
        return draggingConnections;
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        scrolling = false;
        if (button != 0) return false;
        boolean wasDragging = draggingConnections;
        if (wasDragging) {
            String target = dropTarget(mouseX, mouseY, originX + SIDE_PANEL_X, originY);
            if (target != null && dragConnections.size() <= 256) {
                List<ConnectionKey> moved = dragConnections.stream()
                    .filter(key -> !GroupHierarchy.connectionFolder(hierarchy, key).equals(target)).toList();
                if (!moved.isEmpty() && Minecraft.getInstance().player != null) {
                    int menuId = Minecraft.getInstance().player.containerMenu.containerId;
                    PortPacketDistributor.sendToServer(new C2SManageConnectionsPayload(menuId, moved, "move", target));
                }
            }
        } else if (pendingSingleClick != null) {
            selectedConnections.clear();
            selectedConnections.add(pendingSingleClick);
            connectionAnchor = pendingSingleClick;
        }
        pendingSingleClick = null;
        dragConnections = List.of();
        draggingConnections = false;
        return wasDragging;
    }

    @Nullable
    private String dropTarget(double mouseX, double mouseY, int panelX, int topPos) {
        int listX = panelX + LIST_OFFSET_X, listY = topPos + LIST_OFFSET_Y;
        if (!inside(mouseX, mouseY, listX, listY, SELECTION_WIDTH, LIST_HEIGHT)) return null;
        for (int index = 0; index < directoryRows.size(); index++) {
            int y = rowY(index, listY);
            if (inside(mouseX, mouseY, listX, y, SELECTION_WIDTH, ROW_HEIGHT)) {
                Row row = directoryRows.get(index);
                return row.connection() == null ? row.id() : null;
            }
        }
        return "";
    }

    private void updateScrollFromMouse(double mouseY, int topPos) {
        int maximum = maxScroll();
        if (maximum <= 0) return;
        float relative = (float) ((mouseY - (topPos + SCROLLBAR_Y + SCROLLBAR_THUMB_Y_OFFSET)
            - scrollGrabOffset) / scrollbarTravel());
        scrollOffset = Mth.clamp(relative * maximum, 0, maximum);
    }

    private void refreshGroups() {
        int version = ClientLinkData.INSTANCE.getDataVersion();
        if (version == lastSeenVersion) return;
        var previousKeys = cachedGroups.stream().map(GroupRef::key).collect(Collectors.toSet());
        cachedGroups = ConnectionDirectoryModel.filterAndSort(
            ClientLinkData.INSTANCE.getAccessibleGroupRefs(), "");
        if (!previousKeys.equals(cachedGroups.stream().map(GroupRef::key).collect(Collectors.toSet())))
            request("query", "", "", "");
        lastSeenVersion = version;
        clampScroll();
    }

    private void clampScroll() {
        scrollOffset = Mth.clamp(scrollOffset, 0, maxScroll());
    }

    private int maxScroll() {
        return Math.max(0, directoryRows.size() * ROW_HEIGHT
            - LIST_HEIGHT);
    }

    private int scrollKnobOffset(int maximum) {
        return maximum <= 0 ? 0
            : Math.round(scrollOffset / maximum * scrollbarTravel());
    }

    private static int scrollbarTravel() {
        return Math.max(1, SCROLLBAR_TRACK_HEIGHT - SLGuiTextures.Scrollbar.HEIGHT - 2 * SCROLLBAR_THUMB_Y_OFFSET);
    }

    private static int addRowY(int listY) {
        return listY + LIST_HEIGHT;
    }

    private int rowY(int rowIndex, int listY) {
        return listY + rowIndex * ROW_HEIGHT - (int) scrollOffset;
    }

    public void renderGroupTooltip(GuiGraphics graphics, Font font,
                                   int mouseX, int mouseY) {
        if (hoveredConnection == null) {
            if (hoveredDirectory == null) return;
            Map<ConnectionKey, Boolean> states = new LinkedHashMap<>();
            for (ClientConnection connection : monitoredConnections) {
                if (!GroupHierarchy.connectionFolder(hierarchy, connection.key()).equals(hoveredDirectory)) continue;
                states.merge(connection.key(), connection.transfersFirstToSecond() || connection.transfersSecondToFirst(), Boolean::logicalOr);
            }
            long active = states.values().stream().filter(Boolean::booleanValue).count();
            graphics.renderComponentTooltip(font, List.of(
                Component.translatable("gui.staticlogistics.monitor.connections", states.size()),
                Component.translatable("gui.staticlogistics.monitor.configured", active, states.size() - active),
                Component.translatable("gui.staticlogistics.monitor.configured_only")), mouseX, mouseY);
            return;
        }
        ClientConnection connection = hoveredConnection;
        String direction = connection.transfersFirstToSecond()
            ? connection.transfersSecondToFirst() ? "both" : "forward"
            : connection.transfersSecondToFirst() ? "reverse" : "inactive";
        graphics.renderComponentTooltip(font, List.of(Component.literal(connectionName(connection)),
            Component.translatable("gui.staticlogistics.diagnostic.endpoint_a", NodeDisplayText.details(connection.first())),
            Component.translatable("gui.staticlogistics.diagnostic.endpoint_b", NodeDisplayText.details(connection.second())),
            Component.translatable("gui.staticlogistics.diagnostic.direction." + direction),
            Component.translatable("gui.staticlogistics.diagnostic.topology_only"),
            Component.translatable("gui.staticlogistics.tooltip.group_context")), mouseX, mouseY);
    }


    private static boolean inside(double mouseX, double mouseY,
                                  int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width
            && mouseY >= y && mouseY < y + height;
    }

    public record ClickResult(Action action, GroupRef group) {
        public enum Action {SELECT, CONSUME}

        public static ClickResult select(GroupRef group) {
            return new ClickResult(Action.SELECT, group);
        }

        public static ClickResult consumed(GroupRef group) {
            return new ClickResult(Action.CONSUME, group);
        }

        public Action getAction() {
            return action;
        }

        public GroupRef getGroup() {
            return group;
        }
    }
}
