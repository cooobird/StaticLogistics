package com.coobird.staticlogistics.client.render;

import com.coobird.staticlogistics.Staticlogistics;
import com.coobird.staticlogistics.common.init.SLDataComponents;
import com.coobird.staticlogistics.common.item.LinkConfiguratorItem;
import com.coobird.staticlogistics.core.ClientLinkCache;
import com.coobird.staticlogistics.core.FaceConfig;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

import java.util.List;

@EventBusSubscriber(modid = Staticlogistics.MODID, value = Dist.CLIENT)
public class LinkWorldRenderer {

    private static final float PIXEL = 1.0f / 16.0f;
    private static final float TUBE_WIDTH = PIXEL / 2.0f;

    public static final RenderType PIPE_TRANSLUCENT = RenderType.create(
        "pipe_translucent",
        DefaultVertexFormat.POSITION_COLOR,
        VertexFormat.Mode.QUADS,
        1536,
        false,
        false,
        RenderType.CompositeState.builder()
            .setShaderState(RenderType.RENDERTYPE_GUI_SHADER)
            .setTransparencyState(RenderType.TRANSLUCENT_TRANSPARENCY)
            .setWriteMaskState(RenderType.COLOR_WRITE)
            .setDepthTestState(RenderType.LEQUAL_DEPTH_TEST)
            .setCullState(RenderType.NO_CULL)
            .createCompositeState(false)
    );

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        ItemStack stack = getActiveConfigurator(mc);
        if (stack.isEmpty()) return;

        PoseStack poseStack = event.getPoseStack();
        Vec3 cam = event.getCamera().getPosition();
        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();

        VertexConsumer builder = bufferSource.getBuffer(PIPE_TRANSLUCENT);

        poseStack.pushPose();
        poseStack.translate(-cam.x, -cam.y, -cam.z);
        Matrix4f mat = poseStack.last().pose();

        BlockPos firstPos = stack.get(SLDataComponents.FIRST_POS.get());
        Direction firstFace = stack.get(SLDataComponents.FIRST_FACE.get());

        if (firstPos != null && firstFace != null) {
            double fx = firstPos.getX() + 0.5 + firstFace.getStepX() * 0.51;
            double fy = firstPos.getY() + 0.5 + firstFace.getStepY() * 0.51;
            double fz = firstPos.getZ() + 0.5 + firstFace.getStepZ() * 0.51;
            drawSimpleFace(builder, mat, fx, fy, fz, firstFace, 0.3f, 1.0f, 0.3f, 0.6f);
        }

        String activeGroupId = stack.getOrDefault(SLDataComponents.SELECTED_GROUP.get(), "1");
        List<StaticLink> nearby = ClientLinkCache.getLinksInArea(mc.player.blockPosition(), 64);

        for (StaticLink link : nearby) {
            if (!link.groupId().equals(activeGroupId)) continue;

            AABB linkBounds = AABB.encapsulatingFullBlocks(link.sourcePos(), link.destPos());
            if (!event.getFrustum().isVisible(linkBounds)) continue;

            double sx = link.sourcePos().getX() + 0.5 + link.sourceFace().getStepX() * 0.505;
            double sy = link.sourcePos().getY() + 0.5 + link.sourceFace().getStepY() * 0.505;
            double sz = link.sourcePos().getZ() + 0.5 + link.sourceFace().getStepZ() * 0.505;
            double ex = link.destPos().getX() + 0.5 + link.destFace().getStepX() * 0.505;
            double ey = link.destPos().getY() + 0.5 + link.destFace().getStepY() * 0.505;
            double ez = link.destPos().getZ() + 0.5 + link.destFace().getStepZ() * 0.505;

            drawSimpleFace(builder, mat, sx, sy, sz, link.sourceFace(), 0.2f, 0.6f, 1.0f, 0.4f);
            drawSimpleFace(builder, mat, ex, ey, ez, link.destFace(), 1.0f, 0.8f, 0.2f, 0.4f);

            renderDirectionalPipes(builder, mat, link, sx, sy, sz, ex, ey, ez);
        }

