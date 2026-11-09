package com.coobird.staticlogistics.client.render;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.GlobalPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.RenderShape;
import net.minecraftforge.client.model.data.ModelData;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.Map;

/// 使用世界中的方块状态和方块实体绘制模型，拾取与显示共用变换。
public final class GuiBlockModel {
    private GuiBlockModel() {
    }

    /// 同一视口的模型共享缓冲批次，避免每个方块单独提交。
    public static void renderScene(GuiGraphics graphics, Map<GlobalPos, Matrix4f> models) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        graphics.flush();
        var buffers = mc.renderBuffers().bufferSource();
        /// 视口使用 Y 翻转的 GUI 坐标，光源也使用相机空间，避免继承世界或物品图标的残留光照。
        RenderSystem.setShaderLights(new Vector3f(-0.35F, -1, 0.7F).normalize(),
            new Vector3f(0.5F, -0.3F, -1).normalize());
        RenderSystem.enableDepthTest();
        float fogStart = RenderSystem.getShaderFogStart(), fogEnd = RenderSystem.getShaderFogEnd();
        RenderSystem.setShaderFogStart(Float.MAX_VALUE);
        RenderSystem.setShaderFogEnd(Float.MAX_VALUE);
        RandomSource random = RandomSource.create();
        try {
            for (var entry : models.entrySet()) {
                GlobalPos position = entry.getKey();
                if (!mc.level.dimension().equals(position.dimension())
                    || !mc.level.getChunkSource().hasChunk(position.pos().getX() >> 4,
                    position.pos().getZ() >> 4)) continue;
                var state = mc.level.getBlockState(position.pos());
                if (state.isAir()) continue;
                graphics.pose().pushPose();
                try {
                    graphics.pose().mulPoseMatrix(entry.getValue());
                    var entity = mc.level.getBlockEntity(position.pos());
                    if (state.getRenderShape() == RenderShape.MODEL) {
                        var dispatcher = mc.getBlockRenderer();
                        var model = dispatcher.getBlockModel(state);
                        ModelData data = model.getModelData(mc.level, position.pos(), state,
                            entity == null ? ModelData.EMPTY : entity.getModelData());
                        long seed = state.getSeed(position.pos());
                        random.setSeed(seed);
                        /// 与世界共用模型数据、染色、环境光遮蔽和逐面亮度；不使用物品渲染替代方块。
                        for (var renderType : model.getRenderTypes(state, random, data)) {
                            graphics.pose().pushPose();
                            try {
                                dispatcher.getModelRenderer().tesselateBlock(mc.level, model, state, position.pos(),
                                    graphics.pose(), buffers.getBuffer(renderType), false, random, seed,
                                    OverlayTexture.NO_OVERLAY, data, renderType);
                            } finally {
                                graphics.pose().popPose();
                            }
                        }
                    } else if (entity == null && state.getRenderShape() != RenderShape.INVISIBLE) {
                        mc.getBlockRenderer().renderSingleBlock(state, graphics.pose(), buffers,
                            LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, null);
                    }
                    if (entity != null) mc.getBlockEntityRenderDispatcher().renderItem(entity, graphics.pose(), buffers,
                        LevelRenderer.getLightColor(mc.level, position.pos()), OverlayTexture.NO_OVERLAY);
                } finally {
                    graphics.pose().popPose();
                }
            }
            buffers.endBatch();
        } finally {
            RenderSystem.setShaderFogStart(fogStart);
            RenderSystem.setShaderFogEnd(fogEnd);
            Lighting.setupFor3DItems();
        }
    }

}
