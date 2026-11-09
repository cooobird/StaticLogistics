package com.coobird.staticlogistics.client.gui.component;

import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.client.data.ClientConnection;
import com.coobird.staticlogistics.client.render.GuiBlockModel;
import com.coobird.staticlogistics.client.render.LogisticsRenderHelper;
import com.coobird.staticlogistics.client.render.StoredSelectionRenderer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.*;

/// 方块的绘制、框选和拾取共用同一相机变换；不改变世界或建链选择。
public final class BlueprintBlockViewport {
    private List<BlockPos> positions = List.of();
    private final LinkedHashSet<BlockPos> selected = new LinkedHashSet<>();
    private final Set<BlockPos> dragBase = new LinkedHashSet<>();
    private final List<Projected> projected = new ArrayList<>();

    private record Link(LogisticsNode first, LogisticsNode second) {
    }

    private final Map<Link, Integer> links = new LinkedHashMap<>();
    private final Map<LogisticsNode, Integer> faceRoles = new LinkedHashMap<>();
    private List<LogisticsRenderHelper.LinkMesh> connectionMeshes = List.of();
    private List<LogisticsRenderHelper.LinkMesh> faceMeshes = List.of();
    private boolean topologyGeometryDirty = true;
    private Set<BlockPos> available = Set.of();
    private final Map<GlobalPos, Matrix4f> models = new LinkedHashMap<>();

    private record Camera(int x, int y, int width, int height, float yaw, float pitch, float zoom, float panX,
                          float panY) {
    }

    private Camera camera;
    private float projectedMinX, projectedMaxX, projectedMinY, projectedMaxY, linkRadius;
    private double hitX = Double.NaN, hitY = Double.NaN;
    private Projected hovered;

    public void setConnections(List<ClientConnection> connections) {
        links.clear();
        faceRoles.clear();
        for (var connection : connections) {
            var key = new Link(connection.first().connectionFace(), connection.second().connectionFace());
            int directions = (connection.transfersFirstToSecond() ? 1 : 0) | (connection.transfersSecondToFirst() ? 2 : 0);
            links.merge(key, directions, (a, b) -> a | b);
            faceRoles.merge(key.first, (connection.firstTopology().role().canReceive() ? 1 : 0) | (connection.firstTopology().role().canSend() ? 2 : 0), (a, b) -> a | b);
            faceRoles.merge(key.second, (connection.secondTopology().role().canReceive() ? 1 : 0) | (connection.secondTopology().role().canSend() ? 2 : 0), (a, b) -> a | b);
        }
        topologyGeometryDirty = true;
    }

    public boolean isIdleEmptySpace(double mx, double my) {
        return gesture < 0 && contains(mx, my) && hovered(mx, my) == null;
    }

    private BlockPos anchor;
    private IntSummaryStatistics xs = new IntSummaryStatistics(), ys = new IntSummaryStatistics(), zs = new IntSummaryStatistics();
    private float yaw = 45, pitch = 30, zoom = 1, panX, panY;
    private int x, y, width, height, gesture = -1;
    private double startX, startY, endX, endY;
    private boolean additive;

    private record Point(float x, float y) {
    }

    private record Projected(BlockPos pos, Matrix4f matrix, List<Point> hull, float cx, float cy, float depth) {
    }

    public record ViewState(Set<BlockPos> selected, BlockPos anchor, float yaw, float pitch, float zoom, float panX,
                            float panY) {
    }

    public ViewState saveView() {
        return new ViewState(Set.copyOf(selected), anchor, yaw, pitch, zoom, panX, panY);
    }

    public void restoreView(ViewState view) {
        restoreSelection(List.copyOf(view.selected()), view.anchor());
        yaw = view.yaw();
        pitch = view.pitch();
        zoom = view.zoom();
        panX = view.panX();
        panY = view.panY();
        camera = null;
    }

