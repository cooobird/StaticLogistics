package com.coobird.staticlogistics.client.render;

import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.api.group.GroupKey;
import com.coobird.staticlogistics.client.data.*;
import com.coobird.staticlogistics.content.item.BlueprintItem;
import com.coobird.staticlogistics.content.item.LinkConfiguratorItem;
import com.coobird.staticlogistics.content.item.ToolMode;
import com.coobird.staticlogistics.logistics.node.ConnectionKey;
import com.coobird.staticlogistics.logistics.node.FaceTopology;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import java.util.*;

/**
 * 世界内链接渲染器 —— 持连接配置器/蓝图时，在世界中渲染物流网络的可视化。
 *
 * <p>渲染内容：
 * <ul>
 *   <li>存储节点高亮（选中的面 + 方块线框）</li>
 *   <li>选中组的所有节点：输入/输出面状态指示器</li>
 *   <li>链接流动粒子（从输出面流向输入面的彩色粒子）</li>
 * </ul>
 *
 * <p>使用 X-Ray 渲染（穿透方块），在 {@link RenderLevelStageEvent.Stage#AFTER_TRANSLUCENT_BLOCKS} 阶段绘制。
 * 只在手持连接配置器或蓝图时激活。
 */
@Mod.EventBusSubscriber(modid = StaticLogistics.MODID, value = Dist.CLIENT)
public class LinkWorldRenderer {
    /**
     * 保留连接最近出现过的方向和各方向动画时间，暂停后不会跳回起点。
     */
    private static final Map<ConnectionKey, FlowAnimationState> FLOW_STATES =
        new HashMap<>();
    private static Level flowStateLevel;
    public static final RenderType PIPE_XRAY = RenderType.create(
        "pipe_xray", DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 1536, false, false,
        RenderType.CompositeState.builder()
            .setShaderState(new RenderType.ShaderStateShard(GameRenderer::getPositionColorShader))
            .setTransparencyState(RenderType.TRANSLUCENT_TRANSPARENCY)
            .setWriteMaskState(RenderType.COLOR_WRITE)
            .setDepthTestState(RenderType.NO_DEPTH_TEST)
            .setCullState(RenderType.NO_CULL)
            .setLayeringState(RenderType.POLYGON_OFFSET_LAYERING)
            .createCompositeState(false)
    );

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        if (flowStateLevel != mc.level) {
            FLOW_STATES.clear();
            flowStateLevel = mc.level;
        }

        ItemStack stack = getActiveConfigurator(mc);
        if (stack.isEmpty()) return;

        LinkSelectionScope scope = SelectionContext.getSelectionScope(stack);
        GroupKey groupKey = scope == null ? null : scope.groupKey();
        if (groupKey == null) {
            groupKey = ClientLinkData.INSTANCE.resolveUniqueGroupKey(
                SelectionContext.getSelectedGroupId(stack));
        }
        ConnectionKey connectionKey = scope == null ? null : scope.connectionKey();

        PoseStack ps = event.getPoseStack();
        Vec3 cam = event.getCamera().getPosition();
        MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();
        VertexConsumer b = buf.getBuffer(PIPE_XRAY);
        ResourceKey<Level> dim = mc.level.dimension();
        WorldOverlayVisibility visibility = new WorldOverlayVisibility(mc.levelRenderer, event.getFrustum());

        ps.pushPose();
        ps.translate(-cam.x, -cam.y, -cam.z);
        Matrix4f mat = ps.last().pose();
        long frameTimeMillis = System.currentTimeMillis();
        float pulse = (float) Math.sin(frameTimeMillis / 200.0) * 0.03f;
        double flowTime = frameTimeMillis / 1000.0;
        ParticleStatus particleStatus = mc.options.particles().get();

