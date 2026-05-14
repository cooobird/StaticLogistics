package com.coobird.staticlogistics.client.render;

import com.coobird.staticlogistics.Staticlogistics;
import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.api.type.TransferType;
import com.coobird.staticlogistics.client.data.ClientLinkData;
import com.coobird.staticlogistics.client.data.SelectionContext;
import com.coobird.staticlogistics.client.util.RenderConstants;
import com.coobird.staticlogistics.core.registration.TransferRegistries;
import com.coobird.staticlogistics.item.LinkConfiguratorItem;
import com.coobird.staticlogistics.item.util.ToolMode;
import com.coobird.staticlogistics.storage.config.FaceConfigComposite;
import com.coobird.staticlogistics.storage.config.LinkConfig;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

import java.util.HashSet;
import java.util.Set;

@EventBusSubscriber(modid = Staticlogistics.MODID, value = Dist.CLIENT)
public class LinkWorldRenderer {
    private static final double MAX_RENDER_DIST_SQ = 64.0 * 64.0;
    private static final float BOX_EXPAND = 0.005f;
    private static final float TUBE_RADIUS = 0.015f;

    public static final RenderType PIPE_XRAY = RenderType.create(
        "pipe_xray", DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 1536, false, false,
        RenderType.CompositeState.builder()
            .setShaderState(new RenderType.ShaderStateShard(GameRenderer::getPositionColorShader))
            .setTransparencyState(RenderType.TRANSLUCENT_TRANSPARENCY)
            .setWriteMaskState(RenderType.COLOR_WRITE)
            .setDepthTestState(RenderType.NO_DEPTH_TEST)
            .setCullState(RenderType.NO_CULL)
            .createCompositeState(false)
    );

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        ItemStack stack = getActiveConfigurator(mc);
        if (stack.isEmpty()) return;

        SelectionContext.syncFromItem(stack);
        String currentGroupId = SelectionContext.getSelectedGroupId();

        PoseStack poseStack = event.getPoseStack();
        Vec3 cam = event.getCamera().getPosition();
        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();
        VertexConsumer builder = bufferSource.getBuffer(PIPE_XRAY);
        ResourceKey<Level> currentDim = mc.level.dimension();

        Frustum frustum = event.getFrustum();

        poseStack.pushPose();
        poseStack.translate(-cam.x, -cam.y, -cam.z);
        Matrix4f mat = poseStack.last().pose();

        float pulse = (float) Math.sin(System.currentTimeMillis() / 200.0) * 0.03f;
        LinkConfiguratorItem.ToolSettings settings = ((LinkConfiguratorItem) stack.getItem()).getSettings(stack);

        if (!settings.storedNodes().isEmpty() && settings.storedMode() != null) {
            for (LogisticsNode node : settings.storedNodes()) {
                if (node.gPos().dimension().equals(currentDim)) {
                    BlockPos p = node.gPos().pos();
                    if (p.distToCenterSqr(cam.x, cam.y, cam.z) <= MAX_RENDER_DIST_SQ && frustum.isVisible(new AABB(p))) {
                        drawFrame(builder, mat, p, 0.8f, 0.8f, 0.8f, 0.4f);
                        boolean isIn = settings.storedMode() == ToolMode.LINK_AS_INSERT;
                        float r = isIn ? 0.2f : 1.0f, g = isIn ? 0.5f : 0.6f, b = isIn ? 1.0f : 0.0f;
                        double px = p.getX() + 0.5 + node.face().getStepX() * 0.51;
                        double py = p.getY() + 0.5 + node.face().getStepY() * 0.51;
                        double pz = p.getZ() + 0.5 + node.face().getStepZ() * 0.51;
                        drawSimpleFace(builder, mat, px, py, pz, node.face(), r, g, b, 0.6f, 0.4f + pulse);
                    }
                }
            }
        }

        if (!currentGroupId.isEmpty()) {
            Set<BlockPos> renderedFrames = new HashSet<>();
            var activeNodes = ClientLinkData.INSTANCE.getActiveNodesWithConfig(currentDim);

            activeNodes.forEach((node, cfg) -> {
                if (cfg.isDefault() || !currentGroupId.equals(cfg.faceConfig.getGroupId())) return;

                BlockPos p = node.gPos().pos();
                if (p.distToCenterSqr(cam.x, cam.y, cam.z) > MAX_RENDER_DIST_SQ || !frustum.isVisible(new AABB(p)))
                    return;

                if (renderedFrames.add(p)) {
                    drawFrame(builder, mat, p, 1.0f, 1.0f, 1.0f, 0.25f);
                }

                renderNodeFaceStatus(node, cfg, builder, mat, pulse);

                renderFlows(node, cfg, currentDim, builder, mat);
            });
        }

