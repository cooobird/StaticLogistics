package com.coobird.staticlogistics.client.render;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.List;

/// 方块模型选面、临时选点反馈与蓝图预览共用的绘制工具。
public final class LogisticsRenderHelper {
    public static final int INPUT_COLOR = 0xFF55AAFF;
    public static final int OUTPUT_COLOR = 0xFFFFAA33;
    public static final int FLOW_COLOR = 0xFF98FB98;

    private LogisticsRenderHelper() {
    }

    /// 缓存连线与端面的顶点，仅在位置或外观变化时重建。
    public record LinkMesh(double[] vertices, int color, int alpha) {
        public void draw(VertexConsumer buffer, Matrix4f matrix) {
            drawRelative(buffer, matrix, Vec3.ZERO);
        }

        public void drawRelative(VertexConsumer buffer, Matrix4f matrix, Vec3 origin) {
            for (int i = 0; i < vertices.length; i += 3) {
                buffer.addVertex(matrix, (float) (vertices[i] - origin.x), (float) (vertices[i + 1] - origin.y), (float) (vertices[i + 2] - origin.z))
                    .setColor((color >> 16) & 255, (color >> 8) & 255, color & 255, alpha);
            }
        }
    }

    private static final int[] TUBE_INDICES = {
        0, 1, 2, 3, 4, 7, 6, 5,
        0, 4, 5, 1, 1, 5, 6, 2, 2, 6, 7, 3, 3, 7, 4, 0
    };

    public static LinkMesh buildFaceMesh(Vec3 center, Vec3 normal, Vec3 axis1, Vec3 axis2,
                                         int color, float offset, float widthMultiplier) {
        Vec3 n = normal.normalize();
        Vec3 a = axis1.subtract(n.scale(axis1.dot(n))).normalize().scale(0.4);
        Vec3 axis = axis2.subtract(n.scale(axis2.dot(n))).normalize();
        Vec3 b = axis.scale(0.4 * widthMultiplier);
        Vec3 origin = center.add(axis.scale(offset * 0.4));
        double[] vertices = new double[12];
        for (int i = 0; i < 4; i++) {
            double sa = i == 0 || i == 3 ? -1 : 1;
            double sb = i < 2 ? -1 : 1;
            vertices[i * 3] = origin.x + sa * a.x + sb * b.x;
            vertices[i * 3 + 1] = origin.y + sa * a.y + sb * b.y;
            vertices[i * 3 + 2] = origin.z + sa * a.z + sb * b.z;
        }
        return new LinkMesh(vertices, color, (int) (0.85F * 255));
    }

    public static LinkMesh buildLinkMesh(Vec3 start, Vec3 end, int color, float alpha, float radius) {
        if (start.distanceToSqr(end) < 0.0001D) return new LinkMesh(new double[0], color, 0);
        Vec3 direction = end.subtract(start).normalize();
        Vec3 reference = Math.abs(direction.y) < 0.9 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
        Vec3 u = direction.cross(reference).normalize().scale(radius);
        Vec3 v = direction.cross(u).normalize().scale(radius);
        double[] vertices = new double[TUBE_INDICES.length * 3];
        for (int i = 0; i < TUBE_INDICES.length; i++) {
            int corner = TUBE_INDICES[i];
            Vec3 center = corner < 4 ? start : end;
            int side = corner & 3;
            double su = side < 2 ? 1 : -1;
            double sv = side == 0 || side == 3 ? 1 : -1;
            vertices[i * 3] = center.x + su * u.x + sv * v.x;
            vertices[i * 3 + 1] = center.y + su * u.y + sv * v.y;
            vertices[i * 3 + 2] = center.z + su * u.z + sv * v.z;
        }
        return new LinkMesh(vertices, color, (int) (alpha * 255));
    }

    /// 世界与蓝图共用同一份连接几何规则，方向位：1 为正向，2 为反向。
    public static List<LinkMesh> buildConnectionMeshes(
        Vec3 first, Vec3 second, Vec3 firstAxis, Vec3 secondAxis,
        int directions, float alpha
    ) {
        return buildConnectionMeshes(first, second, firstAxis, secondAxis, directions, alpha, 0.008F);
    }

    public static List<LinkMesh> buildConnectionMeshes(
        Vec3 first, Vec3 second, Vec3 firstAxis, Vec3 secondAxis,
        int directions, float alpha, float radius
    ) {
        if (directions == 3) {
            Vec3 firstOffset = firstAxis.normalize().scale(0.20);
            Vec3 secondOffset = secondAxis.normalize().scale(0.20);
            return List.of(
                buildLinkMesh(first.add(firstOffset), second.subtract(secondOffset),
                    OUTPUT_COLOR, alpha, radius),
                buildLinkMesh(second.add(secondOffset), first.subtract(firstOffset),
                    INPUT_COLOR, alpha, radius));
        }
        if (directions == 2) {
            return List.of(buildLinkMesh(second, first, FLOW_COLOR, alpha, radius));
        }
        int color = directions == 0 ? 0xFF888888 : FLOW_COLOR;
        float resolvedAlpha = directions == 0 ? Math.min(alpha, 0.45F) : alpha;
        return List.of(buildLinkMesh(first, second, color, resolvedAlpha, directions == 0 ? radius * 0.75F : radius));
    }