    public void setBlocks(List<BlockPos> blocks, boolean reset) {
        positions = List.copyOf(blocks);
        available = Set.copyOf(blocks);
        camera = null;
        models.clear();
        topologyGeometryDirty = true;
        xs = new IntSummaryStatistics();
        ys = new IntSummaryStatistics();
        zs = new IntSummaryStatistics();
        positions.forEach(pos -> {
            xs.accept(pos.getX());
            ys.accept(pos.getY());
            zs.accept(pos.getZ());
        });
        if (reset) {
            selected.clear();
            anchor = null;
            yaw = 45;
            pitch = 30;
            zoom = 1;
            panX = 0;
            panY = 0;
        }
        selected.retainAll(positions);
        if (!selected.contains(anchor)) anchor = selected.stream().findFirst().orElse(null);
        gesture = -1;
        projected.clear();
        hitX = Double.NaN;
    }

    public void restoreSelection(List<BlockPos> blocks, BlockPos anchor) {
        selected.clear();
        selected.addAll(blocks);
        selected.retainAll(positions);
        this.anchor = selected.contains(anchor) ? anchor : selected.stream().findFirst().orElse(null);
        topologyGeometryDirty = true;
    }

    public Set<BlockPos> selection() {
        return Set.copyOf(selected);
    }

    public BlockPos anchor() {
        return anchor;
    }

    public void selectAll() {
        selected.clear();
        selected.addAll(positions);
        anchor = positions.isEmpty() ? null : positions.get(0);
        topologyGeometryDirty = true;
    }

    public void resetView() {
        yaw = 45;
        pitch = 30;
        zoom = 1;
        panX = 0;
        panY = 0;
        camera = null;
    }

    public boolean contains(double mx, double my) {
        return mx >= x && mx < x + width && my >= y && my < y + height;
    }

