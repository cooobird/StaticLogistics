package com.coobird.staticlogistics.client.render;

import PortLib.extensions.net.minecraft.world.item.ItemStack.PortItemStackExtension;
import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.content.item.BlueprintItem;
import com.coobird.staticlogistics.logistics.SLDataComponents;
import com.coobird.staticlogistics.logistics.blueprint.BlueprintData;
import com.coobird.staticlogistics.logistics.blueprint.BlueprintGeometry;
import com.coobird.staticlogistics.logistics.node.persistence.ConfigKeys;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.logging.LogUtils;
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
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.slf4j.Logger;

import java.util.HashSet;
import java.util.Set;

@Mod.EventBusSubscriber(modid = StaticLogistics.MODID, value = Dist.CLIENT)
public class BlueprintRegionRenderer {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final RenderType BP_BOX = RenderType.create(
        "staticlogistics:blueprint_box", DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 1536, false, false,
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

        try {
            BlueprintData data = PortItemStackExtension.getDataOrDefault(bp, SLDataComponents.BLUEPRINT_DATA.get(), BlueprintData.EMPTY);
            String anchorS = PortItemStackExtension.getDataOrDefault(bp, SLDataComponents.BLUEPRINT_ANCHOR.get(), "");
            String previewS = PortItemStackExtension.getDataOrDefault(bp, SLDataComponents.BLUEPRINT_PREVIEW_ANCHOR.get(), "");
            int rot = PortItemStackExtension.getDataOrDefault(bp, SLDataComponents.BLUEPRINT_PREVIEW_ROTATION.get(), 0);

            if (!previewS.isEmpty() && !data.isEmpty()) {
                renderPreview(event, data, parsePos(previewS), rot);
                return;
            }

            BlockPos a, b, green;
            if (!data.isEmpty()) {
                green = data.anchor();
                a = green;
                b = data.corner2();
            } else if (!anchorS.isEmpty()) {
                a = parsePos(anchorS);
                green = a;
                if (a == null) return;
                HitResult hit = mc.hitResult;
                if (!(hit instanceof BlockHitResult bh) || hit.getType() == HitResult.Type.MISS) return;
                b = bh.getBlockPos();
            } else return;

            renderBox(event, a, b, green, 0.3f, 0.6f, 1f, 0.7f);
        } catch (Exception e) {
            LOGGER.debug("Blueprint rendering failed", e);
        }
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
        BlockPos c2 = BlueprintGeometry.rotateToAbsolute(
            data.corner2().subtract(data.anchor()), anchor, rot);
        int cx1 = Math.min(anchor.getX(), c2.getX()), cy1 = Math.min(anchor.getY(), c2.getY()), cz1 = Math.min(anchor.getZ(), c2.getZ());
        int cx2 = Math.max(anchor.getX(), c2.getX()), cy2 = Math.max(anchor.getY(), c2.getY()), cz2 = Math.max(anchor.getZ(), c2.getZ());

        // 构建绝对坐标 → BlockEntry 的快速查找表
        var entryMap = new java.util.HashMap<BlockPos, BlueprintData.BlockEntry>();
        var relativeEntryMap = new java.util.HashMap<BlockPos, BlueprintData.BlockEntry>();
        for (BlueprintData.BlockEntry e : data.blocks()) {
            BlockPos abs = BlueprintGeometry.rotateToAbsolute(e.relativePos(), anchor, rot);
            entryMap.put(abs, e);
            relativeEntryMap.put(e.relativePos(), e);
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
                Direction rotatedFace = BlueprintGeometry.rotateDirection(faceEntry.getKey(), rot);
                BlueprintData.FaceEntry fe = faceEntry.getValue();
                CompoundTag ft = fe.faceConfig();
                int inCh = ft.getInt(ConfigKeys.INPUT_CHANNEL);
                int outCh = ft.getInt(ConfigKeys.OUTPUT_CHANNEL);
                boolean hasIn = ft.getBoolean(ConfigKeys.GLOBAL_INPUT);
                boolean hasOut = ft.getBoolean(ConfigKeys.GLOBAL_OUTPUT);
                if (vis) {
                    LogisticsRenderHelper.drawFaceStatus(bc, mat, absPos, rotatedFace,
                        inCh, outCh, hasIn, hasOut, pulse);
                }

                // 流动粒子
                if (hasOut && outCh >= 1 && outCh <= 16) {
                    for (BlueprintData.LinkEntry exactLink : BlueprintGeometry.resolveLinks(
                        be, fe, relativeEntryMap)) {
                        BlockPos linkAbs = BlueprintGeometry.rotateToAbsolute(
                            exactLink.relativePos(), anchor, rot);
                        BlueprintData.BlockEntry dstEntry = entryMap.get(linkAbs);
                        if (dstEntry == null) continue;
                        BlueprintData.FaceEntry destination = dstEntry.faces().get(exactLink.face());
                        if (destination != null) {
                            CompoundTag dstFt = destination.faceConfig();
                            if (!dstFt.getBoolean(ConfigKeys.GLOBAL_INPUT)
                                || dstFt.getInt(ConfigKeys.INPUT_CHANNEL) != outCh) continue;
                            Direction dstRotatedFace = BlueprintGeometry.rotateDirection(exactLink.face(), rot);
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

    @Nullable
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
