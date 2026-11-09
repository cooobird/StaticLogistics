package com.coobird.staticlogistics.client.render;

import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.content.item.BlueprintModeHandler;
import com.coobird.staticlogistics.content.item.LinkConfiguratorItem;
import com.coobird.staticlogistics.content.item.ToolMode;
import com.coobird.staticlogistics.integration.sable.DynamicNodeSpace;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

import java.util.List;


/// 仅绘制配置器本次尚未建链的选面反馈。
@EventBusSubscriber(modid = StaticLogistics.MODID, value = Dist.CLIENT)
public final class StoredSelectionRenderer {
    private static float renderPartialTick;

    public static final RenderType PIPE_XRAY = RenderType.create(
        "pipe_xray",
        DefaultVertexFormat.POSITION_COLOR,
        VertexFormat.Mode.QUADS,
        1536,
        false,
        false,
        RenderType.CompositeState.builder()
            .setShaderState(new RenderType.ShaderStateShard(
                GameRenderer::getPositionColorShader))
            .setTransparencyState(RenderType.TRANSLUCENT_TRANSPARENCY)
            .setWriteMaskState(RenderType.COLOR_WRITE)
            .setDepthTestState(RenderType.NO_DEPTH_TEST)
            .setCullState(RenderType.NO_CULL)
            .setLayeringState(RenderType.POLYGON_OFFSET_LAYERING)
            .createCompositeState(false)
    );

    private StoredSelectionRenderer() {
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage()
            != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) return;
        ItemStack stack = getActiveConfigurator(minecraft);
        if (stack.isEmpty() || BlueprintModeHandler.isActive(stack)) return;
        LinkConfiguratorItem.ToolSettings settings = ((LinkConfiguratorItem) stack.getItem()).getSettings(stack);
        if (settings.storedMode() == null || settings.storedNodes().isEmpty()) return;

        PoseStack poseStack = event.getPoseStack();
        Vec3 camera = event.getCamera().getPosition();
        MultiBufferSource.BufferSource buffers =
            minecraft.renderBuffers().bufferSource();
        VertexConsumer faceBuffer = buffers.getBuffer(PIPE_XRAY);
        ResourceKey<Level> dimension = minecraft.level.dimension();
        WorldOverlayVisibility visibility = new WorldOverlayVisibility(
            minecraft.levelRenderer, event.getFrustum());
        renderPartialTick =
            event.getPartialTick().getGameTimeDeltaPartialTick(true);

        poseStack.pushPose();
        poseStack.translate(-camera.x, -camera.y, -camera.z);
        Matrix4f matrix = poseStack.last().pose();

        renderStoredNodes(settings.storedNodes(), settings.storedMode(), dimension, matrix, faceBuffer, visibility);
        buffers.endBatch(PIPE_XRAY);

        poseStack.popPose();
    }

    private static void renderStoredNodes(
        List<LogisticsNode> nodes, ToolMode mode,
        ResourceKey<Level> dimension,
        Matrix4f matrix,
        VertexConsumer buffer,
        WorldOverlayVisibility visibility
    ) {
        Level level = Minecraft.getInstance().level;
        if (level == null) return;
        boolean input = mode == ToolMode.LINK_AS_INSERT;
        int faceColor = input
            ? LogisticsRenderHelper.INPUT_COLOR
            : LogisticsRenderHelper.OUTPUT_COLOR;
        for (LogisticsNode node : nodes) {
            if (!node.gPos().dimension().equals(dimension)) continue;
            BlockPos position = node.gPos().pos();
            Vec3 center =
                DynamicNodeSpace.blockCenter(level, position, renderPartialTick);
            if (!visibility.isBlockVisible(BlockPos.containing(center))) continue;
            Vec3 normal = DynamicNodeSpace.faceNormal(
                level, position, node.face(), renderPartialTick);
            Vec3 axis1 = DynamicNodeSpace.faceAxis1(
                level, position, node.face(), renderPartialTick);
            Vec3 axis2 = DynamicNodeSpace.faceAxis2(
                level, position, node.face(), renderPartialTick);
            LogisticsRenderHelper.drawFaceQuad(
                buffer, matrix,
                center.add(normal.scale(0.514)), normal, axis1, axis2,
                faceColor, 0.58F, 0.30F, 0.0F, 1.0F);
        }
    }

    private static ItemStack getActiveConfigurator(Minecraft minecraft) {
        if (minecraft.player == null) return ItemStack.EMPTY;
        ItemStack mainHand = minecraft.player.getMainHandItem();
        if (isLinkTool(mainHand)) return mainHand;
        ItemStack offHand = minecraft.player.getOffhandItem();
        return isLinkTool(offHand) ? offHand : ItemStack.EMPTY;
    }

    private static boolean isLinkTool(ItemStack stack) {
        return !stack.isEmpty()
            && stack.getItem() instanceof LinkConfiguratorItem;
    }

}
