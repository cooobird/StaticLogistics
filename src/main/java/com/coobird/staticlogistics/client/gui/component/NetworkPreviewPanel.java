package com.coobird.staticlogistics.client.gui.component;

import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.api.group.GroupKey;
import com.coobird.staticlogistics.client.data.*;
import com.coobird.staticlogistics.client.key.SLKeyMappings;
import com.coobird.staticlogistics.config.SLConfig;
import com.coobird.staticlogistics.logistics.node.ConnectionKey;
import com.coobird.staticlogistics.logistics.node.FaceTopology;
import com.coobird.staticlogistics.logistics.redstone.RedstoneControlBinding;
import com.coobird.staticlogistics.logistics.util.NodeDisplayText;
import com.coobird.staticlogistics.transfer.LogisticsCalculator;
import com.coobird.staticlogistics.transfer.TransferTypeDisplay;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.Nameable;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * 连接配置器中的数据流拓扑预览。
 *
 * <p>这里只保存当前界面的平移、缩放与选择状态，节点位置由本地布局仓库持久化，
 * 拓扑真相始终来自 {@link ClientLinkData}。节点采用信息卡片表达，每条连接保留独立曲线，
 * 仅合并同一接入侧的终点箭头，避免高扇入节点出现箭头堆叠或梳子状共享母线。
 */
public final class NetworkPreviewPanel {
    private static final int MIN_NODE_WIDTH = 72;
    private static final int NODE_HEIGHT = 27;
    private static final int LAYOUT_PADDING = 1;
    private static final int NODE_GAP = 5;
    private static final int SELECTED_CONNECTION_COLOR = 0xFFFFD45A;
    private static final int CONTROL_SELECTION_COLOR = 0xFF55D7FF;
    private static final int CONTROL_FRAME_COLOR = 0xB055D7FF;
    private static final int CONTROL_FRAME_FILL = 0x182C7180;
    private static final int CONTROL_FRAME_PADDING = 7;
    private static final int CONTROL_FRAME_TITLE_HEIGHT = 11;
    private static final int CONTROL_NODE_HEIGHT = 27;
    private static final double CONNECTION_LINE_WIDTH = 2.0D;
    private static final double SELECTED_CONNECTION_LINE_WIDTH = 4.0D;
    private static final int BOX_SELECTION_COLOR = 0xFF98FB98;
    private static final int BOX_SELECTION_FILL = 0x303A8F4A;
    private static final double BOX_SELECTION_THRESHOLD = 3.0D;
    private static final int MAX_CONTROL_SELECTION = 256;
    private static final double MIN_ZOOM = 0.35D;
    private static final double MAX_ZOOM = 1.8D;

    private final List<NodeHit> nodeHits = new ArrayList<>();
    private final List<ConnectionHit> connectionHits = new ArrayList<>();
    private final List<ControlFrameHit> controlFrameHits = new ArrayList<>();
    private final Map<LogisticsNode, Point> currentLocalPositions = new LinkedHashMap<>();
    private final Map<LogisticsNode, Integer> currentNodeWidths = new LinkedHashMap<>();
    private final Map<ConnectionKey, CachedCurveRoute> curveRouteCache = new HashMap<>();
    private final Map<LogisticsNode, NetworkPreviewLayoutStore.Position>
        dragLandingPositions = new LinkedHashMap<>();
    private final LinkedHashSet<LogisticsNode> selectedNodes = new LinkedHashSet<>();
    private final LinkedHashSet<ConnectionKey> controlSelection = new LinkedHashSet<>();
    @Nullable
    private GroupKey groupKey;
    @Nullable
    private LogisticsNode selectedNode;
    private double panX;
    private double panY;
    private double zoom = 1.0D;
    private boolean centerViewOnNextLayout = true;
    private boolean panning;
    private double panningDistance;
    private boolean boxSelecting;
    private double boxStartX;
    private double boxStartY;
    private double boxEndX;
    private double boxEndY;
    private int previewX;
    private int previewY;
    private int previewWidth;
    private int previewHeight;
    private boolean selectionChangedOnLastRelease;
    private final LinkedHashSet<LogisticsNode> boxSelectionCandidates = new LinkedHashSet<>();
    @Nullable
    private LogisticsNode draggingNode;
    private final LinkedHashSet<LogisticsNode> draggingNodes = new LinkedHashSet<>();

    public void setGroup(@Nullable GroupKey groupKey) {
        if (Objects.equals(this.groupKey, groupKey)) return;
        this.groupKey = groupKey;
        this.selectedNode = null;
        this.selectedNodes.clear();
        this.controlSelection.clear();
        this.panX = 0.0D;
        this.panY = 0.0D;
        this.zoom = 1.0D;
        this.centerViewOnNextLayout = true;
        this.panning = false;
        this.boxSelecting = false;
        this.boxSelectionCandidates.clear();
        this.draggingNode = null;
        this.draggingNodes.clear();
        this.currentLocalPositions.clear();
        this.currentNodeWidths.clear();
        this.curveRouteCache.clear();
        this.dragLandingPositions.clear();
    }

    public void removeLayout(GroupKey groupKey) {
        NetworkPreviewLayoutStore.INSTANCE.remove(groupKey);
    }

    public void flushLayout() {
        NetworkPreviewLayoutStore.INSTANCE.flush();
    }

    /**
     * 让下一帧按当前画布尺寸重新计算可读的居中视图。
     */
    public void resetView() {
        centerViewOnNextLayout = true;
    }

    public boolean isShowingGroup(GroupKey groupKey) {
        return Objects.equals(this.groupKey, groupKey);
    }

    @Nullable
    public LogisticsNode getSelectedNode() {
        return selectedNode;
    }

    public List<LogisticsNode> getSelectedNodes() {
        return List.copyOf(selectedNodes);
    }

    /**
     * 返回准备共同绑定到一个红石检测点的连接。
     */
    public List<ConnectionKey> getRedstoneControlSelection() {
        if (!controlSelection.isEmpty()) {
            controlSelection.removeIf(key -> ClientLinkData.INSTANCE.findConnection(key) == null);
            return List.copyOf(controlSelection);
        }
        if (groupKey != null && selectedNodes.size() > 1) {
            List<ConnectionKey> enclosed = ClientLinkData.INSTANCE
                .getConnectionsForGroup(groupKey).stream()
                .filter(connection -> selectedNodes.contains(connection.first())
                    && selectedNodes.contains(connection.second()))
                .map(ClientConnection::key)
                .limit(MAX_CONTROL_SELECTION)
                .toList();
            if (!enclosed.isEmpty()) return enclosed;
        }
        ConnectionKey focused = SelectionContext.getFocusedConnectionKey();
        return focused == null || ClientLinkData.INSTANCE.findConnection(focused) == null
            ? List.of() : List.of(focused);
    }

    @Nullable
    public ClientConnection getSelectedConnection() {
        ConnectionKey key = SelectionContext.getFocusedConnectionKey();
        if (key == null || !Objects.equals(groupKey, key.groupKey())) {
            return null;
        }
        ClientConnection connection = ClientLinkData.INSTANCE.findConnection(key);
        return connection;
    }

    public void selectConnection(@Nullable ClientConnection connection) {
        if (connection == null) {
            SelectionContext.clearConnectionFocus();
        } else {
            SelectionContext.focusConnection(connection.key());
        }
        this.selectedNode = null;
        this.selectedNodes.clear();
    }

    public void selectNode(@Nullable LogisticsNode node) {
        this.selectedNode = node;
        this.selectedNodes.clear();
        if (node != null) this.selectedNodes.add(node);
        SelectionContext.clearConnectionFocus();
    }

