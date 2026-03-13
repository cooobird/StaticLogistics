package com.coobird.staticlogistics.client.render;

import com.coobird.staticlogistics.Staticlogistics;
import com.coobird.staticlogistics.client.ClientLinkCache;
import com.coobird.staticlogistics.common.init.SLDataComponents;
import com.coobird.staticlogistics.common.item.LinkConfiguratorItem;
import com.coobird.staticlogistics.core.FaceConfig;
import com.coobird.staticlogistics.core.StaticLink;
import com.coobird.staticlogistics.core.TransferType;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.List;

@EventBusSubscriber(modid = Staticlogistics.MODID, value = Dist.CLIENT)
public class LinkWorldRenderer extends RenderStateShard {

    public LinkWorldRenderer(String name, Runnable setup, Runnable clear) {
        super(name, setup, clear);
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        ItemStack stack = mc.player.getMainHandItem();
        if (!(stack.getItem() instanceof LinkConfiguratorItem)) {
            stack = mc.player.getOffhandItem();
        }
        if (!(stack.getItem() instanceof LinkConfiguratorItem)) return;

        Vec3 cam = event.getCamera().getPosition();
        PoseStack ps = event.getPoseStack();
        MultiBufferSource.BufferSource buffer = mc.renderBuffers().bufferSource();

        if (stack.has(SLDataComponents.FIRST_POS.get()) && stack.has(SLDataComponents.FIRST_FACE.get())) {
            BlockPos firstPos = stack.get(SLDataComponents.FIRST_POS.get());
            Direction firstFace = stack.get(SLDataComponents.FIRST_FACE.get());

            if (firstPos != null && !mc.level.getBlockState(firstPos).isAir()) {
                renderSourceSelection(ps, buffer, cam, firstPos, firstFace);
            }
        }

        String activeGroupId = stack.getOrDefault(SLDataComponents.SELECTED_GROUP.get(), "default");
        List<StaticLink> nearby = ClientLinkCache.getLinksInArea(mc.player.blockPosition(), 64);

        for (StaticLink link : nearby) {
            if (link.groupId().equals(activeGroupId)) {
                if (!mc.level.getBlockState(link.sourcePos()).isAir() && !mc.level.getBlockState(link.destPos()).isAir()) {
                    renderDynamicLink(ps, buffer, cam, link);
                }
            }
        }
        buffer.endBatch();
    }

    private static void renderSourceSelection(PoseStack ps, MultiBufferSource buffer, Vec3 cam, BlockPos pos, Direction face) {
        ps.pushPose();
        ps.translate(pos.getX() - cam.x + 0.5 + face.getStepX() * 0.507,
            pos.getY() - cam.y + 0.5 + face.getStepY() * 0.507,
            pos.getZ() - cam.z + 0.5 + face.getStepZ() * 0.507);
        ps.mulPose(face.getRotation());
        ps.mulPose(Axis.XP.rotationDegrees(-90.0F));
        Matrix4f mat = ps.last().pose();

        VertexConsumer builder = buffer.getBuffer(RenderType.gui());
        float r = 1.0f, g = 0.7f, b = 0.0f, a = 0.4f;
        builder.addVertex(mat, -0.5f, -0.5f, 0).setColor(r, g, b, a);
        builder.addVertex(mat, 0.5f, -0.5f, 0).setColor(r, g, b, a);
        builder.addVertex(mat, 0.5f, 0.5f, 0).setColor(r, g, b, a);
        builder.addVertex(mat, -0.5f, 0.5f, 0).setColor(r, g, b, a);
        ps.popPose();
    }

    private static void renderDynamicLink(PoseStack ps, MultiBufferSource buffer, Vec3 cam, StaticLink link) {
        drawFaceOutline(ps, buffer, cam, link.sourcePos(), link.sourceFace());
        drawFaceOutline(ps, buffer, cam, link.destPos(), link.destFace());

        FaceConfig config = ClientLinkCache.getFaceConfig(link.sourcePos(), link.sourceFace());
        Vec3 startBase = getAbsoluteFaceCenter(link.sourcePos(), link.sourceFace());
        Vec3 endBase = getAbsoluteFaceCenter(link.destPos(), link.destFace());

        for (TransferType type : TransferType.values()) {
            if (!link.hasType(type)) continue;

            int color = 0xFF888888;
            if (config != null) {
                color = config.getSettings(type).getRenderColor(type);
            }

            Vec3 offset = type.getVisualOffset(link.sourceFace());
            Vec3 start = startBase.add(offset).subtract(cam);
            Vec3 end = endBase.add(offset).subtract(cam);

            VertexConsumer tubeBuilder = buffer.getBuffer(RenderType.lightning());
            drawCubeTube(ps, tubeBuilder, start, end, 0.015f, color);
        }
    }

