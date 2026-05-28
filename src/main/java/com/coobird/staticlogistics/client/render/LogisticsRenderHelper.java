package com.coobird.staticlogistics.client.render;

import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.client.util.RenderConstants;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * 物流渲染共享工具 —— 面指示器、粒子、光束、线框，供 LinkWorldRenderer 和 BlueprintRegionRenderer 共用。
 */
public class LogisticsRenderHelper {

    // ── 方块面着色指示器 ──

    public static void drawFaceStatus(VertexConsumer b, Matrix4f mat,
                                      BlockPos pos, Direction face,
                                      int inChannel, int outChannel,
                                      boolean hasIn, boolean hasOut, float pulse) {
        double px = pos.getX() + 0.5 + face.getStepX() * 0.508;
        double py = pos.getY() + 0.5 + face.getStepY() * 0.508;
        double pz = pos.getZ() + 0.5 + face.getStepZ() * 0.508;
        float size = 0.4f + pulse;
        int inIdx = clampChannel(inChannel);
        int outIdx = clampChannel(outChannel);

        if (hasIn && hasOut) {
            drawFaceQuad(b, mat, px, py, pz, face, inIdx, 0.85f, size, -0.5f, 0.45f);
            drawFaceQuad(b, mat, px, py, pz, face, outIdx, 0.85f, size, 0.5f, 0.45f);
        } else if (hasIn) {
            drawFaceQuad(b, mat, px, py, pz, face, inIdx, 0.85f, size, 0, 1f);
        } else if (hasOut) {
            drawFaceQuad(b, mat, px, py, pz, face, outIdx, 0.85f, size, 0, 1f);
        }
    }

    public static void drawFaceQuad(VertexConsumer b, Matrix4f mat,
                                    double x, double y, double z, Direction face,
                                    int colorIdx, float alpha, float size,
                                    float offset, float widthMult) {
        int color = RenderConstants.DYE_COLORS[colorIdx % RenderConstants.DYE_COLORS.length];
        float r = ((color >> 16) & 0xFF) / 255f, g = ((color >> 8) & 0xFF) / 255f, bl = (color & 0xFF) / 255f;
        int ir = (int) (r * 255), ig = (int) (g * 255), ib = (int) (bl * 255), ia = (int) (alpha * 255);

        Vec3 n = Vec3.atLowerCornerOf(face.getNormal());
        Vec3 a1 = (Math.abs(n.y) > 0.5) ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 a2 = n.cross(a1).normalize();
        a1 = n.cross(a2).normalize();
        double ox = a2.x * offset * size, oy = a2.y * offset * size, oz = a2.z * offset * size;
        float x1 = (float) (a1.x * size), y1 = (float) (a1.y * size), z1 = (float) (a1.z * size);
        float x2 = (float) (a2.x * size * widthMult), y2 = (float) (a2.y * size * widthMult), z2 = (float) (a2.z * size * widthMult);

        b.addVertex(mat, (float) (x + ox - x1 - x2), (float) (y + oy - y1 - y2), (float) (z + oz - z1 - z2)).setColor(ir, ig, ib, ia);
        b.addVertex(mat, (float) (x + ox + x1 - x2), (float) (y + oy + y1 - y2), (float) (z + oz + z1 - z2)).setColor(ir, ig, ib, ia);
        b.addVertex(mat, (float) (x + ox + x1 + x2), (float) (y + oy + y1 + y2), (float) (z + oz + z1 + z2)).setColor(ir, ig, ib, ia);
        b.addVertex(mat, (float) (x + ox - x1 + x2), (float) (y + oy - y1 + y2), (float) (z + oz - z1 + z2)).setColor(ir, ig, ib, ia);
    }

    // ── 流动粒子线 ──

    public static void drawFlowParticles(VertexConsumer b, Matrix4f mat,
                                         Vec3 from, Vec3 to, int channel, double time) {
        int idx = clampChannel(channel);
        int color = RenderConstants.DYE_COLORS[idx];
        float r = ((color >> 16) & 0xFF) / 255f, g = ((color >> 8) & 0xFF) / 255f, bl = (color & 0xFF) / 255f;

        double dx = to.x - from.x, dy = to.y - from.y, dz = to.z - from.z;
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (dist < 0.1) return;

        float speed = 2f;
        int cnt = (int) Math.min(12, Math.max(3, dist * 2));
        for (int i = 0; i < cnt; i++) {
            float progress = (float) (((time * speed + (double) i / cnt * dist) % dist) / dist);
            float px = (float) (from.x + dx * progress);
            float py = (float) (from.y + dy * progress);
            float pz = (float) (from.z + dz * progress);
            float s = 0.03f;
            renderBox(b, mat, px - s, py - s, pz - s, px + s, py + s, pz + s, r, g, bl, 0.85f);
        }
    }

