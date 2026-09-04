package com.coobird.staticlogistics.client.gui.component;

import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.client.data.ClientConnection;

import java.util.*;

/**
 * 网络预览的纯布局引擎。
 *
 * <p>每个弱连通分量先在自己的局部坐标中按传输方向分层，再将多个紧凑拓扑岛装入画布。
 * 该类只生成首次布局，不读取或修改玩家保存的位置。
 */
final class NetworkPreviewLayoutEngine {
    private static final int CANVAS_PADDING = 18;
    private static final int NODE_GAP = 6;
    private static final int LAYER_GAP = 44;
    private static final int COMPONENT_GAP = 20;
    private static final int MIN_PACKING_WIDTH = 260;

    private NetworkPreviewLayoutEngine() {
    }

    static Map<LogisticsNode, Point> layout(
        List<ClientConnection> connections,
        Map<LogisticsNode, Size> sizes,
        int viewportWidth
    ) {
        if (sizes.isEmpty()) return Map.of();

        Graph graph = Graph.create(connections, sizes.keySet());
        List<ComponentLayout> components = weakComponents(graph.undirected).stream()
            .map(nodes -> layoutComponent(nodes, graph, sizes))
            .sorted(Comparator.comparing(component -> nodeKey(component.firstNode)))
            .toList();
        return packComponents(components, viewportWidth);
    }

    private static ComponentLayout layoutComponent(
        Set<LogisticsNode> nodes,
        Graph graph,
        Map<LogisticsNode, Size> sizes
    ) {
        Map<LogisticsNode, Integer> layers = assignLayers(nodes, graph.directed);
        Map<Integer, List<LogisticsNode>> nodesByLayer = new TreeMap<>();
        nodes.forEach(node -> nodesByLayer
            .computeIfAbsent(layers.getOrDefault(node, 0), ignored -> new ArrayList<>())
            .add(node));
        nodesByLayer.values().forEach(layer -> layer.sort(Comparator.comparing(
            NetworkPreviewLayoutEngine::nodeKey)));
        improveOrder(nodesByLayer, graph.undirected, layers);

        Map<Integer, Integer> layerWidths = new LinkedHashMap<>();
        Map<Integer, Integer> layerHeights = new LinkedHashMap<>();
        nodesByLayer.forEach((layer, values) -> {
            layerWidths.put(layer, values.stream().map(sizes::get)
                .mapToInt(Size::width).max().orElse(1));
            layerHeights.put(layer, values.stream().map(sizes::get)
                .mapToInt(Size::height).sum() + Math.max(0, values.size() - 1) * NODE_GAP);
        });

        int componentHeight = layerHeights.values().stream()
            .mapToInt(Integer::intValue).max().orElse(1);
        Map<Integer, Integer> layerX = new LinkedHashMap<>();
        int componentWidth = 0;
        for (int layer : nodesByLayer.keySet()) {
            layerX.put(layer, componentWidth);
            componentWidth += layerWidths.get(layer) + LAYER_GAP;
        }
        componentWidth = Math.max(1, componentWidth - LAYER_GAP);

        Map<LogisticsNode, Point> positions = new LinkedHashMap<>();
        nodesByLayer.forEach((layer, values) -> {
            int y = (componentHeight - layerHeights.get(layer)) / 2;
            int columnWidth = layerWidths.get(layer);
            for (LogisticsNode node : values) {
                Size size = sizes.get(node);
                int x = layerX.get(layer) + (columnWidth - size.width) / 2;
                positions.put(node, new Point(x, y));
                y += size.height + NODE_GAP;
            }
        });
        LogisticsNode firstNode = nodes.stream()
            .min(Comparator.comparing(NetworkPreviewLayoutEngine::nodeKey)).orElseThrow();
        return new ComponentLayout(firstNode, positions, componentWidth, componentHeight);
    }

