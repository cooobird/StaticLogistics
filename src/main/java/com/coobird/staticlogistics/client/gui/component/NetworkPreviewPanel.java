package com.coobird.staticlogistics.client.gui.component;

import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.api.group.GroupKey;
import com.coobird.staticlogistics.client.data.ClientConnection;
import com.coobird.staticlogistics.client.data.ClientLinkData;
import com.coobird.staticlogistics.client.data.NetworkPreviewLayoutStore;
import com.coobird.staticlogistics.client.data.SelectionContext;
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
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * 连接配置器中的拓扑预览。
 *
 * <p>这里只保存视图平移、缩放与选择状态，拓扑真相始终来自
 * {@link ClientLinkData}。
 */
public final class NetworkPreviewPanel {
    private static final int NODE_WIDTH = 72;
    private static final int NODE_HEIGHT = 27;
    private static final int LAYOUT_PADDING = 1;
    private static final int NODE_GAP = 5;
    private static final int SELECTED_CONNECTION_COLOR = 0xFFFFD45A;
    private static final double MIN_ZOOM = 0.35D;
    private static final double MAX_ZOOM = 1.8D;

    private final List<NodeHit> nodeHits = new ArrayList<>();
    private final List<ConnectionHit> connectionHits = new ArrayList<>();
    @Nullable
    private GroupKey groupKey;
    @Nullable
    private LogisticsNode selectedNode;
    private double panX;
    private double panY;
    private double zoom = 1.0D;
    private boolean panning;
    @Nullable
    private LogisticsNode draggingNode;

    public void setGroup(@Nullable GroupKey groupKey) {
        if (Objects.equals(this.groupKey, groupKey)) return;
        this.groupKey = groupKey;
        this.selectedNode = null;
        if (groupKey == null) {
            this.panX = 0.0D;
            this.panY = 0.0D;
            this.zoom = 1.0D;
        } else {
            NetworkPreviewLayoutStore.Layout layout = NetworkPreviewLayoutStore.INSTANCE.getOrCreate(groupKey);
            this.panX = layout.panX();
            this.panY = layout.panY();
            this.zoom = layout.zoom();
        }
        this.panning = false;
        this.draggingNode = null;
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
    }

