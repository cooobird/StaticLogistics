package com.coobird.staticlogistics.client.render;

import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;

/**
 * 世界覆盖层的统一可见性判定。
 *
 * <p>{@link LevelRenderer#isSectionCompiled(BlockPos)} 直接复用原版当前区块渲染器的
 * 可用范围；视锥判定则避免提交摄像机当前看不到的覆盖层顶点。
 */
public final class WorldOverlayVisibility {
    private final LevelRenderer levelRenderer;
    private final Frustum frustum;

    public WorldOverlayVisibility(LevelRenderer levelRenderer, Frustum frustum) {
        if (levelRenderer == null) {
            throw new IllegalArgumentException("Level renderer must not be null");
        }
        if (frustum == null) {
            throw new IllegalArgumentException("Render frustum must not be null");
        }
        this.levelRenderer = levelRenderer;
        this.frustum = frustum;
    }

    public boolean isBlockVisible(BlockPos position) {
        return levelRenderer.isSectionCompiled(position)
            && frustum.isVisible(new AABB(position));
    }

    /**
     * 连接两端都必须位于原版当前可渲染区段内；曲线包围盒还必须与视锥相交。
     */
    public boolean isConnectionVisible(BlockPos first, BlockPos second) {
        if (!levelRenderer.isSectionCompiled(first) || !levelRenderer.isSectionCompiled(second)) {
            return false;
        }
        return frustum.isVisible(new AABB(
            Math.min(first.getX(), second.getX()),
            Math.min(first.getY(), second.getY()),
            Math.min(first.getZ(), second.getZ()),
            Math.max(first.getX(), second.getX()) + 1.0D,
            Math.max(first.getY(), second.getY()) + 1.0D,
            Math.max(first.getZ(), second.getZ()) + 1.0D
        ));
    }
}
