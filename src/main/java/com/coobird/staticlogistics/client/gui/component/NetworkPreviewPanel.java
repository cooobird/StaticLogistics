package com.coobird.staticlogistics.client.gui.component;

import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.api.group.GroupKey;
import com.coobird.staticlogistics.client.data.ClientConnection;
import com.coobird.staticlogistics.client.data.ClientLinkData;
import com.coobird.staticlogistics.client.data.NetworkPreviewLayoutStore;
import com.coobird.staticlogistics.client.data.SelectionContext;
import com.coobird.staticlogistics.client.key.SLKeyMappings;
import com.coobird.staticlogistics.logistics.node.ConnectionKey;
import com.coobird.staticlogistics.logistics.node.FaceTopology;
import com.coobird.staticlogistics.logistics.util.NodeDisplayText;
import com.coobird.staticlogistics.transfer.LogisticsCalculator;
import com.coobird.staticlogistics.transfer.TransferTypeDisplay;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.Nameable;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * 连接配置器中的拓扑预览。
 *
 * <p>这里只保存当前界面的平移、缩放与选择状态，节点位置由本地布局仓库持久化，
 * 拓扑真相始终来自 {@link ClientLinkData}。
 */
public final class NetworkPreviewPanel {
    private static final int NODE_WIDTH = 72;
    private static final int NODE_HEIGHT = 27;
    private static final int LAYOUT_PADDING = 1;
    private static final int NODE_GAP = 5;
    private static final int COMPONENT_GAP = 14;
    private static final int SELECTED_CONNECTION_COLOR = 0xFFFFD45A;
    private static final double MIN_ZOOM = 0.35D;
    private static final double MAX_ZOOM = 1.8D;

    private final List<NodeHit> nodeHits = new ArrayList<>();
    private final List<ConnectionHit> connectionHits = new ArrayList<>();
    private final Map<LogisticsNode, Point> currentLocalPositions = new LinkedHashMap<>();
    private final LinkedHashSet<LogisticsNode> selectedNodes = new LinkedHashSet<>();
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
    @Nullable
    private LogisticsNode draggingNode;
    private final LinkedHashSet<LogisticsNode> draggingNodes = new LinkedHashSet<>();

    public void setGroup(@Nullable GroupKey groupKey) {
        if (Objects.equals(this.groupKey, groupKey)) return;
        this.groupKey = groupKey;
        this.selectedNode = null;
        this.selectedNodes.clear();
        this.panX = 0.0D;
        this.panY = 0.0D;
        this.zoom = 1.0D;
        this.centerViewOnNextLayout = true;
        this.panning = false;
        this.draggingNode = null;
        this.draggingNodes.clear();
    }

    public void removeLayout(GroupKey groupKey) {
        NetworkPreviewLayoutStore.INSTANCE.remove(groupKey);
    }

    public void flushLayout() {
        NetworkPreviewLayoutStore.INSTANCE.flush();
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
                       int mouseX, int mouseY) {
        nodeHits.clear();
        connectionHits.clear();
        // 调用方传入的就是 atlas 内部可绘制区，不再重复推算外框缩进。
        graphics.enableScissor(x, y, x + width, y + height);

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

        Map<LogisticsNode, VisualNode> nodes = collectNodes(connections);
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
        for (ClientConnection connection : connections) {
            Point first = positions.get(connection.first());
            Point second = positions.get(connection.second());
            if (first == null || second == null) continue;
            renderConnection(graphics, connection, first, second, transform);
        }
        for (var entry : nodes.entrySet()) {
            Point point = positions.get(entry.getKey());
            if (point != null) {
                renderNode(graphics, font, entry.getValue(), point, transform);
            }
        }
        graphics.pose().popPose();
        graphics.disableScissor();
    }

    private Map<LogisticsNode, VisualNode> collectNodes(List<ClientConnection> connections) {
        Map<LogisticsNode, VisualNode> nodes = new LinkedHashMap<>();
        for (ClientConnection connection : connections) {
            collectNode(nodes, connection.first(), connection.firstTopology());
            collectNode(nodes, connection.second(), connection.secondTopology());
        }
        return nodes;
    }

    private static void collectNode(Map<LogisticsNode, VisualNode> nodes,
                                    LogisticsNode node, FaceTopology topology) {
        nodes.putIfAbsent(node, new VisualNode(node, topology));
    }

