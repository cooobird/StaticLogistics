package com.coobird.staticlogistics.client.render;

import com.coobird.staticlogistics.Staticlogistics;
import com.coobird.staticlogistics.client.ClientLinkCache;
import com.coobird.staticlogistics.common.item.LinkConfiguratorItem;
import com.coobird.staticlogistics.core.FaceConfig;
import com.coobird.staticlogistics.core.NodeEntry;
import com.coobird.staticlogistics.core.StaticLink;
import com.coobird.staticlogistics.transfer.TransferType;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@EventBusSubscriber(modid = Staticlogistics.MODID, value = Dist.CLIENT)
public class LinkWorldRenderer {
    private static final float PIXEL = 1.0f / 16.0f;
    private static final float TUBE_WIDTH = PIXEL / 2.0f;
    private static final double MAX_RENDER_DIST_SQ = 64.0 * 64.0;

    public static final RenderType PIPE_XRAY = RenderType.create(
        "pipe_xray", DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 1536, false, false,
        RenderType.CompositeState.builder()
            .setShaderState(RenderType.POSITION_COLOR_SHADER)
            .setTransparencyState(RenderType.TRANSLUCENT_TRANSPARENCY)
            .setWriteMaskState(RenderType.COLOR_WRITE)
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

        LinkConfiguratorItem.ToolSettings settings = ((LinkConfiguratorItem) stack.getItem()).getSettings(stack);
        Collection<StaticLink> allLinks = ClientLinkCache.getAllLinks();
        if (allLinks.isEmpty() && settings.storedNodes().isEmpty()) return;

        PoseStack poseStack = event.getPoseStack();
        Vec3 cam = event.getCamera().getPosition();
        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();
        VertexConsumer builder = bufferSource.getBuffer(PIPE_XRAY);
        ResourceKey<Level> currentDim = mc.level.dimension();

        poseStack.pushPose();
        poseStack.translate(-cam.x, -cam.y, -cam.z);
        Matrix4f mat = poseStack.last().pose();
        float pulse = (float) Math.sin(System.currentTimeMillis() / 200.0) * 0.03f;

        if (!settings.storedNodes().isEmpty()) {
            boolean isInput = settings.storedMode() == LinkConfiguratorItem.ToolMode.LINK_AS_INPUT;
            float r = isInput ? 1.0f : 0.3f;
            float g = isInput ? 0.7f : 1.0f;
            float b = isInput ? 0.3f : 1.0f;
            for (NodeEntry node : settings.storedNodes()) {
                if (node.pos().dimension().equals(currentDim)) {
                    BlockPos p = node.pos().pos();
                    if (p.distToCenterSqr(cam.x, cam.y, cam.z) > MAX_RENDER_DIST_SQ) continue;

                    double px = p.getX() + 0.5 + node.face().getStepX() * 0.505;
                    double py = p.getY() + 0.5 + node.face().getStepY() * 0.505;
                    double pz = p.getZ() + 0.5 + node.face().getStepZ() * 0.505;
                    drawSimpleFace(builder, mat, px, py, pz, node.face(), r, g, b, 0.7f, 0.4f + pulse);
                }
            }
        }

        for (StaticLink link : allLinks) {
            if (isLinkInvalid(mc.level, link)) {
                ClientLinkCache.removeLinkById(link.linkId());
                continue;
            }

            if (link.sourcePos().distToCenterSqr(cam.x, cam.y, cam.z) > MAX_RENDER_DIST_SQ) continue;

            boolean isCurrentGroup = link.groupId().equals(settings.group());
            float alphaMod = isCurrentGroup ? 1.0f : 0.20f;

            boolean srcIn = link.sourceDimension().equals(currentDim);
            boolean dstIn = link.destDimension().equals(currentDim);
            if (!srcIn && !dstIn) continue;

            AABB bounds;
            if (srcIn && dstIn) {
                bounds = new AABB(link.sourcePos()).minmax(new AABB(link.destPos())).inflate(1.0);
            } else {
                bounds = new AABB(link.sourcePos()).inflate(128.0);
            }

            if (!event.getFrustum().isVisible(bounds)) continue;

            double sx = link.sourcePos().getX() + 0.5 + link.sourceFace().getStepX() * 0.51;
            double sy = link.sourcePos().getY() + 0.5 + link.sourceFace().getStepY() * 0.51;
            double sz = link.sourcePos().getZ() + 0.5 + link.sourceFace().getStepZ() * 0.51;
            double ex = link.destPos().getX() + 0.5 + link.destFace().getStepX() * 0.51;
            double ey = link.destPos().getY() + 0.5 + link.destFace().getStepY() * 0.51;
            double ez = link.destPos().getZ() + 0.5 + link.destFace().getStepZ() * 0.51;

            if (srcIn)
                drawSimpleFace(builder, mat, sx, sy, sz, link.sourceFace(), 1.0f, 0.6f, 0.0f, 0.6f * alphaMod, 0.4f + pulse);
            if (dstIn)
                drawSimpleFace(builder, mat, ex, ey, ez, link.destFace(), 0.0f, 0.8f, 1.0f, 0.6f * alphaMod, 0.4f + pulse);

            if (srcIn && dstIn) {
                renderDirectionalPipes(builder, mat, link, sx, sy, sz, ex, ey, ez, alphaMod);
            } else {
                double tx = srcIn ? sx + link.sourceFace().getStepX() : ex + link.destFace().getStepX();
                double ty = srcIn ? sy + link.sourceFace().getStepY() : ey + link.destFace().getStepY();
                double tz = srcIn ? sz + link.sourceFace().getStepZ() : ez + link.destFace().getStepZ();
                drawPipe(builder, mat, srcIn ? sx : ex, srcIn ? sy : ey, srcIn ? sz : ez, tx, ty, tz, TUBE_WIDTH * 1.5f, 0xFFFFFFFF, alphaMod);
            }
        }

        poseStack.popPose();
        bufferSource.endBatch(PIPE_XRAY);
    }