    /**
     * 返回右侧列表需要联动高亮的连接，不改变节点配置或红石控制选择。
     */
    public Set<ConnectionKey> getListHighlightedConnections() {
        if (!controlSelection.isEmpty()) return Set.copyOf(controlSelection);
        ConnectionKey focused = SelectionContext.getFocusedConnectionKey();
        if (focused != null) return Set.of(focused);
        if (groupKey == null || selectedNodes.isEmpty()) return Set.of();
        boolean singleNode = selectedNodes.size() == 1;
        return ClientLinkData.INSTANCE.getConnectionsForGroup(groupKey).stream()
            .filter(connection -> singleNode
                ? selectedNodes.contains(connection.first())
                || selectedNodes.contains(connection.second())
                : selectedNodes.contains(connection.first())
                && selectedNodes.contains(connection.second()))
            .map(ClientConnection::key)
            .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    public void render(GuiGraphics graphics, Font font, int x, int y, int width, int height,
                       int mouseX, int mouseY, double interfaceScale) {
        previewX = x;
        previewY = y;
        previewWidth = width;
        previewHeight = height;
        nodeHits.clear();
        connectionHits.clear();
        controlFrameHits.clear();
        // 调用方传入的就是 atlas 内部可绘制区，不再重复推算外框缩进。
        GuiScissor.enable(graphics, interfaceScale, x, y, x + width, y + height);

        List<ClientConnection> connections = groupKey == null
            ? List.of() : ClientLinkData.INSTANCE.getConnectionsForGroup(groupKey);
        if (connections.isEmpty()) {
            retainVisibleNodes(Set.of());
            currentLocalPositions.clear();
            currentNodeWidths.clear();
            curveRouteCache.clear();
            dragLandingPositions.clear();
            controlSelection.clear();
            Component empty = Component.translatable("gui.staticlogistics.network_preview.empty")
                .withStyle(ChatFormatting.DARK_GRAY);
            graphics.drawString(font, empty,
                x + (width - font.width(empty)) / 2,
                y + (height - font.lineHeight) / 2, 0xFF777777, false);
            graphics.disableScissor();
            return;
        }

        Map<LogisticsNode, VisualNode> nodes = collectNodes(connections, font);
        retainVisibleNodes(nodes.keySet());
        Map<LogisticsNode, Point> positions = layout(connections, nodes, x, y, width, height);
        ViewTransform transform = new ViewTransform(
            x + width / 2.0D,
            y + height / 2.0D,
            zoom,
            panX,
            panY);

        graphics.pose().pushPose();
        graphics.pose().translate(
            transform.centerX + transform.panX,
            transform.centerY + transform.panY,
            0.0D);
        graphics.pose().scale((float) transform.zoom, (float) transform.zoom, 1.0F);
        graphics.pose().translate(-transform.centerX, -transform.centerY, 0.0D);
        renderRedstoneControlFrames(
            graphics, font, connections, nodes, positions, transform);
        Map<LogisticsNode, NetworkPreviewCurveRouter.Rect> nodeRectangles =
            buildNodeRectangles(nodes, positions);
        long curveLayoutSignature = curveLayoutSignature(nodes, positions);
        Set<ConnectionKey> visibleConnectionKeys = connections.stream()
            .map(ClientConnection::key).collect(java.util.stream.Collectors.toSet());
        curveRouteCache.keySet().removeIf(key -> !visibleConnectionKeys.contains(key));
        // 普通线路先绘制，高亮线路最后绘制；视觉层级与点击层级保持一致。
        for (int pass = 0; pass < 2; pass++) {
            boolean highlightedPass = pass == 1;
            for (ClientConnection connection : connections) {
                boolean highlighted = isConnectionHighlighted(connection);
                if (highlighted != highlightedPass) continue;
                Point first = positions.get(connection.first());
                Point second = positions.get(connection.second());
                if (first == null || second == null) continue;
                VisualNode firstNode = nodes.get(connection.first());
                VisualNode secondNode = nodes.get(connection.second());
                if (firstNode == null || secondNode == null) continue;
                renderConnection(graphics, connection, nodeRectangles,
                    curveLayoutSignature, transform, highlighted);
            }
        }
        renderDragLandingPreview(graphics, nodes);
        Set<LogisticsNode> overlappingDraggedNodes = getOverlappingDraggedNodes();
        for (var entry : nodes.entrySet()) {
            Point point = positions.get(entry.getKey());
            if (point != null) {
                renderNode(graphics, font, entry.getValue(), point, transform,
                    overlappingDraggedNodes.contains(entry.getKey()));
            }
        }
        graphics.pose().popPose();
        renderSelectionBox(graphics);
        graphics.disableScissor();
    }

    /**
     * 在拓扑底层按检测点归并连接，用统一范围框表达红石控制集合。
     * 框色不承担分组身份，只用于把控制层与资源传输层区分开。
     */
    private void renderRedstoneControlFrames(
        GuiGraphics graphics,
        Font font,
        List<ClientConnection> connections,
        Map<LogisticsNode, VisualNode> nodes,
        Map<LogisticsNode, Point> positions,
        ViewTransform transform
    ) {
        Map<com.coobird.staticlogistics.logistics.redstone.RedstoneControlBinding,
            List<ClientConnection>> controlGroups = new LinkedHashMap<>();
        for (ClientConnection connection : connections) {
            ClientRedstoneControlData.State state =
                ClientRedstoneControlData.INSTANCE.get(connection.key());
            if (state == null || state.binding() == null) continue;
            controlGroups.computeIfAbsent(state.binding(), ignored -> new ArrayList<>())
                .add(connection);
        }

        for (List<ClientConnection> controlled : controlGroups.values()) {
            ClientRedstoneControlData.State controlState = ClientRedstoneControlData.INSTANCE
                .get(controlled.get(0).key());
            RedstoneControlBinding binding = controlState.binding();
            boolean selectedControlGroup = binding != null && groupKey != null
                && ClientRedstoneControlData.INSTANCE.isControlGroupSelected(
                groupKey, binding);
            FrameBounds bounds = renderRedstoneControlFrame(
                graphics, font, controlled, nodes, positions,
                Component.translatable("gui.staticlogistics.redstone.control_frame",
                    controlled.size()),
                selectedControlGroup ? CONTROL_SELECTION_COLOR : CONTROL_FRAME_COLOR,
                selectedControlGroup ? 0x30408090 : CONTROL_FRAME_FILL,
                binding == null ? null : new ControlNodeInfo(
                    binding, controlState.powered(), controlled.size()));
            if (bounds != null && binding != null && groupKey != null) {
                Point topLeft = transform.apply(new Point(bounds.left, bounds.top));
                controlFrameHits.add(new ControlFrameHit(
                    groupKey, binding,
                    controlled.stream().map(ClientConnection::key).toList(),
                    topLeft.x, topLeft.y,
                    Math.max(1, (int) Math.round(bounds.width() * transform.zoom)),
                    Math.max(1, (int) Math.round(bounds.height() * transform.zoom))));
            }
        }

        if (!controlSelection.isEmpty()) {
            List<ClientConnection> pending = connections.stream()
                .filter(connection -> controlSelection.contains(connection.key()))
                .toList();
            renderRedstoneControlFrame(graphics, font, pending, nodes, positions,
                Component.translatable("gui.staticlogistics.redstone.pending_frame",
                    pending.size()), CONTROL_SELECTION_COLOR, 0x20408090, null);
        }
    }

    private static FrameBounds renderRedstoneControlFrame(
        GuiGraphics graphics,
        Font font,
        List<ClientConnection> connections,
        Map<LogisticsNode, VisualNode> nodes,
        Map<LogisticsNode, Point> positions,
        Component title,
        int borderColor,
        int fillColor,
        @Nullable ControlNodeInfo controlNode
    ) {
        if (connections.isEmpty()) return null;
        int minimumX = Integer.MAX_VALUE;
        int minimumY = Integer.MAX_VALUE;
        int maximumX = Integer.MIN_VALUE;
        int maximumY = Integer.MIN_VALUE;
        for (ClientConnection connection : connections) {
            for (LogisticsNode node : List.of(connection.first(), connection.second())) {
                Point point = positions.get(node);
                VisualNode visual = nodes.get(node);
                if (point == null || visual == null) continue;
                minimumX = Math.min(minimumX, point.x);
                minimumY = Math.min(minimumY, point.y);
                maximumX = Math.max(maximumX, point.x + visual.width);
                maximumY = Math.max(maximumY, point.y + NODE_HEIGHT);
            }
        }
        if (minimumX == Integer.MAX_VALUE) return null;

        int left = minimumX - CONTROL_FRAME_PADDING;
        int right = maximumX + CONTROL_FRAME_PADDING;
        int top;
        int controlLeft = 0;
        int controlWidth = 0;
        Component controlTitle = null;
        Component controlPosition = null;
        if (controlNode != null) {
            controlTitle = Component.translatable(
                "gui.staticlogistics.redstone.control_node",
                controlNode.connectionCount(),
                Component.translatable(controlNode.powered()
                    ? "gui.staticlogistics.redstone.signal_on"
                    : "gui.staticlogistics.redstone.signal_off"));
            controlPosition = Component.translatable(
                "gui.staticlogistics.redstone.control_node_position",
                controlNode.binding().controller().pos().toShortString());
            controlWidth = Math.max(104,
                Math.max(font.width(controlTitle), font.width(controlPosition)) + 8);
            int controlCenter = (minimumX + maximumX) / 2;
            controlLeft = controlCenter - controlWidth / 2;
            left = Math.min(left, controlLeft - CONTROL_FRAME_PADDING);
            right = Math.max(right, controlLeft + controlWidth + CONTROL_FRAME_PADDING);
            top = minimumY - CONTROL_NODE_HEIGHT - CONTROL_FRAME_PADDING - 5;
        } else {
            top = minimumY - CONTROL_FRAME_PADDING - CONTROL_FRAME_TITLE_HEIGHT;
        }
        int bottom = maximumY + CONTROL_FRAME_PADDING;
        graphics.fill(left, top, right, bottom, fillColor);
        graphics.renderOutline(left, top, right - left, bottom - top, borderColor);
        if (controlNode == null) {
            graphics.drawString(font, title, left + 4, top + 2, borderColor, false);
        } else {
            int controlTop = top + CONTROL_FRAME_PADDING;
            int controlCenter = controlLeft + controlWidth / 2;
            int stemBottom = minimumY - 1;
            graphics.fill(controlCenter, controlTop + CONTROL_NODE_HEIGHT,
                controlCenter + 1, stemBottom, borderColor);
            graphics.fill(controlLeft, controlTop,
                controlLeft + controlWidth, controlTop + CONTROL_NODE_HEIGHT,
                0xE0283438);
            graphics.renderOutline(controlLeft, controlTop,
                controlWidth, CONTROL_NODE_HEIGHT, borderColor);
            graphics.drawString(font, controlTitle,
                controlLeft + 4, controlTop + 3, borderColor, false);
            graphics.drawString(font, controlPosition,
                controlLeft + 4, controlTop + 14, 0xFFB9C7CA, false);
        }
        return new FrameBounds(left, top, right, bottom);
    }

    @Nullable
    public RedstoneControlFrameSelection getRedstoneControlFrameAt(
        double mouseX, double mouseY
    ) {
        // 控制框可以因平移或缩放延伸到裁剪区外，但裁掉的部分不能继续抢占界面按钮。
        if (!isInside(mouseX, mouseY,
            previewX, previewY, previewWidth, previewHeight)) return null;
        return controlFrameHits.stream()
            .filter(hit -> hit.contains(mouseX, mouseY))
            .min(Comparator.comparingInt(hit -> hit.width * hit.height))
            .map(hit -> new RedstoneControlFrameSelection(
                hit.groupKey, hit.binding, hit.connections))
            .orElse(null);
    }

    /**
     * 节点和线路绘制在红石控制框上方，交互层级也必须保持一致。
     */
    public boolean hasForegroundHitAt(double mouseX, double mouseY) {
        return isInside(mouseX, mouseY,
            previewX, previewY, previewWidth, previewHeight)
            && (findNodeHit(mouseX, mouseY) != null
            || findConnectionHit(mouseX, mouseY) != null);
    }

    private Map<LogisticsNode, VisualNode> collectNodes(
        List<ClientConnection> connections,
        Font font
    ) {
        Map<LogisticsNode, VisualNode> nodes = new LinkedHashMap<>();
        for (ClientConnection connection : connections) {
            collectNode(nodes, connection.first(), connection.firstTopology(), font);
            collectNode(nodes, connection.second(), connection.secondTopology(), font);
        }
        return nodes;
    }

    private void collectNode(
        Map<LogisticsNode, VisualNode> nodes,
        LogisticsNode node,
        FaceTopology topology,
        Font font
    ) {
        nodes.computeIfAbsent(node, ignored -> {
            String title = nodeName(node) + " · " + NodeDisplayText.direction(node.face()).getString();
            String position = node.gPos().pos().toShortString();
            int contentWidth = Math.max(font.width(title), font.width(position)) + 8;
            int width = Math.max(contentWidth, MIN_NODE_WIDTH);
            return new VisualNode(node, topology, title, position, width);
        });
    }

    /**
     * 拓扑同步可能在拖动途中移除节点；所有瞬态交互状态都只保留当前可见节点。
     */
    private void retainVisibleNodes(Set<LogisticsNode> visibleNodes) {
        selectedNodes.retainAll(visibleNodes);
        boxSelectionCandidates.retainAll(visibleNodes);
        if (selectedNode != null && !visibleNodes.contains(selectedNode)) {
            selectedNode = selectedNodes.stream().findFirst().orElse(null);
        }
        boolean dragChanged = draggingNodes.retainAll(visibleNodes);
        if (draggingNode != null && !visibleNodes.contains(draggingNode)) {
            // 保留锚点到 mouseReleased，仅用于消费这次已由预览开始的鼠标操作。
            draggingNodes.clear();
            dragChanged = true;
        }
        if (dragChanged) dragLandingPositions.clear();
        dragLandingPositions.keySet().retainAll(visibleNodes);
    }

    private Map<LogisticsNode, Point> layout(
        List<ClientConnection> connections,
        Map<LogisticsNode, VisualNode> nodes,
        int x, int y, int width, int height) {
        NetworkPreviewLayoutStore.Layout storedLayout =
            NetworkPreviewLayoutStore.INSTANCE.getOrCreate(Objects.requireNonNull(groupKey));
        Map<LogisticsNode, NetworkPreviewLayoutStore.Position> saved =
            storedLayout.nodePositions();
        boolean needsAutomaticLayout = !storedLayout.legacyNodePositions().isEmpty()
            || nodes.keySet().stream().anyMatch(node -> !saved.containsKey(node));
        Map<LogisticsNode, Point> automatic = new LinkedHashMap<>();
        if (needsAutomaticLayout) {
            Map<LogisticsNode, NetworkPreviewLayoutEngine.Size> sizes =
                new LinkedHashMap<>();
            nodes.forEach((node, visual) -> sizes.put(node,
                new NetworkPreviewLayoutEngine.Size(visual.width, NODE_HEIGHT)));
            NetworkPreviewLayoutEngine.layout(connections, sizes, width)
                .forEach((node, point) -> automatic.put(node,
                    new Point(point.x(), point.y())));
        }
        /*
         * 不在增量拓扑同步期间删除暂时不可见的节点布局。分组真正删除时由布局仓库的
         * removeGroup 统一清理，避免重进存档后刚打开界面就丢失尚未同步到的节点位置。
         */
        boolean layoutChanged = migrateLegacyPositions(storedLayout, automatic);
        if (layoutChanged) {
            NetworkPreviewLayoutStore.INSTANCE.markDirty();
        }
        Map<LogisticsNode, Point> localPositions = new LinkedHashMap<>(automatic);
        saved.forEach((node, point) -> {
            if (nodes.containsKey(node)) {
                localPositions.put(node, new Point(
                    (int) Math.round(point.x()), (int) Math.round(point.y())));
            }
        });
        if (placeNewNodesWithoutMovingSaved(
            localPositions, nodes, saved, connections)) {
            NetworkPreviewLayoutStore.INSTANCE.markDirty();
        }
        if (centerViewOnNextLayout) centerView(localPositions, nodes, width, height);

        currentLocalPositions.clear();
        currentLocalPositions.putAll(localPositions);
        currentNodeWidths.clear();
        nodes.forEach((node, visual) -> currentNodeWidths.put(node, visual.width));
        Map<LogisticsNode, Point> result = new LinkedHashMap<>(localPositions.size());
        localPositions.forEach((node, point) -> result.put(node, new Point(x + point.x, y + point.y)));
        return result;
    }

    /**
     * 已存在的节点位置具有最高优先级，永远不因其他节点加入而移动。尚未保存位置的新节点
     * 会按弱连通分量整体评分空位，并在确定后立即固定下来。
     */
    private static boolean placeNewNodesWithoutMovingSaved(
        Map<LogisticsNode, Point> positions,
        Map<LogisticsNode, VisualNode> nodes,
        Map<LogisticsNode, NetworkPreviewLayoutStore.Position> saved,
        List<ClientConnection> connections
    ) {
        LinkedHashSet<LogisticsNode> newNodes = positions.keySet().stream()
            .filter(node -> !saved.containsKey(node))
            .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        if (newNodes.isEmpty()) return false;
        List<NetworkPreviewPlacementEngine.Edge<LogisticsNode>> edges =
            placementEdges(connections);
        for (Set<LogisticsNode> component : newNodeComponents(newNodes, connections)) {
            Map<LogisticsNode, NetworkPreviewPlacementEngine.Rect> bounds =
                placementBounds(positions, nodes);
            NetworkPreviewPlacementEngine.Offset offset =
                NetworkPreviewPlacementEngine.findBestOffset(
                        bounds, component, edges, NODE_GAP)
                    .map(NetworkPreviewPlacementEngine.Placement::offset)
                    .orElse(new NetworkPreviewPlacementEngine.Offset(0.0D, 0.0D));
            for (LogisticsNode node : component) {
                Point original = positions.get(node);
                Point placed = new Point(
                    (int) Math.round(original.x + offset.x()),
                    (int) Math.round(original.y + offset.y()));
                positions.put(node, placed);
                saved.put(node, new NetworkPreviewLayoutStore.Position(
                    placed.x, placed.y));
            }
        }
        return true;
    }

    private static List<Set<LogisticsNode>> newNodeComponents(
        Set<LogisticsNode> nodes,
        List<ClientConnection> connections
    ) {
        Map<LogisticsNode, Set<LogisticsNode>> neighbors = new LinkedHashMap<>();
        nodes.forEach(node -> neighbors.put(node, new LinkedHashSet<>()));
        for (ClientConnection connection : connections) {
            if (nodes.contains(connection.first()) && nodes.contains(connection.second())) {
                neighbors.get(connection.first()).add(connection.second());
                neighbors.get(connection.second()).add(connection.first());
            }
        }
        List<Set<LogisticsNode>> result = new ArrayList<>();
        Set<LogisticsNode> visited = new HashSet<>();
        for (LogisticsNode start : nodes) {
            if (!visited.add(start)) continue;
            LinkedHashSet<LogisticsNode> component = new LinkedHashSet<>();
            ArrayDeque<LogisticsNode> pending = new ArrayDeque<>();
            pending.add(start);
            while (!pending.isEmpty()) {
                LogisticsNode node = pending.removeFirst();
                component.add(node);
                neighbors.getOrDefault(node, Set.of()).stream()
                    .filter(visited::add).forEach(pending::addLast);
            }
            result.add(Collections.unmodifiableSet(component));
        }
        return List.copyOf(result);
    }

    /**
     * 将旧版按方块坐标保存的位置迁移为按连接面保存。一个方块上的多个面只在旧位置附近展开，
     * 不再通过全局避让把节点推到远处。
     */
    private static boolean migrateLegacyPositions(
        NetworkPreviewLayoutStore.Layout layout,
        Map<LogisticsNode, Point> automatic
    ) {
        if (layout.legacyNodePositions().isEmpty()) return false;
        Map<GlobalPos, List<LogisticsNode>> nodesByPosition = new LinkedHashMap<>();
        automatic.keySet().forEach(node ->
            nodesByPosition.computeIfAbsent(node.gPos(), ignored -> new ArrayList<>()).add(node));
        layout.legacyNodePositions().forEach((position, savedPosition) -> {
            List<LogisticsNode> matching = new ArrayList<>(nodesByPosition.getOrDefault(position, List.of()));
            matching.sort(Comparator.comparing(NetworkPreviewPanel::nodeKey));
            double startY = savedPosition.y() - (matching.size() - 1) * (NODE_HEIGHT + NODE_GAP) / 2.0D;
            for (int index = 0; index < matching.size(); index++) {
                layout.nodePositions().putIfAbsent(matching.get(index),
                    new NetworkPreviewLayoutStore.Position(savedPosition.x(),
                        startY + index * (NODE_HEIGHT + NODE_GAP)));
            }
        });
        layout.legacyNodePositions().clear();
        return true;
    }

    /**
     * 根据已保存的节点布局重建临时视图，使每次打开界面都能看到位于画布中心的网络。
     */
    private void centerView(
        Map<LogisticsNode, Point> positions,
        Map<LogisticsNode, VisualNode> nodes,
        int viewportWidth,
        int viewportHeight
    ) {
        centerViewOnNextLayout = false;
        if (positions.isEmpty()) {
            panX = 0.0D;
            panY = 0.0D;
            zoom = 1.0D;
            return;
        }
        int minimumX = positions.values().stream().mapToInt(Point::x).min().orElse(0);
        int maximumX = positions.entrySet().stream()
            .mapToInt(entry -> entry.getValue().x + nodes.get(entry.getKey()).width)
            .max().orElse(viewportWidth);
        int minimumY = positions.values().stream().mapToInt(Point::y).min().orElse(0);
        int maximumY = positions.values().stream()
            .mapToInt(point -> point.y + NODE_HEIGHT).max().orElse(viewportHeight);
        int contentWidth = Math.max(1, maximumX - minimumX);
        int contentHeight = Math.max(1, maximumY - minimumY);
        double widthScale = (viewportWidth - LAYOUT_PADDING * 2.0D) / contentWidth;
        double heightScale = (viewportHeight - LAYOUT_PADDING * 2.0D) / contentHeight;
        // 小窗与展开窗都按同一套适配比例缩放；窗口扩大时节点、曲线和控制框同步放大。
        zoom = Mth.clamp(Math.min(widthScale, heightScale), MIN_ZOOM, MAX_ZOOM);
        // 节点位置和平移量均使用预览内部坐标，避免重复叠加屏幕绝对坐标。
        panX = (viewportWidth / 2.0D - (minimumX + maximumX) / 2.0D) * zoom;
        panY = (viewportHeight / 2.0D - (minimumY + maximumY) / 2.0D) * zoom;
    }

    private void renderConnection(
        GuiGraphics graphics,
        ClientConnection connection,
        Map<LogisticsNode, NetworkPreviewCurveRouter.Rect> nodeRectangles,
        long curveLayoutSignature,
        ViewTransform transform,
        boolean selected
    ) {
        NetworkPreviewCurveRouter.Route route = curveRoute(
            connection, nodeRectangles, curveLayoutSignature);
        boolean controlSelected = controlSelection.contains(connection.key());
        int selectedColor = controlSelected
            ? CONTROL_SELECTION_COLOR : SELECTED_CONNECTION_COLOR;
        ClientRedstoneControlData.State redstoneState =
            ClientRedstoneControlData.INSTANCE.get(connection.key());
        boolean redstoneAllowed = redstoneState == null || redstoneState.allowed();
        List<CurvePoint> centerLine = route.samples().stream()
            .map(point -> new CurvePoint(point.x(), point.y())).toList();

        DirectionState forward = DirectionState.of(
            connection.first(), connection.firstTopology(), connection.second(),
            connection.transfersFirstToSecond() && redstoneAllowed);
        DirectionState backward = DirectionState.of(
            connection.second(), connection.secondTopology(), connection.first(),
            connection.transfersSecondToFirst() && redstoneAllowed);
        int activeDirections = (forward.active ? 1 : 0) + (backward.active ? 1 : 0);
        if (activeDirections == 0) {
            if (selected) {
                drawSmoothLine(graphics, centerLine,
                    selectedColor, SELECTED_CONNECTION_LINE_WIDTH * transform.zoom, true);
            }
            drawSmoothLine(graphics, centerLine, 0xFF888888,
                CONNECTION_LINE_WIDTH * transform.zoom, true);
            connectionHits.add(new ConnectionHit(connection,
                List.of(centerLine.stream().map(transform::apply).toList())));
            return;
        }

        List<List<Point>> hitPaths = new ArrayList<>(activeDirections);
        double laneOffset = activeDirections == 2 ? 5.5D : 0.0D;
        if (forward.active) {
            renderDirection(graphics, hitPaths, centerLine,
                forward, true, -laneOffset,
                selected, selectedColor, transform);
        }
        if (backward.active) {
            renderDirection(graphics, hitPaths, centerLine,
                backward, false, laneOffset,
                selected, selectedColor, transform);
        }
        connectionHits.add(new ConnectionHit(connection, List.copyOf(hitPaths)));
    }

    private boolean isConnectionHighlighted(ClientConnection connection) {
        if (controlSelection.contains(connection.key())
            || connection.key().equals(SelectionContext.getFocusedConnectionKey())) {
            return true;
        }
        if (selectedNodes.size() == 1) {
            return selectedNodes.contains(connection.first())
                || selectedNodes.contains(connection.second());
        }
        return selectedNodes.size() > 1
            && selectedNodes.contains(connection.first())
            && selectedNodes.contains(connection.second());
    }

    private static Map<LogisticsNode, NetworkPreviewCurveRouter.Rect> buildNodeRectangles(
        Map<LogisticsNode, VisualNode> nodes,
        Map<LogisticsNode, Point> positions
    ) {
        Map<LogisticsNode, NetworkPreviewCurveRouter.Rect> result = new LinkedHashMap<>();
        nodes.forEach((node, visual) -> {
            Point point = positions.get(node);
            if (point != null) {
                result.put(node, new NetworkPreviewCurveRouter.Rect(
                    point.x, point.y, visual.width, NODE_HEIGHT));
            }
        });
        return result;
    }

    private static Map<LogisticsNode, NetworkPreviewPlacementEngine.Rect> placementBounds(
        Map<LogisticsNode, Point> positions,
        Map<LogisticsNode, VisualNode> nodes
    ) {
        Map<LogisticsNode, NetworkPreviewPlacementEngine.Rect> result =
            new LinkedHashMap<>();
        positions.forEach((node, point) -> {
            VisualNode visual = nodes.get(node);
            if (visual != null) {
                result.put(node, new NetworkPreviewPlacementEngine.Rect(
                    point.x, point.y, visual.width, NODE_HEIGHT));
            }
        });
        return result;
    }

    private static List<NetworkPreviewPlacementEngine.Edge<LogisticsNode>> placementEdges(
        Collection<ClientConnection> connections
    ) {
        return connections.stream()
            .map(connection -> new NetworkPreviewPlacementEngine.Edge<>(
                connection.first(), connection.second()))
            .distinct().toList();
    }

    private NetworkPreviewCurveRouter.Route curveRoute(
        ClientConnection connection,
        Map<LogisticsNode, NetworkPreviewCurveRouter.Rect> rectangles,
        long layoutSignature
    ) {
        NetworkPreviewCurveRouter.Rect source = rectangles.get(connection.first());
        NetworkPreviewCurveRouter.Rect target = rectangles.get(connection.second());
        if (source == null || target == null) {
            throw new IllegalStateException("Connection endpoints must have preview rectangles");
        }
        if (draggingNodes.isEmpty()) {
            CachedCurveRoute cached = curveRouteCache.get(connection.key());
            if (cached != null && cached.layoutSignature == layoutSignature) {
                return cached.route;
            }
        }
        Collection<NetworkPreviewCurveRouter.Rect> obstacles = draggingNodes.isEmpty()
            ? rectangles.entrySet().stream()
            .filter(entry -> !entry.getKey().equals(connection.first())
                && !entry.getKey().equals(connection.second()))
            .map(Map.Entry::getValue)
            .filter(obstacle -> isRelevantCurveObstacle(
                source, target, obstacle))
            .toList()
            : List.of();
        NetworkPreviewCurveRouter.Route route =
            NetworkPreviewCurveRouter.route(source, target, obstacles);
        if (draggingNodes.isEmpty()) {
            curveRouteCache.put(connection.key(),
                new CachedCurveRoute(layoutSignature, route));
        }
        return route;
    }

    private static boolean isRelevantCurveObstacle(
        NetworkPreviewCurveRouter.Rect source,
        NetworkPreviewCurveRouter.Rect target,
        NetworkPreviewCurveRouter.Rect obstacle
    ) {
        double padding = 72.0D;
        double left = Math.min(source.left(), target.left()) - padding;
        double right = Math.max(source.right(), target.right()) + padding;
        double top = Math.min(source.top(), target.top()) - padding;
        double bottom = Math.max(source.bottom(), target.bottom()) + padding;
        return obstacle.right() >= left && obstacle.left() <= right
            && obstacle.bottom() >= top && obstacle.top() <= bottom;
    }

    private static long curveLayoutSignature(
        Map<LogisticsNode, VisualNode> nodes,
        Map<LogisticsNode, Point> positions
    ) {
        long hash = 0xcbf29ce484222325L;
        for (LogisticsNode node : nodes.keySet()) {
            Point point = positions.get(node);
            VisualNode visual = nodes.get(node);
            if (point == null || visual == null) continue;
            hash = (hash ^ node.hashCode()) * 0x100000001b3L;
            hash = (hash ^ point.x) * 0x100000001b3L;
            hash = (hash ^ point.y) * 0x100000001b3L;
            hash = (hash ^ visual.width) * 0x100000001b3L;
        }
        return hash;
    }

    /**
     * 一条可传输方向对应一条独立曲线。双向连接因此会形成两条平行曲线，
     * 每条曲线独立表达自己的距离上限、可达状态与传输方向。
     */
    private static void renderDirection(
        GuiGraphics graphics,
        List<List<Point>> hitPaths,
        List<CurvePoint> centerLine,
        DirectionState direction,
        boolean startToEnd,
        double offset,
        boolean selected,
        int selectedColor,
        ViewTransform transform
    ) {
        List<CurvePoint> line = offsetLine(centerLine, offset);
        if (selected) {
            drawSmoothLine(graphics, line,
                selectedColor, SELECTED_CONNECTION_LINE_WIDTH * transform.zoom,
                !direction.allowed);
        }
        int semanticColor = direction.color();
        drawSmoothLine(graphics, line, semanticColor,
            CONNECTION_LINE_WIDTH * transform.zoom, !direction.allowed);
        drawDirectionArrow(graphics, line, startToEnd,
            semanticColor, CONNECTION_LINE_WIDTH * transform.zoom);
        hitPaths.add(line.stream().map(transform::apply).toList());
    }

    /**
     * 双向连接使用两条完整错开的曲线，避免方向信息挤在同一条线上。
     */
    private static List<CurvePoint> offsetLine(List<CurvePoint> centerLine, double offset) {
        if (offset == 0.0D) return centerLine;
        List<CurvePoint> result = new ArrayList<>(centerLine.size());
        for (int index = 0; index < centerLine.size(); index++) {
            CurvePoint point = centerLine.get(index);
            CurvePoint previous = centerLine.get(Math.max(0, index - 1));
            CurvePoint next = centerLine.get(Math.min(centerLine.size() - 1, index + 1));
            double tangentX = next.x - previous.x;
            double tangentY = next.y - previous.y;
            double length = Math.hypot(tangentX, tangentY);
            double normalX = length < 0.001D ? 0.0D : -tangentY / length;
            double normalY = length < 0.001D ? 0.0D : tangentX / length;
            result.add(new CurvePoint(
                point.x + normalX * offset,
                point.y + normalY * offset));
        }
        return List.copyOf(result);
    }

    /**
     * 每条传输方向在自己的目标端绘制箭头，保证双向连接清晰可辨。
     */
    private static void drawDirectionArrow(
        GuiGraphics graphics,
        List<CurvePoint> line,
        boolean startToEnd,
        int color,
        double width
    ) {
        int arrowIndex = Mth.clamp((int) Math.round(
                (startToEnd ? 0.68D : 0.32D) * (line.size() - 1)),
            0, line.size() - 1);
        CurvePoint before = line.get(Math.max(0, arrowIndex - 2));
        CurvePoint after = line.get(Math.min(line.size() - 1, arrowIndex + 2));
        double directionX = after.x - before.x;
        double directionY = after.y - before.y;
        double length = Math.hypot(directionX, directionY);
        if (length < 0.001D) return;
        directionX /= length;
        directionY /= length;
        if (!startToEnd) {
            directionX = -directionX;
            directionY = -directionY;
        }
        CurvePoint tip = line.get(arrowIndex);
        double baseX = tip.x - directionX * 5.0D;
        double baseY = tip.y - directionY * 5.0D;
        double normalX = -directionY * 2.8D;
        double normalY = directionX * 2.8D;
        List<CurvePoint> arrow = List.of(
            new CurvePoint(baseX + normalX, baseY + normalY),
            tip,
            new CurvePoint(baseX - normalX, baseY - normalY));
        drawLineStrip(graphics, arrow, 0, arrow.size(), color, width);
    }

    /**
     * 使用 GPU 线带绘制曲线，避免逐像素填充产生明显的阶梯和方块感。
     * 每条连接单独提交，防止不同连接被 LINE_STRIP 自动首尾相连。
     */
    private static void drawSmoothLine(
        GuiGraphics graphics,
        List<CurvePoint> points,
        int color,
        double width,
        boolean dashed
    ) {
        if (points.size() < 2) return;
        if (!dashed) {
            drawLineStrip(graphics, points, 0, points.size(), color, width);
            return;
        }

        // 虚线仍由平滑线段组成，只通过间隔表达不可用状态。
        int dashLength = 5;
        int gapLength = 3;
        int step = dashLength + gapLength;
        for (int start = 0; start < points.size() - 1; start += step) {
            int end = Math.min(start + dashLength + 1, points.size());
            drawLineStrip(graphics, points, start, end, color, width);
        }
    }

    private static void drawLineStrip(
        GuiGraphics graphics,
        List<CurvePoint> points,
        int start,
        int end,
        int color,
        double width
    ) {
        if (end - start < 2) return;
        VertexConsumer consumer = graphics.bufferSource()
            .getBuffer(RenderType.debugLineStrip(width));
        int alpha = color >>> 24;
        int red = color >> 16 & 0xFF;
        int green = color >> 8 & 0xFF;
        int blue = color & 0xFF;
        for (int i = start; i < end; i++) {
            CurvePoint point = points.get(i);
            consumer.vertex(graphics.pose().last().pose(),
                    (float) point.x, (float) point.y, 0.0F)
                .color(red, green, blue, alpha).endVertex();
        }
        graphics.flush();
    }

    private void renderNode(GuiGraphics graphics, Font font, VisualNode visualNode, Point point,
                            ViewTransform transform, boolean overlappingWhileDragging) {
        LogisticsNode node = visualNode.representative();
        FaceTopology topology = visualNode.topology();
        boolean primarySelected = node.equals(selectedNode);
        boolean selected = selectedNodes.contains(node);
        boolean boxCandidate = boxSelectionCandidates.contains(node) && !selected;
        int border = overlappingWhileDragging ? 0xFFFF5555 : primarySelected ? 0xFF98FB98
            : selected ? 0xFFFFD45A : boxCandidate ? BOX_SELECTION_COLOR : 0xFF767676;
        int background = overlappingWhileDragging ? 0xFF6A3030 : primarySelected ? 0xFF315B36
            : selected ? 0xFF5A5130 : boxCandidate ? 0xFF3F5942 : 0xFF3A3A3A;
        int nodeWidth = visualNode.width;
        graphics.fill(point.x, point.y, point.x + nodeWidth, point.y + NODE_HEIGHT, border);
        graphics.fill(point.x + 2, point.y + 2,
            point.x + nodeWidth - 2, point.y + NODE_HEIGHT - 2, background);

        graphics.drawString(font, font.plainSubstrByWidth(visualNode.title, nodeWidth - 8),
            point.x + 4, point.y + 4, primarySelected ? 0xFF98FB98 : selected ? 0xFFFFD45A : 0xFFEDEDED, false);
        graphics.drawString(font, font.plainSubstrByWidth(visualNode.positionText, nodeWidth - 8),
            point.x + 4, point.y + 15, 0xFFAAAAAA, false);
        Point screenPoint = transform.apply(point);
        nodeHits.add(new NodeHit(visualNode, topology, screenPoint.x, screenPoint.y,
            Math.max(1, (int) Math.round(nodeWidth * transform.zoom)),
            Math.max(1, (int) Math.round(NODE_HEIGHT * transform.zoom))));
    }

    /**
     * 红色实体表示当前冲突位置，绿色幽灵提前显示松手后的评分落点。
     */
    private void renderDragLandingPreview(
        GuiGraphics graphics,
        Map<LogisticsNode, VisualNode> nodes
    ) {
        if (net.minecraft.client.gui.screens.Screen.hasAltDown()) return;
        for (var entry : dragLandingPositions.entrySet()) {
            VisualNode visual = nodes.get(entry.getKey());
            if (visual == null) continue;
            int x = previewX + (int) Math.round(entry.getValue().x());
            int y = previewY + (int) Math.round(entry.getValue().y());
            graphics.fill(x, y, x + visual.width, y + NODE_HEIGHT, 0x303A8F4A);
            graphics.renderOutline(x, y, visual.width, NODE_HEIGHT, 0xCC98FB98);
        }
    }

    private String nodeName(LogisticsNode node) {
        Minecraft minecraft = Minecraft.getInstance();
        int chunkX = node.gPos().pos().getX() >> 4;
        int chunkZ = node.gPos().pos().getZ() >> 4;
        if (minecraft.level == null
            || !node.isInSameDimension(minecraft.level.dimension())
            || !minecraft.level.hasChunk(chunkX, chunkZ)) {
            return Component.translatable("gui.staticlogistics.network_preview.node").getString();
        }
        if (minecraft.level.getBlockEntity(node.gPos().pos()) instanceof Nameable nameable) {
            return nameable.getDisplayName().getString();
        }
        return minecraft.level.getBlockState(node.gPos().pos()).getBlock()
            .getName().getString();
    }

    /**
     * 在容器及所有区域完成绘制后渲染悬停信息，避免被后绘制的面板覆盖。
     */
    public void renderTooltip(
        GuiGraphics graphics,
        Font font,
        int mouseX,
        int mouseY,
        int x,
        int y,
        int width,
        int height
    ) {
        if (!isInside(mouseX, mouseY, x, y, width, height)) return;
        NodeHit hoveredNode = findNodeHit(mouseX, mouseY);
        if (hoveredNode != null) {
            NodeHit hit = hoveredNode;
            graphics.renderComponentTooltip(font,
                buildNodeTooltip(hit.node.representative(), hit.topology), mouseX, mouseY);
            return;
        }
        ConnectionHit hoveredConnection = findConnectionHit(mouseX, mouseY);
        if (hoveredConnection != null) {
            ConnectionHit hit = hoveredConnection;
            LinkState state = LinkState.of(hit.connection);
            List<Component> tooltip = new ArrayList<>();
            tooltip.add(Component.translatable("gui.staticlogistics.connection")
                .withStyle(ChatFormatting.GOLD));
            tooltip.addAll(state.details);
            graphics.renderComponentTooltip(font, tooltip, mouseX, mouseY);
            return;
        }
        ControlFrameHit controlHit = controlFrameHits.stream()
            .filter(hit -> hit.contains(mouseX, mouseY))
            .min(Comparator.comparingInt(hit -> hit.width * hit.height))
            .orElse(null);
        if (controlHit != null) {
            ClientRedstoneControlData.State state = ClientRedstoneControlData.INSTANCE
                .get(controlHit.connections.get(0));
            List<Component> tooltip = new ArrayList<>();
            tooltip.add(Component.translatable(
                "gui.staticlogistics.redstone.control_frame",
                controlHit.connections.size()).withStyle(ChatFormatting.AQUA));
            tooltip.add(Component.translatable(
                state != null && state.powered()
                    ? "gui.staticlogistics.redstone.tooltip.powered_state"
                    : "gui.staticlogistics.redstone.tooltip.unpowered_state"));
            tooltip.add(Component.translatable(
                    "gui.staticlogistics.redstone.tooltip.preview_group")
                .withStyle(ChatFormatting.GREEN));
            tooltip.add(Component.translatable(
                    "gui.staticlogistics.redstone.tooltip.remove_group")
                .withStyle(ChatFormatting.RED));
            graphics.renderComponentTooltip(font, tooltip, mouseX, mouseY);
            return;
        }
        graphics.renderComponentTooltip(font, List.of(
            Component.translatable("gui.staticlogistics.network_preview.controls")
                .withStyle(ChatFormatting.GOLD),
            Component.translatable("gui.staticlogistics.network_preview.multi_select_hint",
                SLKeyMappings.NETWORK_PREVIEW_MULTI_SELECT.getTranslatedKeyMessage()),
            Component.translatable("gui.staticlogistics.network_preview.box_select_hint",
                SLKeyMappings.NETWORK_PREVIEW_MULTI_SELECT.getTranslatedKeyMessage()),
            Component.translatable("gui.staticlogistics.network_preview.drag_selected_hint"),
            Component.translatable("gui.staticlogistics.network_preview.landing_preview_hint"),
            Component.translatable("gui.staticlogistics.network_preview.force_overlap_hint"),
            Component.translatable("gui.staticlogistics.network_preview.clear_selection_hint"),
            Component.translatable("gui.staticlogistics.network_preview.zoom_hint")
        ), mouseX, mouseY);
    }

    /** 距离优先；距离相同时选择最后绘制、也就是视觉上位于最上层的线路。 */
    /**
     * 节点按列表正序绘制，因此逆序命中才能选中视觉上最上层的重叠节点。
     */
    @Nullable
    private NodeHit findNodeHit(double mouseX, double mouseY) {
        for (int index = nodeHits.size() - 1; index >= 0; index--) {
            NodeHit hit = nodeHits.get(index);
            if (hit.contains(mouseX, mouseY)) return hit;
        }
        return null;
    }

    @Nullable
    private ConnectionHit findConnectionHit(double mouseX, double mouseY) {
        ConnectionHit best = null;
        double bestDistance = ConnectionHit.HIT_TOLERANCE_SQUARED;
        for (ConnectionHit hit : connectionHits) {
            double distance = hit.distanceSquared(mouseX, mouseY);
            if (distance > ConnectionHit.HIT_TOLERANCE_SQUARED) continue;
            if (best == null || distance < bestDistance - 1.0E-6D
                || Math.abs(distance - bestDistance) <= 1.0E-6D) {
                best = hit;
                bestDistance = distance;
            }
        }
        return best;
    }

    private List<Component> buildNodeTooltip(LogisticsNode node, FaceTopology topology) {
        List<Component> lines = new ArrayList<>();
        lines.add(NodeDisplayText.details(node));
        lines.add(Component.empty());
        lines.add(stateLine("gui.staticlogistics.input", topology.role().canReceive()));
        lines.add(stateLine("gui.staticlogistics.output", topology.role().canSend()));
        if (topology.role().canReceive()) {
            lines.add(resourceTypeLine(
                "gui.staticlogistics.receive_types", topology.acceptedTypeIds()));
        }
        if (topology.role().canSend()) {
            lines.add(resourceTypeLine(
                "gui.staticlogistics.transfer_types", topology.outputTypeIds()));
            lines.addAll(TransferTypeDisplay.formatTransferAmounts(
                topology.outputTypeIds(), topology.stackMultiplier()));
        }
        if (!SLConfig.isSimpleMode()) {
            lines.add(Component.empty());
            lines.add(Component.translatable("gui.staticlogistics.upgrades").withStyle(ChatFormatting.AQUA));
            lines.addAll(NodeUpgradeDisplay.all(topology.speedMultiplier(), topology.rangeMultiplier(), topology.stackMultiplier(), topology.dimensionEffective()));
        }
        return lines;
    }

    private static Component resourceTypeLine(
        String translationKey,
        List<ResourceLocation> typeIds
    ) {
        return Component.translatable(
            translationKey,
            TransferTypeDisplay.format(typeIds, "gui.staticlogistics.no_resource_types")
        ).withStyle(ChatFormatting.GRAY);
    }

    private static Component stateLine(String labelKey, boolean enabled) {
        return Component.translatable(labelKey)
            .withStyle(ChatFormatting.GRAY)
            .append(Component.literal(": "))
            .append(Component.translatable(enabled
                    ? "gui.staticlogistics.enabled"
                    : "gui.staticlogistics.disabled")
                .withStyle(enabled
                    ? ChatFormatting.GREEN
                    : ChatFormatting.DARK_GRAY));
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button,
                                int x, int y, int width, int height) {
        if (!isInside(mouseX, mouseY, x, y, width, height)) return false;
        boolean multiSelect = SLKeyMappings.isKeyDown(SLKeyMappings.NETWORK_PREVIEW_MULTI_SELECT);
        NodeHit clickedNodeHit = findNodeHit(mouseX, mouseY);
        if (clickedNodeHit != null) {
            NodeHit hit = clickedNodeHit;
            LogisticsNode clickedNode = hit.node.representative();
            if (multiSelect && button == 0) {
                toggleNodeSelection(clickedNode);
            } else if (button == 0 && selectedNodes.contains(clickedNode)) {
                selectedNode = clickedNode;
            } else {
                selectedNodes.clear();
                selectedNodes.add(clickedNode);
                selectedNode = clickedNode;
            }
            SelectionContext.clearConnectionFocus();
            draggingNode = button == 0
                && !multiSelect
                ? hit.node.position() : null;
            draggingNodes.clear();
            if (draggingNode != null) {
                draggingNodes.addAll(selectedNodes.contains(draggingNode)
                    ? selectedNodes : List.of(draggingNode));
            }
            panning = false;
            boxSelecting = false;
            boxSelectionCandidates.clear();
            panningDistance = 0.0D;
            SoundUtil.playClickSound();
            return true;
        }
        ConnectionHit clickedConnection = findConnectionHit(mouseX, mouseY);
        if (clickedConnection != null) {
            ConnectionHit hit = clickedConnection;
            if (multiSelect && button == 0) {
                ConnectionKey key = hit.connection.key();
                if (!controlSelection.remove(key)
                    && controlSelection.size() < MAX_CONTROL_SELECTION) {
                    controlSelection.add(key);
                }
            } else {
                controlSelection.clear();
                selectConnection(hit.connection);
            }
            draggingNode = null;
            draggingNodes.clear();
            panning = false;
            boxSelecting = false;
            boxSelectionCandidates.clear();
            panningDistance = 0.0D;
            SoundUtil.playClickSound();
            return true;
        }
        if (button == 0) {
            draggingNode = null;
            draggingNodes.clear();
            if (multiSelect) {
                beginBoxSelection(mouseX, mouseY, x, y, width, height);
            } else {
                panning = true;
                boxSelecting = false;
                boxSelectionCandidates.clear();
            }
            panningDistance = 0.0D;
            return true;
        }
        if (button == 1 && (!selectedNodes.isEmpty() || getSelectedConnection() != null)) {
            selectedNode = null;
            selectedNodes.clear();
            SelectionContext.clearConnectionFocus();
            SoundUtil.playClickSound();
            return true;
        }
        return false;
    }

    public boolean mouseDragged(double deltaX, double deltaY) {
        if (boxSelecting) {
            boxEndX = Mth.clamp(boxEndX + deltaX, previewX, previewX + previewWidth);
            boxEndY = Mth.clamp(boxEndY + deltaY, previewY, previewY + previewHeight);
            updateBoxSelectionCandidatesFromHits();
            return true;
        }
        if (draggingNode != null && groupKey != null) {
            Map<LogisticsNode, NetworkPreviewLayoutStore.Position> layout =
                NetworkPreviewLayoutStore.INSTANCE.getOrCreate(groupKey)
                    .nodePositions();
            double localDeltaX = deltaX / zoom;
            double localDeltaY = deltaY / zoom;
            Map<LogisticsNode, NetworkPreviewLayoutStore.Position> proposed =
                new LinkedHashMap<>();
            for (LogisticsNode node : draggingNodes) {
                NetworkPreviewLayoutStore.Position point = layout.get(node);
                if (point == null) {
                    Point current = currentLocalPositions.get(node);
                    if (current == null) continue;
                    point = new NetworkPreviewLayoutStore.Position(current.x, current.y);
                }
                proposed.put(node, new NetworkPreviewLayoutStore.Position(
                    point.x() + localDeltaX, point.y() + localDeltaY));
            }
            if (proposed.isEmpty()) return true;
            layout.putAll(proposed);
            updateDragLandingPreview(layout);
            NetworkPreviewLayoutStore.INSTANCE.markDirty();
            return true;
        }
        if (!panning) return false;
        panningDistance += Math.hypot(deltaX, deltaY);
        if (panningDistance < 2.0D) return true;
        panX += deltaX;
        panY += deltaY;
        return true;
    }

    private void updateDragLandingPreview(
        Map<LogisticsNode, NetworkPreviewLayoutStore.Position> layout
    ) {
        dragLandingPositions.clear();
        if (groupKey == null || draggingNodes.isEmpty()
            || net.minecraft.client.gui.screens.Screen.hasAltDown()) return;
        Map<LogisticsNode, NetworkPreviewPlacementEngine.Rect> bounds =
            new LinkedHashMap<>();
        currentNodeWidths.forEach((node, width) -> {
            NetworkPreviewLayoutStore.Position position = layout.get(node);
            if (position == null) {
                Point fallback = currentLocalPositions.get(node);
                if (fallback != null) {
                    position = new NetworkPreviewLayoutStore.Position(
                        fallback.x, fallback.y);
                }
            }
            if (position != null) {
                bounds.put(node, new NetworkPreviewPlacementEngine.Rect(
                    position.x(), position.y(), width, NODE_HEIGHT));
            }
        });
        LinkedHashSet<LogisticsNode> visibleMovingNodes = draggingNodes.stream()
            .filter(bounds::containsKey)
            .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        if (visibleMovingNodes.isEmpty()) return;
        Optional<NetworkPreviewPlacementEngine.Placement> placement =
            NetworkPreviewPlacementEngine.findBestOffset(
                bounds, visibleMovingNodes,
                placementEdges(ClientLinkData.INSTANCE.getConnectionsForGroup(groupKey)),
                NODE_GAP);
        if (placement.isEmpty()) return;
        NetworkPreviewPlacementEngine.Offset offset = placement.get().offset();
        if (Math.abs(offset.x()) < 0.001D && Math.abs(offset.y()) < 0.001D) return;
        for (LogisticsNode node : visibleMovingNodes) {
            NetworkPreviewLayoutStore.Position point = layout.get(node);
            if (point != null) {
                dragLandingPositions.put(node,
                    new NetworkPreviewLayoutStore.Position(
                        point.x() + offset.x(), point.y() + offset.y()));
            }
        }
    }

    private void applyDragLandingPreview() {
        if (groupKey == null || dragLandingPositions.isEmpty()) return;
        NetworkPreviewLayoutStore.INSTANCE.getOrCreate(groupKey)
            .nodePositions().putAll(dragLandingPositions);
        NetworkPreviewLayoutStore.INSTANCE.markDirty();
    }

    private Set<LogisticsNode> getOverlappingDraggedNodes() {
        if (draggingNodes.isEmpty()) return Set.of();
        Set<LogisticsNode> result = new HashSet<>();
        for (LogisticsNode moved : draggingNodes) {
            Point movedPoint = currentLocalPositions.get(moved);
            if (movedPoint == null) continue;
            int movedWidth = currentNodeWidths.getOrDefault(moved, MIN_NODE_WIDTH);
            for (var stationary : currentLocalPositions.entrySet()) {
                if (draggingNodes.contains(stationary.getKey())) continue;
                int stationaryWidth = currentNodeWidths.getOrDefault(
                    stationary.getKey(), MIN_NODE_WIDTH);
                if (overlaps(movedPoint, movedWidth,
                    stationary.getValue(), stationaryWidth)) {
                    result.add(moved);
                    break;
                }
            }
        }
        return Set.copyOf(result);
    }

    private static boolean overlaps(Point first, int firstWidth,
                                    Point second, int secondWidth) {
        return first.x < second.x + secondWidth + NODE_GAP
            && first.x + firstWidth + NODE_GAP > second.x
            && first.y < second.y + NODE_HEIGHT + NODE_GAP
            && first.y + NODE_HEIGHT + NODE_GAP > second.y;
    }

    /**
     * 结束节点拖动、框选或画布平移，并告知上层是否应拦截本次释放事件。
     *
     * <p>若把预览内开始的释放事件继续交给容器界面，原版槽位拖拽状态可能被意外结束，
     * 因此预览负责完整消费自身发起的拖动与平移操作。
     */
    public boolean mouseReleased() {
        selectionChangedOnLastRelease = false;
        boolean handled = boxSelecting || panning || draggingNode != null;
        if (boxSelecting && selectionBoxHasArea()) {
            LogisticsNode lastAdded = null;
            for (LogisticsNode node : boxSelectionCandidates) {
                if (selectedNodes.add(node)) lastAdded = node;
            }
            if (lastAdded != null) {
                selectedNode = lastAdded;
                SelectionContext.clearConnectionFocus();
                selectionChangedOnLastRelease = true;
                SoundUtil.playClickSound();
            }
        }
        if (draggingNode != null && groupKey != null
            && !net.minecraft.client.gui.screens.Screen.hasAltDown()) {
            updateDragLandingPreview(NetworkPreviewLayoutStore.INSTANCE
                .getOrCreate(groupKey).nodePositions());
            applyDragLandingPreview();
        }
        panning = false;
        panningDistance = 0.0D;
        boxSelecting = false;
        boxSelectionCandidates.clear();
        draggingNode = null;
        draggingNodes.clear();
        dragLandingPositions.clear();
        return handled;
    }

    public boolean selectionChangedOnLastRelease() {
        return selectionChangedOnLastRelease;
    }

    private void beginBoxSelection(double mouseX, double mouseY,
                                   int x, int y, int width, int height) {
        previewX = x;
        previewY = y;
        previewWidth = width;
        previewHeight = height;
        boxStartX = boxEndX = mouseX;
        boxStartY = boxEndY = mouseY;
        boxSelecting = true;
        panning = false;
        boxSelectionCandidates.clear();
    }

    private void updateBoxSelectionCandidatesFromHits() {
        if (!selectionBoxHasArea()) {
            boxSelectionCandidates.clear();
            return;
        }
        boxSelectionCandidates.clear();
        for (NodeHit hit : nodeHits) {
            // 以节点卡片中心是否落入选框为准，避免只擦到边框就意外选中。
            double centerX = hit.x + hit.width / 2.0D;
            double centerY = hit.y + hit.height / 2.0D;
            if (isInsideSelectionBox(centerX, centerY)) {
                boxSelectionCandidates.add(hit.node.representative());
            }
        }
    }

    private boolean selectionBoxHasArea() {
        return Math.abs(boxEndX - boxStartX) >= BOX_SELECTION_THRESHOLD
            || Math.abs(boxEndY - boxStartY) >= BOX_SELECTION_THRESHOLD;
    }

    private boolean isInsideSelectionBox(double x, double y) {
        return x >= Math.min(boxStartX, boxEndX) && x <= Math.max(boxStartX, boxEndX)
            && y >= Math.min(boxStartY, boxEndY) && y <= Math.max(boxStartY, boxEndY)
            && isInside(x, y, previewX, previewY, previewWidth, previewHeight);
    }

    private void renderSelectionBox(GuiGraphics graphics) {
        if (!boxSelecting || !selectionBoxHasArea()) return;
        int left = (int) Math.floor(Math.min(boxStartX, boxEndX));
        int top = (int) Math.floor(Math.min(boxStartY, boxEndY));
        int right = (int) Math.ceil(Math.max(boxStartX, boxEndX));
        int bottom = (int) Math.ceil(Math.max(boxStartY, boxEndY));
        graphics.fill(left, top, right, bottom, BOX_SELECTION_FILL);
        graphics.fill(left, top, right, top + 1, BOX_SELECTION_COLOR);
        graphics.fill(left, bottom - 1, right, bottom, BOX_SELECTION_COLOR);
        graphics.fill(left, top, left + 1, bottom, BOX_SELECTION_COLOR);
        graphics.fill(right - 1, top, right, bottom, BOX_SELECTION_COLOR);
    }

    private void toggleNodeSelection(LogisticsNode node) {
        if (selectedNodes.remove(node)) {
            selectedNode = selectedNodes.isEmpty() ? null
                : selectedNodes.stream().reduce((first, second) -> second).orElse(null);
            return;
        }
        selectedNodes.add(node);
        selectedNode = node;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double delta,
                                 int x, int y, int width, int height) {
        if (!isInside(mouseX, mouseY, x, y, width, height)) return false;
        if (delta == 0.0D) return false;

        double previousZoom = zoom;
        double nextZoom = Mth.clamp(
            previousZoom * Math.pow(1.12D, delta), MIN_ZOOM, MAX_ZOOM);
        if (nextZoom == previousZoom) return true;

        // 以鼠标指向的位置为缩放锚点，缩放前后的拓扑点保持在光标下方。
        double centerX = x + width / 2.0D;
        double centerY = y + height / 2.0D;
        double worldOffsetX = (mouseX - centerX - panX) / previousZoom;
        double worldOffsetY = (mouseY - centerY - panY) / previousZoom;
        panX = mouseX - centerX - worldOffsetX * nextZoom;
        panY = mouseY - centerY - worldOffsetY * nextZoom;
        zoom = nextZoom;
        return true;
    }

    private static String nodeKey(LogisticsNode node) {
        return node.gPos().dimension().location() + "/" + node.gPos().pos().asLong()
            + "/" + node.face().getName();
    }

    private static boolean isInside(double mouseX, double mouseY,
                                    int x, int y, int width, int height) {
        return mouseX >= x
            && mouseX < x + width
            && mouseY >= y
            && mouseY < y + height;
    }

    private record Point(int x, int y) {
    }

    /**
     * 保留亚像素精度的曲线采样点，避免 GPU 线带沿整数网格折动。
     */
    private record CurvePoint(double x, double y) {
    }

    private record CachedCurveRoute(
        long layoutSignature,
        NetworkPreviewCurveRouter.Route route
    ) {
    }

    /**
     * 将拓扑布局坐标统一映射到屏幕坐标；绘制与命中检测必须共享该变换。
     */
    private record ViewTransform(
        double centerX,
        double centerY,
        double zoom,
        double panX,
        double panY
    ) {
        private Point apply(Point point) {
            return apply(point.x, point.y);
        }

        private Point apply(CurvePoint point) {
            return apply(point.x, point.y);
        }

        private Point apply(double x, double y) {
            return new Point(
                (int) Math.round(centerX + (x - centerX) * zoom + panX),
                (int) Math.round(centerY + (y - centerY) * zoom + panY));
        }
    }

    private record NodeHit(
        VisualNode node,
        FaceTopology topology,
        int x,
        int y,
        int width,
        int height
    ) {
        private boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX < x + width
                && mouseY >= y && mouseY < y + height;
        }
    }

