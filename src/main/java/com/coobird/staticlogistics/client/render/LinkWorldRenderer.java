package com.coobird.staticlogistics.client.render;

import com.coobird.staticlogistics.Staticlogistics;
import com.coobird.staticlogistics.client.ClientLinkCache;
import com.coobird.staticlogistics.common.item.LinkConfiguratorItem;
import com.coobird.staticlogistics.core.FaceConfig;
import com.coobird.staticlogistics.core.StaticLink;
import com.coobird.staticlogistics.storage.GroupService;
import com.coobird.staticlogistics.transfer.TransferType;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
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
import java.util.List;

@EventBusSubscriber(modid = Staticlogistics.MODID, value = Dist.CLIENT)
public class LinkWorldRenderer {
    private static final float PIXEL = 1.0f / 16.0f;
    private static final float TUBE_WIDTH = PIXEL / 2.0f;

    public static final RenderType PIPE_XRAY = RenderType.create(
        "pipe_xray", DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 1536, false, false,
        RenderType.CompositeState.builder()
            .setShaderState(RenderType.POSITION_COLOR_SHADER)
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

        LinkConfiguratorItem linkerItem = (LinkConfiguratorItem) stack.getItem();
        LinkConfiguratorItem.ToolSettings settings = linkerItem.getSettings(stack, mc.level);

        List<StaticLink> groupLinks = ClientLinkCache.getLinksByGroup(settings.group());
        if (groupLinks.isEmpty() && settings.firstPos() == null) return;

        PoseStack poseStack = event.getPoseStack();
        Vec3 cam = event.getCamera().getPosition();
        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();
        VertexConsumer builder = bufferSource.getBuffer(PIPE_XRAY);

        poseStack.pushPose();
        poseStack.translate(-cam.x, -cam.y, -cam.z);
        Matrix4f mat = poseStack.last().pose();
        float pulse = (float) Math.sin(System.currentTimeMillis() / 200.0) * 0.03f;
        ResourceKey<Level> currentDim = mc.level.dimension();

        if (settings.firstPos() != null && settings.firstFace() != null) {
            double fx = settings.firstPos().getX() + 0.5 + settings.firstFace().getStepX() * 0.51;
            double fy = settings.firstPos().getY() + 0.5 + settings.firstFace().getStepY() * 0.51;
            double fz = settings.firstPos().getZ() + 0.5 + settings.firstFace().getStepZ() * 0.51;
            drawSimpleFace(builder, mat, fx, fy, fz, settings.firstFace(), 0.3f, 1.0f, 0.3f, 0.8f, 0.45f + pulse);
        }

        for (StaticLink link : groupLinks) {
            if (isLinkInvalid(mc.level, link)) {
                ClientLinkCache.removeLinkById(link.linkId());
                continue;
            }

            if (!GroupService.canAccess(link, mc.player)) continue;

            boolean srcIn = link.sourceDimension().equals(currentDim);
            boolean dstIn = link.destDimension().equals(currentDim);
            if (!srcIn && !dstIn) continue;

            AABB bounds = (srcIn && dstIn) ? AABB.encapsulatingFullBlocks(link.sourcePos(), link.destPos())
                : new AABB(srcIn ? link.sourcePos() : link.destPos()).inflate(1.0);

            if (!event.getFrustum().isVisible(bounds) || bounds.distanceToSqr(mc.player.position()) > 65536) continue;

            double sx = link.sourcePos().getX() + 0.5 + link.sourceFace().getStepX() * 0.505;
            double sy = link.sourcePos().getY() + 0.5 + link.sourceFace().getStepY() * 0.505;
            double sz = link.sourcePos().getZ() + 0.5 + link.sourceFace().getStepZ() * 0.505;
            double ex = link.destPos().getX() + 0.5 + link.destFace().getStepX() * 0.505;
            double ey = link.destPos().getY() + 0.5 + link.destFace().getStepY() * 0.505;
            double ez = link.destPos().getZ() + 0.5 + link.destFace().getStepZ() * 0.505;

            if (srcIn) drawSimpleFace(builder, mat, sx, sy, sz, link.sourceFace(), 0.0f, 0.8f, 1.0f, 0.6f, 0.45f + pulse);
            if (dstIn) drawSimpleFace(builder, mat, ex, ey, ez, link.destFace(), 1.0f, 0.6f, 0.0f, 0.6f, 0.45f + pulse);
            if (srcIn && dstIn) renderDirectionalPipes(builder, mat, link, sx, sy, sz, ex, ey, ez);
        }

        poseStack.popPose();
        bufferSource.endBatch(PIPE_XRAY);
    }

    private static boolean isLinkInvalid(Level level, StaticLink link) {
        ResourceKey<Level> dim = level.dimension();
        if (link.sourceDimension().equals(dim) && level.isLoaded(link.sourcePos())) {
            if (level.getBlockState(link.sourcePos()).isAir()) return true;
        }
        if (link.destDimension().equals(dim) && level.isLoaded(link.destPos())) {
            if (level.getBlockState(link.destPos()).isAir()) return true;
        }
        return false;
    }