    // 线框渲染
    public static void renderBox(VertexConsumer b, Matrix4f mat,
                                 float x1, float y1, float z1, float x2, float y2, float z2,
                                 float r, float g, float bl, float a) {
        int ir = (int) (r * 255), ig = (int) (g * 255), ib = (int) (bl * 255), ia = (int) (a * 255);
        b.addVertex(mat, x1, y1, z1).setColor(ir, ig, ib, ia);
        b.addVertex(mat, x2, y1, z1).setColor(ir, ig, ib, ia);
        b.addVertex(mat, x2, y2, z1).setColor(ir, ig, ib, ia);
        b.addVertex(mat, x1, y2, z1).setColor(ir, ig, ib, ia);
        b.addVertex(mat, x1, y1, z2).setColor(ir, ig, ib, ia);
        b.addVertex(mat, x1, y2, z2).setColor(ir, ig, ib, ia);
        b.addVertex(mat, x2, y2, z2).setColor(ir, ig, ib, ia);
        b.addVertex(mat, x2, y1, z2).setColor(ir, ig, ib, ia);
        b.addVertex(mat, x1, y1, z1).setColor(ir, ig, ib, ia);
        b.addVertex(mat, x1, y2, z1).setColor(ir, ig, ib, ia);
        b.addVertex(mat, x1, y2, z2).setColor(ir, ig, ib, ia);
        b.addVertex(mat, x1, y1, z2).setColor(ir, ig, ib, ia);
        b.addVertex(mat, x2, y1, z1).setColor(ir, ig, ib, ia);
        b.addVertex(mat, x2, y1, z2).setColor(ir, ig, ib, ia);
        b.addVertex(mat, x2, y2, z2).setColor(ir, ig, ib, ia);
        b.addVertex(mat, x2, y2, z1).setColor(ir, ig, ib, ia);
        b.addVertex(mat, x1, y1, z1).setColor(ir, ig, ib, ia);
        b.addVertex(mat, x1, y1, z2).setColor(ir, ig, ib, ia);
        b.addVertex(mat, x2, y1, z2).setColor(ir, ig, ib, ia);
        b.addVertex(mat, x2, y1, z1).setColor(ir, ig, ib, ia);
        b.addVertex(mat, x1, y2, z1).setColor(ir, ig, ib, ia);
        b.addVertex(mat, x2, y2, z1).setColor(ir, ig, ib, ia);
        b.addVertex(mat, x2, y2, z2).setColor(ir, ig, ib, ia);
        b.addVertex(mat, x1, y2, z2).setColor(ir, ig, ib, ia);
    }

    public static void drawFrame(VertexConsumer b, Matrix4f mat, BlockPos pos,
                                 float r, float g, float bl, float a) {
        float x1 = pos.getX() - 0.005f, y1 = pos.getY() - 0.005f, z1 = pos.getZ() - 0.005f;
        float x2 = pos.getX() + 1.005f, y2 = pos.getY() + 1.005f, z2 = pos.getZ() + 1.005f;
        drawBoxEdges(b, mat, x1, y1, z1, x2, y2, z2, r, g, bl, a);
    }

    public static void drawBoxEdges(VertexConsumer b, Matrix4f mat,
                                    float x1, float y1, float z1, float x2, float y2, float z2,
                                    float r, float g, float bl, float a) {
        float R = 0.015f;
        drawEdge(b, mat, x1, y1, z1, x2, y1, z1, R, r, g, bl, a);
        drawEdge(b, mat, x1, y1, z2, x2, y1, z2, R, r, g, bl, a);
        drawEdge(b, mat, x1, y2, z1, x2, y2, z1, R, r, g, bl, a);
        drawEdge(b, mat, x1, y2, z2, x2, y2, z2, R, r, g, bl, a);
        drawEdge(b, mat, x1, y1, z1, x1, y2, z1, R, r, g, bl, a);
        drawEdge(b, mat, x2, y1, z1, x2, y2, z1, R, r, g, bl, a);
        drawEdge(b, mat, x1, y1, z2, x1, y2, z2, R, r, g, bl, a);
        drawEdge(b, mat, x2, y1, z2, x2, y2, z2, R, r, g, bl, a);
        drawEdge(b, mat, x1, y1, z1, x1, y1, z2, R, r, g, bl, a);
        drawEdge(b, mat, x2, y1, z1, x2, y1, z2, R, r, g, bl, a);
        drawEdge(b, mat, x1, y2, z1, x1, y2, z2, R, r, g, bl, a);
        drawEdge(b, mat, x2, y2, z1, x2, y2, z2, R, r, g, bl, a);
    }

    public static void drawEdge(VertexConsumer b, Matrix4f mat,
                                float x1, float y1, float z1, float x2, float y2, float z2,
                                float radius, float r, float g, float bl, float a) {
        float dx = Math.abs(x2 - x1), dy = Math.abs(y2 - y1);
        float mx1 = x1, my1 = y1, mz1 = z1, mx2 = x2, my2 = y2, mz2 = z2;
        if (dx > 0.1f) { my1 -= radius; my2 += radius; mz1 -= radius; mz2 += radius; }
        else if (dy > 0.1f) { mx1 -= radius; mx2 += radius; mz1 -= radius; mz2 += radius; }
        else { mx1 -= radius; mx2 += radius; my1 -= radius; my2 += radius; }
        renderBox(b, mat, mx1, my1, mz1, mx2, my2, mz2, r, g, bl, a);
    }

    private static int clampChannel(int ch) {
        return (ch >= 1 && ch <= 16) ? (ch - 1) % RenderConstants.DYE_COLORS.length : 0;
    }
}