    private static Map<LogisticsNode, Integer> assignLayers(
        Set<LogisticsNode> nodes,
        Map<LogisticsNode, Set<LogisticsNode>> directed
    ) {
        List<Set<LogisticsNode>> stronglyConnected = stronglyConnectedComponents(nodes, directed);
        Map<LogisticsNode, Integer> componentByNode = new HashMap<>();
        for (int index = 0; index < stronglyConnected.size(); index++) {
            for (LogisticsNode node : stronglyConnected.get(index)) componentByNode.put(node, index);
        }

        // SCC 只负责消除有向环；其内部仍按无向距离展开，避免双向链和环挤在同一列。
        Map<LogisticsNode, Integer> internalLayers = new HashMap<>();
        int[] componentSpans = new int[stronglyConnected.size()];
        for (int index = 0; index < stronglyConnected.size(); index++) {
            Map<LogisticsNode, Integer> localLayers = assignInternalLayers(
                stronglyConnected.get(index), directed);
            internalLayers.putAll(localLayers);
            componentSpans[index] = localLayers.values().stream()
                .mapToInt(Integer::intValue).max().orElse(0) + 1;
        }

        Map<Integer, Set<Integer>> outgoing = new LinkedHashMap<>();
        int[] indegrees = new int[stronglyConnected.size()];
        for (int index = 0; index < stronglyConnected.size(); index++) {
            outgoing.put(index, new LinkedHashSet<>());
        }
        for (LogisticsNode source : nodes) {
            int sourceComponent = componentByNode.get(source);
            for (LogisticsNode target : directed.getOrDefault(source, Set.of())) {
                if (!nodes.contains(target)) continue;
                int targetComponent = componentByNode.get(target);
                if (sourceComponent != targetComponent
                    && outgoing.get(sourceComponent).add(targetComponent)) {
                    indegrees[targetComponent]++;
                }
            }
        }

        ArrayDeque<Integer> pending = new ArrayDeque<>();
        int[] componentLayers = new int[stronglyConnected.size()];
        for (int index = 0; index < indegrees.length; index++) {
            if (indegrees[index] == 0) pending.addLast(index);
        }
        while (!pending.isEmpty()) {
            int source = pending.removeFirst();
            for (int target : outgoing.get(source)) {
                componentLayers[target] = Math.max(
                    componentLayers[target],
                    componentLayers[source] + componentSpans[source]);
                if (--indegrees[target] == 0) pending.addLast(target);
            }
        }

        Map<LogisticsNode, Integer> result = new LinkedHashMap<>();
        for (LogisticsNode node : nodes) {
            result.put(node, componentLayers[componentByNode.get(node)]
                + internalLayers.getOrDefault(node, 0));
        }
        return result;
    }

    /**
     * 在一个强连通分量内部按无向最短距离生成子层。
     *
     * <p>链优先从确定的端点展开，从而保留完整的横向走势；没有端点时通过一次最远点查找
     * 选择稳定的外围起点，环会自然形成左右数量均衡的分层。所有邻居均按节点键遍历，
     * 因而相同网络每次都会得到相同结果。
     */
    private static Map<LogisticsNode, Integer> assignInternalLayers(
        Set<LogisticsNode> component,
        Map<LogisticsNode, Set<LogisticsNode>> directed
    ) {
        List<LogisticsNode> orderedNodes = component.stream()
            .sorted(Comparator.comparing(NetworkPreviewLayoutEngine::nodeKey)).toList();
        if (orderedNodes.size() == 1) return Map.of(orderedNodes.get(0), 0);

        Map<LogisticsNode, Set<LogisticsNode>> undirected = new LinkedHashMap<>();
        orderedNodes.forEach(node -> undirected.put(node, new LinkedHashSet<>()));
        for (LogisticsNode source : orderedNodes) {
            directed.getOrDefault(source, Set.of()).stream()
                .filter(component::contains)
                .filter(target -> !target.equals(source))
                .sorted(Comparator.comparing(NetworkPreviewLayoutEngine::nodeKey))
                .forEach(target -> {
                    undirected.get(source).add(target);
                    undirected.get(target).add(source);
                });
        }

        LogisticsNode root = orderedNodes.stream()
            .filter(node -> undirected.get(node).size() <= 1)
            .findFirst()
            .orElseGet(() -> farthestNode(
                breadthFirstDistances(orderedNodes.get(0), undirected), orderedNodes));
        return breadthFirstDistances(root, undirected);
    }

