package com.coobird.staticlogistics.client.render;

import com.coobird.staticlogistics.Staticlogistics;
import com.coobird.staticlogistics.api.BlueprintData;
import com.coobird.staticlogistics.item.BlueprintItem;
import com.coobird.staticlogistics.registry.SLDataComponents;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

@EventBusSubscriber(modid = Staticlogistics.MODID, value = Dist.CLIENT)
public class BlueprintRegionRenderer {

    private static final float EDGE_RADIUS = 0.025f;

    private static final RenderType BP_BOX = RenderType.create(
        "blueprint_box", DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 1536, false, false,
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

        ItemStack bp = getBlueprintStack(mc);
        if (bp == null) return;

        BlueprintData data = bp.getOrDefault(SLDataComponents.BLUEPRINT_DATA.get(), BlueprintData.EMPTY);
        String anchorStr = bp.getOrDefault(SLDataComponents.BLUEPRINT_ANCHOR.get(), "");
        String previewStr = bp.getOrDefault(SLDataComponents.BLUEPRINT_PREVIEW_ANCHOR.get(), "");
        int rotation = bp.getOrDefault(SLDataComponents.BLUEPRINT_PREVIEW_ROTATION.get(), 0);

        // 粘贴预览
        if (!previewStr.isEmpty() && !data.isEmpty()) {
            BlockPos previewAnchor = posFromString(previewStr);
            if (previewAnchor == null) return;
            renderPreview(event, mc, data, previewAnchor, rotation);
            return;
        }

        BlockPos a, b;
        BlockPos greenCorner;

        if (!data.isEmpty()) {
            greenCorner = data.anchor();
            a = greenCorner;
            b = greenCorner;
            for (BlueprintData.BlockEntry e : data.blocks()) {
                BlockPos abs = greenCorner.offset(e.relativePos());
                a = new BlockPos(Math.min(a.getX(), abs.getX()), Math.min(a.getY(), abs.getY()), Math.min(a.getZ(), abs.getZ()));
                b = new BlockPos(Math.max(b.getX(), abs.getX()), Math.max(b.getY(), abs.getY()), Math.max(b.getZ(), abs.getZ()));
            }
        } else if (!anchorStr.isEmpty()) {
            a = posFromString(anchorStr);
            greenCorner = a;
            if (a == null) return;
            HitResult hit = mc.hitResult;
            if (!(hit instanceof BlockHitResult blockHit) || hit.getType() == HitResult.Type.MISS) return;
            b = blockHit.getBlockPos();
        } else {
            return;
        }

        PoseStack poseStack = event.getPoseStack();
        Vec3 cam = event.getCamera().getPosition();
        int chunkDist = mc.options.renderDistance().get();
        double maxDistSq = chunkDist * 16 * 0.4;
        maxDistSq *= maxDistSq;
        if (a.distToCenterSqr(cam.x, cam.y, cam.z) > maxDistSq
            && b.distToCenterSqr(cam.x, cam.y, cam.z) > maxDistSq) return;

        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();
        VertexConsumer builder = bufferSource.getBuffer(BP_BOX);

        poseStack.pushPose();
        poseStack.translate(-cam.x, -cam.y, -cam.z);
        Matrix4f mat = poseStack.last().pose();

        float minX = Math.min(a.getX(), b.getX()) - EDGE_RADIUS;
        float minY = Math.min(a.getY(), b.getY()) - EDGE_RADIUS;
        float minZ = Math.min(a.getZ(), b.getZ()) - EDGE_RADIUS;
        float maxX = Math.max(a.getX(), b.getX()) + 1 + EDGE_RADIUS;
        float maxY = Math.max(a.getY(), b.getY()) + 1 + EDGE_RADIUS;
        float maxZ = Math.max(a.getZ(), b.getZ()) + 1 + EDGE_RADIUS;

        float r = 0.3f, g = 0.6f, bl = 1.0f, al = 0.7f;
        drawEdge(builder, mat, minX, minY, minZ, maxX, minY, minZ, r, g, bl, al);
        drawEdge(builder, mat, minX, minY, maxZ, maxX, minY, maxZ, r, g, bl, al);
        drawEdge(builder, mat, minX, maxY, minZ, maxX, maxY, minZ, r, g, bl, al);
        drawEdge(builder, mat, minX, maxY, maxZ, maxX, maxY, maxZ, r, g, bl, al);
        drawEdge(builder, mat, minX, minY, minZ, minX, maxY, minZ, r, g, bl, al);
        drawEdge(builder, mat, maxX, minY, minZ, maxX, maxY, minZ, r, g, bl, al);
        drawEdge(builder, mat, minX, minY, maxZ, minX, maxY, maxZ, r, g, bl, al);
        drawEdge(builder, mat, maxX, minY, maxZ, maxX, maxY, maxZ, r, g, bl, al);
        drawEdge(builder, mat, minX, minY, minZ, minX, minY, maxZ, r, g, bl, al);
        drawEdge(builder, mat, maxX, minY, minZ, maxX, minY, maxZ, r, g, bl, al);
        drawEdge(builder, mat, minX, maxY, minZ, minX, maxY, maxZ, r, g, bl, al);
        drawEdge(builder, mat, maxX, maxY, minZ, maxX, maxY, maxZ, r, g, bl, al);

        // 角框画在蓝色大框之后（同批次内后画=画在上面）
        drawFrame(builder, mat, b, 1.0f, 0.3f, 0.3f, 0.9f);
        if (greenCorner != null) {
            drawFrame(builder, mat, greenCorner, 0.3f, 1.0f, 0.3f, 0.9f);
        }

        poseStack.popPose();
        bufferSource.endBatch(BP_BOX);
    }

