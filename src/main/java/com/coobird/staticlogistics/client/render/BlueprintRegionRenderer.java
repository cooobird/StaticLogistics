package com.coobird.staticlogistics.client.render;

import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.client.gui.screen.LinkConfiguratorScreen;
import com.coobird.staticlogistics.content.item.BlueprintModeHandler;
import com.coobird.staticlogistics.logistics.SLDataComponents;
import com.coobird.staticlogistics.logistics.blueprint.BlueprintData;
import com.coobird.staticlogistics.logistics.blueprint.BlueprintGeometry;
import com.coobird.staticlogistics.logistics.node.ChannelPort;
import com.coobird.staticlogistics.logistics.node.persistence.ConfigKeys;
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
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

/// 在世界中渲染蓝图选区和放置预览。
@EventBusSubscriber(modid = StaticLogistics.MODID, value = Dist.CLIENT)
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
        if (bp == null || mc.screen instanceof LinkConfiguratorScreen) return;

        BlueprintData data = bp.getOrDefault(SLDataComponents.BLUEPRINT_DATA.get(), BlueprintData.EMPTY);
        String previewS = bp.getOrDefault(SLDataComponents.BLUEPRINT_PREVIEW_ANCHOR.get(), "");
        int rot = bp.getOrDefault(SLDataComponents.BLUEPRINT_PREVIEW_ROTATION.get(), 0);

        String dimension = mc.level.dimension().location().toString();
        if (!previewS.isEmpty() && dimension.equals(bp.getOrDefault(SLDataComponents.BLUEPRINT_PREVIEW_DIMENSION.get(), "")) && !data.isEmpty()) {
            BlockPos anchor = parsePos(previewS);
            if (anchor != null) renderPreview(event, data, anchor, rot);
        }

    }

    private static void renderPreview(RenderLevelStageEvent event, BlueprintData data, BlockPos anchor, int rot) {
        BlockPos c2 = BlueprintGeometry.rotateToAbsolute(
            data.corner2().subtract(data.anchor()), anchor, rot);
        int cx1 = Math.min(anchor.getX(), c2.getX()), cy1 = Math.min(anchor.getY(), c2.getY()), cz1 = Math.min(anchor.getZ(), c2.getZ());
        int cx2 = Math.max(anchor.getX(), c2.getX()), cy2 = Math.max(anchor.getY(), c2.getY()), cz2 = Math.max(anchor.getZ(), c2.getZ());

        /// 构建绝对坐标 → BlockEntry 的快速查找表
        var entryMap = new HashMap<BlockPos, BlueprintData.BlockEntry>();
        for (BlueprintData.BlockEntry e : data.blocks()) {
            BlockPos abs = BlueprintGeometry.rotateToAbsolute(e.relativePos(), anchor, rot);
            entryMap.put(abs, e);
            cx1 = Math.min(cx1, abs.getX());
            cy1 = Math.min(cy1, abs.getY());
            cz1 = Math.min(cz1, abs.getZ());
            cx2 = Math.max(cx2, abs.getX());
            cy2 = Math.max(cy2, abs.getY());
            cz2 = Math.max(cz2, abs.getZ());
        }

        PoseStack ps = event.getPoseStack();
        Vec3 cam = event.getCamera().getPosition();
        Minecraft mc = Minecraft.getInstance();
        WorldOverlayVisibility visibility = new WorldOverlayVisibility(mc.levelRenderer, event.getFrustum());
        BlockPos corner2Abs = new BlockPos(cx2, cy2, cz2);
        boolean anyVisible = visibility.isBlockVisible(anchor)
            || visibility.isBlockVisible(corner2Abs);
        if (!anyVisible) {
            for (BlockPos position : entryMap.keySet()) {
                if (visibility.isBlockVisible(position)) {
                    anyVisible = true;
                    break;
                }
            }
        }
        if (!anyVisible) return;

        MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();
        VertexConsumer bc = buf.getBuffer(BP_BOX);
        ps.pushPose();
        ps.translate(-cam.x, -cam.y, -cam.z);
        Matrix4f mat = ps.last().pose();

        /// 选区外框
        float R = 0.025f;
        drawEdges12(bc, mat, cx1 - R, cy1 - R, cz1 - R, cx2 + 1 + R, cy2 + 1 + R, cz2 + 1 + R, R, 0.3f, 0.6f, 1f, 0.7f);
        /// 锚点（起点）绿色高亮
        if (visibility.isBlockVisible(anchor)) {
            LogisticsRenderHelper.drawFrame(
                bc, mat, anchor, 0.3f, 1f, 0.3f, 0.9f);
        }
        /// 终点（对角）红色高亮
        if (visibility.isBlockVisible(corner2Abs)) {
            LogisticsRenderHelper.drawFrame(
                bc, mat, corner2Abs, 1f, 0.3f, 0.3f, 0.9f);
        }

        Set<BlockPos> renderedFrames = new HashSet<>();
        for (var entry : entryMap.entrySet()) {
            BlockPos absPos = entry.getKey();
            BlueprintData.BlockEntry be = entry.getValue();
            boolean vis = visibility.isBlockVisible(absPos);

            /// 方块线框（锚点和终点已单独高亮，跳过以免覆盖颜色）
            if (vis && renderedFrames.add(absPos) && !absPos.equals(anchor) && !absPos.equals(corner2Abs)) {
                LogisticsRenderHelper.drawFrame(bc, mat, absPos, 1f, 1f, 1f, 0.25f);
            }

            /// 面指示器
            for (var faceEntry : be.faces().entrySet()) {
                Direction rotatedFace = BlueprintGeometry.rotateDirection(faceEntry.getKey().face(), rot);
                BlueprintData.FaceEntry fe = faceEntry.getValue();
                CompoundTag ft = fe.faceConfig();
                boolean hasIn = ft.getBoolean(ConfigKeys.GLOBAL_INPUT);
                boolean hasOut = ft.getBoolean(ConfigKeys.GLOBAL_OUTPUT);
                if (vis) {
                    LogisticsRenderHelper.drawFaceStatus(bc, mat, absPos, rotatedFace,
                        LogisticsRenderHelper.INPUT_COLOR,
                        LogisticsRenderHelper.OUTPUT_COLOR,
                        hasIn, hasOut, 0.0F);
                }

                /// 蓝图中的连接也使用静态线，不生成随时间更新的粒子。
                if (hasOut) {
                    for (BlueprintData.LinkEntry exactLink : fe.linkedTo()) {
                        BlockPos linkAbs = BlueprintGeometry.rotateToAbsolute(
                            exactLink.relativePos(), anchor, rot);
                        if (!visibility.isConnectionVisible(absPos, linkAbs)) continue;
                        BlueprintData.BlockEntry dstEntry = entryMap.get(linkAbs);
                        if (dstEntry == null) continue;
                        BlueprintData.FaceEntry destination = dstEntry.faces().get(new ChannelPort(exactLink.face(), exactLink.channel()));
                        if (destination != null) {
                            CompoundTag dstFt = destination.faceConfig();
                            if (!dstFt.getBoolean(ConfigKeys.GLOBAL_INPUT)) continue;
                            Direction dstRotatedFace = BlueprintGeometry.rotateDirection(exactLink.face(), rot);
                            Vec3 s = Vec3.atCenterOf(absPos)
                                .add(Vec3.atLowerCornerOf(rotatedFace.getNormal()).scale(0.52));
                            Vec3 t = Vec3.atCenterOf(linkAbs)
                                .add(Vec3.atLowerCornerOf(dstRotatedFace.getNormal()).scale(0.52));
                            LogisticsRenderHelper.drawStaticLink(
                                bc, mat, s, t,
                                LogisticsRenderHelper.FLOW_COLOR, 0.62F, 0.018F);
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
        if (BlueprintModeHandler.isActive(m)) return m;
        ItemStack o = mc.player.getOffhandItem();
        return BlueprintModeHandler.isActive(o) ? o : null;
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
