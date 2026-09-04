package com.coobird.staticlogistics.client.gui.component;

import java.util.*;

/**
 * 网络预览节点的纯放置评分器。
 *
 * <p>调用方传入节点在鼠标释放时的位置。本类只为拖动节点集合寻找一个共同平移量，
 * 不会修改传入数据，也不会移动任何已有节点。候选位置由局部网格、障碍边缘和全局
 * 脱离位置共同组成，避免在大范围内逐像素或逐网格暴力扫描。
 */
final class NetworkPreviewPlacementEngine {
    private static final double EPSILON = 1.0E-7D;
    private static final Score ZERO_SCORE = new Score(
        0.0D, 0.0D, 0.0D, 0, 0, 0.0D);

    private NetworkPreviewPlacementEngine() {
    }

    /**
     * 使用默认评分参数寻找拖动集合的最佳公共平移量。
     */
    static <N> Optional<Placement> findBestOffset(
        Map<N, Rect> nodeBounds,
        Set<N> movingNodes,
        Collection<Edge<N>> edges,
        double nodeGap
    ) {
        return findBestOffset(nodeBounds, movingNodes, edges,
            Settings.defaults(nodeGap));
    }

    /**
     * 寻找拖动集合的最佳公共平移量。
     *
     * <p>若释放位置本身没有与固定节点重叠，直接返回零偏移，尊重玩家的精确摆放。
     * 只有发生重叠时才比较候选位置的释放点距离、关联边长度、直线边交叉、边穿过
     * 节点以及整体边界向外膨胀程度。
     */
    static <N> Optional<Placement> findBestOffset(
        Map<N, Rect> nodeBounds,
        Set<N> movingNodes,
        Collection<Edge<N>> edges,
        Settings settings
    ) {
        Objects.requireNonNull(nodeBounds, "nodeBounds");
        Objects.requireNonNull(movingNodes, "movingNodes");
        Objects.requireNonNull(edges, "edges");
        Objects.requireNonNull(settings, "settings");
        if (movingNodes.isEmpty()) return Optional.empty();

        LinkedHashMap<N, Rect> bounds = new LinkedHashMap<>();
        nodeBounds.forEach((node, rect) -> bounds.put(
            Objects.requireNonNull(node, "nodeBounds contains a null node"),
            Objects.requireNonNull(rect, "nodeBounds contains a null rectangle")));
        LinkedHashSet<N> moving = new LinkedHashSet<>();
        for (N node : movingNodes) {
            Objects.requireNonNull(node, "movingNodes contains a null node");
            if (!bounds.containsKey(node)) {
                throw new IllegalArgumentException(
                    "Every moving node must have a rectangle");
            }
            moving.add(node);
        }

        Offset zero = Offset.ZERO;
        if (isCollisionFree(bounds, moving, zero, settings.nodeGap)) {
            // 评分只用于发生重叠后比较多个纠正候选；原位置可用时无需扫描线路。
            return Optional.of(new Placement(zero, ZERO_SCORE, 0));
        }

        List<Edge<N>> usableEdges = edges.stream()
            .filter(Objects::nonNull)
            .filter(edge -> bounds.containsKey(edge.first)
                && bounds.containsKey(edge.second))
            .toList();
        Rect releaseBounds = union(bounds.values()).orElseThrow();
        LinkedHashSet<Offset> candidates = generateCandidates(
            bounds, moving, settings);
        Placement best = null;
        int evaluated = 0;
        for (Offset offset : candidates) {
            if (!isCollisionFree(bounds, moving, offset, settings.nodeGap)) {
                continue;
            }
            evaluated++;
            Score candidateScore = score(bounds, moving, usableEdges,
                offset, releaseBounds, settings.weights);
            Placement candidate = new Placement(offset, candidateScore, 0);
            if (best == null || compare(candidate, best) < 0) best = candidate;
        }
        return best == null ? Optional.empty() : Optional.of(
            new Placement(best.offset, best.score, evaluated));
    }

