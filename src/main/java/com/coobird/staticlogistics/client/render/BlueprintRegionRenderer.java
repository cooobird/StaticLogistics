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

        LogisticsRenderHelper.drawBoxEdges(bc, mat, x1, y1, z1, x2, y2, z2, r, g, bl, alpha);
        LogisticsRenderHelper.drawFrame(bc, mat, p2, 1f, 0.3f, 0.3f, 0.9f);
        if (green != null) LogisticsRenderHelper.drawFrame(bc, mat, green, 0.3f, 1f, 0.3f, 0.9f);

        ps.popPose();
        buf.endBatch(BP_BOX);
    }

    private static void renderPreview(RenderLevelStageEvent event, BlueprintData data, BlockPos anchor, int rot) {
        int cx1 = Integer.MAX_VALUE, cy1 = Integer.MAX_VALUE, cz1 = Integer.MAX_VALUE;
        int cx2 = Integer.MIN_VALUE, cy2 = Integer.MIN_VALUE, cz2 = Integer.MIN_VALUE;
        for (BlueprintData.BlockEntry e : data.blocks()) {
            BlockPos abs = BlueprintItem.rotateRelToAbs(e.relativePos(), anchor, rot);
            if (abs.getX() < cx1) cx1 = abs.getX();
            if (abs.getY() < cy1) cy1 = abs.getY();
            if (abs.getZ() < cz1) cz1 = abs.getZ();
            if (abs.getX() > cx2) cx2 = abs.getX();
            if (abs.getY() > cy2) cy2 = abs.getY();
            if (abs.getZ() > cz2) cz2 = abs.getZ();
        }

        PoseStack ps = event.getPoseStack();
        Vec3 cam = event.getCamera().getPosition();
        if (anchor.distToCenterSqr(cam.x, cam.y, cam.z) > 1024) return;

        Minecraft mc = Minecraft.getInstance();
        MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();
        VertexConsumer bc = buf.getBuffer(BP_BOX);
        ps.pushPose();
        ps.translate(-cam.x, -cam.y, -cam.z);
        Matrix4f mat = ps.last().pose();

        float R = 0.025f;
        LogisticsRenderHelper.drawBoxEdges(bc, mat, cx1 - R, cy1 - R, cz1 - R, cx2 + 1 + R, cy2 + 1 + R, cz2 + 1 + R,
            0.3f, 0.6f, 1f, 0.7f);
        LogisticsRenderHelper.drawFrame(bc, mat, anchor, 1f, 1f, 1f, 0.9f);

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
        try { return new BlockPos(Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2])); }
        catch (NumberFormatException e) { return null; }
    }
}