    private Map<LogisticsNode, Point> layout(
        List<ClientConnection> connections,
        Map<LogisticsNode, VisualNode> nodes,
        int x, int y, int width, int height) {
        Map<LogisticsNode, Point> automatic = buildTopologyLayout(
            connections, nodes.keySet(), width);

        NetworkPreviewLayoutStore.Layout storedLayout =
            NetworkPreviewLayoutStore.INSTANCE.getOrCreate(Objects.requireNonNull(groupKey));
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
        if (centerViewOnNextLayout) centerView(localPositions, x, y, width, height);

        currentLocalPositions.clear();
        currentLocalPositions.putAll(localPositions);
        Map<LogisticsNode, Point> result = new LinkedHashMap<>(localPositions.size());
        localPositions.forEach((node, point) -> result.put(node,
            new Point(x + point.x, y + point.y)));
        return result;
    }

    /**
     * 生成分层拓扑布局：先拆分独立连通分量，再按传输方向确定层级，最后用相邻层
     * 重心排序减少交叉。双向和停用连接使用稳定身份确定展示方向，不改变真实传输语义。
     */
    private static Map<LogisticsNode, Point> buildTopologyLayout(
        List<ClientConnection> connections, Set<LogisticsNode> nodes, int width) {
        Map<LogisticsNode, Set<LogisticsNode>> neighbors = new LinkedHashMap<>();
        nodes.forEach(node -> neighbors.put(node, new LinkedHashSet<>()));
        for (ClientConnection connection : connections) {
            neighbors.get(connection.first()).add(connection.second());
            neighbors.get(connection.second()).add(connection.first());
        }
        List<Set<LogisticsNode>> components = connectedComponents(neighbors);
        List<TopologyComponent> layouts = components.stream()
            .map(component -> layoutComponent(component, neighbors))
            .sorted(Comparator.comparing(layout -> nodeKey(layout.firstNode())))
            .toList();

        Map<LogisticsNode, Point> result = new LinkedHashMap<>();
        int componentY = LAYOUT_PADDING;
        int left = 18;
        int right = Math.max(left, width - NODE_WIDTH - 18);
        for (TopologyComponent component : layouts) {
            int maximumLayer = component.layers().keySet().stream()
                .mapToInt(Integer::intValue).max().orElse(0);
            int componentHeight = component.layers().values().stream()
                .mapToInt(layer -> NODE_HEIGHT + Math.max(0, layer.size() - 1)
                    * (NODE_HEIGHT + NODE_GAP))
                .max().orElse(NODE_HEIGHT);
            for (var entry : component.layers().entrySet()) {
                int x = maximumLayer == 0 ? (left + right) / 2
                    : Mth.lerpInt(entry.getKey() / (float) maximumLayer, left, right);
                List<LogisticsNode> layer = entry.getValue();
                int layerHeight = NODE_HEIGHT + Math.max(0, layer.size() - 1)
                    * (NODE_HEIGHT + NODE_GAP);
                int y = componentY + (componentHeight - layerHeight) / 2;
                for (LogisticsNode node : layer) {
                    result.put(node, new Point(x, y));
                    y += NODE_HEIGHT + NODE_GAP;
                }
            }
            componentY += componentHeight + COMPONENT_GAP;
        }
        return result;
    }

    private static List<Set<LogisticsNode>> connectedComponents(
        Map<LogisticsNode, Set<LogisticsNode>> neighbors) {
        List<Set<LogisticsNode>> result = new ArrayList<>();
        Set<LogisticsNode> visited = new HashSet<>();
        neighbors.keySet().stream().sorted(Comparator.comparing(NetworkPreviewPanel::nodeKey))
            .forEach(start -> {
                if (!visited.add(start)) return;
                LinkedHashSet<LogisticsNode> component = new LinkedHashSet<>();
                ArrayDeque<LogisticsNode> pending = new ArrayDeque<>();
                pending.add(start);
                while (!pending.isEmpty()) {
                    LogisticsNode node = pending.removeFirst();
                    component.add(node);
                    neighbors.getOrDefault(node, Set.of()).stream()
                        .sorted(Comparator.comparing(NetworkPreviewPanel::nodeKey))
                        .filter(visited::add).forEach(pending::addLast);
                }
                result.add(component);
            });
        return result;
    }