    private static <N> LinkedHashSet<Offset> generateCandidates(
        Map<N, Rect> bounds,
        Set<N> moving,
        Settings settings
    ) {
        LinkedHashSet<Offset> candidates = new LinkedHashSet<>();
        candidates.add(Offset.ZERO);

        // 小范围规则网格负责处理没有明显障碍边缘可借用的局部空隙。
        for (int gridX = -settings.gridRadius;
             gridX <= settings.gridRadius; gridX++) {
            for (int gridY = -settings.gridRadius;
                 gridY <= settings.gridRadius; gridY++) {
                if (gridX == 0 && gridY == 0) continue;
                candidates.add(new Offset(
                    gridX * settings.gridStep,
                    gridY * settings.gridStep));
            }
        }

        List<Rect> movingBounds = moving.stream().map(bounds::get).toList();
        Rect movingUnion = union(movingBounds).orElseThrow();
        List<IndexedRect> allObstacles = new ArrayList<>();
        int index = 0;
        for (Map.Entry<N, Rect> entry : bounds.entrySet()) {
            if (!moving.contains(entry.getKey())) {
                allObstacles.add(new IndexedRect(entry.getValue(), index));
            }
            index++;
        }
        List<IndexedRect> obstacles = new ArrayList<>(allObstacles);
        obstacles.sort(Comparator
            .comparingDouble((IndexedRect obstacle) ->
                movingUnion.distanceSquaredTo(obstacle.rect))
            .thenComparingInt(IndexedRect::index));
        if (obstacles.size() > settings.maxConsideredObstacles) {
            obstacles = new ArrayList<>(obstacles.subList(
                0, settings.maxConsideredObstacles));
        }

        List<Double> horizontalOffsets = new ArrayList<>();
        List<Double> verticalOffsets = new ArrayList<>();
        for (IndexedRect obstacle : obstacles) {
            Rect fixed = obstacle.rect;
            for (Rect moved : movingBounds) {
                horizontalOffsets.add(fixed.left() - settings.nodeGap
                    - moved.right());
                horizontalOffsets.add(fixed.right() + settings.nodeGap
                    - moved.left());
                verticalOffsets.add(fixed.top() - settings.nodeGap
                    - moved.bottom());
                verticalOffsets.add(fixed.bottom() + settings.nodeGap
                    - moved.top());
            }
        }
        List<Double> nearestX = nearestDistinctOffsets(
            horizontalOffsets, settings.maxObstacleAxisOffsets);
        List<Double> nearestY = nearestDistinctOffsets(
            verticalOffsets, settings.maxObstacleAxisOffsets);
        nearestX.forEach(offset -> candidates.add(new Offset(offset, 0.0D)));
        nearestY.forEach(offset -> candidates.add(new Offset(0.0D, offset)));
        for (double offsetX : nearestX) {
            for (double offsetY : nearestY) {
                candidates.add(new Offset(offsetX, offsetY));
            }
        }

        // 四个包围盒外侧位置是兜底候选，即使局部网格被完全堵住也能给出结果。
        union(allObstacles.stream().map(IndexedRect::rect).toList())
            .ifPresent(stationaryUnion -> {
                candidates.add(new Offset(
                    stationaryUnion.left() - settings.nodeGap
                        - movingUnion.right(), 0.0D));
                candidates.add(new Offset(
                    stationaryUnion.right() + settings.nodeGap
                        - movingUnion.left(), 0.0D));
                candidates.add(new Offset(0.0D,
                    stationaryUnion.top() - settings.nodeGap
                        - movingUnion.bottom()));
                candidates.add(new Offset(0.0D,
                    stationaryUnion.bottom() + settings.nodeGap
                        - movingUnion.top()));
            });
        return candidates;
    }

    private static List<Double> nearestDistinctOffsets(
        List<Double> offsets,
        int limit
    ) {
        return offsets.stream()
            .map(NetworkPreviewPlacementEngine::normalizeZero)
            .filter(Double::isFinite)
            .distinct()
            .sorted(Comparator.comparingDouble((Double value) -> Math.abs(value))
                .thenComparingDouble(Double::doubleValue))
            .limit(limit)
            .toList();
    }

    private static <N> boolean isCollisionFree(
        Map<N, Rect> bounds,
        Set<N> moving,
        Offset offset,
        double gap
    ) {
        for (N movedNode : moving) {
            Rect moved = bounds.get(movedNode).translate(offset.x, offset.y);
            for (Map.Entry<N, Rect> stationary : bounds.entrySet()) {
                if (moving.contains(stationary.getKey())) continue;
                if (moved.overlaps(stationary.getValue(), gap)) return false;
            }
        }
        return true;
    }