        // 先绘制已有连接，最后覆盖当前存点，确保两种状态不会互相淹没。
        LinkConfiguratorItem.ToolSettings settings = stack.getItem() instanceof LinkConfiguratorItem lci
            ? lci.getSettings(stack) : null;
        if (groupKey != null) {
            renderGroupLinks(
                groupKey, connectionKey,
                dim, mat, b, visibility, pulse,
                flowTime, particleStatus);
            renderRedstoneControlPoint(groupKey, dim, mat, b, visibility);
        }
        if (settings != null && !settings.storedNodes().isEmpty() && settings.storedMode() != null)
            renderStoredNodes(settings, dim, mat, b, visibility, pulse);

        ps.popPose();
        buf.endBatch(PIPE_XRAY);
    }

    /**
     * 只渲染网络预览中当前选中控制组的检测点，并使用红石色线框。
     */
    private static void renderRedstoneControlPoint(
        GroupKey groupKey,
        ResourceKey<Level> dimension,
        Matrix4f matrix,
        VertexConsumer buffer,
        WorldOverlayVisibility visibility
    ) {
        ClientRedstoneControlData.ControlGroup control =
            ClientRedstoneControlData.INSTANCE.getSelectedControlGroup(groupKey);
        if (control == null
            || !control.binding().controller().dimension().equals(dimension)) return;
        BlockPos position = control.binding().controller().pos();
        if (!visibility.isBlockVisible(position)) return;
        float brightness = control.powered() ? 1.0F : 0.45F;
        LogisticsRenderHelper.drawFrame(buffer, matrix, position,
            brightness, 0.12F * brightness, 0.08F * brightness,
            control.powered() ? 0.62F : 0.34F);
    }

    private static void renderStoredNodes(LinkConfiguratorItem.ToolSettings settings,
                                          ResourceKey<Level> dim, Matrix4f mat, VertexConsumer b,
                                          WorldOverlayVisibility visibility, float pulse) {
        boolean isIn = settings.storedMode() == ToolMode.LINK_AS_INSERT;
        int faceColor = isIn
            ? LogisticsRenderHelper.INPUT_COLOR
            : LogisticsRenderHelper.OUTPUT_COLOR;

        for (LogisticsNode node : settings.storedNodes()) {
            if (!node.gPos().dimension().equals(dim)) continue;
            BlockPos p = node.gPos().pos();
            if (!visibility.isBlockVisible(p)) continue;

            LogisticsRenderHelper.drawFrame(b, mat, p, 1.0f, 0.95f, 0.2f, 0.48f);
            double px = p.getX() + 0.5 + node.face().getStepX() * 0.512;
            double py = p.getY() + 0.5 + node.face().getStepY() * 0.512;
            double pz = p.getZ() + 0.5 + node.face().getStepZ() * 0.512;
            LogisticsRenderHelper.drawFaceQuad(
                b, mat,
                px + node.face().getStepX() * 0.003,
                py + node.face().getStepY() * 0.003,
                pz + node.face().getStepZ() * 0.003,
                node.face(),
                faceColor, 0.58f, 0.30f + pulse * 0.35f, 0, 1f);
        }
    }

    private static void renderGroupLinks(
        GroupKey groupKey,
        ConnectionKey focusedConnection,
        ResourceKey<Level> dim,
        Matrix4f mat, VertexConsumer b,
        WorldOverlayVisibility visibility, float pulse,
        double flowTime, ParticleStatus particleStatus) {
        Set<BlockPos> renderedFrames = new HashSet<>();
        List<ClientConnection> connections = ClientLinkData.INSTANCE.getConnectionsForGroup(groupKey);
        Set<ConnectionKey> currentConnections = connections.stream()
            .map(ClientConnection::key).collect(java.util.stream.Collectors.toSet());
        FLOW_STATES.keySet().removeIf(key -> key.groupKey().equals(groupKey)
            && !currentConnections.contains(key));
        boolean wholeGroup = focusedConnection == null;
        if (focusedConnection != null) {
            ClientConnection focused = findConnection(
                connections, focusedConnection);
            if (focused != null) {
                renderConnection(
                    focused, dim, mat, b, visibility, pulse,
                    flowTime, particleStatus, renderedFrames,
                    new HashSet<>());
                return;
            }
        }

        Set<LogisticsNode> renderedFaces = new HashSet<>();
        for (ClientConnection connection : connections) {
            renderConnection(
                connection, dim, mat, b, visibility, pulse,
                flowTime, particleStatus, renderedFrames, renderedFaces);
        }
        if (wholeGroup) {
            for (LogisticsNode node :
                ClientLinkData.INSTANCE.getNodesForGroup(groupKey)) {
                FaceTopology topology = ClientLinkData.INSTANCE.getTopology(node);
                if (topology != null) {
                    renderEndpoint(node, topology, dim, mat, b, visibility,
                        pulse, renderedFrames, renderedFaces);
                }
            }
        }
    }

    private static ClientConnection findConnection(
        List<ClientConnection> connections,
        ConnectionKey key
    ) {
        for (ClientConnection connection : connections) {
            if (connection.key().equals(key)) return connection;
        }
        return null;
    }

    private static void renderConnection(
        ClientConnection connection,
        ResourceKey<Level> dimension,
        Matrix4f matrix,
        VertexConsumer buffer,
        WorldOverlayVisibility visibility,
        float pulse,
        double flowTime,
        ParticleStatus particleStatus,
        Set<BlockPos> renderedFrames,
        Set<LogisticsNode> renderedFaces
    ) {
        renderEndpoint(connection.first(), connection.firstTopology(),
            dimension, matrix, buffer, visibility, pulse,
            renderedFrames, renderedFaces);
        renderEndpoint(connection.second(), connection.secondTopology(),
            dimension, matrix, buffer, visibility, pulse,
            renderedFrames, renderedFaces);
        renderConnectionFlows(
            connection, dimension, matrix, buffer, visibility,
            flowTime, particleStatus);
    }

    private static void renderEndpoint(
        LogisticsNode node,
        FaceTopology topology,
        ResourceKey<Level> dimension,
        Matrix4f matrix,
        VertexConsumer buffer,
        WorldOverlayVisibility visibility,
        float pulse,
        Set<BlockPos> renderedFrames,
        Set<LogisticsNode> renderedFaces
    ) {
        if (!node.gPos().dimension().equals(dimension)) return;
        BlockPos position = node.gPos().pos();
        if (!visibility.isBlockVisible(position)) return;
        if (renderedFrames.add(position)) {
            LogisticsRenderHelper.drawFrame(
                buffer, matrix, position,
                1.0F, 1.0F, 1.0F, 0.25F);
        }
        if (renderedFaces.add(node)) {
            LogisticsRenderHelper.drawFaceStatus(
                buffer, matrix, position, node.face(),
                LogisticsRenderHelper.INPUT_COLOR,
                LogisticsRenderHelper.OUTPUT_COLOR,
                topology.role().canReceive(),
                topology.role().canSend(), pulse);
        }
    }

    private static void renderConnectionFlows(
        ClientConnection connection,
        ResourceKey<Level> dimension,
        Matrix4f matrix,
        VertexConsumer buffer,
        WorldOverlayVisibility visibility,
        double flowTime,
        ParticleStatus particleStatus
    ) {
        LogisticsNode first = connection.first();
        LogisticsNode second = connection.second();
        if (!first.gPos().dimension().equals(dimension)
            || !second.gPos().dimension().equals(dimension)) {
            return;
        }
        BlockPos firstPosition = first.gPos().pos();
        BlockPos secondPosition = second.gPos().pos();
        if (!visibility.isConnectionVisible(firstPosition, secondPosition)) return;

        boolean activeFirstToSecond = connection.transfersFirstToSecond();
        boolean activeSecondToFirst = connection.transfersSecondToFirst();
        ConnectionKey connectionKey = connection.key();
        ClientRedstoneControlData.State redstoneState =
            ClientRedstoneControlData.INSTANCE.get(connectionKey);
        boolean redstoneAllowed = redstoneState == null || redstoneState.allowed();
        FlowAnimationState flowState = FLOW_STATES.computeIfAbsent(
            connectionKey, ignored -> new FlowAnimationState());
        flowState.update(flowTime, activeFirstToSecond,
            activeSecondToFirst, redstoneAllowed);

        if (flowState.firstToSecondVisible) {
            drawFlow(first, connection.firstTopology(),
                second, connection.secondTopology(),
                matrix, buffer, flowState.firstToSecondTime, particleStatus);
        }
        if (flowState.secondToFirstVisible) {
            drawFlow(second, connection.secondTopology(),
                first, connection.firstTopology(),
                matrix, buffer, flowState.secondToFirstTime, particleStatus);
        }
    }

    /**
     * 每个方向独立累计动画时间；输入/输出或红石停运时冻结，恢复后继续。
     */
    private static final class FlowAnimationState {
        private boolean firstToSecondVisible;
        private boolean secondToFirstVisible;
        private double firstToSecondTime;
        private double secondToFirstTime;
        private double lastFrameTime = Double.NaN;

        private void update(double frameTime, boolean firstActive,
                            boolean secondActive, boolean redstoneAllowed) {
            firstToSecondVisible |= firstActive;
            secondToFirstVisible |= secondActive;
            if (!firstToSecondVisible && !secondToFirstVisible) {
                firstToSecondVisible = true;
            }
            if (Double.isNaN(lastFrameTime)) {
                lastFrameTime = frameTime;
                return;
            }
            double elapsed = Math.max(0.0D,
                Math.min(0.25D, frameTime - lastFrameTime));
            if (redstoneAllowed && firstActive) firstToSecondTime += elapsed;
            if (redstoneAllowed && secondActive) secondToFirstTime += elapsed;
            lastFrameTime = frameTime;
        }
    }

    private static void drawFlow(
        LogisticsNode source,
        FaceTopology sourceTopology,
        LogisticsNode target,
        FaceTopology targetTopology,
        Matrix4f matrix,
        VertexConsumer buffer,
        double time,
        ParticleStatus particleStatus
    ) {
        Vec3 start = faceOffset(
            source.gPos().pos(), source.face(),
            sourceTopology.role().canReceive() ? 0.3F : 0.0F);
        Vec3 end = faceOffset(
            target.gPos().pos(), target.face(),
            targetTopology.role().canSend() ? -0.3F : 0.0F);
        LogisticsRenderHelper.drawFlowParticles(
            buffer, matrix, start, end,
            LogisticsRenderHelper.FLOW_COLOR, time, particleStatus);
    }

    /**
     * 计算面片中心位置，支持半面片偏移（与 drawFaceQuad 的 offset 对齐）
     */
    private static Vec3 faceOffset(BlockPos pos, Direction face, float offset) {
        Vec3 n = Vec3.atLowerCornerOf(face.getNormal());
        Vec3 center = Vec3.atCenterOf(pos).add(n.scale(0.52));
        if (offset == 0f) return center;
        Vec3 a1 = (Math.abs(n.y) > 0.5) ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 a2 = n.cross(a1).normalize();
        float size = 0.85f;
        return center.add(a2.scale(offset * size));
    }

    private static ItemStack getActiveConfigurator(Minecraft mc) {
        if (mc.player == null) return ItemStack.EMPTY;
        ItemStack m = mc.player.getMainHandItem();
        if (isLinkTool(m)) return m;
        ItemStack o = mc.player.getOffhandItem();
        return isLinkTool(o) ? o : ItemStack.EMPTY;
    }

    private static boolean isLinkTool(ItemStack s) {
        return !s.isEmpty() && (s.getItem() instanceof LinkConfiguratorItem || s.getItem() instanceof BlueprintItem);
    }
}