    // 粘贴预览渲染：整个复制范围的大框 + 锚点
    private static void renderPreview(RenderLevelStageEvent event, Minecraft mc,
                                      BlueprintData data, BlockPos anchor, int rotation) {
        // 计算旋转后的包围盒
        int cx1 = Integer.MAX_VALUE, cy1 = Integer.MAX_VALUE, cz1 = Integer.MAX_VALUE;
        int cx2 = Integer.MIN_VALUE, cy2 = Integer.MIN_VALUE, cz2 = Integer.MIN_VALUE;
        for (BlueprintData.BlockEntry entry : data.blocks()) {
            BlockPos abs = BlueprintItem.rotateRelToAbs(entry.relativePos(), anchor, rotation);
            if (abs.getX() < cx1) cx1 = abs.getX();
            if (abs.getY() < cy1) cy1 = abs.getY();
            if (abs.getZ() < cz1) cz1 = abs.getZ();
            if (abs.getX() > cx2) cx2 = abs.getX();
            if (abs.getY() > cy2) cy2 = abs.getY();
            if (abs.getZ() > cz2) cz2 = abs.getZ();
        }

        PoseStack poseStack = event.getPoseStack();
        Vec3 cam = event.getCamera().getPosition();
        if (anchor.distToCenterSqr(cam.x, cam.y, cam.z) > 1024) return;

        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();
        VertexConsumer builder = bufferSource.getBuffer(BP_BOX);

        poseStack.pushPose();
        poseStack.translate(-cam.x, -cam.y, -cam.z);
        Matrix4f mat = poseStack.last().pose();

        float minX = cx1 - EDGE_RADIUS;
        float minY = cy1 - EDGE_RADIUS;
        float minZ = cz1 - EDGE_RADIUS;
        float maxX = cx2 + 1 + EDGE_RADIUS;
        float maxY = cy2 + 1 + EDGE_RADIUS;
        float maxZ = cz2 + 1 + EDGE_RADIUS;

        drawBoxEdges(builder, mat, minX, minY, minZ, maxX, maxY, maxZ, 0.3f, 0.6f, 1.0f, 0.7f);
        drawFrame(builder, mat, anchor, 1.0f, 1.0f, 1.0f, 0.9f);

        poseStack.popPose();
        bufferSource.endBatch(BP_BOX);
    }