    private static <N> Score score(
        Map<N, Rect> bounds,
        Set<N> moving,
        List<Edge<N>> edges,
        Offset offset,
        Rect releaseBounds,
        Weights weights
    ) {
        Map<N, Rect> placed = new HashMap<>(bounds.size());
        bounds.forEach((node, rect) -> placed.put(node,
            moving.contains(node) ? rect.translate(offset.x, offset.y) : rect));
        List<Segment<N>> affectedSegments = new ArrayList<>();
        List<Segment<N>> stationarySegments = new ArrayList<>();
        double associatedEdgeLength = 0.0D;
        for (Edge<N> edge : edges) {
            Point first = placed.get(edge.first).center();
            Point second = placed.get(edge.second).center();
            boolean affected = moving.contains(edge.first)
                || moving.contains(edge.second);
            Segment<N> segment = new Segment<>(edge, first, second);
            if (affected) {
                affectedSegments.add(segment);
                associatedEdgeLength += first.distanceTo(second);
            } else {
                stationarySegments.add(segment);
            }
        }

        int crossings = 0;
        for (int firstIndex = 0;
             firstIndex < affectedSegments.size(); firstIndex++) {
            Segment<N> affected = affectedSegments.get(firstIndex);
            // 两条受影响线路只比较一次，固定线路之间则完全无需比较。
            for (int secondIndex = firstIndex + 1;
                 secondIndex < affectedSegments.size(); secondIndex++) {
                if (crosses(affected, affectedSegments.get(secondIndex))) {
                    crossings++;
                }
            }
            for (Segment<N> stationary : stationarySegments) {
                if (crosses(affected, stationary)) crossings++;
            }
        }

        int throughNodes = 0;
        // 会移动的线路可能穿过任意节点。
        for (Segment<N> segment : affectedSegments) {
            for (Map.Entry<N, Rect> node : placed.entrySet()) {
                if (segment.edge.contains(node.getKey())) continue;
                if (intersectsInterior(segment.first, segment.second,
                    node.getValue())) throughNodes++;
            }
        }
        // 固定线路的几何没有变化，只需检查它是否穿过被移动的节点。
        for (Segment<N> segment : stationarySegments) {
            for (N movedNode : moving) {
                if (segment.edge.contains(movedNode)) continue;
                if (intersectsInterior(segment.first, segment.second,
                    placed.get(movedNode))) throughNodes++;
            }
        }

        Rect candidateBounds = union(placed.values()).orElseThrow();
        double boundaryExpansion = outwardExpansion(
            releaseBounds, candidateBounds);
        double releaseDistance = Math.hypot(offset.x, offset.y);
        double total = releaseDistance * weights.releaseDistance
            + associatedEdgeLength * weights.associatedEdgeLength
            + crossings * weights.edgeCrossing
            + throughNodes * weights.edgeThroughNode
            + boundaryExpansion * weights.boundaryExpansion;
        return new Score(total, releaseDistance, associatedEdgeLength,
            crossings, throughNodes, boundaryExpansion);
    }

    private static <N> boolean crosses(
        Segment<N> first,
        Segment<N> second
    ) {
        return !first.edge.sharesNode(second.edge)
            && properlyIntersects(first.first, first.second,
            second.first, second.second);
    }

    private static int compare(Placement first, Placement second) {
        int comparison = Double.compare(first.score.total, second.score.total);
        if (comparison != 0) return comparison;
        comparison = Integer.compare(first.score.edgesThroughNodes,
            second.score.edgesThroughNodes);
        if (comparison != 0) return comparison;
        comparison = Integer.compare(first.score.edgeCrossings,
            second.score.edgeCrossings);
        if (comparison != 0) return comparison;
        comparison = Double.compare(first.score.releaseDistance,
            second.score.releaseDistance);
        if (comparison != 0) return comparison;
        comparison = Double.compare(Math.abs(first.offset.y),
            Math.abs(second.offset.y));
        if (comparison != 0) return comparison;
        comparison = Double.compare(Math.abs(first.offset.x),
            Math.abs(second.offset.x));
        if (comparison != 0) return comparison;
        comparison = Double.compare(first.offset.y, second.offset.y);
        return comparison != 0 ? comparison
            : Double.compare(first.offset.x, second.offset.x);
    }