    /// 方块面着色指示器
    public static void drawFaceStatus(VertexConsumer b, Matrix4f mat, BlockPos pos, Direction face, int inputColor, int outputColor, boolean hasIn, boolean hasOut, float pulse) {
        double px = pos.getX() + 0.5 + face.getStepX() * 0.508;
        double py = pos.getY() + 0.5 + face.getStepY() * 0.508;
        double pz = pos.getZ() + 0.5 + face.getStepZ() * 0.508;
        float size = 0.4f + pulse;

        if (hasIn && hasOut) {
            drawFaceQuad(b, mat, px, py, pz, face, inputColor, 0.85f, size, -0.5f, 0.45f);
            drawFaceQuad(b, mat, px, py, pz, face, outputColor, 0.85f, size, 0.5f, 0.45f);
        } else if (hasIn) {
            drawFaceQuad(b, mat, px, py, pz, face, inputColor, 0.85f, size, 0, 1f);
        } else if (hasOut) {
            drawFaceQuad(b, mat, px, py, pz, face, outputColor, 0.85f, size, 0, 1f);
        }
    }

    /// 在任意旋转后的平面上绘制状态，供 Sable 动态子世界使用。
    public static void drawFaceStatus(VertexConsumer b, Matrix4f mat, Vec3 center, Vec3 normal,
                                      int inputColor, int outputColor,
                                      boolean hasIn, boolean hasOut, float pulse) {
        Vec3 n = normal.normalize();
        Vec3 seed = Math.abs(n.y) > 0.5 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 axis2 = n.cross(seed).normalize();
        Vec3 axis1 = n.cross(axis2).normalize();
        drawFaceStatus(b, mat, center, n, axis1, axis2, inputColor, outputColor, hasIn, hasOut, pulse);
    }

    /// 使用明确的平面双轴绘制面片，以保留动态结构的滚转角。
    public static void drawFaceStatus(VertexConsumer b, Matrix4f mat, Vec3 center, Vec3 normal,
                                      Vec3 axis1, Vec3 axis2,
                                      int inputColor, int outputColor,
                                      boolean hasIn, boolean hasOut, float pulse) {
        float size = 0.4f + pulse;
        if (hasIn && hasOut) {
            drawFaceQuad(b, mat, center, normal, axis1, axis2, inputColor, 0.85f, size, -0.5f, 0.45f);
            drawFaceQuad(b, mat, center, normal, axis1, axis2, outputColor, 0.85f, size, 0.5f, 0.45f);
        } else if (hasIn) {
            drawFaceQuad(b, mat, center, normal, axis1, axis2, inputColor, 0.85f, size, 0, 1f);
        } else if (hasOut) {
            drawFaceQuad(b, mat, center, normal, axis1, axis2, outputColor, 0.85f, size, 0, 1f);
        }
    }

    public static void drawFaceQuad(VertexConsumer b, Matrix4f mat,
                                    double x, double y, double z, Direction face,
                                    int color, float alpha, float size,
                                    float offset, float widthMult) {
        Vec3 n = Vec3.atLowerCornerOf(face.getNormal());
        drawFaceQuad(b, mat, new Vec3(x, y, z), n, color, alpha, size, offset, widthMult);
    }

    public static void drawFaceQuad(VertexConsumer b, Matrix4f mat,
                                    Vec3 center, Vec3 normal,
                                    int color, float alpha, float size,
                                    float offset, float widthMult) {
        Vec3 n = normal.normalize();
        Vec3 seed = Math.abs(n.y) > 0.5 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 axis2 = n.cross(seed).normalize();
        Vec3 axis1 = n.cross(axis2).normalize();
        drawFaceQuad(b, mat, center, n, axis1, axis2, color, alpha, size, offset, widthMult);
    }

    public static void drawFaceQuad(VertexConsumer b, Matrix4f mat,
                                    Vec3 center, Vec3 normal, Vec3 axis1, Vec3 axis2,
                                    int color, float alpha, float size,
                                    float offset, float widthMult) {
        float r = ((color >> 16) & 0xFF) / 255f, g = ((color >> 8) & 0xFF) / 255f, bl = (color & 0xFF) / 255f;
        int ir = (int) (r * 255), ig = (int) (g * 255), ib = (int) (bl * 255), ia = (int) (alpha * 255);

        Vec3 n = normal.normalize();
        Vec3 a1 = axis1.subtract(n.scale(axis1.dot(n))).normalize();
        Vec3 a2 = axis2.subtract(n.scale(axis2.dot(n))).normalize();
        double ox = a2.x * offset * size, oy = a2.y * offset * size, oz = a2.z * offset * size;
        float x1 = (float) (a1.x * size), y1 = (float) (a1.y * size), z1 = (float) (a1.z * size);
        float x2 = (float) (a2.x * size * widthMult), y2 = (float) (a2.y * size * widthMult), z2 = (float) (a2.z * size * widthMult);

        double x = center.x, y = center.y, z = center.z;
        b.addVertex(mat, (float) (x + ox - x1 - x2), (float) (y + oy - y1 - y2), (float) (z + oz - z1 - z2)).setColor(ir, ig, ib, ia);
        b.addVertex(mat, (float) (x + ox + x1 - x2), (float) (y + oy + y1 - y2), (float) (z + oz + z1 - z2)).setColor(ir, ig, ib, ia);
        b.addVertex(mat, (float) (x + ox + x1 + x2), (float) (y + oy + y1 + y2), (float) (z + oz + z1 + z2)).setColor(ir, ig, ib, ia);
        b.addVertex(mat, (float) (x + ox - x1 + x2), (float) (y + oy - y1 + y2), (float) (z + oz - z1 + z2)).setColor(ir, ig, ib, ia);
    }