    private static void renderDirectionalPipes(VertexConsumer builder, Matrix4f mat, StaticLink link, double sx, double sy, double sz, double ex, double ey, double ez) {
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

        Vec3 n = Vec3.atLowerCornerOf(link.sourceFace().getNormal());
        Vec3 off = (Math.abs(n.y) > 0.5) ? new Vec3(0.08, 0, 0) : new Vec3(0, 0, 0.08);

        if (!in.isEmpty() && !out.isEmpty()) {
            drawPipe(builder, mat, sx + off.x, sy + off.y, sz + off.z, ex + off.x, ey + off.y, ez + off.z, TUBE_WIDTH, in.get(0));
            drawPipe(builder, mat, sx - off.x, sy - off.y, sz - off.z, ex - off.x, ey - off.y, ez - off.z, TUBE_WIDTH, out.get(0));
        } else {
            drawPipe(builder, mat, sx, sy, sz, ex, ey, ez, TUBE_WIDTH, !in.isEmpty() ? in.get(0) : out.get(0));
        }
    }

    private static void drawPipe(VertexConsumer b, Matrix4f mat, double sx, double sy, double sz, double ex, double ey, double ez, float w, int c) {
        int a = (c >> 24) & 0xFF; if (a == 0) a = 255;
        int r = (c >> 16) & 0xFF, g = (c >> 8) & 0xFF, bl = c & 0xFF;
        float dx = (float) (ex - sx), dy = (float) (ey - sy), dz = (float) (ez - sz);
        if (Math.abs(dx) < 0.001f && Math.abs(dy) < 0.001f && Math.abs(dz) < 0.001f) return;

        float v1x, v1y = 0, v1z;
        if (Math.abs(dx) < 0.001f && Math.abs(dz) < 0.001f) { v1x = w; v1z = 0; }
        else {
            float h = (float) Math.sqrt(dx * dx + dz * dz);
            v1x = (dz / h) * w; v1z = (-dx / h) * w;
        }
        float v2x = dy * v1z - dz * v1y, v2y = dz * v1x - dx * v1z, v2z = dx * v1y - dy * v1x;
        float f = w / (float) Math.sqrt(v2x * v2x + v2y * v2y + v2z * v2z);
        v2x *= f; v2y *= f; v2z *= f;

        renderQuad(b, mat, sx + v1x, sy + v1y, sz + v1z, ex + v1x, ey + v1y, ez + v1z, ex + v2x, ey + v2y, ez + v2z, sx + v2x, sy + v2y, sz + v2z, r, g, bl, a);
        renderQuad(b, mat, sx - v1x, sy - v1y, sz - v1z, ex - v1x, ey - v1y, ez - v1z, ex - v2x, ey - v2y, ez - v2z, sx - v2x, sy - v2y, sz - v2z, r, g, bl, a);
        renderQuad(b, mat, sx + v1x, sy + v1y, sz + v1z, ex + v1x, ey + v1y, ez + v1z, ex - v2x, ey - v2y, ez - v2z, sx - v2x, sy - v2y, sz - v2z, r, g, bl, a);
        renderQuad(b, mat, sx - v1x, sy - v1y, sz - v1z, ex - v1x, ey - v1y, ez - v1z, ex + v2x, ey + v2y, ez + v2z, sx + v2x, sy + v2y, sz + v2z, r, g, bl, a);
    }

    private static void renderQuad(VertexConsumer b, Matrix4f mat, double x1, double y1, double z1, double x2, double y2, double z2, double x3, double y3, double z3, double x4, double y4, double z4, int r, int g, int bl, int a) {
        b.addVertex(mat, (float) x1, (float) y1, (float) z1).setColor(r, g, bl, a);
        b.addVertex(mat, (float) x2, (float) y2, (float) z2).setColor(r, g, bl, a);
        b.addVertex(mat, (float) x3, (float) y3, (float) z3).setColor(r, g, bl, a);
        b.addVertex(mat, (float) x4, (float) y4, (float) z4).setColor(r, g, bl, a);
    }

    private static void drawSimpleFace(VertexConsumer b, Matrix4f mat, double x, double y, double z, Direction face, float r, float g, float bl, float a, float s) {
        int ir = (int) (r * 255), ig = (int) (g * 255), ibl = (int) (bl * 255), ia = (int) (a * 255);
        Vec3 n = Vec3.atLowerCornerOf(face.getNormal());
        Vec3 a1 = (Math.abs(n.y) > 0.5) ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 a2 = n.cross(a1).normalize();
        a1 = n.cross(a2).normalize();
        float x1 = (float) (a1.x * s), y1 = (float) (a1.y * s), z1 = (float) (a1.z * s);
        float x2 = (float) (a2.x * s), y2 = (float) (a2.y * s), z2 = (float) (a2.z * s);
        b.addVertex(mat, (float) x - x1 - x2, (float) y - y1 - y2, (float) z - z1 - z2).setColor(ir, ig, ibl, ia);
        b.addVertex(mat, (float) x + x1 - x2, (float) y + y1 - y2, (float) z + z1 - z2).setColor(ir, ig, ibl, ia);
        b.addVertex(mat, (float) x + x1 + x2, (float) y + y1 + y2, (float) z + z1 + z2).setColor(ir, ig, ibl, ia);
        b.addVertex(mat, (float) x - x1 + x2, (float) y - y1 + y2, (float) z - z1 + z2).setColor(ir, ig, ibl, ia);
    }

    private static ItemStack getActiveConfigurator(Minecraft mc) {
        if (mc.player == null) return ItemStack.EMPTY;
        ItemStack m = mc.player.getMainHandItem();
        if (m.getItem() instanceof LinkConfiguratorItem) return m;
        return (mc.player.getOffhandItem().getItem() instanceof LinkConfiguratorItem) ? mc.player.getOffhandItem() : ItemStack.EMPTY;
    }
}