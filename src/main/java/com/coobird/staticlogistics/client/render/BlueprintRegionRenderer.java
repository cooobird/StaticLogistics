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
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

import java.util.HashSet;
import java.util.Set;

@EventBusSubscriber(modid = Staticlogistics.MODID, value = Dist.CLIENT)
public class BlueprintRegionRenderer {

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

        ItemStack bp = getBlueprint(mc);
        if (bp == null) return;

        BlueprintData data = bp.getOrDefault(SLDataComponents.BLUEPRINT_DATA.get(), BlueprintData.EMPTY);
        String anchorS = bp.getOrDefault(SLDataComponents.BLUEPRINT_ANCHOR.get(), "");
        String previewS = bp.getOrDefault(SLDataComponents.BLUEPRINT_PREVIEW_ANCHOR.get(), "");
        int rot = bp.getOrDefault(SLDataComponents.BLUEPRINT_PREVIEW_ROTATION.get(), 0);

        if (!previewS.isEmpty() && !data.isEmpty()) {
            renderPreview(event, data, parsePos(previewS), rot);
            return;
        }

        BlockPos a, b, green;
        if (!data.isEmpty()) {
            green = data.anchor();
            a = green;
            b = green;
            for (BlueprintData.BlockEntry e : data.blocks()) {
                BlockPos abs = green.offset(e.relativePos());
                a = new BlockPos(Math.min(a.getX(), abs.getX()), Math.min(a.getY(), abs.getY()), Math.min(a.getZ(), abs.getZ()));
                b = new BlockPos(Math.max(b.getX(), abs.getX()), Math.max(b.getY(), abs.getY()), Math.max(b.getZ(), abs.getZ()));
            }
        } else if (!anchorS.isEmpty()) {
            a = parsePos(anchorS);
            green = a;
            if (a == null) return;
            HitResult hit = mc.hitResult;
            if (!(hit instanceof BlockHitResult bh) || hit.getType() == HitResult.Type.MISS) return;
            b = bh.getBlockPos();
        } else return;