    private static Map<LogisticsNode, Integer> breadthFirstDistances(
        LogisticsNode start,
        Map<LogisticsNode, Set<LogisticsNode>> neighbors
    ) {
        Map<LogisticsNode, Integer> distances = new LinkedHashMap<>();
        ArrayDeque<LogisticsNode> pending = new ArrayDeque<>();
        distances.put(start, 0);
        pending.addLast(start);
        while (!pending.isEmpty()) {
            LogisticsNode node = pending.removeFirst();
            int nextDistance = distances.get(node) + 1;
            neighbors.getOrDefault(node, Set.of()).stream()
                .sorted(Comparator.comparing(NetworkPreviewLayoutEngine::nodeKey))
                .filter(neighbor -> !distances.containsKey(neighbor))
                .forEach(neighbor -> {
                    distances.put(neighbor, nextDistance);
                    pending.addLast(neighbor);
                });
        }
        return distances;
    }

    private static LogisticsNode farthestNode(
        Map<LogisticsNode, Integer> distances,
        List<LogisticsNode> orderedNodes
    ) {
        int farthestDistance = distances.values().stream()
            .mapToInt(Integer::intValue).max().orElse(0);
        return orderedNodes.stream()
            .filter(node -> distances.getOrDefault(node, -1) == farthestDistance)
            .findFirst().orElseThrow();
    }

    private static List<Set<LogisticsNode>> stronglyConnectedComponents(
        Set<LogisticsNode> nodes,
        Map<LogisticsNode, Set<LogisticsNode>> directed
    ) {
        List<LogisticsNode> orderedNodes = nodes.stream()
            .sorted(Comparator.comparing(NetworkPreviewLayoutEngine::nodeKey)).toList();
        List<LogisticsNode> finishingOrder = finishingOrder(orderedNodes, nodes, directed);
        Map<LogisticsNode, Set<LogisticsNode>> reversed = new LinkedHashMap<>();
        orderedNodes.forEach(node -> reversed.put(node, new LinkedHashSet<>()));
        for (LogisticsNode source : orderedNodes) {
            for (LogisticsNode target : directed.getOrDefault(source, Set.of())) {
                if (nodes.contains(target)) reversed.get(target).add(source);
            }
        }

        List<Set<LogisticsNode>> result = new ArrayList<>();
        Set<LogisticsNode> visited = new HashSet<>();
        for (int index = finishingOrder.size() - 1; index >= 0; index--) {
            LogisticsNode start = finishingOrder.get(index);
            if (!visited.add(start)) continue;
            LinkedHashSet<LogisticsNode> component = new LinkedHashSet<>();
            ArrayDeque<LogisticsNode> pending = new ArrayDeque<>();
            pending.push(start);
            while (!pending.isEmpty()) {
                LogisticsNode node = pending.pop();
                component.add(node);
                reversed.getOrDefault(node, Set.of()).stream()
                    .sorted(Comparator.comparing(NetworkPreviewLayoutEngine::nodeKey))
                    .filter(visited::add).forEach(pending::push);
            }
            result.add(component);
        }
        return result;
    }

    /**
     * 使用显式栈计算结束顺序，避免大型网络的递归深度耗尽 JVM 栈。
     */
    private static List<LogisticsNode> finishingOrder(
        List<LogisticsNode> orderedNodes,
        Set<LogisticsNode> allowed,
        Map<LogisticsNode, Set<LogisticsNode>> directed
    ) {
        List<LogisticsNode> result = new ArrayList<>(orderedNodes.size());
        Set<LogisticsNode> visited = new HashSet<>();
        for (LogisticsNode start : orderedNodes) {
            if (!visited.add(start)) continue;
            ArrayDeque<TraversalFrame> stack = new ArrayDeque<>();
            stack.push(new TraversalFrame(start, orderedNeighbors(start, allowed, directed)));
            while (!stack.isEmpty()) {
                TraversalFrame frame = stack.peek();
                LogisticsNode next = frame.next();
                if (next != null) {
                    if (visited.add(next)) {
                        stack.push(new TraversalFrame(
                            next, orderedNeighbors(next, allowed, directed)));
                    }
                    continue;
                }
                result.add(frame.node);
                stack.pop();
            }
        }
        return result;
    }