    public void render(GuiGraphics graphics, int x, int y, int width, int height, double interfaceScale) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        Camera nextCamera = new Camera(x, y, width, height, yaw, pitch, zoom, panX, panY);
        boolean rebuild = !nextCamera.equals(camera);
        camera = nextCamera;
        if (rebuild) {
            projected.clear();
            models.clear();
            hitX = Double.NaN;
        }
        if (positions.isEmpty() || Minecraft.getInstance().level == null) return;
        int minX = xs.getMin(), maxX = xs.getMax(), minY = ys.getMin(), maxY = ys.getMax(), minZ = zs.getMin(), maxZ = zs.getMax();
        double centerX = ((double) minX + maxX + 1) / 2, centerY = ((double) minY + maxY + 1) / 2, centerZ = ((double) minZ + maxZ + 1) / 2;
        Matrix4f rotation = new Matrix4f().rotate(Axis.XP.rotationDegrees(pitch)).rotate(Axis.YP.rotationDegrees(yaw));
        if (rebuild) {
            projectedMinX = projectedMinY = Float.POSITIVE_INFINITY;
            projectedMaxX = projectedMaxY = Float.NEGATIVE_INFINITY;
            for (BlockPos block : positions) {
                for (int corner = 0; corner < 8; corner++) {
                    Vector3f point = rotation.transformPosition(new Vector3f((float) ((block.getX() + corner % 2) - centerX),
                        (float) ((block.getY() + corner / 2 % 2) - centerY), (float) ((block.getZ() + corner / 4) - centerZ)));
                    projectedMinX = Math.min(projectedMinX, point.x);
                    projectedMaxX = Math.max(projectedMaxX, point.x);
                    projectedMinY = Math.min(projectedMinY, point.y);
                    projectedMaxY = Math.max(projectedMaxY, point.y);
                }
            }
        }
        float scale = Math.min((width - 24) / Math.max(1, projectedMaxX - projectedMinX),
            (height - 24) / Math.max(1, projectedMaxY - projectedMinY)) * zoom;
        float radius = Math.max(0.008F, 0.45F / Math.max(0.01F, scale));
        if (radius != linkRadius) {
            linkRadius = radius;
            topologyGeometryDirty = true;
        }
        Matrix4f scene = new Matrix4f().translation(x + width / 2f + panX - (projectedMinX + projectedMaxX) * scale / 2,
            y + height / 2f + panY + (projectedMinY + projectedMaxY) * scale / 2, 400).scale(scale, -scale, scale).mul(rotation);
        GuiScissor.enable(graphics, interfaceScale, x, y, x + width, y + height);
        try {
            float floor = (float) (minY - centerY - 0.02);
            int span = Math.min(24, Math.max(maxX - minX, maxZ - minZ) + 4);
            int gridStep = scale < 3 ? 5 : 1;
            for (int grid = -span; grid <= span; grid++) {
                if (grid % gridStep != 0) continue;
                int color = grid == 0 ? 0xFF72797A : grid % 5 == 0 ? 0xFF7B8080 : 0xFF858989;
                drawProjectedLine(graphics, scene, new Vector3f(grid, floor, -span), new Vector3f(grid, floor, span), color);
                drawProjectedLine(graphics, scene, new Vector3f(-span, floor, grid), new Vector3f(span, floor, grid), color);
            }
            if (rebuild) {
                for (BlockPos pos : positions) {
                    Matrix4f matrix = new Matrix4f(scene).translate((float) (pos.getX() - centerX), (float) (pos.getY() - centerY), (float) (pos.getZ() - centerZ));
                    List<Point> corners = new ArrayList<>();
                    for (int corner = 0; corner < 8; corner++) {
                        Vector3f point = matrix.transformPosition(new Vector3f(corner % 2, corner / 2 % 2, corner / 4));
                        corners.add(new Point(point.x, point.y));
                    }
                    if (corners.stream().allMatch(p -> p.x < x) || corners.stream().allMatch(p -> p.x > x + width)
                        || corners.stream().allMatch(p -> p.y < y) || corners.stream().allMatch(p -> p.y > y + height))
                        continue;
                    Vector3f center = matrix.transformPosition(new Vector3f(0.5f));
                    projected.add(new Projected(pos, matrix, hull(corners), center.x, center.y, center.z));
                }
                projected.sort(Comparator.comparingDouble(Projected::depth));
                for (Projected block : projected)
                    models.put(GlobalPos.of(Minecraft.getInstance().level.dimension(), block.pos), block.matrix);
            }
            GuiBlockModel.renderScene(graphics, models);
            renderConnections(graphics, scene, centerX, centerY, centerZ);
            graphics.pose().pushPose();
            graphics.pose().translate(0, 0, 800);
            for (Projected block : projected)
                if (selected.contains(block.pos)) {
                    int color = block.pos.equals(anchor) ? 0xFFFFD278 : 0xFF9EE875;
                    for (int edge = 0; edge < block.hull.size(); edge++)
                        line(graphics, block.hull.get(edge), block.hull.get((edge + 1) % block.hull.size()), color);
                }
            if (gesture == 0 && Math.hypot(endX - startX, endY - startY) > 3) {
                int left = (int) Math.min(startX, endX), top = (int) Math.min(startY, endY);
                int right = (int) Math.max(startX, endX), bottom = (int) Math.max(startY, endY);
                graphics.fill(left, top, right, bottom, 0x224E9E40);
                graphics.renderOutline(left, top, Math.max(1, right - left), Math.max(1, bottom - top), 0xFF9EE875);
            }
            graphics.pose().popPose();
        } finally {
            graphics.flush();
            graphics.disableScissor();
        }
    }

    public boolean mouseClicked(double mx, double my, int button) {
        if (!contains(mx, my) || button > 2) return false;
        gesture = button;
        startX = endX = mx;
        startY = endY = my;
        additive = Screen.hasControlDown() || Screen.hasShiftDown();
        dragBase.clear();
        if (additive) dragBase.addAll(selected);
        return true;
    }

    public boolean mouseDragged(double mx, double my, double dx, double dy) {
        if (gesture < 0) return false;
        endX = Mth.clamp(mx, x, x + width);
        endY = Mth.clamp(my, y, y + height);
        if (gesture == 1) {
            yaw += (float) dx * 0.6f;
            pitch = Mth.clamp(pitch + (float) dy * 0.6f, -80, 80);
        } else if (gesture == 2) {
            panX = Mth.clamp(panX + (float) dx, -width, width);
            panY = Mth.clamp(panY + (float) dy, -height, height);
        } else if (Math.hypot(endX - startX, endY - startY) > 3) {
            selected.clear();
            selected.addAll(dragBase);
            for (Projected block : projected)
                if (block.cx >= Math.min(startX, endX) && block.cx <= Math.max(startX, endX)
                    && block.cy >= Math.min(startY, endY) && block.cy <= Math.max(startY, endY))
                    selected.add(block.pos);
            chooseAnchor();
            topologyGeometryDirty = true;
        }
        return true;
    }

    public boolean mouseReleased(double mx, double my, int button) {
        if (gesture < 0 || button != gesture) return false;
        if (gesture == 0 && Math.hypot(mx - startX, my - startY) <= 3) {
            Projected hit = hovered(mx, my);
            if (hit != null && !additive && selected.contains(hit.pos)) anchor = hit.pos;
            else {
                if (!additive) selected.clear();
                if (hit != null) {
                    if (!selected.add(hit.pos)) selected.remove(hit.pos);
                    else anchor = hit.pos;
                }
                chooseAnchor();
                topologyGeometryDirty = true;
            }
        }
        gesture = -1;
        return true;
    }

    public boolean scroll(double mx, double my, double amount) {
        if (!contains(mx, my)) return false;
        zoom = Mth.clamp(zoom * (float) Math.pow(1.12, amount), 0.25f, 8);
        return true;
    }

    private void chooseAnchor() {
        if (selected.contains(anchor)) return;
        anchor = projected.stream().filter(block -> selected.contains(block.pos))
            .min(Comparator.comparingDouble(block -> Math.hypot(block.cx - startX, block.cy - startY))).map(Projected::pos).orElse(null);
    }

    private void renderConnections(GuiGraphics graphics, Matrix4f scene, double cx, double cy, double cz) {
        if (topologyGeometryDirty) rebuildTopologyGeometry(cx, cy, cz);
        graphics.flush();
        var buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        var buffer = buffers.getBuffer(StoredSelectionRenderer.PIPE_XRAY);
        Matrix4f matrix = new Matrix4f(graphics.pose().last().pose()).mul(scene);
        faceMeshes.forEach(mesh -> mesh.draw(buffer, matrix));
        connectionMeshes.forEach(mesh -> mesh.draw(buffer, matrix));
        buffers.endBatch(StoredSelectionRenderer.PIPE_XRAY);
    }

    private void rebuildTopologyGeometry(double cx, double cy, double cz) {
        List<LogisticsRenderHelper.LinkMesh> nextFaces = new ArrayList<>();
        faceRoles.forEach((node, role) -> {
            if (!available.contains(node.gPos().pos())) return;
            Vec3 normal = Vec3.atLowerCornerOf(node.face().getNormal());
            Vec3 seed = Math.abs(normal.y) > 0.5 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
            Vec3 axis2 = normal.cross(seed).normalize();
            Vec3 axis1 = normal.cross(axis2).normalize();
            boolean input = (role & 1) != 0;
            boolean output = (role & 2) != 0;
            if (input) nextFaces.add(LogisticsRenderHelper.buildFaceMesh(
                endpoint(node, cx, cy, cz), normal, axis1, axis2,
                LogisticsRenderHelper.INPUT_COLOR, output ? -0.5F : 0, output ? 0.45F : 1));
            if (output) nextFaces.add(LogisticsRenderHelper.buildFaceMesh(
                endpoint(node, cx, cy, cz), normal, axis1, axis2,
                LogisticsRenderHelper.OUTPUT_COLOR, input ? 0.5F : 0, input ? 0.45F : 1));
        });
        List<LogisticsRenderHelper.LinkMesh> nextConnections = new ArrayList<>();
        links.forEach((link, directions) -> {
            if (!available.contains(link.first.gPos().pos()) || !available.contains(link.second.gPos().pos())) return;
            boolean highlighted = selected.isEmpty() || selected.contains(link.first.gPos().pos()) || selected.contains(link.second.gPos().pos());
            nextConnections.addAll(LogisticsRenderHelper.buildConnectionMeshes(
                endpoint(link.first, cx, cy, cz), endpoint(link.second, cx, cy, cz),
                axis(link.first.face()), axis(link.second.face()), directions,
                highlighted ? 0.45F : 0.12F, linkRadius));
        });
        faceMeshes = List.copyOf(nextFaces);
        connectionMeshes = List.copyOf(nextConnections);
        topologyGeometryDirty = false;
    }

    private static Vec3 endpoint(LogisticsNode node, double cx, double cy, double cz) {
        return Vec3.atCenterOf(node.gPos().pos()).subtract(cx, cy, cz).add(Vec3.atLowerCornerOf(node.face().getNormal()).scale(0.515));
    }

    private static Vec3 axis(Direction face) {
        Vec3 normal = Vec3.atLowerCornerOf(face.getNormal());
        return normal.cross(Math.abs(normal.y) > 0.5 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0)).normalize();
    }

    private Projected hovered(double mx, double my) {
        if (mx == hitX && my == hitY) return hovered;
        hitX = mx;
        hitY = my;
        hovered = null;
        for (int i = projected.size() - 1; i >= 0; i--) {
            Projected block = projected.get(i);
            if (inside(block.hull, mx, my)) {
                hovered = block;
                break;
            }
        }
        return hovered;
    }

    private static boolean inside(List<Point> hull, double x, double y) {
        if (hull.size() < 3) return false;
        for (int i = 0; i < hull.size(); i++) {
            Point a = hull.get(i), b = hull.get((i + 1) % hull.size());
            if ((b.x - a.x) * (y - a.y) - (b.y - a.y) * (x - a.x) < 0) return false;
        }
        return true;
    }

    private static void drawProjectedLine(GuiGraphics g, Matrix4f matrix, Vector3f from, Vector3f to, int color) {
        matrix.transformPosition(from);
        matrix.transformPosition(to);
        line(g, new Point(from.x, from.y), new Point(to.x, to.y), color);
    }

    private static void line(GuiGraphics g, Point from, Point to, int color) {
        float dx = to.x - from.x, dy = to.y - from.y;
        int length = (int) Math.ceil(Math.hypot(dx, dy));
        if (length == 0) return;
        g.pose().pushPose();
        try {
            g.pose().translate(from.x, from.y, 0);
            g.pose().mulPose(Axis.ZP.rotation((float) Math.atan2(dy, dx)));
            g.fill(0, 0, length, 1, color);
        } finally {
            g.pose().popPose();
        }
    }

    private static float cross(Point a, Point b, Point c) {
        return (b.x - a.x) * (c.y - a.y) - (b.y - a.y) * (c.x - a.x);
    }

    private static List<Point> hull(List<Point> input) {
        var points = input.stream().distinct().sorted(Comparator.comparingDouble(Point::x).thenComparingDouble(Point::y)).toList();
        List<Point> result = new ArrayList<>();
        for (Point p : points) {
            while (result.size() >= 2 && cross(result.get(result.size() - 2), result.get(result.size() - 1), p) <= 0)
                result.remove(result.size() - 1);
            result.add(p);
        }
        int lower = result.size();
        for (int i = points.size() - 2; i >= 0; i--) {
            Point p = points.get(i);
            while (result.size() > lower && cross(result.get(result.size() - 2), result.get(result.size() - 1), p) <= 0)
                result.remove(result.size() - 1);
            result.add(p);
        }
        if (result.size() > 1) result.remove(result.size() - 1);
        return result;
    }
}