    private record VisualNode(
        LogisticsNode representative,
        FaceTopology topology,
        String title,
        String positionText,
        int width
    ) {
        private LogisticsNode position() {
            return representative;
        }
    }

    private record ConnectionHit(ClientConnection connection, List<List<Point>> paths) {
        private static final double HIT_TOLERANCE_SQUARED = 25.0D;

        private boolean contains(double mouseX, double mouseY) {
            return distanceSquared(mouseX, mouseY) <= HIT_TOLERANCE_SQUARED;
        }

        private double distanceSquared(double mouseX, double mouseY) {
            double closest = Double.POSITIVE_INFINITY;
            for (List<Point> path : paths) {
                for (int index = 1; index < path.size(); index++) {
                    closest = Math.min(closest, distanceSquaredToSegment(
                        mouseX, mouseY, path.get(index - 1), path.get(index)));
                }
            }
            return closest;
        }

        private static double distanceSquaredToSegment(
            double x, double y, Point first, Point second
        ) {
            double deltaX = second.x - first.x;
            double deltaY = second.y - first.y;
            double lengthSquared = deltaX * deltaX + deltaY * deltaY;
            if (lengthSquared <= 0.0001D) {
                double pointX = x - first.x;
                double pointY = y - first.y;
                return pointX * pointX + pointY * pointY;
            }
            double projection = Mth.clamp(
                ((x - first.x) * deltaX + (y - first.y) * deltaY)
                    / lengthSquared, 0.0D, 1.0D);
            double nearestX = first.x + projection * deltaX;
            double nearestY = first.y + projection * deltaY;
            double distanceX = x - nearestX;
            double distanceY = y - nearestY;
            return distanceX * distanceX + distanceY * distanceY;
        }
    }

