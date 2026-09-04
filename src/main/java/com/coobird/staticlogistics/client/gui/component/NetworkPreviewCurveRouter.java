package com.coobird.staticlogistics.client.gui.component;

import java.util.*;

/**
 * 网络预览使用的纯曲线路由器。
 *
 * <p>路由器不依赖渲染状态，也不保存任何会随帧变化的数据。相同的矩形、障碍物和设置始终
 * 生成相同结果，因此调用方可以按节点布局版本缓存 {@link Route}。普通情况优先使用一条
 * 三次贝塞尔曲线；只有所有合适的端口组合都会穿过障碍物时，才会在扩张后的障碍物角点间
 * 寻找一条短绕行路径。</p>
 */
public final class NetworkPreviewCurveRouter {
    public static final Settings DEFAULT_SETTINGS = new Settings(8.0D, 13.0D, 2.5D, 11.0D);

    private static final double EPSILON = 1.0E-6D;
    private static final double CORNER_EPSILON = 0.75D;
    private static final int MIN_CURVE_SEGMENTS = 16;
    private static final int MAX_CURVE_SEGMENTS = 512;
    private static final int MAX_LINE_SEGMENTS = 1024;
    private static final int MAX_QUADRATIC_SEGMENTS = 512;

    private NetworkPreviewCurveRouter() {
    }

    /**
     * 使用默认设置生成一条从起点矩形到终点矩形的曲线。
     *
     * @param source    起点节点矩形
     * @param target    终点节点矩形
     * @param obstacles 除起点和终点之外的节点矩形
     */
    public static Route route(Rect source, Rect target, Collection<Rect> obstacles) {
        return route(source, target, obstacles, DEFAULT_SETTINGS);
    }

    /**
     * 生成一条可直接用于渲染、命中检测和法线分轨的采样曲线。
     */
    public static Route route(
        Rect source,
        Rect target,
        Collection<Rect> obstacles,
        Settings settings
    ) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(obstacles, "obstacles");
        Objects.requireNonNull(settings, "settings");

        List<Rect> rawObstacles = obstacles.stream()
            .filter(Objects::nonNull)
            .filter(rect -> rect.width > EPSILON && rect.height > EPSILON)
            .distinct()
            .sorted(RECT_ORDER)
            .toList();
        List<Rect> paddedObstacles = rawObstacles.stream()
            .map(rect -> rect.expanded(settings.obstaclePadding))
            .toList();

        List<PortPair> portPairs = createPortPairs(source, target, settings);
        DirectCandidate bestBlocked = null;
        for (PortPair pair : portPairs) {
            List<Point> samples = sampleCubic(pair, settings.sampleSpacing);
            int collisions = collisionCount(samples, paddedObstacles);
            boolean endpointsSafe = endpointTraversalSafe(samples, source, target);
            DirectCandidate candidate = new DirectCandidate(
                pair, samples, collisions, endpointsSafe);
            if (collisions == 0 && endpointsSafe) {
                return new Route(pair.source, pair.target,
                    List.of(pair.source.point, pair.target.point), samples, false, true);
            }
            if (bestBlocked == null || candidate.compareTo(bestBlocked) < 0) {
                bestBlocked = candidate;
            }
        }

        Route bestDetour = null;
        double bestDetourScore = Double.POSITIVE_INFINITY;
        for (PortPair pair : portPairs) {
            Detour detour = findDetour(pair, rawObstacles, paddedObstacles, settings);
            if (detour == null || !endpointTraversalSafe(detour.samples, source, target)) continue;
            double score = pair.score + polylineLength(detour.waypoints)
                + Math.max(0, detour.waypoints.size() - 2) * 5.0D;
            if (score + EPSILON < bestDetourScore) {
                bestDetourScore = score;
                bestDetour = new Route(pair.source, pair.target,
                    detour.waypoints, detour.samples, true, true);
            }
        }
        if (bestDetour != null) return bestDetour;

