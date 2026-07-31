package com.coobird.staticlogistics.logistics.blueprint;

import com.coobird.staticlogistics.logistics.node.persistence.ConfigKeys;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 蓝图坐标变换与旧链接数据解析规则。
 */
public final class BlueprintGeometry {
    private BlueprintGeometry() {
    }

    /**
     * 将相对坐标按水平旋转映射为绝对坐标。
     */
    public static BlockPos rotateToAbsolute(BlockPos relative, BlockPos anchor, int rotation) {
        return switch (rotation & 3) {
            case 1 -> anchor.offset(-relative.getZ(), relative.getY(), relative.getX());
            case 2 -> anchor.offset(-relative.getX(), relative.getY(), -relative.getZ());
            case 3 -> anchor.offset(relative.getZ(), relative.getY(), -relative.getX());
            default -> anchor.offset(relative);
        };
    }

    /**
     * 将面方向按水平旋转同步变换。
     */
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

    /**
     * 将旧版方块级链接迁移为精确端点链接。
     * 只有目标面唯一可判定时才迁移，避免把链接猜到错误的容器面。
     */
    public static List<BlueprintData.LinkEntry> resolveLinks(
        BlueprintData.BlockEntry sourceEntry,
        BlueprintData.FaceEntry sourceFace,
        Map<BlockPos, BlueprintData.BlockEntry> entriesByRelativePos
    ) {
        if (!sourceFace.linkedTo().isEmpty()) return sourceFace.linkedTo();
        if (sourceEntry.linkedTo().isEmpty()) return List.of();

        List<BlueprintData.LinkEntry> migrated = new ArrayList<>();
        for (BlockPos targetPos : sourceEntry.linkedTo()) {
            BlueprintData.BlockEntry target = entriesByRelativePos.get(targetPos);
            if (target == null) continue;
            List<Direction> candidates = target.faces().entrySet().stream()
                .filter(entry -> entry.getValue().faceConfig().getBoolean(ConfigKeys.GLOBAL_INPUT))
                .map(Map.Entry::getKey)
                .toList();
            if (candidates.size() == 1) {
                migrated.add(new BlueprintData.LinkEntry(targetPos, candidates.get(0)));
            }
        }
        return List.copyOf(migrated);
    }
}
