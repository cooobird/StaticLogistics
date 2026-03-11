package com.coobird.staticlogistics.client.render;

import com.coobird.staticlogistics.Staticlogistics;
import com.coobird.staticlogistics.client.ClientLinkCache;
import com.coobird.staticlogistics.common.init.ModDataComponents;
import com.coobird.staticlogistics.common.item.LinkConfiguratorItem;
import com.coobird.staticlogistics.core.StaticLink;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
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

    private static final ResourceLocation COBBLESTONE_TEX = ResourceLocation.withDefaultNamespace("textures/block/cobblestone.png");

    public LinkWorldRenderer(String name, Runnable setup, Runnable clear) { super(name, setup, clear); }

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

        int activeGroupId = stack.getOrDefault(ModDataComponents.SELECTED_GROUP.get(), 0);

        Vec3 cam = event.getCamera().getPosition();
        PoseStack ps = event.getPoseStack();
        MultiBufferSource.BufferSource buffer = mc.renderBuffers().bufferSource();

        List<StaticLink> nearby = ClientLinkCache.getLinksInArea(mc.player.blockPosition(), 64);

        for (StaticLink link : nearby) {
            if (link.groupId() == activeGroupId) {
                renderDirectLink(ps, buffer, cam, link);
            }
        }

        buffer.endBatch();
    }

    private static void renderDirectLink(PoseStack ps, MultiBufferSource buffer, Vec3 cam, StaticLink link) {
        drawFaceOutline(ps, buffer, cam, new BlockPosFace(link.sourcePos(), link.sourceFace()), true);
        drawFaceOutline(ps, buffer, cam, new BlockPosFace(link.destPos(), link.destFace()), true);

        Vec3 start = getAbsoluteFaceCenter(link.sourcePos(), link.sourceFace());
        Vec3 end = getAbsoluteFaceCenter(link.destPos(), link.destFace());

        VertexConsumer tubeBuilder = buffer.getBuffer(RenderType.lightning());
        drawCubeTube(ps, tubeBuilder, start.subtract(cam), end.subtract(cam), 0.03f, 100, 200, 255, 200);

        renderItemIcon(ps, buffer, start.subtract(cam), end.subtract(cam));
    }

    private static void renderItemIcon(PoseStack ps, MultiBufferSource buffer, Vec3 start, Vec3 end) {
        Vec3 mid = start.add(end).scale(0.5);

        ps.pushPose();
        ps.translate(mid.x, mid.y, mid.z);
        ps.mulPose(Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation());
        ps.mulPose(Axis.YP.rotationDegrees(180.0F));
        ps.scale(0.25f, 0.25f, 0.25f);

        Matrix4f mat = ps.last().pose();
        VertexConsumer b = buffer.getBuffer(RenderType.entityCutout(COBBLESTONE_TEX));

        b.addVertex(mat, -0.5f, -0.5f, 0).setColor(255, 255, 255, 255).setUv(0, 1).setOverlay(0).setLight(15728880).setNormal(0, 1, 0);
        b.addVertex(mat, 0.5f, -0.5f, 0).setColor(255, 255, 255, 255).setUv(1, 1).setOverlay(0).setLight(15728880).setNormal(0, 1, 0);
        b.addVertex(mat, 0.5f, 0.5f, 0).setColor(255, 255, 255, 255).setUv(1, 0).setOverlay(0).setLight(15728880).setNormal(0, 1, 0);
        b.addVertex(mat, -0.5f, 0.5f, 0).setColor(255, 255, 255, 255).setUv(0, 0).setOverlay(0).setLight(15728880).setNormal(0, 1, 0);

        ps.popPose();
    }

    private static void drawCubeTube(PoseStack ps, VertexConsumer b, Vec3 s, Vec3 e, float w, int r, int g, int bl, int a) {
        Vector3f line = new Vector3f((float)(e.x - s.x), (float)(e.y - s.y), (float)(e.z - s.z));
        if (line.lengthSquared() < 0.0001f) return;

        Vector3f a1 = new Vector3f(line).cross(Math.abs(line.y()) < 0.9 ? new Vector3f(0, 1, 0) : new Vector3f(1, 0, 0)).normalize().mul(w);
        Vector3f a2 = new Vector3f(line).cross(a1).normalize().mul(w);

        Matrix4f mat = ps.last().pose();

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
        b.addVertex(mat, (float)(s.x + offS.x), (float)(s.y + offS.y), (float)(s.z + offS.z)).setColor(r, g, bl, a);
        b.addVertex(mat, (float)(e.x + offS.x), (float)(e.y + offS.y), (float)(e.z + offS.z)).setColor(r, g, bl, a);
        b.addVertex(mat, (float)(e.x + offE.x), (float)(e.y + offE.y), (float)(e.z + offE.z)).setColor(r, g, bl, a);
        b.addVertex(mat, (float)(s.x + offE.x), (float)(s.y + offE.y), (float)(s.z + offE.z)).setColor(r, g, bl, a);
    }

    private static void drawFaceOutline(PoseStack ps, MultiBufferSource buffer, Vec3 cam, BlockPosFace pf, boolean focused) {
        ps.pushPose();
        ps.translate(pf.pos.getX() - cam.x + 0.5 + pf.face.getStepX() * 0.505,
            pf.pos.getY() - cam.y + 0.5 + pf.face.getStepY() * 0.505,
            pf.pos.getZ() - cam.z + 0.5 + pf.face.getStepZ() * 0.505);
        ps.mulPose(pf.face.getRotation());
        ps.mulPose(Axis.XP.rotationDegrees(-90.0F));
        Matrix4f mat = ps.last().pose();

        VertexConsumer solidBuilder = buffer.getBuffer(RenderType.gui());
        solidBuilder.addVertex(mat, -0.495f, -0.495f, 0).setColor(1f, 1f, 0f, 0.25f);
        solidBuilder.addVertex(mat, 0.495f, -0.495f, 0).setColor(1f, 1f, 0f, 0.25f);
        solidBuilder.addVertex(mat, 0.495f, 0.495f, 0).setColor(1f, 1f, 0f, 0.25f);
        solidBuilder.addVertex(mat, -0.495f, 0.495f, 0).setColor(1f, 1f, 0f, 0.25f);

        VertexConsumer lineBuilder = buffer.getBuffer(RenderType.lines());
        float s = 0.49f;
        lineBuilder.addVertex(mat, -s, -s, 0).setColor(1f, 1f, 1f, 1.0f).setNormal(0, 0, 1);
        lineBuilder.addVertex(mat, s, -s, 0).setColor(1f, 1f, 1f, 1.0f).setNormal(0, 0, 1);
        lineBuilder.addVertex(mat, s, -s, 0).setColor(1f, 1f, 1f, 1.0f).setNormal(0, 0, 1);
        lineBuilder.addVertex(mat, s, s, 0).setColor(1f, 1f, 1f, 1.0f).setNormal(0, 0, 1);
        lineBuilder.addVertex(mat, s, s, 0).setColor(1f, 1f, 1f, 1.0f).setNormal(0, 0, 1);
        lineBuilder.addVertex(mat, -s, s, 0).setColor(1f, 1f, 1f, 1.0f).setNormal(0, 0, 1);
        lineBuilder.addVertex(mat, -s, s, 0).setColor(1f, 1f, 1f, 1.0f).setNormal(0, 0, 1);
        lineBuilder.addVertex(mat, -s, -s, 0).setColor(1f, 1f, 1f, 1.0f).setNormal(0, 0, 1);

        ps.popPose();
    }

    private static Vec3 getAbsoluteFaceCenter(BlockPos p, Direction f) {
        return new Vec3(p.getX() + 0.5 + f.getStepX() * 0.505, p.getY() + 0.5 + f.getStepY() * 0.505, p.getZ() + 0.5 + f.getStepZ() * 0.505);
    }

    private record BlockPosFace(BlockPos pos, Direction face) {}
}