    private static void renderDirectionalPipes(VertexConsumer builder, Matrix4f mat, StaticLink link, double sx, double sy, double sz, double ex, double ey, double ez, float alphaMod) {
        FaceConfig config = ClientLinkCache.getFaceConfig(link.sourcePos(), link.sourceFace());
        if (config == null) return;

        List<Integer> in = new ArrayList<>(), out = new ArrayList<>();
        for (TransferType t : TransferType.values()) {
            if (!link.hasType(t)) continue;
            FaceConfig.SideData d = config.getSettings(t);
            if (d.mode.allowsInput()) in.add(d.getRenderColor(t));
            if (d.mode.allowsOutput()) out.add(d.getRenderColor(t));
        }
        if (in.isEmpty() && out.isEmpty()) return;

        Vec3 normal = Vec3.atLowerCornerOf(link.sourceFace().getNormal());
        Vec3 axis = (Math.abs(normal.y) > 0.5) ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 ortho = normal.cross(axis).scale(0.08);

        if (!in.isEmpty() && !out.isEmpty()) {
            drawPipe(builder, mat, sx + ortho.x, sy + ortho.y, sz + ortho.z, ex + ortho.x, ey + ortho.y, ez + ortho.z, TUBE_WIDTH, in.getFirst(), alphaMod);
            drawPipe(builder, mat, sx - ortho.x, sy - ortho.y, sz - ortho.z, ex - ortho.x, ey - ortho.y, ez - ortho.z, TUBE_WIDTH, out.getFirst(), alphaMod);
        } else {
            drawPipe(builder, mat, sx, sy, sz, ex, ey, ez, TUBE_WIDTH, !in.isEmpty() ? in.getFirst() : out.getFirst(), alphaMod);
        }
    }

    private static void drawPipe(VertexConsumer b, Matrix4f mat, double sx, double sy, double sz, double ex, double ey, double ez, float w, int c, float alphaMod) {
        int rawA = ((c >> 24) & 0xFF) == 0 ? 180 : (c >> 24) & 0xFF;
        int a = (int) (rawA * alphaMod);
        if (a <= 5) return;

        int r = (c >> 16) & 0xFF, g = (c >> 8) & 0xFF, bl = c & 0xFF;
        float dx = (float) (ex - sx), dy = (float) (ey - sy), dz = (float) (ez - sz);
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 0.01f) return;