    private static List<LogisticsNode> orderedNeighbors(
        LogisticsNode node,
        Set<LogisticsNode> allowed,
        Map<LogisticsNode, Set<LogisticsNode>> directed
    ) {
        return directed.getOrDefault(node, Set.of()).stream()
            .filter(allowed::contains)
            .sorted(Comparator.comparing(NetworkPreviewLayoutEngine::nodeKey)).toList();
    }

    private static void improveOrder(
        Map<Integer, List<LogisticsNode>> layers,
        Map<LogisticsNode, Set<LogisticsNode>> neighbors,
        Map<LogisticsNode, Integer> depth
    ) {
        List<Integer> layerKeys = new ArrayList<>(layers.keySet());
        Map<LogisticsNode, Integer> order = layerOrder(layers);
        for (int pass = 0; pass < 4; pass++) {
            for (int index = 1; index < layerKeys.size(); index++) {
                int layer = layerKeys.get(index);
                order = sortLayer(layers.get(layer), layer, true,
                    neighbors, depth, order, layers);
            }
            for (int index = layerKeys.size() - 2; index >= 0; index--) {
                int layer = layerKeys.get(index);
                order = sortLayer(layers.get(layer), layer, false,
                    neighbors, depth, order, layers);
            }
        }
    }

    /**
     * 交替执行正向和反向重心扫描。旧实现只做正向扫描，第二轮以后通常不会再改变顺序。
     */
    private static Map<LogisticsNode, Integer> sortLayer(
        List<LogisticsNode> values,
        int layer,
        boolean fromPreviousLayers,
        Map<LogisticsNode, Set<LogisticsNode>> neighbors,
        Map<LogisticsNode, Integer> depth,
        Map<LogisticsNode, Integer> order,
        Map<Integer, List<LogisticsNode>> layers
    ) {
        Map<LogisticsNode, Integer> stableOrder = indexOrder(values);
        values.sort(Comparator
            .<LogisticsNode>comparingDouble(node -> barycenter(
                neighbors.getOrDefault(node, Set.of()).stream()
                    .filter(neighbor -> fromPreviousLayers
                        ? depth.getOrDefault(neighbor, layer) < layer
                        : depth.getOrDefault(neighbor, layer) > layer)
                    .toList(), order))
            .thenComparingInt(node -> stableOrder.getOrDefault(node, Integer.MAX_VALUE))
            .thenComparing(NetworkPreviewLayoutEngine::nodeKey));
        return layerOrder(layers);
    }

    private static Map<LogisticsNode, Integer> layerOrder(
        Map<Integer, List<LogisticsNode>> layers
    ) {
        Map<LogisticsNode, Integer> result = new HashMap<>();
        layers.values().forEach(values -> result.putAll(indexOrder(values)));
        return result;
    }

    private static double barycenter(
        Collection<LogisticsNode> nodes,
        Map<LogisticsNode, Integer> order
    ) {
        return nodes.stream().filter(order::containsKey).mapToInt(order::get)
            .average().orElse(Double.MAX_VALUE);
    }

    private static Map<LogisticsNode, Integer> indexOrder(List<LogisticsNode> nodes) {
        Map<LogisticsNode, Integer> result = new HashMap<>();
        for (int index = 0; index < nodes.size(); index++) result.put(nodes.get(index), index);
        return result;
    }