        poseStack.popPose();
        bufferSource.endBatch(PIPE_TRANSLUCENT);
    }

    private static void renderDirectionalPipes(VertexConsumer builder, Matrix4f mat, StaticLink link, double sx, double sy, double sz, double ex, double ey, double ez) {
        FaceConfig config = ClientLinkCache.getFaceConfig(link.sourcePos(), link.sourceFace());
        if (config == null) return;

        boolean hasIn = false, hasOut = false;
        int inColor = 0xFF3498DB, outColor = 0xFFF1C40F;

        for (TransferType type : TransferType.values()) {
            if (!link.hasType(type)) continue;
            FaceConfig.SideData data = config.getSettings(type);
            if (data.mode.allowsInput()) {
                hasIn = true;
                inColor = data.getRenderColor(type);
            }
            if (data.mode.allowsOutput()) {
                hasOut = true;
                outColor = data.getRenderColor(type);
            }
        }

        if (!hasIn && !hasOut) return;

        if (hasIn && hasOut) {
            Vec3 norm = Vec3.atLowerCornerOf(link.sourceFace().getNormal());
            Vec3 offset = (Math.abs(norm.y) > 0.5) ? new Vec3(0.08, 0, 0) : new Vec3(0, 0.08, 0);
            drawPipe(builder, mat, sx + offset.x, sy + offset.y, sz + offset.z, ex + offset.x, ey + offset.y, ez + offset.z, TUBE_WIDTH, inColor);
            drawPipe(builder, mat, sx - offset.x, sy - offset.y, sz - offset.z, ex - offset.x, ey - offset.y, ez - offset.z, TUBE_WIDTH, outColor);
        } else {
            drawPipe(builder, mat, sx, sy, sz, ex, ey, ez, TUBE_WIDTH, hasIn ? inColor : outColor);
        }
    }

    private static void drawPipe(VertexConsumer b, Matrix4f mat, double sx, double sy, double sz, double ex, double ey, double ez, float w, int color) {
        int a = (color >> 24) & 0xFF, r = (color >> 16) & 0xFF, g = (color >> 8) & 0xFF, bl = color & 0xFF;
        if (a == 0) a = 255;

        float dx = (float) (ex - sx), dy = (float) (ey - sy), dz = (float) (ez - sz);

        float v1x, v1y, v1z;
        if (Math.abs(dx) < 0.001f && Math.abs(dz) < 0.001f) {
            v1x = w;
            v1y = 0;
            v1z = 0;
        } else {
            float hLen = (float) Math.sqrt(dx * dx + dz * dz);
            v1x = (dz / hLen) * w;
            v1y = 0;
            v1z = (-dx / hLen) * w;
        }

        float v2x = dy * v1z - dz * v1y, v2y = dz * v1x - dx * v1z, v2z = dx * v1y - dy * v1x;
        float f = w / (float) Math.sqrt(v2x * v2x + v2y * v2y + v2z * v2z);
        v2x *= f;
        v2y *= f;
        v2z *= f;


        renderQuadPoints(b, mat, sx + v1x, sy + v1y, sz + v1z, ex + v1x, ey + v1y, ez + v1z, ex + v2x, ey + v2y, ez + v2z, sx + v2x, sy + v2y, sz + v2z, r, g, bl, a);
        renderQuadPoints(b, mat, sx - v1x, sy - v1y, sz - v1z, ex - v1x, ey - v1y, ez - v1z, ex - v2x, ey - v2y, ez - v2z, sx - v2x, sy - v2y, sz - v2z, r, g, bl, a);
        renderQuadPoints(b, mat, sx + v1x, sy + v1y, sz + v1z, ex + v1x, ey + v1y, ez + v1z, ex - v2x, ey - v2y, ez - v2z, sx - v2x, sy - v2y, sz - v2z, r, g, bl, a);
        renderQuadPoints(b, mat, sx - v1x, sy - v1y, sz - v1z, ex - v1x, ey - v1y, ez - v1z, ex + v2x, ey + v2y, ez + v2z, sx + v2x, sy + v2y, sz + v2z, r, g, bl, a);
    }

    private static void renderQuadPoints(VertexConsumer b, Matrix4f mat, double x1, double y1, double z1, double x2, double y2, double z2, double x3, double y3, double z3, double x4, double y4, double z4, int r, int g, int bl, int a) {
        b.addVertex(mat, (float) x1, (float) y1, (float) z1).setColor(r, g, bl, a);
        b.addVertex(mat, (float) x2, (float) y2, (float) z2).setColor(r, g, bl, a);
        b.addVertex(mat, (float) x3, (float) y3, (float) z3).setColor(r, g, bl, a);
        b.addVertex(mat, (float) x4, (float) y4, (float) z4).setColor(r, g, bl, a);
    }

    private static void drawSimpleFace(VertexConsumer b, Matrix4f mat, double x, double y, double z, Direction face, float r, float g, float bl, float a) {
        float s = 0.42f;
        int ir = (int) (r * 255), ig = (int) (g * 255), ibl = (int) (bl * 255), ia = (int) (a * 255);
        Vec3 norm = Vec3.atLowerCornerOf(face.getNormal());
        Vec3 axis1 = (Math.abs(norm.y) > 0.5) ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 axis2 = norm.cross(axis1).normalize();
        axis1 = norm.cross(axis2).normalize();

        float x1 = (float) (axis1.x * s), y1 = (float) (axis1.y * s), z1 = (float) (axis1.z * s);
        float x2 = (float) (axis2.x * s), y2 = (float) (axis2.y * s), z2 = (float) (axis2.z * s);

        b.addVertex(mat, (float) x - x1 - x2, (float) y - y1 - y2, (float) z - z1 - z2).setColor(ir, ig, ibl, ia);
        b.addVertex(mat, (float) x + x1 - x2, (float) y + y1 - y2, (float) z + z1 - z2).setColor(ir, ig, ibl, ia);
        b.addVertex(mat, (float) x + x1 + x2, (float) y + y1 + y2, (float) z + z1 + z2).setColor(ir, ig, ibl, ia);
        b.addVertex(mat, (float) x - x1 + x2, (float) y - y1 + y2, (float) z - z1 + z2).setColor(ir, ig, ibl, ia);
    }

    private static ItemStack getActiveConfigurator(Minecraft mc) {
        if (mc.player == null) return ItemStack.EMPTY;
        ItemStack main = mc.player.getMainHandItem();
        if (main.getItem() instanceof LinkConfiguratorItem) return main;
        ItemStack off = mc.player.getOffhandItem();
        return (off.getItem() instanceof LinkConfiguratorItem) ? off : ItemStack.EMPTY;
    }
}