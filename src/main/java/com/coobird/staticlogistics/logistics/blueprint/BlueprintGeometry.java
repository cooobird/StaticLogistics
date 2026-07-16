package com.coobird.staticlogistics.logistics.blueprint;

import com.coobird.staticlogistics.logistics.node.LinkConfig;
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

    public static BlockPos rotateToAbsolute(BlockPos relative, BlockPos anchor, int rotation) {
        return switch (rotation & 3) {
            case 1 -> anchor.offset(-relative.getZ(), relative.getY(), relative.getX());
            case 2 -> anchor.offset(-relative.getX(), relative.getY(), -relative.getZ());
            case 3 -> anchor.offset(relative.getZ(), relative.getY(), -relative.getX());
            default -> anchor.offset(relative);
        };
    }

    public static Direction rotateDirection(Direction face, int rotation) {
        if (face.getAxis() == Direction.Axis.Y) return face;
        Direction[] horizontal = {
            Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST
        };
        for (int index = 0; index < horizontal.length; index++) {
            if (horizontal[index] == face) {
                return horizontal[(index + rotation) & 3];
            }
        }
        throw new IllegalArgumentException("Unsupported horizontal direction: " + face);
    }

    /**
     * 旧版只记录目标方块；仅当目标面能够唯一判定时才迁移，避免猜错连接面。
     */
    public static List<BlueprintData.LinkEntry> resolveLinks(
        BlueprintData.BlockEntry sourceEntry,
        BlueprintData.FaceEntry sourceFace,
        Map<BlockPos, BlueprintData.BlockEntry> entriesByRelativePos
    ) {
        if (!sourceFace.linkedTo().isEmpty()) return sourceFace.linkedTo();
        if (sourceEntry.linkedTo().isEmpty()) return List.of();

        int outputChannel = sourceFace.faceConfig().getInt(ConfigKeys.OUTPUT_CHANNEL);
        List<BlueprintData.LinkEntry> migrated = new ArrayList<>();
        for (BlockPos targetPos : sourceEntry.linkedTo()) {
            BlueprintData.BlockEntry target = entriesByRelativePos.get(targetPos);
            if (target == null) continue;
            List<Direction> candidates = target.faces().entrySet().stream()
                .filter(entry -> entry.getValue().faceConfig().getBoolean(ConfigKeys.GLOBAL_INPUT))
                .filter(entry -> LinkConfig.channelsMatch(outputChannel,
                    entry.getValue().faceConfig().getInt(ConfigKeys.INPUT_CHANNEL)))
                .map(Map.Entry::getKey)
                .toList();
            if (candidates.size() == 1) {
                migrated.add(new BlueprintData.LinkEntry(targetPos, candidates.get(0)));
            }
        }
        return List.copyOf(migrated);
    }
}