    private static TopologyComponent layoutComponent(
        Set<LogisticsNode> component,
        Map<LogisticsNode, Set<LogisticsNode>> neighbors
    ) {
        LogisticsNode root = component.stream().min(
            Comparator.<LogisticsNode>comparingInt(node ->
                    -neighbors.getOrDefault(node, Set.of()).size())
                .thenComparing(NetworkPreviewPanel::nodeKey)).orElseThrow();
        Map<LogisticsNode, Integer> depth = new LinkedHashMap<>();
        ArrayDeque<LogisticsNode> pending = new ArrayDeque<>();
        depth.put(root, 0);
        pending.add(root);
        while (!pending.isEmpty()) {
            LogisticsNode node = pending.removeFirst();
            List<LogisticsNode> adjacent = neighbors.getOrDefault(node, Set.of()).stream()
                .filter(component::contains)
                .sorted(Comparator.comparing(NetworkPreviewPanel::nodeKey)).toList();
            for (LogisticsNode next : adjacent) {
                if (depth.putIfAbsent(next, depth.get(node) + 1) == null) pending.addLast(next);
            }
        }
        Map<Integer, List<LogisticsNode>> layers = new TreeMap<>();
        component.forEach(node -> layers.computeIfAbsent(depth.get(node), ignored -> new ArrayList<>()).add(node));
        layers.values().forEach(layer -> layer.sort(Comparator.comparing(NetworkPreviewPanel::nodeKey)));
        improveTopologyOrder(layers, neighbors, depth);
        return new TopologyComponent(component.stream()
            .min(Comparator.comparing(NetworkPreviewPanel::nodeKey)).orElseThrow(), layers);
    }

    private static void improveTopologyOrder(
        Map<Integer, List<LogisticsNode>> layers,
        Map<LogisticsNode, Set<LogisticsNode>> neighbors,
        Map<LogisticsNode, Integer> depth
    ) {
        for (int pass = 0; pass < 4; pass++) {
            Map<LogisticsNode, Integer> previousOrder = Map.of();
            for (Map.Entry<Integer, List<LogisticsNode>> entry : layers.entrySet()) {
                int layerDepth = entry.getKey();
                Map<LogisticsNode, Integer> order = previousOrder;
                entry.getValue().sort(Comparator
                    .<LogisticsNode>comparingDouble(node -> barycenter(
                        neighbors.getOrDefault(node, Set.of()).stream()
                            .filter(adjacent -> depth.get(adjacent) < layerDepth).toList(), order))
                    .thenComparing(NetworkPreviewPanel::nodeKey));
                previousOrder = indexOrder(entry.getValue());
            }
        }
    }

    private static double barycenter(Collection<LogisticsNode> nodes,
                                     Map<LogisticsNode, Integer> order) {
        return nodes.stream().filter(order::containsKey).mapToInt(order::get)
            .average().orElse(Double.MAX_VALUE);
    }