    private static void drawBoxEdges(VertexConsumer b, Matrix4f mat,
                                     float x1, float y1, float z1, float x2, float y2, float z2,
                                     float r, float g, float bl, float a) {
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

    /**
     * 画单条粗边，向外扩张 EDGE_RADIUS
     */
    private static void drawEdgeFlat(VertexConsumer b, Matrix4f mat,
                                     float x1, float y1, float z1, float x2, float y2, float z2,
                                     float r, float g, float bl, float a) {
        float dx = Math.abs(x2 - x1), dy = Math.abs(y2 - y1);
        float minX = x1, minY = y1, minZ = z1, maxX = x2, maxY = y2, maxZ = z2;
        if (dx > 0.1f) {
            minY -= EDGE_RADIUS;
            maxY += EDGE_RADIUS;
            minZ -= EDGE_RADIUS;
            maxZ += EDGE_RADIUS;
        } else if (dy > 0.1f) {
            minX -= EDGE_RADIUS;
            maxX += EDGE_RADIUS;
            minZ -= EDGE_RADIUS;
            maxZ += EDGE_RADIUS;
        } else {
            minX -= EDGE_RADIUS;
            maxX += EDGE_RADIUS;
            minY -= EDGE_RADIUS;
            maxY += EDGE_RADIUS;
        }
        renderBox(b, mat, minX, minY, minZ, maxX, maxY, maxZ, r, g, bl, a);
    }

    private static void drawEdge(VertexConsumer b, Matrix4f mat,
                                 float x1, float y1, float z1, float x2, float y2, float z2,
                                 float r, float g, float bl, float a) {
        float dx = Math.abs(x2 - x1), dy = Math.abs(y2 - y1);
        float minX = x1, minY = y1, minZ = z1, maxX = x2, maxY = y2, maxZ = z2;
        if (dx > 0.1f) {
            minY -= EDGE_RADIUS;
            maxY += EDGE_RADIUS;
            minZ -= EDGE_RADIUS;
            maxZ += EDGE_RADIUS;
        } else if (dy > 0.1f) {
            minX -= EDGE_RADIUS;
            maxX += EDGE_RADIUS;
            minZ -= EDGE_RADIUS;
            maxZ += EDGE_RADIUS;
        } else {
            minX -= EDGE_RADIUS;
            maxX += EDGE_RADIUS;
            minY -= EDGE_RADIUS;
            maxY += EDGE_RADIUS;
        }
        renderBox(b, mat, minX, minY, minZ, maxX, maxY, maxZ, r, g, bl, a);
    }

    /**
     * 在指定方块位置画 1x1 的线框
     */
    private static void drawFrame(VertexConsumer b, Matrix4f mat, BlockPos pos, float r, float g, float bl, float a) {
        float x1 = pos.getX() - EDGE_RADIUS;
        float y1 = pos.getY() - EDGE_RADIUS;
        float z1 = pos.getZ() - EDGE_RADIUS;
        float x2 = pos.getX() + 1 + EDGE_RADIUS;
        float y2 = pos.getY() + 1 + EDGE_RADIUS;
        float z2 = pos.getZ() + 1 + EDGE_RADIUS;
        drawBoxEdges(b, mat, x1, y1, z1, x2, y2, z2, r, g, bl, a);
    }

    private static void renderBox(VertexConsumer b, Matrix4f mat,
                                  float x1, float y1, float z1, float x2, float y2, float z2,
                                  float r, float g, float bl, float a) {
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

    private static ItemStack getBlueprintStack(Minecraft mc) {
        if (mc.player == null) return null;
        ItemStack main = mc.player.getMainHandItem();
        if (main.getItem() instanceof BlueprintItem) return main;
        ItemStack off = mc.player.getOffhandItem();
        if (off.getItem() instanceof BlueprintItem) return off;
        return null;
    }

    private static BlockPos posFromString(String s) {
        if (s == null || s.isEmpty()) return null;
        String[] parts = s.split(", ");
        if (parts.length != 3) return null;
        try {
            return new BlockPos(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