    private static boolean properlyIntersects(
        Point firstStart,
        Point firstEnd,
        Point secondStart,
        Point secondEnd
    ) {
        double firstSideStart = cross(firstStart, firstEnd, secondStart);
        double firstSideEnd = cross(firstStart, firstEnd, secondEnd);
        double secondSideStart = cross(secondStart, secondEnd, firstStart);
        double secondSideEnd = cross(secondStart, secondEnd, firstEnd);
        return oppositeSigns(firstSideStart, firstSideEnd)
            && oppositeSigns(secondSideStart, secondSideEnd);
    }

    private static boolean oppositeSigns(double first, double second) {
        return first > EPSILON && second < -EPSILON
            || first < -EPSILON && second > EPSILON;
    }

    private static double cross(Point start, Point end, Point point) {
        return (end.x - start.x) * (point.y - start.y)
            - (end.y - start.y) * (point.x - start.x);
    }

    /**
     * 使用 Liang-Barsky 裁剪判断线段是否进入矩形内部。先略微收缩矩形，避免仅与边框
     * 相切的线路被误判为穿过节点。
     */
    private static boolean intersectsInterior(Point start, Point end, Rect rect) {
        double inset = Math.min(1.0D,
            Math.min(rect.width, rect.height) * 0.1D);
        double left = rect.left() + inset;
        double right = rect.right() - inset;
        double top = rect.top() + inset;
        double bottom = rect.bottom() - inset;
        if (left >= right || top >= bottom) return false;

        double deltaX = end.x - start.x;
        double deltaY = end.y - start.y;
        double[] interval = {0.0D, 1.0D};
        return clip(-deltaX, start.x - left, interval)
            && clip(deltaX, right - start.x, interval)
            && clip(-deltaY, start.y - top, interval)
            && clip(deltaY, bottom - start.y, interval)
            && interval[1] - interval[0] > EPSILON;
    }

    private static boolean clip(double direction, double distance,
                                double[] interval) {
        if (Math.abs(direction) <= EPSILON) return distance >= 0.0D;
        double ratio = distance / direction;
        if (direction < 0.0D) {
            if (ratio > interval[1]) return false;
            interval[0] = Math.max(interval[0], ratio);
        } else {
            if (ratio < interval[0]) return false;
            interval[1] = Math.min(interval[1], ratio);
        }
        return true;
    }

    private static double outwardExpansion(Rect original, Rect candidate) {
        return Math.max(0.0D, original.left() - candidate.left())
            + Math.max(0.0D, candidate.right() - original.right())
            + Math.max(0.0D, original.top() - candidate.top())
            + Math.max(0.0D, candidate.bottom() - original.bottom());
    }

    private static Optional<Rect> union(Collection<Rect> rectangles) {
        if (rectangles.isEmpty()) return Optional.empty();
        double left = Double.POSITIVE_INFINITY;
        double top = Double.POSITIVE_INFINITY;
        double right = Double.NEGATIVE_INFINITY;
        double bottom = Double.NEGATIVE_INFINITY;
        for (Rect rect : rectangles) {
            left = Math.min(left, rect.left());
            top = Math.min(top, rect.top());
            right = Math.max(right, rect.right());
            bottom = Math.max(bottom, rect.bottom());
        }
        return Optional.of(new Rect(left, top, right - left, bottom - top));
    }

    private static double normalizeZero(double value) {
        return Math.abs(value) <= EPSILON ? 0.0D : value;
    }

    /**
     * 节点矩形，坐标使用网络预览的局部坐标系。
     */
    record Rect(double x, double y, double width, double height) {
        Rect {
            if (!Double.isFinite(x) || !Double.isFinite(y)
                || !Double.isFinite(width) || !Double.isFinite(height)
                || width <= 0.0D || height <= 0.0D) {
                throw new IllegalArgumentException(
                    "Node rectangle must be finite and have a positive size");
            }
        }

        double left() {
            return x;
        }

        double right() {
            return x + width;
        }

        double top() {
            return y;
        }