        poseStack.popPose();
        bufferSource.endBatch(PIPE_XRAY);
    }

    private static void renderFlows(LogisticsNode src, FaceConfigComposite srcCfg, ResourceKey<Level> currentDim, VertexConsumer builder, Matrix4f mat) {
        for (TransferType type : TransferRegistries.getAllActive()) {
            LinkConfig.SideData srcData = srcCfg.linkConfig.getSettings(type);

            if (srcData.outputEnabled && !srcData.linkedInputs.isEmpty()) {
                Vec3 sPos = Vec3.atCenterOf(src.gPos().pos()).add(Vec3.atLowerCornerOf(src.face().getNormal()).scale(0.52));

                for (LogisticsNode dst : srcData.linkedInputs) {
                    if (dst.gPos().dimension().equals(currentDim)) {
                        var dstCfg = ClientLinkData.INSTANCE.getFaceConfig(dst);
                        if (dstCfg != null) {
                            LinkConfig.SideData dstData = dstCfg.linkConfig.getSettings(type);

                            boolean bothUnset = (srcData.outputChannel == 0 && dstData.inputChannel == 0);
                            boolean bothSetAndEqual = (srcData.outputChannel != 0 && dstData.inputChannel != 0 && dstData.inputChannel == srcData.outputChannel);
                            boolean channelMatch = bothUnset || bothSetAndEqual;

                            if (channelMatch && dstData.inputEnabled) {
                                Vec3 dPos = Vec3.atCenterOf(dst.gPos().pos()).add(Vec3.atLowerCornerOf(dst.face().getNormal()).scale(0.52));

                                float offset = (srcData.inputEnabled && dstData.outputEnabled) ? 0.15f : 0.0f;

                                drawDirectedLine(builder, mat, sPos, dPos, src.face(), srcData.outputChannel, offset);
                            }
                        }
                    }
                }
            }
        }
    }

    private static void renderNodeFaceStatus(LogisticsNode node, FaceConfigComposite cfg, VertexConsumer b, Matrix4f m, float pulse) {
        BlockPos p = node.gPos().pos();
        Direction f = node.face();
        double px = p.getX() + 0.5 + f.getStepX() * 0.508;
        double py = p.getY() + 0.5 + f.getStepY() * 0.508;
        double pz = p.getZ() + 0.5 + f.getStepZ() * 0.508;

        boolean hasIn = false;
        boolean hasOut = false;
        int inChannel = -1;
        int outChannel = -1;

        for (TransferType type : TransferRegistries.getAllActive()) {
            LinkConfig.SideData data = cfg.linkConfig.getSettings(type);
            if (data.inputEnabled) {
                hasIn = true;
                inChannel = data.inputChannel;
            }
            if (data.outputEnabled) {
                hasOut = true;
                outChannel = data.outputChannel;
            }
            if (hasIn && hasOut) break;
        }

        float size = 0.4f + pulse;

        if (hasIn && hasOut) {
            drawFaceByChannel(b, m, px, py, pz, f, inChannel, 0.85f, size, -0.5f, 0.45f);
            drawFaceByChannel(b, m, px, py, pz, f, outChannel, 0.85f, size, 0.5f, 0.45f);
        } else if (hasIn) {
            drawFaceByChannel(b, m, px, py, pz, f, inChannel, 0.85f, size, 0, 1.0f);
        } else if (hasOut) {
            drawFaceByChannel(b, m, px, py, pz, f, outChannel, 0.85f, size, 0, 1.0f);
        }
    }

    private static void drawDirectedLine(VertexConsumer b, Matrix4f mat, Vec3 start, Vec3 end, Direction face, int colorIdx, float offset) {
        Vec3 diff = end.subtract(start);
        double dist = diff.length();
        if (dist < 0.1 || Double.isNaN(dist) || Double.isInfinite(dist)) return;
        int particleCount = (int) Math.min(100, Math.max(3, dist * 3.5));

        Vec3 n = Vec3.atLowerCornerOf(face.getNormal());
        Vec3 a1 = (Math.abs(n.y) > 0.5) ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 lateralVec = n.cross(a1).normalize();

        Vec3 offsetStart = start.add(lateralVec.scale(offset));
        Vec3 offsetEnd = end.add(lateralVec.scale(offset));
        Vec3 offsetDiff = offsetEnd.subtract(offsetStart);

        int color = RenderConstants.DYE_COLORS[colorIdx % RenderConstants.DYE_COLORS.length];
        float r = ((color >> 16) & 0xFF) / 255f, g = ((color >> 8) & 0xFF) / 255f, bl = (color & 0xFF) / 255f;

        double time = System.currentTimeMillis() / 1000.0;
        float speed = 1.2f;

        for (int i = 0; i < particleCount; i++) {
            double spacing = (double) i / particleCount;
            float progress = (float) ((time * speed + spacing * dist) % dist) / (float) dist;
            Vec3 pos = offsetStart.add(offsetDiff.scale(progress));
            float size = 0.025f;
            renderBox(b, mat, (float) pos.x - size, (float) pos.y - size, (float) pos.z - size, (float) pos.x + size, (float) pos.y + size, (float) pos.z + size, r, g, bl, 0.9f);
        }
    }

    private static void drawFaceByChannel(VertexConsumer b, Matrix4f mat, double x, double y, double z, Direction face, int colorIdx, float a, float s, float offset, float widthMult) {
        int color = RenderConstants.DYE_COLORS[colorIdx % RenderConstants.DYE_COLORS.length];
        float r = ((color >> 16) & 0xFF) / 255f, g = ((color >> 8) & 0xFF) / 255f, bl = (color & 0xFF) / 255f;
        int ir = (int) (r * 255), ig = (int) (g * 255), ib = (int) (bl * 255), ia = (int) (a * 255);

        Vec3 n = Vec3.atLowerCornerOf(face.getNormal());
        Vec3 a1 = (Math.abs(n.y) > 0.5) ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 a2 = n.cross(a1).normalize();
        a1 = n.cross(a2).normalize();

        double ox = a2.x * offset * s;
        double oy = a2.y * offset * s;
        double oz = a2.z * offset * s;

        float x1 = (float) (a1.x * s), y1 = (float) (a1.y * s), z1 = (float) (a1.z * s);
        float x2 = (float) (a2.x * s * widthMult), y2 = (float) (a2.y * s * widthMult), z2 = (float) (a2.z * s * widthMult);

        b.addVertex(mat, (float) (x + ox - x1 - x2), (float) (y + oy - y1 - y2), (float) (z + oz - z1 - z2)).setColor(ir, ig, ib, ia);
        b.addVertex(mat, (float) (x + ox + x1 - x2), (float) (y + oy + y1 - y2), (float) (z + oz + z1 - z2)).setColor(ir, ig, ib, ia);
        b.addVertex(mat, (float) (x + ox + x1 + x2), (float) (y + oy + y1 + y2), (float) (z + oz + z1 + z2)).setColor(ir, ig, ib, ia);
        b.addVertex(mat, (float) (x + ox - x1 + x2), (float) (y + oy - y1 + y2), (float) (z + oz - z1 + z2)).setColor(ir, ig, ib, ia);
    }

    private static void drawFrame(VertexConsumer b, Matrix4f mat, BlockPos pos, float r, float g, float bl, float a) {
        float x1 = pos.getX() - BOX_EXPAND, y1 = pos.getY() - BOX_EXPAND, z1 = pos.getZ() - BOX_EXPAND;
        float x2 = pos.getX() + 1 + BOX_EXPAND, y2 = pos.getY() + 1 + BOX_EXPAND, z2 = pos.getZ() + 1 + BOX_EXPAND;
        drawEdge(b, mat, x1, y1, z1, x2, y1, z1, r, g, bl, a);
        drawEdge(b, mat, x1, y1, z2, x2, y1, z2, r, g, bl, a);
        drawEdge(b, mat, x1, y2, z1, x2, y2, z1, r, g, bl, a);
        drawEdge(b, mat, x1, y2, z2, x2, y2, z2, r, g, bl, a);
        drawEdge(b, mat, x1, y1, z1, x1, y2, z1, r, g, bl, a);
        drawEdge(b, mat, x2, y1, z1, x2, y2, z1, r, g, bl, a);
        drawEdge(b, mat, x1, y1, z2, x1, y2, z2, r, g, bl, a);
        drawEdge(b, mat, x2, y1, z2, x2, y2, z2, r, g, bl, a);
        drawEdge(b, mat, x1, y1, z1, x1, y1, z2, r, g, bl, a);
        drawEdge(b, mat, x2, y1, z1, x2, y1, z2, r, g, bl, a);
        drawEdge(b, mat, x1, y2, z1, x1, y2, z2, r, g, bl, a);
        drawEdge(b, mat, x2, y2, z1, x2, y2, z2, r, g, bl, a);
    }

    private static void drawEdge(VertexConsumer b, Matrix4f mat, float x1, float y1, float z1, float x2, float y2, float z2, float r, float g, float bl, float a) {
        float dx = Math.abs(x2 - x1), dy = Math.abs(y2 - y1);
        float minX = x1, minY = y1, minZ = z1, maxX = x2, maxY = y2, maxZ = z2;
        if (dx > 0.1f) {
            minY -= TUBE_RADIUS;
            maxY += TUBE_RADIUS;
            minZ -= TUBE_RADIUS;
            maxZ += TUBE_RADIUS;
        } else if (dy > 0.1f) {
            minX -= TUBE_RADIUS;
            maxX += TUBE_RADIUS;
            minZ -= TUBE_RADIUS;
            maxZ += TUBE_RADIUS;
        } else {
            minX -= TUBE_RADIUS;
            maxX += TUBE_RADIUS;
            minY -= TUBE_RADIUS;
            maxY += TUBE_RADIUS;
        }
        renderBox(b, mat, minX, minY, minZ, maxX, maxY, maxZ, r, g, bl, a);
    }

    private static void renderBox(VertexConsumer b, Matrix4f mat, float x1, float y1, float z1, float x2, float y2, float z2, float r, float g, float bl, float a) {
        int ir = (int) (r * 255), ig = (int) (g * 255), ib = (int) (bl * 255), ia = (int) (a * 255);
        b.addVertex(mat, x1, y1, z1).setColor(ir, ig, ib, ia);
        b.addVertex(mat, x2, y1, z1).setColor(ir, ig, ib, ia);
        b.addVertex(mat, x2, y2, z1).setColor(ir, ig, ib, ia);
        b.addVertex(mat, x1, y2, z1).setColor(ir, ig, ib, ia);
        b.addVertex(mat, x1, y1, z2).setColor(ir, ig, ib, ia);
        b.addVertex(mat, x1, y2, z2).setColor(ir, ig, ib, ia);
        b.addVertex(mat, x2, y2, z2).setColor(ir, ig, ib, ia);
        b.addVertex(mat, x2, y1, z2).setColor(ir, ig, ib, ia);
        b.addVertex(mat, x1, y1, z1).setColor(ir, ig, ib, ia);
        b.addVertex(mat, x1, y2, z1).setColor(ir, ig, ib, ia);
        b.addVertex(mat, x1, y2, z2).setColor(ir, ig, ib, ia);
        b.addVertex(mat, x1, y1, z2).setColor(ir, ig, ib, ia);
        b.addVertex(mat, x2, y1, z1).setColor(ir, ig, ib, ia);
        b.addVertex(mat, x2, y1, z2).setColor(ir, ig, ib, ia);
        b.addVertex(mat, x2, y2, z2).setColor(ir, ig, ib, ia);
        b.addVertex(mat, x2, y2, z1).setColor(ir, ig, ib, ia);
        b.addVertex(mat, x1, y1, z1).setColor(ir, ig, ib, ia);
        b.addVertex(mat, x1, y1, z2).setColor(ir, ig, ib, ia);
        b.addVertex(mat, x2, y1, z2).setColor(ir, ig, ib, ia);
        b.addVertex(mat, x2, y1, z1).setColor(ir, ig, ib, ia);
        b.addVertex(mat, x1, y2, z1).setColor(ir, ig, ib, ia);
        b.addVertex(mat, x2, y2, z1).setColor(ir, ig, ib, ia);
        b.addVertex(mat, x2, y2, z2).setColor(ir, ig, ib, ia);
        b.addVertex(mat, x1, y2, z2).setColor(ir, ig, ib, ia);
    }

    private static void drawSimpleFace(VertexConsumer b, Matrix4f mat, double x, double y, double z, Direction face, float r, float g, float bl, float a, float s) {
        int ir = (int) (r * 255), ig = (int) (g * 255), ib = (int) (bl * 255), ia = (int) (a * 255);
        Vec3 n = Vec3.atLowerCornerOf(face.getNormal());
        Vec3 a1 = (Math.abs(n.y) > 0.5) ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 a2 = n.cross(a1).normalize();
        a1 = n.cross(a2).normalize();
        float x1 = (float) (a1.x * s), y1 = (float) (a1.y * s), z1 = (float) (a1.z * s);
        float x2 = (float) (a2.x * s), y2 = (float) (a2.y * s), z2 = (float) (a2.z * s);
        b.addVertex(mat, (float) (x - x1 - x2), (float) (y - y1 - y2), (float) (z - z1 - z2)).setColor(ir, ig, ib, ia);
        b.addVertex(mat, (float) (x + x1 - x2), (float) (y + y1 - y2), (float) (z + z1 - z2)).setColor(ir, ig, ib, ia);
        b.addVertex(mat, (float) (x + x1 + x2), (float) (y + y1 + y2), (float) (z + z1 + z2)).setColor(ir, ig, ib, ia);
        b.addVertex(mat, (float) (x - x1 + x2), (float) (y - y1 + y2), (float) (z - z1 + z2)).setColor(ir, ig, ib, ia);
    }

    private static ItemStack getActiveConfigurator(Minecraft mc) {
        if (mc.player == null) return ItemStack.EMPTY;
        ItemStack m = mc.player.getMainHandItem();
        if (m.getItem() instanceof LinkConfiguratorItem) return m;
        ItemStack o = mc.player.getOffhandItem();
        return (o.getItem() instanceof LinkConfiguratorItem) ? o : ItemStack.EMPTY;
    }
}