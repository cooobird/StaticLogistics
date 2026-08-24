package com.coobird.staticlogistics.client.gui.component;

import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.api.group.GroupKey;
import com.coobird.staticlogistics.client.data.ClientConnection;
import com.coobird.staticlogistics.client.data.ClientLinkData;
import com.coobird.staticlogistics.client.data.ClientRedstoneControlData;
import com.coobird.staticlogistics.client.data.NetworkPreviewLayoutStore;
import com.coobird.staticlogistics.client.data.SelectionContext;
import com.coobird.staticlogistics.client.key.SLKeyMappings;
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
    private static final double MIN_AUTO_FIT_ZOOM = 0.72D;
    private static final double MAX_ZOOM = 1.8D;

    private final List<NodeHit> nodeHits = new ArrayList<>();
    private final List<ConnectionHit> connectionHits = new ArrayList<>();
    private final List<ControlFrameHit> controlFrameHits = new ArrayList<>();
    private final Map<LogisticsNode, Point> currentLocalPositions = new LinkedHashMap<>();
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

    public void render(GuiGraphics graphics, Font font, int x, int y, int width, int height,
                       int mouseX, int mouseY, double interfaceScale) {
        nodeHits.clear();
        connectionHits.clear();
        controlFrameHits.clear();
        // 调用方传入的就是 atlas 内部可绘制区，不再重复推算外框缩进。
        GuiScissor.enable(graphics, interfaceScale, x, y, x + width, y + height);

        List<ClientConnection> connections = groupKey == null
            ? List.of() : ClientLinkData.INSTANCE.getConnectionsForGroup(groupKey);
        if (connections.isEmpty()) {
            Component empty = Component.translatable("gui.staticlogistics.network_preview.empty")
                .withStyle(ChatFormatting.DARK_GRAY);
            graphics.drawString(font, empty,
                x + (width - font.width(empty)) / 2,
                y + (height - font.lineHeight) / 2, 0xFF777777, false);
            graphics.disableScissor();
            return;
        }

        Map<LogisticsNode, VisualNode> nodes = collectNodes(connections, font);
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
        for (ClientConnection connection : connections) {
            Point first = positions.get(connection.first());
            Point second = positions.get(connection.second());
            if (first == null || second == null) continue;
            VisualNode firstNode = nodes.get(connection.first());
            VisualNode secondNode = nodes.get(connection.second());
            if (firstNode == null || secondNode == null) continue;
            renderConnection(graphics, connection, first, second, firstNode, secondNode, transform);
        }
        for (var entry : nodes.entrySet()) {
            Point point = positions.get(entry.getKey());
            if (point != null) {
                renderNode(graphics, font, entry.getValue(), point, transform);
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
                .get(controlled.getFirst().key());
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
        return controlFrameHits.stream()
            .filter(hit -> hit.contains(mouseX, mouseY))
            .min(Comparator.comparingInt(hit -> hit.width * hit.height))
            .map(hit -> new RedstoneControlFrameSelection(
                hit.groupKey, hit.binding, hit.connections))
            .orElse(null);
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

    private Map<LogisticsNode, Point> layout(
        List<ClientConnection> connections,
        Map<LogisticsNode, VisualNode> nodes,
        int x, int y, int width, int height) {
        Map<LogisticsNode, NetworkPreviewLayoutEngine.Size> sizes = new LinkedHashMap<>();
        nodes.forEach((node, visual) -> sizes.put(node, new NetworkPreviewLayoutEngine.Size(visual.width, NODE_HEIGHT)));
        Map<LogisticsNode, Point> automatic = new LinkedHashMap<>();
        NetworkPreviewLayoutEngine.layout(connections, sizes, width).forEach((node, point) -> automatic.put(node, new Point(point.x(), point.y())));

        NetworkPreviewLayoutStore.Layout storedLayout = NetworkPreviewLayoutStore.INSTANCE.getOrCreate(Objects.requireNonNull(groupKey));
        Map<LogisticsNode, NetworkPreviewLayoutStore.Position> saved = storedLayout.nodePositions();
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
            if (automatic.containsKey(node)) {
                localPositions.put(node, new Point(
                    (int) Math.round(point.x()), (int) Math.round(point.y())));
            }
        });
        if (resolveNodeOverlaps(localPositions, nodes, saved)) {
            NetworkPreviewLayoutStore.INSTANCE.markDirty();
        }
        if (centerViewOnNextLayout) centerView(localPositions, nodes, width, height);

        currentLocalPositions.clear();
        currentLocalPositions.putAll(localPositions);
        Map<LogisticsNode, Point> result = new LinkedHashMap<>(localPositions.size());
        localPositions.forEach((node, point) -> result.put(node, new Point(x + point.x, y + point.y)));
        return result;
    }

    /**
     * 历史拖拽坐标在节点尺寸或拓扑改变后可能互相覆盖。按当前视觉顺序保留靠前节点，
     * 只把后续冲突节点向下推到最近的空位；已保存节点的修正会同步回布局仓库。
     */
    private static boolean resolveNodeOverlaps(
        Map<LogisticsNode, Point> positions,
        Map<LogisticsNode, VisualNode> nodes,
        Map<LogisticsNode, NetworkPreviewLayoutStore.Position> saved
    ) {
        List<LogisticsNode> ordered = positions.keySet().stream()
            .sorted(Comparator
                .comparingInt((LogisticsNode node) -> saved.containsKey(node) ? 0 : 1)
                .thenComparingInt(node -> positions.get(node).y)
                .thenComparingInt(node -> positions.get(node).x)
                .thenComparing(NetworkPreviewPanel::nodeKey))
            .toList();
        List<LogisticsNode> placed = new ArrayList<>();
        boolean savedLayoutChanged = false;
        for (LogisticsNode node : ordered) {
            Point original = positions.get(node);
            Point candidate = original;
            boolean moved;
            do {
                moved = false;
                for (LogisticsNode other : placed) {
                    Point otherPoint = positions.get(other);
                    if (!overlaps(candidate, nodes.get(node), otherPoint, nodes.get(other))) continue;
                    candidate = new Point(candidate.x,
                        otherPoint.y + NODE_HEIGHT + NODE_GAP);
                    moved = true;
                }
            } while (moved);
            if (!candidate.equals(original)) {
                positions.put(node, candidate);
                if (saved.containsKey(node)) {
                    saved.put(node, new NetworkPreviewLayoutStore.Position(
                        candidate.x, candidate.y));
                    savedLayoutChanged = true;
                }
            }
            placed.add(node);
        }
        return savedLayoutChanged;
    }

    private static boolean overlaps(Point first, VisualNode firstNode,
                                    Point second, VisualNode secondNode) {
        return first.x < second.x + secondNode.width + NODE_GAP
            && first.x + firstNode.width + NODE_GAP > second.x
            && first.y < second.y + NODE_HEIGHT + NODE_GAP
            && first.y + NODE_HEIGHT + NODE_GAP > second.y;
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
        zoom = Mth.clamp(Math.min(widthScale, heightScale), MIN_AUTO_FIT_ZOOM, 1.0D);
        // 节点位置和平移量均使用预览内部坐标，避免重复叠加屏幕绝对坐标。
        panX = (viewportWidth / 2.0D - (minimumX + maximumX) / 2.0D) * zoom;
        panY = (viewportHeight / 2.0D - (minimumY + maximumY) / 2.0D) * zoom;
    }

    private void renderConnection(
        GuiGraphics graphics,
        ClientConnection connection,
        Point first,
        Point second,
        VisualNode firstNode,
        VisualNode secondNode,
        ViewTransform transform
    ) {
        double firstCenterX = first.x + firstNode.width / 2.0D;
        double firstCenterY = first.y + NODE_HEIGHT / 2.0D;
        double secondCenterX = second.x + secondNode.width / 2.0D;
        double secondCenterY = second.y + NODE_HEIGHT / 2.0D;
        double centerDeltaX = secondCenterX - firstCenterX;
        double centerDeltaY = secondCenterY - firstCenterY;
        boolean horizontalPorts = Math.abs(centerDeltaX) >= Math.abs(centerDeltaY) * 0.8D;

        double startX;
        double startY;
        double endX;
        double endY;
        double firstControlX;
        double firstControlY;
        double secondControlX;
        double secondControlY;
        if (horizontalPorts) {
            double sign = centerDeltaX >= 0.0D ? 1.0D : -1.0D;
            startX = firstCenterX + sign * firstNode.width / 2.0D;
            startY = firstCenterY;
            endX = secondCenterX - sign * secondNode.width / 2.0D;
            endY = secondCenterY;
            double tangent = Mth.clamp(Math.abs(endX - startX) * 0.46D, 24.0D, 110.0D);
            firstControlX = startX + sign * tangent;
            firstControlY = startY;
            secondControlX = endX - sign * tangent;
            secondControlY = endY;
        } else {
            double sign = centerDeltaY >= 0.0D ? 1.0D : -1.0D;
            startX = firstCenterX;
            startY = firstCenterY + sign * NODE_HEIGHT / 2.0D;
            endX = secondCenterX;
            endY = secondCenterY - sign * NODE_HEIGHT / 2.0D;
            double tangent = Mth.clamp(Math.abs(endY - startY) * 0.42D, 22.0D, 100.0D);
            firstControlX = startX;
            firstControlY = startY + sign * tangent;
            secondControlX = endX;
            secondControlY = endY - sign * tangent;
        }
        ConnectionKey selectedKey = SelectionContext.getFocusedConnectionKey();
        boolean controlSelected = controlSelection.contains(connection.key());
        boolean selected = connection.key().equals(selectedKey) || controlSelected;
        int selectedColor = controlSelected
            ? CONTROL_SELECTION_COLOR : SELECTED_CONNECTION_COLOR;
        ClientRedstoneControlData.State redstoneState =
            ClientRedstoneControlData.INSTANCE.get(connection.key());
        boolean redstoneAllowed = redstoneState == null || redstoneState.allowed();
        int segments = Math.max(24, (int) Math.ceil(Math.hypot(
            endX - startX, endY - startY) / 2.0D));
        List<CurvePoint> centerLine = new ArrayList<>(segments + 1);
        for (int i = 0; i <= segments; i++) {
            double t = i / (double) segments;
            double inverse = 1.0D - t;
            double px = inverse * inverse * inverse * startX
                + 3.0D * inverse * inverse * t * firstControlX
                + 3.0D * inverse * t * t * secondControlX
                + t * t * t * endX;
            double py = inverse * inverse * inverse * startY
                + 3.0D * inverse * inverse * t * firstControlY
                + 3.0D * inverse * t * t * secondControlY
                + t * t * t * endY;
            centerLine.add(new CurvePoint(px, py));
        }

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
                    selectedColor, SELECTED_CONNECTION_LINE_WIDTH, true);
            }
            drawSmoothLine(graphics, centerLine, 0xFF888888, CONNECTION_LINE_WIDTH, true);
            connectionHits.add(new ConnectionHit(connection,
                centerLine.stream().map(transform::apply).toList()));
            return;
        }

        List<Point> hitPoints = new ArrayList<>(
            centerLine.size() * activeDirections);
        double laneOffset = activeDirections == 2 ? 5.5D : 0.0D;
        if (forward.active) {
            renderDirection(graphics, hitPoints, centerLine,
                forward, true, -laneOffset, horizontalPorts,
                selected, selectedColor, transform);
        }
        if (backward.active) {
            renderDirection(graphics, hitPoints, centerLine,
                backward, false, laneOffset, horizontalPorts,
                selected, selectedColor, transform);
        }
        connectionHits.add(new ConnectionHit(connection, List.copyOf(hitPoints)));
    }

    /**
     * 一条可传输方向对应一条独立曲线。双向连接因此会形成两条平行曲线，
     * 每条曲线独立表达自己的距离上限、可达状态与传输方向。
     */
    private static void renderDirection(
        GuiGraphics graphics,
        List<Point> hitPoints,
        List<CurvePoint> centerLine,
        DirectionState direction,
        boolean startToEnd,
        double offset,
        boolean horizontalPorts,
        boolean selected,
        int selectedColor,
        ViewTransform transform
    ) {
        List<CurvePoint> line = offsetLine(centerLine, offset, horizontalPorts);
        if (selected) {
            drawSmoothLine(graphics, line,
                selectedColor, SELECTED_CONNECTION_LINE_WIDTH, !direction.allowed);
        }
        int semanticColor = direction.color();
        drawSmoothLine(graphics, line, semanticColor,
            CONNECTION_LINE_WIDTH, !direction.allowed);
        drawDirectionArrow(graphics, line, startToEnd,
            semanticColor, CONNECTION_LINE_WIDTH);
        line.stream().map(transform::apply).forEach(hitPoints::add);
    }

    /**
     * 双向连接使用两条完整错开的曲线，避免方向信息挤在同一条线上。
     */
    private static List<CurvePoint> offsetLine(List<CurvePoint> centerLine, double offset,
                                               boolean horizontalPorts) {
        if (offset == 0.0D) return centerLine;
        List<CurvePoint> result = new ArrayList<>(centerLine.size());
        for (CurvePoint point : centerLine) {
            result.add(new CurvePoint(
                point.x + (horizontalPorts ? 0.0D : offset),
                point.y + (horizontalPorts ? offset : 0.0D)));
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
        int arrowIndex = startToEnd ? line.size() - 1 : 0;
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
        for (int i = start; i < end; i++) {
            CurvePoint point = points.get(i);
            consumer.addVertex(graphics.pose().last(),
                (float) point.x, (float) point.y, 0.0F).setColor(color);
        }
        graphics.flush();
    }

    private void renderNode(GuiGraphics graphics, Font font, VisualNode visualNode, Point point,
                            ViewTransform transform) {
        LogisticsNode node = visualNode.representative();
        FaceTopology topology = visualNode.topology();
        boolean primarySelected = node.equals(selectedNode);
        boolean selected = selectedNodes.contains(node);
        boolean boxCandidate = boxSelectionCandidates.contains(node) && !selected;
        int border = primarySelected ? 0xFF98FB98
            : selected ? 0xFFFFD45A : boxCandidate ? BOX_SELECTION_COLOR : 0xFF767676;
        int background = primarySelected ? 0xFF315B36
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
        for (NodeHit hit : nodeHits) {
            if (!hit.contains(mouseX, mouseY)) continue;
            graphics.renderComponentTooltip(font,
                buildNodeTooltip(hit.node.representative(), hit.topology), mouseX, mouseY);
            return;
        }
        for (ConnectionHit hit : connectionHits) {
            if (!hit.contains(mouseX, mouseY)) continue;
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
                .get(controlHit.connections.getFirst());
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
            Component.translatable("gui.staticlogistics.network_preview.clear_selection_hint"),
            Component.translatable("gui.staticlogistics.network_preview.zoom_hint")
        ), mouseX, mouseY);
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
        lines.add(Component.empty());
        lines.add(Component.translatable("gui.staticlogistics.upgrades").withStyle(ChatFormatting.AQUA));
        lines.addAll(NodeUpgradeDisplay.all(topology.speedMultiplier(), topology.rangeMultiplier(), topology.stackMultiplier(), topology.dimensionEffective()));
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
        for (NodeHit hit : nodeHits) {
            if (!hit.contains(mouseX, mouseY)) continue;
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
        for (ConnectionHit hit : connectionHits) {
            if (!hit.contains(mouseX, mouseY)) continue;
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
            boolean moved = false;
            for (LogisticsNode node : draggingNodes) {
                NetworkPreviewLayoutStore.Position point = layout.get(node);
                if (point == null) {
                    Point current = currentLocalPositions.get(node);
                    if (current == null) continue;
                    point = new NetworkPreviewLayoutStore.Position(current.x, current.y);
                }
                layout.put(node, new NetworkPreviewLayoutStore.Position(
                    point.x() + localDeltaX, point.y() + localDeltaY));
                moved = true;
            }
            if (!moved) return false;
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
        panning = false;
        panningDistance = 0.0D;
        boxSelecting = false;
        boxSelectionCandidates.clear();
        draggingNode = null;
        draggingNodes.clear();
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

    private record ConnectionHit(ClientConnection connection, List<Point> points) {
        private boolean contains(double mouseX, double mouseY) {
            for (Point point : points) {
                if (Math.abs(mouseX - point.x) <= 3 && Math.abs(mouseY - point.y) <= 3) {
                    return true;
                }
            }
            return false;
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
                    Minecraft.getInstance().level, source.gPos(), target.gPos(), topology.maxTransferBlocks(),
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