    public void selectNode(@Nullable LogisticsNode node) {
        this.selectedNode = node;
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

        Map<LogisticsNode, FaceTopology> nodes = collectNodes(connections);
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
                renderNode(graphics, font, entry.getKey(), entry.getValue(),
                    point, transform);
            }
        }
        graphics.pose().popPose();
        graphics.disableScissor();
    }

    private Map<LogisticsNode, FaceTopology> collectNodes(List<ClientConnection> connections) {
        Map<LogisticsNode, FaceTopology> nodes = new LinkedHashMap<>();
        for (ClientConnection connection : connections) {
            nodes.putIfAbsent(connection.first(), connection.firstTopology());
            nodes.putIfAbsent(connection.second(), connection.secondTopology());
        }
        return nodes;
    }

    private Map<LogisticsNode, Point> layout(
        List<ClientConnection> connections,
        Map<LogisticsNode, FaceTopology> nodes,
        int x, int y, int width, int height) {
        Map<LogisticsNode, Integer> sourceVotes = new LinkedHashMap<>();
        Map<LogisticsNode, Integer> targetVotes = new LinkedHashMap<>();
        for (ClientConnection connection : connections) {
            boolean forward = connection.transfersFirstToSecond();
            boolean backward = connection.transfersSecondToFirst();
            if (forward && !backward) {
                vote(sourceVotes, connection.first());
                vote(targetVotes, connection.second());
            } else if (backward && !forward) {
                vote(sourceVotes, connection.second());
                vote(targetVotes, connection.first());
            } else {
                // 双向或暂时停用的连接保持稳定的左右顺序，避免刷新时节点跳动。
                vote(sourceVotes, connection.first());
                vote(targetVotes, connection.second());
            }
        }

        List<Map.Entry<LogisticsNode, FaceTopology>> sources = new ArrayList<>();
        List<Map.Entry<LogisticsNode, FaceTopology>> targets = new ArrayList<>();
        nodes.entrySet().stream()
            .sorted(Comparator.comparing(entry -> nodeKey(entry.getKey())))
            .forEach(entry -> {
                int sourceScore = sourceVotes.getOrDefault(entry.getKey(), 0);
                int targetScore = targetVotes.getOrDefault(entry.getKey(), 0);
                if (sourceScore >= targetScore && sourceScore > 0) {
                    sources.add(entry);
                } else {
                    targets.add(entry);
                }
            });

        Map<LogisticsNode, Point> automatic = new LinkedHashMap<>();
        placeColumn(automatic, sources, 18, LAYOUT_PADDING,
            height - LAYOUT_PADDING * 2);
        placeColumn(automatic, targets, width - NODE_WIDTH - 18,
            LAYOUT_PADDING, height - LAYOUT_PADDING * 2);

        NetworkPreviewLayoutStore.Layout storedLayout =
            NetworkPreviewLayoutStore.INSTANCE.getOrCreate(Objects.requireNonNull(groupKey));
        Map<LogisticsNode, NetworkPreviewLayoutStore.Position> saved = storedLayout.nodePositions();
        /*
         * 不在增量拓扑同步期间删除暂时不可见的节点布局。分组真正删除时由布局仓库的
         * removeGroup 统一清理，避免重进存档后刚打开界面就丢失尚未同步到的节点位置。
         */
        boolean layoutChanged = false;
        boolean firstLayout = saved.isEmpty();
        for (Map.Entry<LogisticsNode, Point> entry : automatic.entrySet()) {
            Point point = entry.getValue();
            if (saved.putIfAbsent(entry.getKey(),
                new NetworkPreviewLayoutStore.Position(
                    point.x, point.y)) == null) {
                layoutChanged = true;
            }
        }
        if (layoutChanged) {
            NetworkPreviewLayoutStore.INSTANCE.markDirty();
        }
        if (firstLayout) {
            Map<LogisticsNode, Point> initialPositions = new LinkedHashMap<>(saved.size());
            saved.forEach((node, point) -> initialPositions.put(
                node, new Point(
                    (int) Math.round(point.x()),
                    (int) Math.round(point.y()))));
            zoom = initialZoom(initialPositions, height);
            persistView();
        }

        Map<LogisticsNode, Point> result = new LinkedHashMap<>(saved.size());
        saved.forEach((node, point) ->
            result.put(node, new Point(
                (int) Math.round(x + point.x()),
                (int) Math.round(y + point.y()))));
        return result;
    }

    private static void vote(Map<LogisticsNode, Integer> votes, LogisticsNode node) {
        votes.merge(node, 1, Integer::sum);
    }

    private void placeColumn(Map<LogisticsNode, Point> result,
                             List<Map.Entry<LogisticsNode, FaceTopology>> entries,
                             int baseX, int baseY, int availableHeight) {
        if (entries.isEmpty()) return;
        double step = entries.size() == 1
            ? 0.0D
            : Math.max(
            NODE_HEIGHT + NODE_GAP,
            (availableHeight - NODE_HEIGHT)
                / (double) (entries.size() - 1));
        double total = NODE_HEIGHT + step * (entries.size() - 1);
        double start = baseY + (availableHeight - total) / 2.0D;
        for (int i = 0; i < entries.size(); i++) {
            result.put(entries.get(i).getKey(),
                new Point(baseX, (int) Math.round(start + i * step)));
        }
    }

    /**
     * 首次布局只在节点无法原尺寸容纳时缩小，保证节点之间永不互相覆盖。
     */
    private static double initialZoom(
        Map<LogisticsNode, Point> positions,
        int viewportHeight
    ) {
        if (positions.isEmpty()) return 1.0D;
        int minimum = positions.values().stream()
            .mapToInt(Point::y).min().orElse(0);
        int maximum = positions.values().stream()
            .mapToInt(point -> point.y + NODE_HEIGHT).max().orElse(viewportHeight);
        int contentHeight = Math.max(1, maximum - minimum);
        return Mth.clamp(
            (viewportHeight - LAYOUT_PADDING * 2.0D) / contentHeight,
            MIN_ZOOM, 1.0D);
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

    private void renderNode(GuiGraphics graphics, Font font, LogisticsNode node,
                            FaceTopology topology, Point point,
                            ViewTransform transform) {
        boolean selected = node.equals(selectedNode);
        int border = selected ? 0xFF98FB98 : 0xFF767676;
        int background = selected ? 0xFF315B36 : 0xFF3A3A3A;
        graphics.fill(point.x, point.y, point.x + NODE_WIDTH, point.y + NODE_HEIGHT, border);
        graphics.fill(point.x + 2, point.y + 2,
            point.x + NODE_WIDTH - 2, point.y + NODE_HEIGHT - 2, background);

        String name = nodeName(node);
        graphics.drawString(font, font.plainSubstrByWidth(name, NODE_WIDTH - 8),
            point.x + 4, point.y + 4, selected ? 0xFF98FB98 : 0xFFEDEDED, false);
        String position = node.gPos().pos().toShortString();
        graphics.drawString(font, font.plainSubstrByWidth(position, NODE_WIDTH - 8),
            point.x + 4, point.y + 15, 0xFFAAAAAA, false);
        Point screenPoint = transform.apply(point);
        nodeHits.add(new NodeHit(node, topology, screenPoint.x, screenPoint.y,
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
                buildNodeTooltip(hit.node, hit.topology), mouseX, mouseY);
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
            selectedNode = hit.node;
            SelectionContext.clearConnectionFocus();
            draggingNode = button == 0 ? hit.node : null;
            panning = false;
            SoundUtil.playClickSound();
            return true;
        }
        for (ConnectionHit hit : connectionHits) {
            if (!hit.contains(mouseX, mouseY)) continue;
            selectConnection(hit.connection);
            draggingNode = null;
            panning = false;
            SoundUtil.playClickSound();
            return true;
        }
        if (button == 0) {
            selectedNode = null;
            SelectionContext.clearConnectionFocus();
            draggingNode = null;
            panning = true;
            return true;
        }
        return false;
    }

    public boolean mouseDragged(double deltaX, double deltaY) {
        if (draggingNode != null && groupKey != null) {
            Map<LogisticsNode, NetworkPreviewLayoutStore.Position> layout =
                NetworkPreviewLayoutStore.INSTANCE.getOrCreate(groupKey)
                    .nodePositions();
            NetworkPreviewLayoutStore.Position point = layout.get(draggingNode);
            if (point == null) {
                draggingNode = null;
                return false;
            }
            layout.put(draggingNode, new NetworkPreviewLayoutStore.Position(
                point.x() + deltaX / zoom,
                point.y() + deltaY / zoom));
            NetworkPreviewLayoutStore.INSTANCE.markDirty();
            return true;
        }
        if (!panning) return false;
        panX += deltaX;
        panY += deltaY;
        persistView();
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
        panning = false;
        draggingNode = null;
        return handled;
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
        persistView();
        return true;
    }

    private void persistView() {
        if (groupKey == null) return;
        NetworkPreviewLayoutStore.INSTANCE.getOrCreate(groupKey)
            .setView(panX, panY, zoom);
        NetworkPreviewLayoutStore.INSTANCE.markDirty();
    }

    private static String nodeKey(LogisticsNode node) {
        return node.gPos().dimension().location() + "/" + node.gPos().pos().asLong()
            + "/" + node.face().ordinal();
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
        LogisticsNode node,
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
