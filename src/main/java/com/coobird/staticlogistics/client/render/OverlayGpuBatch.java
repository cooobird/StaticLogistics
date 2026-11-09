package com.coobird.staticlogistics.client.render;

import com.mojang.blaze3d.vertex.BufferBuilder;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.LinkedHashMap;
import java.util.Map;

/// 按空间分组缓存几何，仅在批次内容变化后重新上传。
final class OverlayGpuBatch implements AutoCloseable {
    final Vec3 origin;
    final Map<LogisticsRenderHelper.LinkMesh, AABB> meshes = new LinkedHashMap<>();
    AABB bounds;
    private VertexBuffer buffer;

    OverlayGpuBatch(Vec3 origin) {
        this.origin = origin;
    }

    void add(LogisticsRenderHelper.LinkMesh mesh, AABB box) {
        if (mesh == null || mesh.vertices().length == 0) return;
        if (meshes.putIfAbsent(mesh, box) != null) return;
        close();
        bounds = bounds == null ? box : bounds.minmax(box);
    }

    void remove(LogisticsRenderHelper.LinkMesh mesh) {
        if (meshes.remove(mesh) == null) return;
        close();
        bounds = null;
        for (AABB box : meshes.values()) bounds = bounds == null ? box : bounds.minmax(box);
    }

    boolean uploaded() {
        return buffer != null;
    }

    void draw(Matrix4f view, Matrix4f projection, Vec3 camera) {
        if (meshes.isEmpty()) return;
        if (buffer == null) {
            VertexBuffer uploaded = new VertexBuffer(VertexBuffer.Usage.STATIC);
            try {
                BufferBuilder builder = new BufferBuilder(1536);
                builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
                Matrix4f identity = new Matrix4f();
                for (var mesh : meshes.keySet()) mesh.drawRelative(builder, identity, origin);
                uploaded.bind();
                uploaded.upload(builder.end());
                buffer = uploaded;
            } catch (RuntimeException | Error failure) {
                uploaded.close();
                throw failure;
            } finally {
                VertexBuffer.unbind();
            }
        }
        /// 先用双精度计算相对坐标，再转换为 GPU 使用的局部单精度坐标。
        Matrix4f modelView = new Matrix4f(view).translate((float) (origin.x - camera.x), (float) (origin.y - camera.y), (float) (origin.z - camera.z));
        buffer.bind();
        try {
            buffer.drawWithShader(modelView, projection, GameRenderer.getPositionColorShader());
        } finally {
            VertexBuffer.unbind();
        }
    }

    @Override
    public void close() {
        if (buffer != null) {
            buffer.close();
            buffer = null;
        }
    }
}