        Vec3 dir = new Vec3(dx, dy, dz).normalize();
        Vec3 p = dir.cross(Math.abs(dir.y) < 0.9 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0)).normalize().scale(w);
        Vec3 q = dir.cross(p).normalize().scale(w);

        renderQuad(b, mat, sx + p.x, sy + p.y, sz + p.z, ex + p.x, ey + p.y, ez + p.z, ex + q.x, ey + q.y, ez + q.z, sx + q.x, sy + q.y, sz + q.z, r, g, bl, a);
        renderQuad(b, mat, sx - p.x, sy - p.y, sz - p.z, ex - p.x, ey - p.y, ez - p.z, ex - q.x, ey - q.y, ez - q.z, sx - q.x, sy - q.y, sz - q.z, r, g, bl, a);
        renderQuad(b, mat, sx + p.x, sy + p.y, sz + p.z, ex + p.x, ey + p.y, ez + p.z, ex - q.x, ey - q.y, ez - q.z, sx - q.x, sy - q.y, sz - q.z, r, g, bl, a);
        renderQuad(b, mat, sx - p.x, sy - p.y, sz - p.z, ex - p.x, ey - p.y, ez - p.z, ex + q.x, ey + q.y, ez + q.z, sx + q.x, sy + q.y, sz + q.z, r, g, bl, a);
    }

    private static void renderQuad(VertexConsumer b, Matrix4f mat, double x1, double y1, double z1, double x2, double y2, double z2, double x3, double y3, double z3, double x4, double y4, double z4, int r, int g, int bl, int a) {
        b.addVertex(mat, (float) x1, (float) y1, (float) z1).setColor(r, g, bl, a);
        b.addVertex(mat, (float) x2, (float) y2, (float) z2).setColor(r, g, bl, a);
        b.addVertex(mat, (float) x3, (float) y3, (float) z3).setColor(r, g, bl, a);
        b.addVertex(mat, (float) x4, (float) y4, (float) z4).setColor(r, g, bl, a);
    }

    private static void drawSimpleFace(VertexConsumer b, Matrix4f mat, double x, double y, double z, Direction face, float r, float g, float bl, float a, float s) {
        int ir = (int) (r * 255), ig = (int) (g * 255), ibl = (int) (bl * 255), ia = (int) (a * 255);
        if (ia <= 5) return;

        Vec3 n = Vec3.atLowerCornerOf(face.getNormal());
        Vec3 a1 = (Math.abs(n.y) > 0.5) ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 a2 = n.cross(a1).normalize();
        a1 = n.cross(a2).normalize();

        float x1 = (float) (a1.x * s), y1 = (float) (a1.y * s), z1 = (float) (a1.z * s);
        float x2 = (float) (a2.x * s), y2 = (float) (a2.y * s), z2 = (float) (a2.z * s);

        b.addVertex(mat, (float) (x - x1 - x2), (float) (y - y1 - y2), (float) (z - z1 - z2)).setColor(ir, ig, ibl, ia);
        b.addVertex(mat, (float) (x + x1 - x2), (float) (y + y1 - y2), (float) (z + z1 - z2)).setColor(ir, ig, ibl, ia);
        b.addVertex(mat, (float) (x + x1 + x2), (float) (y + y1 + y2), (float) (z + z1 + z2)).setColor(ir, ig, ibl, ia);
        b.addVertex(mat, (float) (x - x1 + x2), (float) (y - y1 + y2), (float) (z - z1 + z2)).setColor(ir, ig, ibl, ia);
    }

    private static boolean isLinkInvalid(Level level, StaticLink link) {
        if (link.sourceDimension().equals(level.dimension()) && level.isLoaded(link.sourcePos())) {
            return level.getBlockState(link.sourcePos()).isAir();
        }
        return false;
    }

    private static ItemStack getActiveConfigurator(Minecraft mc) {
        if (mc.player == null) return ItemStack.EMPTY;
        ItemStack main = mc.player.getMainHandItem();
        if (main.getItem() instanceof LinkConfiguratorItem) return main;
        ItemStack off = mc.player.getOffhandItem();
        if (off.getItem() instanceof LinkConfiguratorItem) return off;
        return ItemStack.EMPTY;
    }
}