    private record FrameBounds(int left, int top, int right, int bottom) {
        int width() {
            return right - left;
        }

        int height() {
            return bottom - top;
        }
    }

    private record ControlNodeInfo(
        RedstoneControlBinding binding,
        boolean powered,
        int connectionCount
    ) {
    }

    private record ControlFrameHit(
        GroupKey groupKey,
        RedstoneControlBinding binding,
        List<ConnectionKey> connections,
        int x, int y, int width, int height
    ) {
        boolean contains(double mouseX, double mouseY) {
            if (mouseX < x || mouseX > x + width
                || mouseY < y || mouseY > y + height) return false;
            int edge = 5;
            return mouseY <= y + CONTROL_NODE_HEIGHT + CONTROL_FRAME_PADDING + 2
                || mouseX <= x + edge || mouseX >= x + width - edge
                || mouseY >= y + height - edge;
        }
    }

    public record RedstoneControlFrameSelection(
        GroupKey groupKey,
        RedstoneControlBinding binding,
        List<ConnectionKey> connections
    ) {
        public RedstoneControlFrameSelection {
            connections = List.copyOf(connections);
        }
    }

    private record DirectionState(boolean active, boolean crossDimension,
                                  boolean allowed, int actual, int maximum) {
        private static DirectionState of(LogisticsNode source, FaceTopology topology,
                                         LogisticsNode target, boolean active) {
            if (!active) return new DirectionState(false, false, false, 0, 0);
            var assessment =
                LogisticsCalculator.assessTransferRange(
                    source.gPos(), target.gPos(), topology.maxTransferBlocks(),
                    topology.dimensionEffective());
            return new DirectionState(
                true, assessment.crossDimension(), assessment.allowed(),
                assessment.actualBlocks(), assessment.maximumBlocks());
        }

        private Component label(String arrow) {
            if (!active) return Component.empty();
            if (crossDimension) {
                return Component.literal(arrow + " ")
                    .append(Component.translatable(allowed
                        ? "gui.staticlogistics.network_preview.cross_dimension_available"
                        : "gui.staticlogistics.network_preview.cross_dimension_blocked"));
            }
            String maximumText = maximum == Integer.MAX_VALUE ? "∞" : String.valueOf(maximum);
            return Component.literal(arrow + " ").append(Component.translatable(
                "gui.staticlogistics.network_preview.distance_details",
                actual, maximumText));
        }

        private int color() {
            if (!allowed) return 0xFFFF6868;
            return isNearLimit() ? 0xFFFFD45A : 0xFF98FB98;
        }

        private boolean isNearLimit() {
            return active && !crossDimension
                && maximum != Integer.MAX_VALUE
                && (long) actual * 5L >= (long) maximum * 4L;
        }
    }

    private record LinkState(List<Component> details) {
        private static LinkState of(ClientConnection connection) {
            DirectionState forward = DirectionState.of(
                connection.first(), connection.firstTopology(), connection.second(),
                connection.transfersFirstToSecond());
            DirectionState backward = DirectionState.of(
                connection.second(), connection.secondTopology(), connection.first(),
                connection.transfersSecondToFirst());
            List<Component> details = new ArrayList<>(2);
            if (forward.active) {
                details.add(forward.label("→").copy().withStyle(forward.allowed
                    ? ChatFormatting.GREEN : ChatFormatting.RED));
            }
            if (backward.active) {
                details.add(backward.label("←").copy().withStyle(backward.allowed
                    ? ChatFormatting.GREEN : ChatFormatting.RED));
            }
            if (details.isEmpty()) {
                details.add(Component.translatable(
                        "gui.staticlogistics.connection.blocked")
                    .withStyle(ChatFormatting.RED));
            }
            return new LinkState(List.copyOf(details));
        }

    }
}