    private static void drawCubeTube(PoseStack ps, VertexConsumer b, Vec3 s, Vec3 e, float w, int color) {
        Vector3f line = new Vector3f((float) (e.x - s.x), (float) (e.y - s.y), (float) (e.z - s.z));
        if (line.lengthSquared() < 0.0001f) return;

        int a = (color >> 24) & 0xFF;
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int bl = color & 0xFF;

        Vector3f a1 = new Vector3f(line).cross(Math.abs(line.y()) < 0.9 ? new Vector3f(0, 1, 0) : new Vector3f(1, 0, 0)).normalize().mul(w);
        Matrix4f mat = ps.last().pose();

        Vector3f a2 = new Vector3f(line).cross(a1).normalize().mul(w);
        Vector3f v1 = new Vector3f(a1).add(a2);
        Vector3f v2 = new Vector3f(a1).sub(a2);
        Vector3f v3 = new Vector3f(a1).mul(-1).sub(a2);
        Vector3f v4 = new Vector3f(a1).mul(-1).add(a2);

        drawQuad(b, mat, s, e, v1, v2, r, g, bl, a);
        drawQuad(b, mat, s, e, v2, v3, r, g, bl, a);
        drawQuad(b, mat, s, e, v3, v4, r, g, bl, a);
        drawQuad(b, mat, s, e, v4, v1, r, g, bl, a);
    }

    private static void drawQuad(VertexConsumer b, Matrix4f mat, Vec3 s, Vec3 e, Vector3f offS, Vector3f offE, int r, int g, int bl, int a) {
        b.addVertex(mat, (float) (s.x + offS.x), (float) (s.y + offS.y), (float) (s.z + offS.z)).setColor(r, g, bl, a);
        b.addVertex(mat, (float) (e.x + offS.x), (float) (e.y + offS.y), (float) (e.z + offS.z)).setColor(r, g, bl, a);
        b.addVertex(mat, (float) (e.x + offE.x), (float) (e.y + offE.y), (float) (e.z + offE.z)).setColor(r, g, bl, a);
        b.addVertex(mat, (float) (s.x + offE.x), (float) (s.y + offE.y), (float) (s.z + offE.z)).setColor(r, g, bl, a);
    }

    private static void drawFaceOutline(PoseStack ps, MultiBufferSource buffer, Vec3 cam, BlockPos pos, Direction face) {
        ps.pushPose();
        ps.translate(pos.getX() - cam.x + 0.5 + face.getStepX() * 0.505,
            pos.getY() - cam.y + 0.5 + face.getStepY() * 0.505,
            pos.getZ() - cam.z + 0.5 + face.getStepZ() * 0.505);
        ps.mulPose(face.getRotation());
        ps.mulPose(Axis.XP.rotationDegrees(-90.0F));
        Matrix4f mat = ps.last().pose();

        VertexConsumer solidBuilder = buffer.getBuffer(RenderType.gui());
        solidBuilder.addVertex(mat, -0.495f, -0.495f, 0).setColor(1f, 1f, 0f, 0.15f);
        solidBuilder.addVertex(mat, 0.495f, -0.495f, 0).setColor(1f, 1f, 0f, 0.15f);
        solidBuilder.addVertex(mat, 0.495f, 0.495f, 0).setColor(1f, 1f, 0f, 0.15f);
        solidBuilder.addVertex(mat, -0.495f, 0.495f, 0).setColor(1f, 1f, 0f, 0.15f);
        ps.popPose();
    }

    private static Vec3 getAbsoluteFaceCenter(BlockPos p, Direction f) {
        return new Vec3(p.getX() + 0.5 + f.getStepX() * 0.505, p.getY() + 0.5 + f.getStepY() * 0.505, p.getZ() + 0.5 + f.getStepZ() * 0.505);
    }
}