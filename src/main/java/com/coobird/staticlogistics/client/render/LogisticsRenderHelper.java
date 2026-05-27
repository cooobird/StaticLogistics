package com.coobird.staticlogistics.client.render;

import com.coobird.staticlogistics.client.util.RenderConstants;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * 物流渲染共享工具 —— 面状态指示器 + 流动粒子，供 LinkWorldRenderer 和 BlueprintRegionRenderer 共用。
 */
public class LogisticsRenderHelper {

    /**
     * 在方块面贴着色指示器
     */
    public static void drawFaceStatus(VertexConsumer b, Matrix4f mat,
                                      BlockPos pos, Direction face,
                                      int inChannel, int outChannel,
                                      boolean hasGlobalIn, boolean hasGlobalOut,
                                      float pulse) {
        double px = pos.getX() + 0.5 + face.getStepX() * 0.508;
        double py = pos.getY() + 0.5 + face.getStepY() * 0.508;
        double pz = pos.getZ() + 0.5 + face.getStepZ() * 0.508;

        float size = 0.4f + pulse;
        int inIdx = (inChannel >= 1 && inChannel <= 16) ? (inChannel - 1) % RenderConstants.DYE_COLORS.length : 0;
        int outIdx = (outChannel >= 1 && outChannel <= 16) ? (outChannel - 1) % RenderConstants.DYE_COLORS.length : 0;

        if (hasGlobalIn && hasGlobalOut) {
            drawFaceQuad(b, mat, px, py, pz, face, inIdx, 0.85f, size, -0.5f, 0.45f);
            drawFaceQuad(b, mat, px, py, pz, face, outIdx, 0.85f, size, 0.5f, 0.45f);
        } else if (hasGlobalIn) {
            drawFaceQuad(b, mat, px, py, pz, face, inIdx, 0.85f, size, 0, 1.0f);
        } else if (hasGlobalOut) {
            drawFaceQuad(b, mat, px, py, pz, face, outIdx, 0.85f, size, 0, 1.0f);
        }
    }

    /**
     * 着色方块面四角四边形
     */
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

        double ox = a2.x * offset * size;
        double oy = a2.y * offset * size;
        double oz = a2.z * offset * size;

        float x1 = (float) (a1.x * size), y1 = (float) (a1.y * size), z1 = (float) (a1.z * size);
        float x2 = (float) (a2.x * size * widthMult), y2 = (float) (a2.y * size * widthMult), z2 = (float) (a2.z * size * widthMult);

