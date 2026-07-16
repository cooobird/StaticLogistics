package com.coobird.staticlogistics.logistics.node;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * 节点交互中不依赖服务端状态的边界规则。
 */
public final class NodeInteractionRules {
    public static final double MAX_REACH_SQUARED = 64.0D;

    private NodeInteractionRules() {
    }

    public static boolean matchesTarget(BlockPos expectedPos, Direction expectedFace,
                                        BlockPos actualPos, Direction actualFace) {
        return expectedPos != null && expectedFace != null
            && expectedPos.equals(actualPos) && expectedFace == actualFace;
    }

    public static boolean isWithinReach(double playerX, double playerY, double playerZ, BlockPos pos) {
        if (pos == null) return false;
        double dx = playerX - (pos.getX() + 0.5D);
        double dy = playerY - (pos.getY() + 0.5D);
        double dz = playerZ - (pos.getZ() + 0.5D);
        return dx * dx + dy * dy + dz * dz <= MAX_REACH_SQUARED;
    }
}