    private static Map<LogisticsNode, Integer> indexOrder(List<LogisticsNode> nodes) {
        Map<LogisticsNode, Integer> result = new HashMap<>();
        for (int index = 0; index < nodes.size(); index++) result.put(nodes.get(index), index);
        return result;
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
        Map<net.minecraft.core.GlobalPos, List<LogisticsNode>> nodesByPosition = new LinkedHashMap<>();
        automatic.keySet().forEach(node ->
            nodesByPosition.computeIfAbsent(node.gPos(), ignored -> new ArrayList<>()).add(node));
        layout.legacyNodePositions().forEach((position, savedPosition) -> {
            List<LogisticsNode> matching = new ArrayList<>(
                nodesByPosition.getOrDefault(position, List.of()));
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
    private void centerView(Map<LogisticsNode, Point> positions, int viewportX, int viewportY,
                            int viewportWidth, int viewportHeight) {
        centerViewOnNextLayout = false;
        if (positions.isEmpty()) {
            panX = 0.0D;
            panY = 0.0D;
            zoom = 1.0D;
            return;
        }
        int minimumX = positions.values().stream().mapToInt(Point::x).min().orElse(0);
        int maximumX = positions.values().stream()
            .mapToInt(point -> point.x + NODE_WIDTH).max().orElse(viewportWidth);
        int minimumY = positions.values().stream().mapToInt(Point::y).min().orElse(0);
        int maximumY = positions.values().stream()
            .mapToInt(point -> point.y + NODE_HEIGHT).max().orElse(viewportHeight);
        int contentWidth = Math.max(1, maximumX - minimumX);
        int contentHeight = Math.max(1, maximumY - minimumY);
        double widthScale = (viewportWidth - LAYOUT_PADDING * 2.0D) / contentWidth;
        double heightScale = (viewportHeight - LAYOUT_PADDING * 2.0D) / contentHeight;
        zoom = Mth.clamp(Math.min(widthScale, heightScale), MIN_ZOOM, 1.0D);
        panX = (viewportX + viewportWidth / 2.0D - (minimumX + maximumX) / 2.0D) * zoom;
        panY = (viewportY + viewportHeight / 2.0D - (minimumY + maximumY) / 2.0D) * zoom;
    }

    private void renderConnection(
        GuiGraphics graphics,
        ClientConnection connection,
        Point first,
        Point second,
        ViewTransform transform
    ) {
        boolean firstOnLeft = first.x <= second.x;
        int startX = firstOnLeft ? first.x + NODE_WIDTH : first.x;
        int startY = first.y + NODE_HEIGHT / 2;
        int endX = firstOnLeft ? second.x : second.x + NODE_WIDTH;
        int endY = second.y + NODE_HEIGHT / 2;
        ConnectionKey selectedKey = SelectionContext.getFocusedConnectionKey();
        boolean selected = connection.key().equals(selectedKey);
        int segments = Math.max(24, Math.abs(endX - startX) / 2);
        List<CurvePoint> centerLine = new ArrayList<>(segments + 1);
        int deltaX = endX - startX;
        double firstControlX = startX + deltaX * 0.42D;
        double secondControlX = endX - deltaX * 0.42D;
        for (int i = 0; i <= segments; i++) {
            double t = i / (double) segments;
            double inverse = 1.0D - t;
            double px = inverse * inverse * inverse * startX
                + 3.0D * inverse * inverse * t * firstControlX
                + 3.0D * inverse * t * t * secondControlX
                + t * t * t * endX;
            double py = inverse * inverse * inverse * startY
                + 3.0D * inverse * inverse * t * startY
                + 3.0D * inverse * t * t * endY
                + t * t * t * endY;
            centerLine.add(new CurvePoint(px, py));
        }

        DirectionState forward = DirectionState.of(
            connection.first(), connection.firstTopology(), connection.second(),
            connection.transfersFirstToSecond());
        DirectionState backward = DirectionState.of(
            connection.second(), connection.secondTopology(), connection.first(),
            connection.transfersSecondToFirst());
        int activeDirections = (forward.active ? 1 : 0) + (backward.active ? 1 : 0);
        if (activeDirections == 0) {
            if (selected) {
                drawSmoothLine(graphics, centerLine,
                    SELECTED_CONNECTION_COLOR, 3.5D, true);
            }
            drawSmoothLine(graphics, centerLine, 0xFF888888, 1.5D, true);
            connectionHits.add(new ConnectionHit(connection,
                centerLine.stream().map(transform::apply).toList()));
            return;
        }

        List<Point> hitPoints = new ArrayList<>(
            centerLine.size() * activeDirections);
        double offset = activeDirections == 2 ? 7.0D : 0.0D;
        if (forward.active) {
            renderDirection(graphics, hitPoints, centerLine,
                forward, true, -offset, selected, transform);
        }
        if (backward.active) {
            renderDirection(graphics, hitPoints, centerLine,
                backward, false, offset, selected, transform);
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
        double offsetY,
        boolean selected,
        ViewTransform transform
    ) {
        List<CurvePoint> line = offsetLine(centerLine, offsetY);
        if (selected) {
            drawSmoothLine(graphics, line,
                SELECTED_CONNECTION_COLOR, 3.5D, !direction.allowed);
        }
        int semanticColor = direction.color();
        drawSmoothLine(graphics, line, semanticColor,
            1.5D, !direction.allowed);
        drawDirectionArrow(graphics, line, startToEnd,
            semanticColor, 1.5D);
        line.stream().map(transform::apply).forEach(hitPoints::add);
    }

    private static List<CurvePoint> offsetLine(
        List<CurvePoint> centerLine,
        double offsetY
    ) {
        if (offsetY == 0.0D) return centerLine;
        List<CurvePoint> result = new ArrayList<>(centerLine.size());
        for (CurvePoint point : centerLine) {
            result.add(new CurvePoint(point.x, point.y + offsetY));
        }
        return result;
    }

    /**
     * 箭头固定在目标节点边缘之前，并沿目标端切线绘制。
     * 这样箭头表达的是明确终点，不会被位于曲线中部的距离标签遮挡。
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
            new CurvePoint(baseX - normalX, baseY - normalY)
        );
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
            consumer.vertex(graphics.pose().last().pose(),
                    (float) point.x, (float) point.y, 0.0F)
                .color((color >> 16) & 0xFF, (color >> 8) & 0xFF,
                    color & 0xFF, (color >>> 24) & 0xFF)
                .endVertex();
        }
        graphics.flush();
    }

    private void renderNode(GuiGraphics graphics, Font font, VisualNode visualNode, Point point,
                            ViewTransform transform) {
        LogisticsNode node = visualNode.representative();
        FaceTopology topology = visualNode.topology();
        boolean primarySelected = node.equals(selectedNode);
        boolean selected = selectedNodes.contains(node);
        int border = primarySelected ? 0xFF98FB98 : selected ? 0xFFFFD45A : 0xFF767676;
        int background = primarySelected ? 0xFF315B36 : selected ? 0xFF5A5130 : 0xFF3A3A3A;
        graphics.fill(point.x, point.y, point.x + NODE_WIDTH, point.y + NODE_HEIGHT, border);
        graphics.fill(point.x + 2, point.y + 2,
            point.x + NODE_WIDTH - 2, point.y + NODE_HEIGHT - 2, background);

        String title = nodeName(node) + " · "
            + NodeDisplayText.direction(node.face()).getString();
        graphics.drawString(font, font.plainSubstrByWidth(title, NODE_WIDTH - 8),
            point.x + 4, point.y + 4, primarySelected ? 0xFF98FB98 : selected ? 0xFFFFD45A : 0xFFEDEDED, false);
        String position = node.gPos().pos().toShortString();
        graphics.drawString(font, font.plainSubstrByWidth(position, NODE_WIDTH - 8),
            point.x + 4, point.y + 15, 0xFFAAAAAA, false);
        Point screenPoint = transform.apply(point);
        nodeHits.add(new NodeHit(visualNode, topology, screenPoint.x, screenPoint.y,
            Math.max(1, (int) Math.round(NODE_WIDTH * transform.zoom)),
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
        graphics.renderComponentTooltip(font, List.of(
            Component.translatable("gui.staticlogistics.network_preview.controls")
                .withStyle(ChatFormatting.GOLD),
            Component.translatable("gui.staticlogistics.network_preview.multi_select_hint",
                SLKeyMappings.NETWORK_PREVIEW_MULTI_SELECT.getTranslatedKeyMessage()),
            Component.translatable("gui.staticlogistics.network_preview.drag_selected_hint"),
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
        for (NodeHit hit : nodeHits) {
            if (!hit.contains(mouseX, mouseY)) continue;
            LogisticsNode clickedNode = hit.node.representative();
            if (SLKeyMappings.isKeyDown(SLKeyMappings.NETWORK_PREVIEW_MULTI_SELECT) && button == 0) {
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
                && !SLKeyMappings.isKeyDown(SLKeyMappings.NETWORK_PREVIEW_MULTI_SELECT)
                ? hit.node.position() : null;
            draggingNodes.clear();
            if (draggingNode != null) {
                draggingNodes.addAll(selectedNodes.contains(draggingNode)
                    ? selectedNodes : List.of(draggingNode));
            }
            panning = false;
            panningDistance = 0.0D;
            SoundUtil.playClickSound();
            return true;
        }
        for (ConnectionHit hit : connectionHits) {
            if (!hit.contains(mouseX, mouseY)) continue;
            selectConnection(hit.connection);
            draggingNode = null;
            draggingNodes.clear();
            panning = false;
            panningDistance = 0.0D;
            SoundUtil.playClickSound();
            return true;
        }
        if (button == 0) {
            draggingNode = null;
            draggingNodes.clear();
            panning = true;
            panningDistance = 0.0D;
            return true;
        }
        return false;
    }

    public boolean mouseDragged(double deltaX, double deltaY) {
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
     * 结束节点拖动或画布平移，并告知上层是否应拦截本次释放事件。
     *
     * <p>若把预览内开始的释放事件继续交给容器界面，原版槽位拖拽状态可能被意外结束，
     * 这正是空白取消选择后偶发异常的来源。
     */
    public boolean mouseReleased() {
        boolean handled = panning || draggingNode != null;
        if (panning && panningDistance < 2.0D) {
            selectedNode = null;
            selectedNodes.clear();
            SelectionContext.clearConnectionFocus();
        }
        panning = false;
        panningDistance = 0.0D;
        draggingNode = null;
        draggingNodes.clear();
        return handled;
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

    private record TopologyComponent(LogisticsNode firstNode,
                                     Map<Integer, List<LogisticsNode>> layers) {
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

    private record VisualNode(LogisticsNode representative, FaceTopology topology) {
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
