package com.coobird.staticlogistics.client.render;

import com.coobird.staticlogistics.Staticlogistics;
import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.api.type.ToolMode;
import com.coobird.staticlogistics.client.data.ClientLinkData;
import com.coobird.staticlogistics.client.data.SelectionContext;
import com.coobird.staticlogistics.item.BlueprintItem;
import com.coobird.staticlogistics.item.LinkConfiguratorItem;
import com.coobird.staticlogistics.storage.config.FaceConfigComposite;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@EventBusSubscriber(modid = Staticlogistics.MODID, value = Dist.CLIENT)
public class LinkWorldRenderer {

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

    private static double maxRenderDistSq() {
        double d = Minecraft.getInstance().options.renderDistance().get() * 16 * 0.4;
        return d * d;
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        ItemStack stack = getActiveConfigurator(mc);
        if (stack.isEmpty()) return;

        SelectionContext.syncFromItem(stack);
        String groupId = SelectionContext.getSelectedGroupId();

        PoseStack ps = event.getPoseStack();
        Vec3 cam = event.getCamera().getPosition();
        MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();
        VertexConsumer b = buf.getBuffer(PIPE_XRAY);
        ResourceKey<Level> dim = mc.level.dimension();

        ps.pushPose();
        ps.translate(-cam.x, -cam.y, -cam.z);
        Matrix4f mat = ps.last().pose();
        double maxD2 = maxRenderDistSq();
        float pulse = (float) Math.sin(System.currentTimeMillis() / 200.0) * 0.03f;

        // 存点预览
        LinkConfiguratorItem.ToolSettings settings = stack.getItem() instanceof LinkConfiguratorItem lci
            ? lci.getSettings(stack) : null;
        if (settings != null && !settings.storedNodes().isEmpty() && settings.storedMode() != null)
            renderStoredNodes(settings, dim, mat, b, cam, maxD2, pulse);

        // 选中分组的所有链接
        if (!groupId.isEmpty())
            renderGroupLinks(groupId, dim, mat, b, cam, maxD2, pulse);

        ps.popPose();
        buf.endBatch(PIPE_XRAY);
    }

    private static void renderStoredNodes(LinkConfiguratorItem.ToolSettings settings,
                                          ResourceKey<Level> dim, Matrix4f mat, VertexConsumer b,
                                          Vec3 cam, double maxD2, float pulse) {
        boolean isIn = settings.storedMode() == ToolMode.LINK_AS_INSERT;
        float r = isIn ? 0.2f : 1f, g = isIn ? 0.5f : 0.6f, bl = isIn ? 1f : 0f;

        for (LogisticsNode node : settings.storedNodes()) {
            if (!node.gPos().dimension().equals(dim)) continue;
            BlockPos p = node.gPos().pos();
            if (p.distToCenterSqr(cam.x, cam.y, cam.z) > maxD2) continue;

            LogisticsRenderHelper.drawFrame(b, mat, p, 0.8f, 0.8f, 0.8f, 0.4f);
            double px = p.getX() + 0.5 + node.face().getStepX() * 0.51;
            double py = p.getY() + 0.5 + node.face().getStepY() * 0.51;
            double pz = p.getZ() + 0.5 + node.face().getStepZ() * 0.51;
            LogisticsRenderHelper.drawFaceQuad(b, mat, px, py, pz, node.face(), 0, 0.6f, 0.4f + pulse, 0, 1f);
        }
    }

    private static void renderGroupLinks(String groupId, ResourceKey<Level> dim,
                                         Matrix4f mat, VertexConsumer b,
                                         Vec3 cam, double maxD2, float pulse) {
        Set<BlockPos> renderedFrames = new HashSet<>();
        var nodes = ClientLinkData.INSTANCE.getActiveNodesWithConfig(dim);

        for (var entry : nodes.entrySet()) {
            LogisticsNode node = entry.getKey();
            FaceConfigComposite cfg = entry.getValue();
            if (cfg.isDefault() || !cfg.faceConfig.getGroupIds().contains(groupId)) continue;

            BlockPos p = node.gPos().pos();
            double d2 = p.distToCenterSqr(cam.x, cam.y, cam.z);
            boolean vis = d2 <= maxD2;

            if (vis && renderedFrames.add(p))
                LogisticsRenderHelper.drawFrame(b, mat, p, 1f, 1f, 1f, 0.25f);
            if (vis)
                LogisticsRenderHelper.drawFaceStatus(b, mat, p, node.face(),
                    cfg.isGlobalInputEnabled() ? cfg.linkConfig.getInputChannel() : 0,
                    cfg.isGlobalOutputEnabled() ? cfg.linkConfig.getOutputChannel() : 0,
                    cfg.isGlobalInputEnabled(), cfg.isGlobalOutputEnabled(), pulse);
            renderNodeFlows(node, cfg, nodes, dim, groupId, mat, b, cam, d2, vis, maxD2);
        }
    }

    private static void renderNodeFlows(LogisticsNode src, FaceConfigComposite srcCfg,
                                        Map<LogisticsNode, FaceConfigComposite> all,
                                        ResourceKey<Level> dim, String groupId,
                                        Matrix4f mat, VertexConsumer b, Vec3 cam,
                                        double srcD2, boolean srcVis, double maxD2) {
        if (!srcCfg.isGlobalOutputEnabled()) return;
        int outCh = srcCfg.linkConfig.getOutputChannel();
        if (outCh < 1 || outCh > 16) return;

        BlockPos sp = src.gPos().pos();
        double time = System.currentTimeMillis() / 1000.0;

        for (LogisticsNode dst : srcCfg.getLinkedNodes()) {
            if (!dst.gPos().dimension().equals(dim)) continue;
            FaceConfigComposite dstCfg = all.get(dst);
            if (dstCfg == null) continue;
            if (!dstCfg.faceConfig.getGroupIds().contains(groupId)) continue;
            if (!dstCfg.isGlobalInputEnabled()) continue;
            if (dstCfg.linkConfig.getInputChannel() != outCh) continue;

            BlockPos dp = dst.gPos().pos();
            double dstD2 = dp.distToCenterSqr(cam.x, cam.y, cam.z);
            if (!srcVis && dstD2 > maxD2) continue;

            Vec3 s = Vec3.atCenterOf(sp).add(Vec3.atLowerCornerOf(src.face().getNormal()).scale(0.52));
            Vec3 t = Vec3.atCenterOf(dp).add(Vec3.atLowerCornerOf(dst.face().getNormal()).scale(0.52));

            LogisticsRenderHelper.drawFlowParticles(b, mat, s, t, outCh, time);
        }
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
