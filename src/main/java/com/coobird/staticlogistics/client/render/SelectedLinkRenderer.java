package com.coobird.staticlogistics.client.render;

import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.client.data.ClientLinkData;
import com.coobird.staticlogistics.client.data.SelectedWorldLinks;
import com.coobird.staticlogistics.client.gui.screen.LinkConfiguratorScreen;
import com.coobird.staticlogistics.content.item.BlueprintModeHandler;
import com.coobird.staticlogistics.content.item.LinkConfiguratorItem;
import com.coobird.staticlogistics.logistics.node.ConnectionKey;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import org.joml.Matrix4f;

import java.util.*;

/// 只绘制右侧目录选中的连接，左侧节点配置不改变渲染范围。
@EventBusSubscriber(modid = StaticLogistics.MODID, value = Dist.CLIENT)
public final class SelectedLinkRenderer {
    private SelectedLinkRenderer() {
    }

    private static boolean cachedBlueprintMode;

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        Minecraft mc = Minecraft.getInstance();
        if (cachedLevel != mc.level) {
            clear();
            cachedLevel = mc.level;
        }
        boolean blueprintMode = mc.screen instanceof LinkConfiguratorScreen screen && screen.isBlueprintPage()
            || mc.player != null && !BlueprintModeHandler.held(mc.player).isEmpty();
        var selection = blueprintMode ? SelectedWorldLinks.blueprint() : SelectedWorldLinks.get();
        if (mc.level == null || mc.player == null || selection.isEmpty()) {
            if (!nodes.isEmpty()) clear();
            return;
        }
        var stack = mc.player.getMainHandItem();
        if (!(stack.getItem() instanceof LinkConfiguratorItem)) stack = mc.player.getOffhandItem();
        if (!(stack.getItem() instanceof LinkConfiguratorItem)) return;
        if (cachedTopology != ClientLinkData.INSTANCE.getDataVersion() || cachedSelection != SelectedWorldLinks.version()
            || cachedBlueprintMode != blueprintMode) {
            rebuild(selection);
            cachedBlueprintMode = blueprintMode;
        }
        var pose = event.getPoseStack();
        Vec3 camera = event.getCamera().getPosition();
        float tick = event.getPartialTick();
        var buffers = mc.renderBuffers().bufferSource();
        VertexConsumer buffer = buffers.getBuffer(StoredSelectionRenderer.PIPE_XRAY);
        pose.pushPose();
        Matrix4f matrix = pose.last().pose();
        long gameTick = mc.level.getGameTime();
        scheduleMaintenance(gameTick);
        maintainNodes(mc.level, tick, gameTick);
        maintainRoutes();
        FrameBudget dynamicRouteBudget = new FrameBudget();
        List<RouteGeometry> immediateSnapshot = List.copyOf(immediateRoutes);
        if (!immediateSnapshot.isEmpty()) {
            int start = Math.floorMod(dynamicRouteCursor, immediateSnapshot.size());
            int visited = 0;
            while (visited < immediateSnapshot.size()) {
                RouteGeometry route = immediateSnapshot.get((start + visited) % immediateSnapshot.size());
                if (!route.first.loaded || !route.second.loaded
                    || route.first.shape == null || route.second.shape == null) {
                    visited++;
                    continue;
                }
                if (route.needsUpdate() && !dynamicRouteBudget.acquire()) break;
                route.update();
                route.syncBatch();
                visited++;
            }
            dynamicRouteCursor = (start + Math.max(1, visited)) % immediateSnapshot.size();
        } else dynamicRouteCursor = 0;
        var immediateIterator = immediateRoutes.iterator();
        while (immediateIterator.hasNext()) {
            RouteGeometry route = immediateIterator.next();
            if (!route.first.loaded || !route.second.loaded
                || route.first.shape == null || route.second.shape == null) {
                route.releaseBatch();
                immediateIterator.remove();
                continue;
            }
            if (route.gpuEligible()) {
                immediateIterator.remove();
                continue;
            }
            /// 按整条线段裁剪；即使两端都在屏幕外，连线仍可能穿过视野。
            if (route.forward == null || !event.getFrustum().isVisible(route.bounds)) continue;
            route.forward.drawRelative(buffer, matrix, camera);
            if (route.backward != null) route.backward.drawRelative(buffer, matrix, camera);
        }
        buffers.endBatch(StoredSelectionRenderer.PIPE_XRAY);
        FrameBudget uploadBudget = new FrameBudget();
        StoredSelectionRenderer.PIPE_XRAY.setupRenderState();
        try {
            for (OverlayGpuBatch batch : batches)
                if (batch.bounds != null && event.getFrustum().isVisible(batch.bounds)
                    && (batch.uploaded() || uploadBudget.acquire()))
                    batch.draw(event.getPoseStack().last().pose(), event.getProjectionMatrix(), camera);
        } finally {
            StoredSelectionRenderer.PIPE_XRAY.clearRenderState();
            pose.popPose();
        }
    }

    private static boolean visible(Level level, LogisticsNode node) {
        return level.dimension().equals(node.gPos().dimension())
            && level.getChunkSource().hasChunk(
            node.gPos().pos().getX() >> 4, node.gPos().pos().getZ() >> 4);
    }

    private static LogisticsNode physical(LogisticsNode node) {
        return new LogisticsNode(node.gPos(), node.face());
    }

    private record Route(LogisticsNode first, LogisticsNode second) {
    }

    private record Geometry(Vec3 center, Vec3 normal, Vec3 axis1, Vec3 axis2) {
    }

    private static final class FrameBudget {
        private final long start = System.nanoTime();
        private int work;

        boolean acquire() {
            if (work >= 32 || (work > 0 && System.nanoTime() - start >= 1_000_000L)) return false;
            work++;
            return true;
        }
    }

    private static final Set<OverlayGpuBatch> batches = new LinkedHashSet<>();
    private static final Map<BlockPos, LinkedHashSet<OverlayGpuBatch>> available = new LinkedHashMap<>();

    /// 保持批次归属稳定：单个几何变化只使其所在批次失效，不重建整个场景。
    private static final class BatchEntry {
        private LogisticsRenderHelper.LinkMesh mesh;
        private OverlayGpuBatch batch;
        private BlockPos origin;

        void update(LogisticsRenderHelper.LinkMesh next, AABB bounds, Vec3 position) {
            if (mesh == next) return;
            release();
            if (next == null || next.vertices().length == 0) return;
            origin = new BlockPos((int) Math.floor(position.x / 32) * 32,
                (int) Math.floor(position.y / 32) * 32, (int) Math.floor(position.z / 32) * 32);
            var candidates = available.computeIfAbsent(origin, ignored -> new LinkedHashSet<>());
            if (candidates.isEmpty()) {
                batch = new OverlayGpuBatch(Vec3.atLowerCornerOf(origin));
                batches.add(batch);
                candidates.add(batch);
            } else batch = candidates.iterator().next();
            mesh = next;
            batch.add(mesh, bounds);
            if (batch.meshes.size() >= 64) candidates.remove(batch);
        }

        void release() {
            if (batch == null) return;
            batch.remove(mesh);
            if (batch.meshes.isEmpty()) {
                batches.remove(batch);
                var candidates = available.get(origin);
                if (candidates != null) {
                    candidates.remove(batch);
                    if (candidates.isEmpty()) available.remove(origin);
                }
            } else available.computeIfAbsent(origin, ignored -> new LinkedHashSet<>()).add(batch);
            batch = null;
            mesh = null;
            origin = null;
        }
    }

    @SubscribeEvent
    public static void disconnected(ClientPlayerNetworkEvent.LoggingOut event) {
        if (RenderSystem.isOnRenderThread()) {
            clear();
            cachedLevel = null;
        } else RenderSystem.recordRenderCall(() -> {
            clear();
            cachedLevel = null;
        });
    }

    private static Level cachedLevel;
    private static final Map<LogisticsNode, NodeGeometry> nodes = new LinkedHashMap<>();
    private static final ArrayList<RouteGeometry> routes = new ArrayList<>();
    private static List<NodeGeometry> nodeOrder = List.of();
    private static final Set<RouteGeometry> immediateRoutes = new LinkedHashSet<>();
    private static int nodeCursor, routeCursor;
    private static int dynamicRouteCursor;
    private static int nodeMaintenanceRemaining, routeMaintenanceRemaining;
    private static long maintenanceTick = Long.MIN_VALUE;

    private static void scheduleMaintenance(long gameTick) {
        if (maintenanceTick == gameTick) return;
        maintenanceTick = gameTick;
        if (nodeMaintenanceRemaining == 0) nodeMaintenanceRemaining = nodeOrder.size();
        if (routeMaintenanceRemaining == 0) routeMaintenanceRemaining = routes.size();
    }

    private static void maintainNodes(Level level, float partialTick, long gameTick) {
        if (nodeMaintenanceRemaining == 0 || nodeOrder.isEmpty()) return;
        FrameBudget budget = new FrameBudget();
        int inspected = 0;
        while (nodeMaintenanceRemaining > 0 && inspected++ < 256) {
            NodeGeometry node = nodeOrder.get(nodeCursor);
            boolean previousLoaded = node.loaded;
            Geometry previousShape = node.shape;
            if (!node.update(level, partialTick, gameTick, budget)) break;
            node.syncBatch();

            if (previousLoaded != node.loaded || previousShape != node.shape) {
                routeMaintenanceRemaining = routes.size();
            }
            nodeCursor = (nodeCursor + 1) % nodeOrder.size();
            nodeMaintenanceRemaining--;
        }
    }

    private static void maintainRoutes() {
        if (routeMaintenanceRemaining == 0 || routes.isEmpty()) return;
        FrameBudget budget = new FrameBudget();
        int inspected = 0;
        while (routeMaintenanceRemaining > 0 && inspected++ < 256) {
            RouteGeometry route = routes.get(routeCursor);
            if (!route.first.loaded || !route.second.loaded
                || route.first.shape == null || route.second.shape == null) {
                route.releaseBatch();
                immediateRoutes.remove(route);
            } else {
                if (route.needsUpdate() && !budget.acquire()) break;
                route.update();
                route.syncBatch();
                if (route.forward != null && !route.gpuEligible()) immediateRoutes.add(route);
                else immediateRoutes.remove(route);
            }
            routeCursor = (routeCursor + 1) % routes.size();
            routeMaintenanceRemaining--;
        }
    }

    private static void clear() {
        batches.forEach(OverlayGpuBatch::close);
        batches.clear();
        available.clear();
        cachedRoutes.clear();
        cachedFaces.clear();
        nodes.clear();
        nodeOrder = List.of();
        immediateRoutes.clear();
        nodeCursor = routeCursor = 0;
        dynamicRouteCursor = 0;
        nodeMaintenanceRemaining = routeMaintenanceRemaining = 0;
        maintenanceTick = Long.MIN_VALUE;
        routes.clear();
        cachedTopology = -1;
        cachedSelection = -1;
    }

    private static final class NodeGeometry {
        private final LogisticsNode node;
        private final int role;
        private long checkedTick = Long.MIN_VALUE;
        private boolean loaded;
        private boolean geometryDirty = true;
        private Geometry shape;
        private AABB bounds;
        private LogisticsRenderHelper.LinkMesh inputFace, outputFace;
        private final BatchEntry inputBatch = new BatchEntry(), outputBatch = new BatchEntry();

        private void syncBatch() {
            boolean visible = loaded && shape != null;
            inputBatch.update(visible ? inputFace : null, bounds, visible ? shape.center() : null);
            outputBatch.update(visible ? outputFace : null, bounds, visible ? shape.center() : null);
        }

        private void releaseBatch() {
            inputBatch.release();
            outputBatch.release();
        }

        private NodeGeometry(LogisticsNode node, int role) {
            this.node = node;
            this.role = role;
        }

        private boolean update(Level level, float partialTick, long gameTick, FrameBudget budget) {
            if (checkedTick != gameTick) {
                checkedTick = gameTick;
                boolean previouslyLoaded = loaded;
                loaded = visible(level, node);
                if (!loaded) return true;
                geometryDirty |= !previouslyLoaded;
            }
            if (!loaded || (!geometryDirty)) return true;
            if (!budget.acquire()) return false;
            Geometry updated = geometry(level, node, partialTick);
            geometryDirty = false;
            if (!updated.equals(shape)) {
                shape = updated;
                boolean both = (role & 3) == 3;
                if ((role & 2) != 0) inputFace = LogisticsRenderHelper.buildFaceMesh(shape.center(), shape.normal(),
                    shape.axis1(), shape.axis2(), LogisticsRenderHelper.INPUT_COLOR, both ? -0.5F : 0, both ? 0.45F : 1);
                if ((role & 1) != 0) outputFace = LogisticsRenderHelper.buildFaceMesh(shape.center(), shape.normal(),
                    shape.axis1(), shape.axis2(), LogisticsRenderHelper.OUTPUT_COLOR, both ? 0.5F : 0, both ? 0.45F : 1);
                Vec3 center = shape.center();
                bounds = new AABB(center.x - 0.6, center.y - 0.6, center.z - 0.6,
                    center.x + 0.6, center.y + 0.6, center.z + 0.6);
            }
            return true;
        }
    }

    private static final class RouteGeometry {
        private final NodeGeometry first, second;
        private final int directions;
        private Geometry previousFirst, previousSecond;
        private AABB bounds;
        private LogisticsRenderHelper.LinkMesh forward, backward;
        private final BatchEntry forwardBatch = new BatchEntry(), backwardBatch = new BatchEntry();

        private void syncBatch() {
            boolean eligible = gpuEligible();
            forwardBatch.update(eligible ? forward : null, bounds, eligible ? first.shape.center() : null);
            backwardBatch.update(eligible ? backward : null, bounds, eligible ? first.shape.center() : null);
        }

        private void releaseBatch() {
            forwardBatch.release();
            backwardBatch.release();
        }

        private RouteGeometry(NodeGeometry first, NodeGeometry second, int directions) {
            this.first = first;
            this.second = second;
            this.directions = directions;
        }

        private boolean gpuEligible() {
            /// 超长连接使用相机相对坐标，避免远端坐标过大导致单精度细节丢失。
            return bounds != null
                && bounds.getXsize() <= 2048 && bounds.getYsize() <= 2048 && bounds.getZsize() <= 2048;
        }

        private boolean needsUpdate() {
            return first.shape != previousFirst || second.shape != previousSecond;
        }

        private void update() {
            Geometry a = first.shape, b = second.shape;
            if (a == previousFirst && b == previousSecond) return;
            previousFirst = a;
            previousSecond = b;
            bounds = new AABB(a.center(), b.center()).inflate(0.3);
            List<LogisticsRenderHelper.LinkMesh> meshes = LogisticsRenderHelper.buildConnectionMeshes(
                a.center(), b.center(), a.axis2(), b.axis2(), directions, 0.85F);
            forward = meshes.get(0);
            backward = meshes.size() > 1 ? meshes.get(1) : null;
        }
    }

    private static int cachedTopology = -1;
    private static long cachedSelection = -1;
    private static final Map<Route, Integer> cachedRoutes = new LinkedHashMap<>();
    private static final Map<LogisticsNode, Integer> cachedFaces = new LinkedHashMap<>();

    private static Geometry geometry(Level level, LogisticsNode node, float tick) {
        Vec3 normal = Vec3.atLowerCornerOf(node.face().getNormal());
        return new Geometry(Vec3.atCenterOf(node.gPos().pos()).add(normal.scale(0.515)), normal,
            LogisticsRenderHelper.faceAxis1(node.face()),
            LogisticsRenderHelper.faceAxis2(node.face()));
    }

    private static void rebuild(Set<ConnectionKey> selected) {
        cachedRoutes.clear();
        cachedFaces.clear();
        selected.stream().map(key -> key.groupKey()).distinct().forEach(group -> {
            for (var link : ClientLinkData.INSTANCE.getConnectionRoutes(group)) {
                if (!selected.contains(link.key())) continue;
                LogisticsNode a = physical(link.first()), b = physical(link.second());
                boolean reverse = ConnectionKey.NODE_ORDER.compare(a, b) > 0;
                Route route = reverse ? new Route(b, a) : new Route(a, b);
                int direction = (link.transfersFirstToSecond() ? (reverse ? 2 : 1) : 0)
                    | (link.transfersSecondToFirst() ? (reverse ? 1 : 2) : 0);
                cachedRoutes.merge(route, direction, (old, added) -> old | added);
                var first = link.firstTopology().role();
                var second = link.secondTopology().role();
                cachedFaces.merge(a, (first.canSend() ? 1 : 0) | (first.canReceive() ? 2 : 0), (old, added) -> old | added);
                cachedFaces.merge(b, (second.canSend() ? 1 : 0) | (second.canReceive() ? 2 : 0), (old, added) -> old | added);
            }
        });
        Map<LogisticsNode, NodeGeometry> previousNodes = new LinkedHashMap<>(nodes);
        Map<Route, RouteGeometry> previousRoutes = new LinkedHashMap<>();
        for (RouteGeometry route : routes) previousRoutes.put(new Route(route.first.node, route.second.node), route);
        nodes.clear();
        routes.clear();
        cachedFaces.forEach((node, role) -> {
            NodeGeometry previous = previousNodes.get(node);
            nodes.put(node, previous != null && previous.role == role ? previous : new NodeGeometry(node, role));
        });
        cachedRoutes.forEach((route, directions) -> {
            NodeGeometry a = nodes.get(route.first()), b = nodes.get(route.second());
            RouteGeometry previous = previousRoutes.remove(route);
            if (previous != null && (previous.first != a || previous.second != b || previous.directions != directions))
                previous.releaseBatch();
            routes.add(previous != null && previous.first == a && previous.second == b && previous.directions == directions
                ? previous : new RouteGeometry(a, b, directions));
        });
        previousNodes.forEach((node, previous) -> {
            if (nodes.get(node) != previous) previous.releaseBatch();
        });
        previousRoutes.values().forEach(RouteGeometry::releaseBatch);
        nodeOrder = List.copyOf(nodes.values());
        immediateRoutes.retainAll(routes);
        nodeCursor = routeCursor = 0;
        dynamicRouteCursor = 0;
        nodeMaintenanceRemaining = nodeOrder.size();
        routeMaintenanceRemaining = routes.size();
        maintenanceTick = Long.MIN_VALUE;
        cachedTopology = ClientLinkData.INSTANCE.getDataVersion();
        cachedSelection = SelectedWorldLinks.version();
    }
}