    /// 线框渲染
    public static void renderBox(VertexConsumer b, Matrix4f mat, float x1, float y1, float z1, float x2, float y2, float z2, float r, float g, float bl, float a) {
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

    /// 围绕动态结构节点绘制具备完整三维朝向的方块线框。
    public static void drawFrame(VertexConsumer b, Matrix4f mat, Vec3 center,
                                 Vec3 xAxis, Vec3 yAxis, Vec3 zAxis,
                                 float r, float g, float bl, float a) {
        double half = 0.505;
        Vec3[] corners = new Vec3[8];
        for (int index = 0; index < corners.length; index++) {
            corners[index] = center
                .add(xAxis.scale((index & 1) == 0 ? -half : half))
                .add(yAxis.scale((index & 2) == 0 ? -half : half))
                .add(zAxis.scale((index & 4) == 0 ? -half : half));
        }
        int[][] edges = {
            {0, 1}, {2, 3}, {4, 5}, {6, 7},
            {0, 2}, {1, 3}, {4, 6}, {5, 7},
            {0, 4}, {1, 5}, {2, 6}, {3, 7}
        };
        for (int[] edge : edges) {
            drawOrientedEdge(b, mat, corners[edge[0]], corners[edge[1]], 0.015F, r, g, bl, a);
        }
    }

    private static void drawOrientedEdge(VertexConsumer b, Matrix4f mat,
                                         Vec3 start, Vec3 end, float radius,
                                         float r, float g, float bl, float a) {
        Vec3 direction = end.subtract(start).normalize();
        Vec3 reference = Math.abs(direction.y) < 0.9 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
        Vec3 u = direction.cross(reference).normalize().scale(radius);
        Vec3 v = direction.cross(u).normalize().scale(radius);
        Vec3[] points = {
            start.add(u).add(v), start.add(u).subtract(v), start.subtract(u).subtract(v), start.subtract(u).add(v),
            end.add(u).add(v), end.add(u).subtract(v), end.subtract(u).subtract(v), end.subtract(u).add(v)
        };
        int ir = (int) (r * 255), ig = (int) (g * 255), ib = (int) (bl * 255), ia = (int) (a * 255);
        int[][] quads = {
            {0, 1, 2, 3}, {4, 7, 6, 5},
            {0, 4, 5, 1}, {1, 5, 6, 2}, {2, 6, 7, 3}, {3, 7, 4, 0}
        };
        for (int[] quad : quads) {
            for (int index : quad) {
                Vec3 point = points[index];
                b.addVertex(mat, (float) point.x, (float) point.y, (float) point.z)
                    .setColor(ir, ig, ib, ia);
            }
        }
    }

    public static void drawStaticLink(
        VertexConsumer buffer,
        Matrix4f matrix,
        Vec3 start,
        Vec3 end,
        int color,
        float alpha,
        float radius
    ) {
        if (start.distanceToSqr(end) < 0.0001D) return;
        float red = ((color >> 16) & 0xFF) / 255.0F;
        float green = ((color >> 8) & 0xFF) / 255.0F;
        float blue = (color & 0xFF) / 255.0F;
        drawOrientedEdge(buffer, matrix, start, end, radius, red, green, blue, alpha);
    }

    public static void drawBoxEdges(VertexConsumer b, Matrix4f mat, float x1, float y1, float z1, float x2, float y2, float z2, float r, float g, float bl, float a) {
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

    public static void drawEdge(VertexConsumer b, Matrix4f mat, float x1, float y1, float z1, float x2, float y2, float z2, float radius, float r, float g, float bl, float a) {
        float dx = Math.abs(x2 - x1), dy = Math.abs(y2 - y1);
        float mx1 = x1, my1 = y1, mz1 = z1, mx2 = x2, my2 = y2, mz2 = z2;
        if (dx > 0.1f) {
            my1 -= radius;
            my2 += radius;
            mz1 -= radius;
            mz2 += radius;
        } else if (dy > 0.1f) {
            mx1 -= radius;
            mx2 += radius;
            mz1 -= radius;
            mz2 += radius;
        } else {
            mx1 -= radius;
            mx2 += radius;
            my1 -= radius;
            my2 += radius;
        }
        renderBox(b, mat, mx1, my1, mz1, mx2, my2, mz2, r, g, bl, a);
    }
}