        double bottom() {
            return y + height;
        }

        Point center() {
            return new Point(x + width / 2.0D, y + height / 2.0D);
        }

        Rect translate(double offsetX, double offsetY) {
            return new Rect(x + offsetX, y + offsetY, width, height);
        }

        boolean overlaps(Rect other, double gap) {
            return left() < other.right() + gap - EPSILON
                && right() + gap > other.left() + EPSILON
                && top() < other.bottom() + gap - EPSILON
                && bottom() + gap > other.top() + EPSILON;
        }

        double distanceSquaredTo(Rect other) {
            double horizontal = Math.max(0.0D,
                Math.max(other.left() - right(), left() - other.right()));
            double vertical = Math.max(0.0D,
                Math.max(other.top() - bottom(), top() - other.bottom()));
            return horizontal * horizontal + vertical * vertical;
        }
    }

    /**
     * 一条仅用于几何评分的无向连接边。
     */
    record Edge<N>(N first, N second) {
        Edge {
            Objects.requireNonNull(first, "first");
            Objects.requireNonNull(second, "second");
        }

        boolean contains(N node) {
            return first.equals(node) || second.equals(node);
        }

        boolean sharesNode(Edge<N> other) {
            return contains(other.first) || contains(other.second);
        }
    }

    /**
     * 最终采用的公共平移量及其评分明细。
     */
    record Placement(Offset offset, Score score, int evaluatedCandidates) {
    }

    record Offset(double x, double y) {
        private static final Offset ZERO = new Offset(0.0D, 0.0D);

        Offset {
            if (!Double.isFinite(x) || !Double.isFinite(y)) {
                throw new IllegalArgumentException("Offset must be finite");
            }
            x = normalizeZero(x);
            y = normalizeZero(y);
        }
    }

    record Score(
        double total,
        double releaseDistance,
        double associatedEdgeLength,
        int edgeCrossings,
        int edgesThroughNodes,
        double boundaryExpansion
    ) {
    }

    /**
     * 各评分项权重，可由面板在后续体验调整时覆盖。
     */
    record Weights(
        double releaseDistance,
        double associatedEdgeLength,
        double edgeCrossing,
        double edgeThroughNode,
        double boundaryExpansion
    ) {
        Weights {
            if (!validWeight(releaseDistance)
                || !validWeight(associatedEdgeLength)
                || !validWeight(edgeCrossing)
                || !validWeight(edgeThroughNode)
                || !validWeight(boundaryExpansion)) {
                throw new IllegalArgumentException(
                    "Placement weights must be finite and non-negative");
            }
        }

        private static boolean validWeight(double value) {
            return Double.isFinite(value) && value >= 0.0D;
        }

        static Weights defaults() {
            return new Weights(1.0D, 0.08D, 160.0D, 360.0D, 0.35D);
        }
    }

    /**
     * 候选生成参数；默认只产生约五百个候选位置。
     */
    record Settings(
        double nodeGap,
        double gridStep,
        int gridRadius,
        int maxConsideredObstacles,
        int maxObstacleAxisOffsets,
        Weights weights
    ) {
        Settings {
            if (!Double.isFinite(nodeGap) || nodeGap < 0.0D) {
                throw new IllegalArgumentException(
                    "Node gap must be finite and non-negative");
            }
            if (!Double.isFinite(gridStep) || gridStep <= 0.0D) {
                throw new IllegalArgumentException(
                    "Grid step must be finite and positive");
            }
            if (gridRadius < 1 || gridRadius > 32
                || maxConsideredObstacles < 1
                || maxObstacleAxisOffsets < 1
                || maxObstacleAxisOffsets > 64) {
                throw new IllegalArgumentException(
                    "Placement candidate limits are out of range");
            }
            Objects.requireNonNull(weights, "weights");
        }

        static Settings defaults(double nodeGap) {
            return new Settings(nodeGap, 8.0D, 5, 32, 18,
                Weights.defaults());
        }
    }

    private record Point(double x, double y) {
        private double distanceTo(Point other) {
            return Math.hypot(other.x - x, other.y - y);
        }
    }

    private record IndexedRect(Rect rect, int index) {
    }

    private record Segment<N>(
        Edge<N> edge,
        Point first,
        Point second
    ) {
    }
}