        renderBox(event, a, b, green, 0.3f, 0.6f, 1f, 0.7f);
    }

    private static void renderBox(RenderLevelStageEvent event, BlockPos p1, BlockPos p2, BlockPos green,
                                  float r, float g, float bl, float alpha) {
        PoseStack ps = event.getPoseStack();
        Vec3 cam = event.getCamera().getPosition();
        Minecraft mc = Minecraft.getInstance();
        double maxD2 = mc.options.renderDistance().get() * 16 * 0.4;
        maxD2 *= maxD2;
        if (p1.distToCenterSqr(cam.x, cam.y, cam.z) > maxD2 && p2.distToCenterSqr(cam.x, cam.y, cam.z) > maxD2) return;

        MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();
        VertexConsumer bc = buf.getBuffer(BP_BOX);
        ps.pushPose();
        ps.translate(-cam.x, -cam.y, -cam.z);
        Matrix4f mat = ps.last().pose();

        float R = 0.025f;
        float x1 = Math.min(p1.getX(), p2.getX()) - R, y1 = Math.min(p1.getY(), p2.getY()) - R, z1 = Math.min(p1.getZ(), p2.getZ()) - R;
        float x2 = Math.max(p1.getX(), p2.getX()) + 1 + R, y2 = Math.max(p1.getY(), p2.getY()) + 1 + R, z2 = Math.max(p1.getZ(), p2.getZ()) + 1 + R;

        drawEdges12(bc, mat, x1, y1, z1, x2, y2, z2, R, r, g, bl, alpha);
        LogisticsRenderHelper.drawFrame(bc, mat, p2, 1f, 0.3f, 0.3f, 0.9f);
        if (green != null) LogisticsRenderHelper.drawFrame(bc, mat, green, 0.3f, 1f, 0.3f, 0.9f);

        ps.popPose();
        buf.endBatch(BP_BOX);
    }

    private static void renderPreview(RenderLevelStageEvent event, BlueprintData data, BlockPos anchor, int rot) {
        // 计算选区包围盒
        int cx1 = Integer.MAX_VALUE, cy1 = Integer.MAX_VALUE, cz1 = Integer.MAX_VALUE;
        int cx2 = Integer.MIN_VALUE, cy2 = Integer.MIN_VALUE, cz2 = Integer.MIN_VALUE;
        // 构建绝对坐标 → BlockEntry 的快速查找表
        var entryMap = new java.util.HashMap<BlockPos, BlueprintData.BlockEntry>();
        for (BlueprintData.BlockEntry e : data.blocks()) {
            BlockPos abs = BlueprintItem.rotateRelToAbs(e.relativePos(), anchor, rot);
            entryMap.put(abs, e);
            if (abs.getX() < cx1) cx1 = abs.getX();
            if (abs.getY() < cy1) cy1 = abs.getY();
            if (abs.getZ() < cz1) cz1 = abs.getZ();
            if (abs.getX() > cx2) cx2 = abs.getX();
            if (abs.getY() > cy2) cy2 = abs.getY();
            if (abs.getZ() > cz2) cz2 = abs.getZ();
        }

        PoseStack ps = event.getPoseStack();
        Vec3 cam = event.getCamera().getPosition();
        double maxD2 = Minecraft.getInstance().options.renderDistance().get() * 16 * 0.4;
        maxD2 *= maxD2;
        if (anchor.distToCenterSqr(cam.x, cam.y, cam.z) > maxD2
            && new BlockPos(cx2, cy2, cz2).distToCenterSqr(cam.x, cam.y, cam.z) > maxD2) return;

        Minecraft mc = Minecraft.getInstance();
        MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();
        VertexConsumer bc = buf.getBuffer(BP_BOX);
        ps.pushPose();
        ps.translate(-cam.x, -cam.y, -cam.z);
        Matrix4f mat = ps.last().pose();
        float pulse = (float) Math.sin(System.currentTimeMillis() / 200.0) * 0.03f;
        double time = System.currentTimeMillis() / 1000.0;

        // 选区外框
        float R = 0.025f;
        drawEdges12(bc, mat, cx1 - R, cy1 - R, cz1 - R, cx2 + 1 + R, cy2 + 1 + R, cz2 + 1 + R,
            R, 0.3f, 0.6f, 1f, 0.7f);
        // 锚点高亮
        LogisticsRenderHelper.drawFrame(bc, mat, anchor, 1f, 1f, 1f, 0.9f);

        Set<BlockPos> renderedFrames = new HashSet<>();
        for (var entry : entryMap.entrySet()) {
            BlockPos absPos = entry.getKey();
            BlueprintData.BlockEntry be = entry.getValue();
            double d2 = absPos.distToCenterSqr(cam.x, cam.y, cam.z);
            boolean vis = d2 <= maxD2;

            // 方块线框
            if (vis && renderedFrames.add(absPos)) {
                LogisticsRenderHelper.drawFrame(bc, mat, absPos, 1f, 1f, 1f, 0.25f);
            }

            // 面指示器
            for (var faceEntry : be.faces().entrySet()) {
                Direction rotatedFace = BlueprintItem.rotateDirection(faceEntry.getKey(), rot);
                BlueprintData.FaceEntry fe = faceEntry.getValue();
                CompoundTag ft = fe.faceConfig();
                int inCh = ft.getInt("input_channel");
                int outCh = ft.getInt("output_channel");
                boolean hasIn = ft.getBoolean("global_input");
                boolean hasOut = ft.getBoolean("global_output");
                if (vis) {
                    LogisticsRenderHelper.drawFaceStatus(bc, mat, absPos, rotatedFace,
                        inCh, outCh, hasIn, hasOut, pulse);
                }

                // 流动粒子
                if (hasOut && outCh >= 1 && outCh <= 16) {
                    for (BlockPos relLink : be.linkedTo()) {
                        BlockPos linkAbs = BlueprintItem.rotateRelToAbs(relLink, anchor, rot);
                        BlueprintData.BlockEntry dstEntry = entryMap.get(linkAbs);
                        if (dstEntry == null) continue;
                        // 在目标方块上找匹配的输入面（同频道）
                        for (var dstFaceEntry : dstEntry.faces().entrySet()) {
                            CompoundTag dstFt = dstFaceEntry.getValue().faceConfig();
                            if (!dstFt.getBoolean("global_input")) continue;
                            if (dstFt.getInt("input_channel") != outCh) continue;
                            Direction dstRotatedFace = BlueprintItem.rotateDirection(dstFaceEntry.getKey(), rot);
                            Vec3 s = Vec3.atCenterOf(absPos)
                                .add(Vec3.atLowerCornerOf(rotatedFace.getNormal()).scale(0.52));
                            Vec3 t = Vec3.atCenterOf(linkAbs)
                                .add(Vec3.atLowerCornerOf(dstRotatedFace.getNormal()).scale(0.52));
                            LogisticsRenderHelper.drawFlowParticles(bc, mat, s, t, outCh, time);
                        }
                    }
                }
            }
        }

        ps.popPose();
        buf.endBatch(BP_BOX);
    }

    private static ItemStack getBlueprint(Minecraft mc) {
        if (mc.player == null) return null;
        ItemStack m = mc.player.getMainHandItem();
        if (m.getItem() instanceof BlueprintItem) return m;
        ItemStack o = mc.player.getOffhandItem();
        return o.getItem() instanceof BlueprintItem ? o : null;
    }

    private static BlockPos parsePos(String s) {
        if (s == null || s.isEmpty()) return null;
        String[] p = s.split(", ");
        if (p.length != 3) return null;
        try {
            return new BlockPos(Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2]));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static void drawEdges12(VertexConsumer b, Matrix4f mat,
                                    float x1, float y1, float z1, float x2, float y2, float z2,
                                    float radius, float r, float g, float bl, float a) {
        LogisticsRenderHelper.drawEdge(b, mat, x1, y1, z1, x2, y1, z1, radius, r, g, bl, a);
        LogisticsRenderHelper.drawEdge(b, mat, x1, y1, z2, x2, y1, z2, radius, r, g, bl, a);
        LogisticsRenderHelper.drawEdge(b, mat, x1, y2, z1, x2, y2, z1, radius, r, g, bl, a);
        LogisticsRenderHelper.drawEdge(b, mat, x1, y2, z2, x2, y2, z2, radius, r, g, bl, a);
        LogisticsRenderHelper.drawEdge(b, mat, x1, y1, z1, x1, y2, z1, radius, r, g, bl, a);
        LogisticsRenderHelper.drawEdge(b, mat, x2, y1, z1, x2, y2, z1, radius, r, g, bl, a);
        LogisticsRenderHelper.drawEdge(b, mat, x1, y1, z2, x1, y2, z2, radius, r, g, bl, a);
        LogisticsRenderHelper.drawEdge(b, mat, x2, y1, z2, x2, y2, z2, radius, r, g, bl, a);
        LogisticsRenderHelper.drawEdge(b, mat, x1, y1, z1, x1, y1, z2, radius, r, g, bl, a);
        LogisticsRenderHelper.drawEdge(b, mat, x2, y1, z1, x2, y1, z2, radius, r, g, bl, a);
        LogisticsRenderHelper.drawEdge(b, mat, x1, y2, z1, x1, y2, z2, radius, r, g, bl, a);
        LogisticsRenderHelper.drawEdge(b, mat, x2, y2, z1, x2, y2, z2, radius, r, g, bl, a);
    }
}