    private static Map<LogisticsNode, Point> packComponents(
        List<ComponentLayout> components,
        int viewportWidth
    ) {
        int packingWidth = Math.max(MIN_PACKING_WIDTH, viewportWidth - CANVAS_PADDING * 2);
        Map<LogisticsNode, Point> result = new LinkedHashMap<>();
        int rowX = CANVAS_PADDING;
        int rowY = CANVAS_PADDING;
        int rowHeight = 0;
        for (ComponentLayout component : components) {
            if (rowX > CANVAS_PADDING
                && rowX + component.width > CANVAS_PADDING + packingWidth) {
                rowX = CANVAS_PADDING;
                rowY += rowHeight + COMPONENT_GAP;
                rowHeight = 0;
            }
            int offsetX = rowX;
            int offsetY = rowY;
            component.positions.forEach((node, point) -> result.put(node,
                new Point(point.x + offsetX, point.y + offsetY)));
            rowX += component.width + COMPONENT_GAP;
            rowHeight = Math.max(rowHeight, component.height);
        }
        return result;
    }

    private static List<Set<LogisticsNode>> weakComponents(
        Map<LogisticsNode, Set<LogisticsNode>> neighbors
    ) {
        List<Set<LogisticsNode>> result = new ArrayList<>();
        Set<LogisticsNode> visited = new HashSet<>();
        neighbors.keySet().stream().sorted(Comparator.comparing(
            NetworkPreviewLayoutEngine::nodeKey)).forEach(start -> {
            if (!visited.add(start)) return;
            LinkedHashSet<LogisticsNode> component = new LinkedHashSet<>();
            ArrayDeque<LogisticsNode> pending = new ArrayDeque<>();
            pending.add(start);
            while (!pending.isEmpty()) {
                LogisticsNode node = pending.removeFirst();
                component.add(node);
                neighbors.getOrDefault(node, Set.of()).stream()
                    .sorted(Comparator.comparing(NetworkPreviewLayoutEngine::nodeKey))
                    .filter(visited::add).forEach(pending::addLast);
            }
            result.add(component);
        });
        return result;
    }

    private static String nodeKey(LogisticsNode node) {
        return node.gPos().dimension().location() + "/" + node.gPos().pos().asLong()
            + "/" + node.face().getName();
    }

    record Point(int x, int y) {
    }

    record Size(int width, int height) {
        Size {
            if (width <= 0 || height <= 0) {
                throw new IllegalArgumentException("Preview node size must be positive");
            }
        }
    }

    private record ComponentLayout(
        LogisticsNode firstNode,
        Map<LogisticsNode, Point> positions,
        int width,
        int height
    ) {
    }

    private record Graph(
        Map<LogisticsNode, Set<LogisticsNode>> undirected,
        Map<LogisticsNode, Set<LogisticsNode>> directed
    ) {
        private static Graph create(
            List<ClientConnection> connections,
            Set<LogisticsNode> nodes
        ) {
            Map<LogisticsNode, Set<LogisticsNode>> undirected = new LinkedHashMap<>();
            Map<LogisticsNode, Set<LogisticsNode>> directed = new LinkedHashMap<>();
            nodes.forEach(node -> {
                undirected.put(node, new LinkedHashSet<>());
                directed.put(node, new LinkedHashSet<>());
            });
            for (ClientConnection connection : connections) {
                LogisticsNode first = connection.first();
                LogisticsNode second = connection.second();
                undirected.get(first).add(second);
                undirected.get(second).add(first);
                boolean forward = connection.transfersFirstToSecond();
                boolean backward = connection.transfersSecondToFirst();
                if (forward) directed.get(first).add(second);
                if (backward) directed.get(second).add(first);
                if (!forward && !backward) directed.get(first).add(second);
            }
            return new Graph(undirected, directed);
        }
    }

    private static final class TraversalFrame {
        private final LogisticsNode node;
        private final List<LogisticsNode> neighbors;
        private int nextIndex;

        private TraversalFrame(LogisticsNode node, List<LogisticsNode> neighbors) {
            this.node = node;
            this.neighbors = neighbors;
        }

        private LogisticsNode next() {
            return nextIndex < neighbors.size() ? neighbors.get(nextIndex++) : null;
        }
    }
}