        // 完全封闭或节点本身互相覆盖时仍返回确定性的曲线，调用方可通过 obstacleFree 标记警示。
        PortPair fallbackPair = Objects.requireNonNull(bestBlocked).pair;
        return new Route(fallbackPair.source, fallbackPair.target,
            List.of(fallbackPair.source.point, fallbackPair.target.point),
            bestBlocked.samples, false, false);
    }

    private static List<PortPair> createPortPairs(Rect source, Rect target, Settings settings) {
        Point sourceCenter = source.center();
        Point targetCenter = target.center();
        Point centerDirection = targetCenter.subtract(sourceCenter).normalizedOr(new Point(1.0D, 0.0D));
        Point lateralDirection = new Point(-centerDirection.y, centerDirection.x);
        List<PortPair> result = new ArrayList<>(Side.values().length * Side.values().length);
        for (Side sourceSide : Side.values()) {
            Port sourcePort = new Port(sourceSide, source.port(sourceSide));
            for (Side targetSide : Side.values()) {
                Port targetPort = new Port(targetSide, target.port(targetSide));
                double distance = sourcePort.point.distanceTo(targetPort.point);
                double sourceAlignment = sourceSide.normal.dot(centerDirection);
                double targetAlignment = targetSide.normal.dot(centerDirection.scale(-1.0D));
                double directionPenalty = (2.0D - sourceAlignment - targetAlignment) * 26.0D;
                double sameSidePenalty = sourceSide == targetSide ? 7.0D : 0.0D;
                double score = distance + directionPenalty + sameSidePenalty;
                double handle = clamp(distance * 0.32D + 4.0D, 8.0D, 120.0D);
                double bow = clamp(distance * 0.08D, 0.0D, 18.0D);
                Point firstControl = sourcePort.point
                    .add(sourceSide.normal.scale(handle))
                    .add(centerDirection.scale(handle * 0.16D))
                    .add(lateralDirection.scale(bow * 0.5D));
                Point secondControl = targetPort.point
                    .add(targetSide.normal.scale(handle))
                    .add(centerDirection.scale(-handle * 0.16D))
                    .add(lateralDirection.scale(-bow * 0.5D));
                result.add(new PortPair(sourcePort, targetPort,
                    firstControl, secondControl, score, settings.portStem));
            }
        }
        result.sort(Comparator.comparingDouble(PortPair::score)
            .thenComparingInt(pair -> pair.source.side.ordinal())
            .thenComparingInt(pair -> pair.target.side.ordinal()));
        return List.copyOf(result);
    }

    private static List<Point> sampleCubic(PortPair pair, double sampleSpacing) {
        Point start = pair.source.point;
        Point firstControl = pair.firstControl;
        Point secondControl = pair.secondControl;
        Point end = pair.target.point;
        double estimatedLength = start.distanceTo(firstControl)
            + firstControl.distanceTo(secondControl) + secondControl.distanceTo(end);
        int segments = clamp((int) Math.ceil(estimatedLength / sampleSpacing),
            MIN_CURVE_SEGMENTS, MAX_CURVE_SEGMENTS);
        List<Point> result = new ArrayList<>(segments + 1);
        for (int index = 0; index <= segments; index++) {
            double progress = index / (double) segments;
            double inverse = 1.0D - progress;
            double firstWeight = inverse * inverse * inverse;
            double secondWeight = 3.0D * inverse * inverse * progress;
            double thirdWeight = 3.0D * inverse * progress * progress;
            double fourthWeight = progress * progress * progress;
            result.add(new Point(
                start.x * firstWeight + firstControl.x * secondWeight
                    + secondControl.x * thirdWeight + end.x * fourthWeight,
                start.y * firstWeight + firstControl.y * secondWeight
                    + secondControl.y * thirdWeight + end.y * fourthWeight));
        }
        return List.copyOf(result);
    }

    private static Detour findDetour(
        PortPair pair,
        List<Rect> rawObstacles,
        List<Rect> paddedObstacles,
        Settings settings
    ) {
        Point sourcePoint = pair.source.point;
        Point targetPoint = pair.target.point;
        Point sourceStem = sourcePoint.add(pair.source.side.normal.scale(pair.stemLength));
        Point targetStem = targetPoint.add(pair.target.side.normal.scale(pair.stemLength));
        if (!segmentClear(sourcePoint, sourceStem, paddedObstacles)
            || !segmentClear(targetPoint, targetStem, paddedObstacles)
            || containsAny(sourceStem, paddedObstacles)
            || containsAny(targetStem, paddedObstacles)) {
            return null;
        }

        List<Point> vertices = new ArrayList<>(2 + paddedObstacles.size() * 4);
        vertices.add(sourceStem);
        vertices.add(targetStem);
        for (Rect obstacle : paddedObstacles) {
            vertices.add(new Point(obstacle.left() - CORNER_EPSILON,
                obstacle.top() - CORNER_EPSILON));
            vertices.add(new Point(obstacle.right() + CORNER_EPSILON,
                obstacle.top() - CORNER_EPSILON));
            vertices.add(new Point(obstacle.right() + CORNER_EPSILON,
                obstacle.bottom() + CORNER_EPSILON));
            vertices.add(new Point(obstacle.left() - CORNER_EPSILON,
                obstacle.bottom() + CORNER_EPSILON));
        }

        List<Point> graphPath = shortestVisiblePath(vertices, paddedObstacles);
        if (graphPath.isEmpty()) return null;
        graphPath = simplifyVisiblePath(graphPath, paddedObstacles);

        List<Point> waypoints = new ArrayList<>(graphPath.size() + 2);
        waypoints.add(sourcePoint);
        waypoints.addAll(graphPath);
        waypoints.add(targetPoint);
        waypoints = removeConsecutiveDuplicates(waypoints);

        double minimumPadding = Math.min(2.0D, settings.obstaclePadding * 0.25D);
        List<Rect> minimumObstacles = rawObstacles.stream()
            .map(rect -> rect.expanded(minimumPadding))
            .toList();
        double radius = settings.cornerRadius;
        for (int attempt = 0; attempt < 4; attempt++) {
            List<Point> samples = sampleRoundedPolyline(waypoints, radius, settings.sampleSpacing);
            if (collisionCount(samples, minimumObstacles) == 0) {
                return new Detour(List.copyOf(waypoints), samples);
            }
            radius *= 0.5D;
        }
        List<Point> unsmoothed = sampleRoundedPolyline(waypoints, 0.0D, settings.sampleSpacing);
        return collisionCount(unsmoothed, minimumObstacles) == 0
            ? new Detour(List.copyOf(waypoints), unsmoothed) : null;
    }

    /**
     * 障碍物角点构成一个很小的可见性图。该步骤只在普通贝塞尔全部失败时运行，
     * 并且路由结果适合由调用方缓存，不会产生逐帧寻路负担。
     */
    private static List<Point> shortestVisiblePath(List<Point> vertices, List<Rect> obstacles) {
        int count = vertices.size();
        double[] distances = new double[count];
        int[] previous = new int[count];
        boolean[] visited = new boolean[count];
        Arrays.fill(distances, Double.POSITIVE_INFINITY);
        Arrays.fill(previous, -1);
        distances[0] = 0.0D;

        PriorityQueue<PathState> pending = new PriorityQueue<>(
            Comparator.comparingDouble(PathState::distance).thenComparingInt(PathState::vertex));
        pending.add(new PathState(0, 0.0D));
        while (!pending.isEmpty()) {
            PathState state = pending.remove();
            int current = state.vertex;
            if (visited[current]) continue;
            visited[current] = true;
            if (current == 1) break;
            for (int next = 0; next < count; next++) {
                if (next == current || visited[next]
                    || !segmentClear(vertices.get(current), vertices.get(next), obstacles)) {
                    continue;
                }
                double candidate = distances[current]
                    + vertices.get(current).distanceTo(vertices.get(next));
                if (candidate + EPSILON < distances[next]
                    || (Math.abs(candidate - distances[next]) <= EPSILON
                    && (previous[next] < 0 || current < previous[next]))) {
                    distances[next] = candidate;
                    previous[next] = current;
                    pending.add(new PathState(next, candidate));
                }
            }
        }
        if (!Double.isFinite(distances[1])) return List.of();

        List<Point> reversed = new ArrayList<>();
        for (int current = 1; current >= 0; current = previous[current]) {
            reversed.add(vertices.get(current));
            if (current == 0) break;
        }
        List<Point> result = new ArrayList<>(reversed.size());
        for (int index = reversed.size() - 1; index >= 0; index--) {
            result.add(reversed.get(index));
        }
        return List.copyOf(result);
    }

    private static List<Point> simplifyVisiblePath(List<Point> path, List<Rect> obstacles) {
        if (path.size() <= 2) return path;
        List<Point> result = new ArrayList<>();
        int current = 0;
        result.add(path.get(0));
        while (current < path.size() - 1) {
            int next = path.size() - 1;
            while (next > current + 1
                && !segmentClear(path.get(current), path.get(next), obstacles)) {
                next--;
            }
            result.add(path.get(next));
            current = next;
        }
        return List.copyOf(result);
    }

    private static List<Point> sampleRoundedPolyline(
        List<Point> waypoints,
        double radius,
        double sampleSpacing
    ) {
        if (waypoints.size() < 2) return List.copyOf(waypoints);
        List<Point> result = new ArrayList<>();
        result.add(waypoints.get(0));
        Point cursor = waypoints.get(0);
        for (int index = 1; index < waypoints.size() - 1; index++) {
            Point before = waypoints.get(index - 1);
            Point corner = waypoints.get(index);
            Point after = waypoints.get(index + 1);
            double incomingLength = before.distanceTo(corner);
            double outgoingLength = corner.distanceTo(after);
            double cornerRadius = Math.min(radius,
                Math.min(incomingLength * 0.32D, outgoingLength * 0.32D));
            if (cornerRadius <= EPSILON) {
                appendLine(result, cursor, corner, sampleSpacing);
                cursor = corner;
                continue;
            }
            Point entry = corner.moveToward(before, cornerRadius);
            Point exit = corner.moveToward(after, cornerRadius);
            appendLine(result, cursor, entry, sampleSpacing);
            appendQuadratic(result, entry, corner, exit, sampleSpacing);
            cursor = exit;
        }
        appendLine(result, cursor, waypoints.get(waypoints.size() - 1), sampleSpacing);
        return List.copyOf(result);
    }

    private static void appendLine(List<Point> result, Point start, Point end, double spacing) {
        double length = start.distanceTo(end);
        if (length <= EPSILON) return;
        int segments = clamp((int) Math.ceil(length / spacing), 1, MAX_LINE_SEGMENTS);
        for (int index = 1; index <= segments; index++) {
            result.add(start.lerp(end, index / (double) segments));
        }
    }

    private static void appendQuadratic(
        List<Point> result,
        Point start,
        Point control,
        Point end,
        double spacing
    ) {
        double estimatedLength = start.distanceTo(control) + control.distanceTo(end);
        if (estimatedLength <= EPSILON) return;
        int segments = clamp((int) Math.ceil(estimatedLength / spacing),
            2, MAX_QUADRATIC_SEGMENTS);
        for (int index = 1; index <= segments; index++) {
            double progress = index / (double) segments;
            double inverse = 1.0D - progress;
            result.add(new Point(
                inverse * inverse * start.x + 2.0D * inverse * progress * control.x
                    + progress * progress * end.x,
                inverse * inverse * start.y + 2.0D * inverse * progress * control.y
                    + progress * progress * end.y));
        }
    }

    private static List<Point> removeConsecutiveDuplicates(List<Point> points) {
        List<Point> result = new ArrayList<>(points.size());
        for (Point point : points) {
            if (result.isEmpty() || result.get(result.size() - 1).distanceTo(point) > EPSILON) {
                result.add(point);
            }
        }
        return result;
    }

    private static int collisionCount(List<Point> samples, List<Rect> obstacles) {
        int collisions = 0;
        for (Rect obstacle : obstacles) {
            boolean collided = false;
            for (int index = 1; index < samples.size(); index++) {
                if (segmentIntersectsRect(samples.get(index - 1), samples.get(index), obstacle)) {
                    collided = true;
                    break;
                }
            }
            if (collided) collisions++;
        }
        return collisions;
    }

    private static boolean containsAny(Point point, List<Rect> obstacles) {
        return obstacles.stream().anyMatch(rect -> rect.contains(point));
    }

    private static boolean segmentClear(Point first, Point second, List<Rect> obstacles) {
        for (Rect obstacle : obstacles) {
            if (segmentIntersectsRect(first, second, obstacle)) return false;
        }
        return true;
    }

    /**
     * 起点只允许从端口向外离开，终点只允许从外部进入端口结束。把终点方向反转后，
     * 两侧都可以用同一条规则检查：首段仅可在参数零处接触矩形，后续不得再次接触。
     */
    private static boolean endpointTraversalSafe(
        List<Point> samples,
        Rect source,
        Rect target
    ) {
        return endpointExitSafe(samples, source, false)
            && endpointExitSafe(samples, target, true);
    }

    private static boolean endpointExitSafe(
        List<Point> samples,
        Rect endpoint,
        boolean reverse
    ) {
        int lastIndex = samples.size() - 1;
        Point port = samples.get(reverse ? lastIndex : 0);
        if (!pointOnBoundary(port, endpoint)) return false;

        for (int step = 1; step <= lastIndex; step++) {
            int firstIndex = reverse ? lastIndex - step + 1 : step - 1;
            int secondIndex = reverse ? lastIndex - step : step;
            ClipInterval intersection = clipSegmentToRect(
                samples.get(firstIndex), samples.get(secondIndex), endpoint);
            if (intersection == null) continue;
            if (step == 1 && intersection.maximum <= EPSILON) continue;
            return false;
        }
        return true;
    }

    private static boolean pointOnBoundary(Point point, Rect rect) {
        boolean withinX = point.x >= rect.left() - EPSILON
            && point.x <= rect.right() + EPSILON;
        boolean withinY = point.y >= rect.top() - EPSILON
            && point.y <= rect.bottom() + EPSILON;
        if (!withinX || !withinY) return false;
        return Math.abs(point.x - rect.left()) <= EPSILON
            || Math.abs(point.x - rect.right()) <= EPSILON
            || Math.abs(point.y - rect.top()) <= EPSILON
            || Math.abs(point.y - rect.bottom()) <= EPSILON;
    }

    /**
     * 使用 Liang-Barsky 裁剪测试线段是否接触矩形。
     */
    private static boolean segmentIntersectsRect(Point first, Point second, Rect rect) {
        return clipSegmentToRect(first, second, rect) != null;
    }

    private static ClipInterval clipSegmentToRect(Point first, Point second, Rect rect) {
        double deltaX = second.x - first.x;
        double deltaY = second.y - first.y;
        double[] p = {-deltaX, deltaX, -deltaY, deltaY};
        double[] q = {
            first.x - rect.left(), rect.right() - first.x,
            first.y - rect.top(), rect.bottom() - first.y
        };
        double minimum = 0.0D;
        double maximum = 1.0D;
        for (int index = 0; index < p.length; index++) {
            if (Math.abs(p[index]) <= EPSILON) {
                if (q[index] < 0.0D) return null;
                continue;
            }
            double ratio = q[index] / p[index];
            if (p[index] < 0.0D) {
                minimum = Math.max(minimum, ratio);
            } else {
                maximum = Math.min(maximum, ratio);
            }
            if (minimum - maximum > EPSILON) return null;
        }
        return new ClipInterval(minimum, maximum);
    }

    private static double polylineLength(List<Point> points) {
        double result = 0.0D;
        for (int index = 1; index < points.size(); index++) {
            result += points.get(index - 1).distanceTo(points.get(index));
        }
        return result;
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static final Comparator<Rect> RECT_ORDER = Comparator
        .comparingDouble(Rect::x)
        .thenComparingDouble(Rect::y)
        .thenComparingDouble(Rect::width)
        .thenComparingDouble(Rect::height);

    public enum Side {
        LEFT(new Point(-1.0D, 0.0D)),
        RIGHT(new Point(1.0D, 0.0D)),
        TOP(new Point(0.0D, -1.0D)),
        BOTTOM(new Point(0.0D, 1.0D));

        private final Point normal;

        Side(Point normal) {
            this.normal = normal;
        }

        public Point normal() {
            return normal;
        }
    }

    public record Point(double x, double y) {
        public Point {
            if (!Double.isFinite(x) || !Double.isFinite(y)) {
                throw new IllegalArgumentException("坐标必须是有限数值");
            }
        }

        public Point add(Point other) {
            return new Point(x + other.x, y + other.y);
        }

        public Point subtract(Point other) {
            return new Point(x - other.x, y - other.y);
        }

        public Point scale(double scale) {
            return new Point(x * scale, y * scale);
        }

        public double dot(Point other) {
            return x * other.x + y * other.y;
        }

        public double distanceTo(Point other) {
            return Math.hypot(other.x - x, other.y - y);
        }

        public Point normalizedOr(Point fallback) {
            double length = Math.hypot(x, y);
            return length <= EPSILON ? fallback : new Point(x / length, y / length);
        }

        public Point moveToward(Point target, double distance) {
            double length = distanceTo(target);
            if (length <= EPSILON || distance >= length) return target;
            return lerp(target, distance / length);
        }

        public Point lerp(Point target, double progress) {
            return new Point(x + (target.x - x) * progress, y + (target.y - y) * progress);
        }
    }

    public record Rect(double x, double y, double width, double height) {
        public Rect {
            if (!Double.isFinite(x) || !Double.isFinite(y)
                || !Double.isFinite(width) || !Double.isFinite(height)
                || width < 0.0D || height < 0.0D) {
                throw new IllegalArgumentException("矩形坐标和尺寸必须是有效的非负数值");
            }
        }

        public double left() {
            return x;
        }

        public double right() {
            return x + width;
        }

        public double top() {
            return y;
        }

        public double bottom() {
            return y + height;
        }

        public Point center() {
            return new Point(x + width / 2.0D, y + height / 2.0D);
        }

        public Point port(Side side) {
            return switch (side) {
                case LEFT -> new Point(left(), y + height / 2.0D);
                case RIGHT -> new Point(right(), y + height / 2.0D);
                case TOP -> new Point(x + width / 2.0D, top());
                case BOTTOM -> new Point(x + width / 2.0D, bottom());
            };
        }

        public Rect expanded(double padding) {
            if (!Double.isFinite(padding) || padding < 0.0D) {
                throw new IllegalArgumentException("矩形扩张距离必须是有效的非负数值");
            }
            return new Rect(x - padding, y - padding,
                width + padding * 2.0D, height + padding * 2.0D);
        }

        public boolean contains(Point point) {
            return point.x >= left() - EPSILON && point.x <= right() + EPSILON
                && point.y >= top() - EPSILON && point.y <= bottom() + EPSILON;
        }
    }

    public record Port(Side side, Point point) {
        public Port {
            Objects.requireNonNull(side, "side");
            Objects.requireNonNull(point, "point");
        }
    }

    /**
     * @param waypoints    路由的少量关键点，可用于调试预览
     * @param samples      从 sourcePort 到 targetPort 有序排列的最终采样点
     * @param detoured     是否因为障碍物启用了绕行
     * @param obstacleFree 最终曲线是否避开了全部障碍物
     */
    public record Route(
        Port sourcePort,
        Port targetPort,
        List<Point> waypoints,
        List<Point> samples,
        boolean detoured,
        boolean obstacleFree
    ) {
        public Route {
            Objects.requireNonNull(sourcePort, "sourcePort");
            Objects.requireNonNull(targetPort, "targetPort");
            waypoints = List.copyOf(waypoints);
            samples = List.copyOf(samples);
            if (samples.size() < 2) {
                throw new IllegalArgumentException("曲线至少需要两个采样点");
            }
        }

        /**
         * 按曲线每一点的局部法线生成平行轨道。正值位于行进方向左侧，负值位于右侧。
         */
        public List<Point> offsetSamples(double distance) {
            if (Math.abs(distance) <= EPSILON) return samples;
            List<Point> result = new ArrayList<>(samples.size());
            for (int index = 0; index < samples.size(); index++) {
                Point before = samples.get(Math.max(0, index - 1));
                Point after = samples.get(Math.min(samples.size() - 1, index + 1));
                Point tangent = after.subtract(before).normalizedOr(new Point(1.0D, 0.0D));
                Point normal = new Point(-tangent.y, tangent.x);
                result.add(samples.get(index).add(normal.scale(distance)));
            }
            return List.copyOf(result);
        }

        public double length() {
            return polylineLength(samples);
        }
    }

    public record Settings(
        double obstaclePadding,
        double portStem,
        double sampleSpacing,
        double cornerRadius
    ) {
        public Settings {
            if (!Double.isFinite(obstaclePadding) || obstaclePadding < 0.0D
                || !Double.isFinite(portStem) || portStem < 0.0D
                || !Double.isFinite(sampleSpacing) || sampleSpacing <= 0.0D
                || !Double.isFinite(cornerRadius) || cornerRadius < 0.0D) {
                throw new IllegalArgumentException("曲线路由设置包含无效数值");
            }
        }
    }

    private record PortPair(
        Port source,
        Port target,
        Point firstControl,
        Point secondControl,
        double score,
        double stemLength
    ) {
    }

    private record DirectCandidate(
        PortPair pair,
        List<Point> samples,
        int collisions,
        boolean endpointsSafe
    )
        implements Comparable<DirectCandidate> {
        @Override
        public int compareTo(DirectCandidate other) {
            if (endpointsSafe != other.endpointsSafe) return endpointsSafe ? -1 : 1;
            int collisionComparison = Integer.compare(collisions, other.collisions);
            if (collisionComparison != 0) return collisionComparison;
            return Double.compare(pair.score, other.pair.score);
        }
    }

    private record Detour(List<Point> waypoints, List<Point> samples) {
    }

    private record PathState(int vertex, double distance) {
    }

    private record ClipInterval(double minimum, double maximum) {
    }
}
