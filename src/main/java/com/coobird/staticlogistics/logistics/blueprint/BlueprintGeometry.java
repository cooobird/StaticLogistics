package com.coobird.staticlogistics.logistics.blueprint;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/// 蓝图坐标与连接面的水平旋转。
public final class BlueprintGeometry {
    private BlueprintGeometry() {
    }

    /// 将相对坐标按水平旋转映射为绝对坐标。
    public static BlockPos rotateToAbsolute(BlockPos relative, BlockPos anchor, int rotation) {
        return switch (rotation & 3) {
            case 1 -> anchor.offset(-relative.getZ(), relative.getY(), relative.getX());
            case 2 -> anchor.offset(-relative.getX(), relative.getY(), -relative.getZ());
            case 3 -> anchor.offset(relative.getZ(), relative.getY(), -relative.getX());
            default -> anchor.offset(relative);
        };
    }

    /// 将面方向按水平旋转同步变换。
    public static Direction rotateDirection(Direction face, int rotation) {
        if (face.getAxis() == Direction.Axis.Y) return face;
        Direction[] horizontal = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
        for (int index = 0; index < horizontal.length; index++) {
            if (horizontal[index] == face) {
                return horizontal[(index + rotation) & 3];
            }
        }
        throw new IllegalArgumentException("Unsupported horizontal direction: " + face);
    }

}