        b.addVertex(mat, (float) (x + ox - x1 - x2), (float) (y + oy - y1 - y2), (float) (z + oz - z1 - z2)).setColor(ir, ig, ib, ia);
        b.addVertex(mat, (float) (x + ox + x1 - x2), (float) (y + oy + y1 - y2), (float) (z + oz + z1 - z2)).setColor(ir, ig, ib, ia);
        b.addVertex(mat, (float) (x + ox + x1 + x2), (float) (y + oy + y1 + y2), (float) (z + oz + z1 + z2)).setColor(ir, ig, ib, ia);
        b.addVertex(mat, (float) (x + ox - x1 + x2), (float) (y + oy - y1 + y2), (float) (z + oz - z1 + z2)).setColor(ir, ig, ib, ia);
    }

    /**
     * 两方块中心之间画流动粒子
     */
    public static void drawFlowParticles(VertexConsumer b, Matrix4f mat,
                                         BlockPos from, BlockPos to,
                                         int channel, boolean isOutput,
                                         double timeSeconds) {
        int ch = (channel >= 1 && channel <= 16) ? channel : 1;
        int color = RenderConstants.DYE_COLORS[(ch - 1) % RenderConstants.DYE_COLORS.length];
        float r = ((color >> 16) & 0xFF) / 255f;
        float g = ((color >> 8) & 0xFF) / 255f;
        float bl = (color & 0xFF) / 255f;

        double sx = from.getX() + 0.5, sy = from.getY() + 0.5, sz = from.getZ() + 0.5;
        double ex = to.getX() + 0.5, ey = to.getY() + 0.5, ez = to.getZ() + 0.5;
        double dx = ex - sx, dy = ey - sy, dz = ez - sz;
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (dist < 0.1) return;

        // 把起止点从方块中心推到面上
        double invDist = 1.0 / dist;
        double nx = dx * invDist, ny = dy * invDist, nz = dz * invDist;
        sx += nx * 0.52;
        sy += ny * 0.52;
        sz += nz * 0.52;
        ex -= nx * 0.52;
        ey -= ny * 0.52;
        ez -= nz * 0.52;
        dx = ex - sx;
        dy = ey - sy;
        dz = ez - sz;
        dist = Math.sqrt(dx * dx + dy * dy + dz * dz);

        float speed = 2.0f;
        int cnt = (int) Math.min(12, Math.max(3, dist * 2));
        for (int i = 0; i < cnt; i++) {
            float progress = (float) (((timeSeconds * speed + (double) i / cnt * dist) % dist) / dist);
            float px = (float) (sx + dx * progress);
            float py = (float) (sy + dy * progress);
            float pz = (float) (sz + dz * progress);
            float s = 0.03f;
            int ir = (int) (r * 255), ig = (int) (g * 255), ib = (int) (bl * 255), ia = (int) (0.85f * 255);
            addBoxVerts(b, mat, px - s, py - s, pz - s, px + s, py + s, pz + s, ir, ig, ib, ia);
        }
    }

    /**
     * 小方块顶点（6 面），复用 renderBox 逻辑
     */
    private static void addBoxVerts(VertexConsumer b, Matrix4f mat,
                                    float x1, float y1, float z1, float x2, float y2, float z2,
                                    int r, int g, int bl, int a) {
        b.addVertex(mat, x1, y1, z1).setColor(r, g, bl, a);
        b.addVertex(mat, x2, y1, z1).setColor(r, g, bl, a);
        b.addVertex(mat, x2, y2, z1).setColor(r, g, bl, a);
        b.addVertex(mat, x1, y2, z1).setColor(r, g, bl, a);
        b.addVertex(mat, x1, y1, z2).setColor(r, g, bl, a);
        b.addVertex(mat, x1, y2, z2).setColor(r, g, bl, a);
        b.addVertex(mat, x2, y2, z2).setColor(r, g, bl, a);
        b.addVertex(mat, x2, y1, z2).setColor(r, g, bl, a);
        b.addVertex(mat, x1, y1, z1).setColor(r, g, bl, a);
        b.addVertex(mat, x1, y2, z1).setColor(r, g, bl, a);
        b.addVertex(mat, x1, y2, z2).setColor(r, g, bl, a);
        b.addVertex(mat, x1, y1, z2).setColor(r, g, bl, a);
        b.addVertex(mat, x2, y1, z1).setColor(r, g, bl, a);
        b.addVertex(mat, x2, y1, z2).setColor(r, g, bl, a);
        b.addVertex(mat, x2, y2, z2).setColor(r, g, bl, a);
        b.addVertex(mat, x2, y2, z1).setColor(r, g, bl, a);
        b.addVertex(mat, x1, y1, z1).setColor(r, g, bl, a);
        b.addVertex(mat, x1, y1, z2).setColor(r, g, bl, a);
        b.addVertex(mat, x2, y1, z2).setColor(r, g, bl, a);
        b.addVertex(mat, x2, y1, z1).setColor(r, g, bl, a);
        b.addVertex(mat, x1, y2, z1).setColor(r, g, bl, a);
        b.addVertex(mat, x2, y2, z1).setColor(r, g, bl, a);
        b.addVertex(mat, x2, y2, z2).setColor(r, g, bl, a);
        b.addVertex(mat, x1, y2, z2).setColor(r, g, bl, a);
    }
}
