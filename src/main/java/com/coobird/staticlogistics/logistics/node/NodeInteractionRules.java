package com.coobird.staticlogistics.logistics.node;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/** 节点交互中不依赖服务端状态的边界规则。 */
public final class NodeInteractionRules {
    public static final double MAX_REACH_SQUARED = 64.0D;

    private NodeInteractionRules() {
    }

    public static boolean matchesTarget(BlockPos expectedPos, Direction expectedFace,
                                        BlockPos actualPos, Direction actualFace) {
        return expectedPos != null && expectedFace != null
            && expectedPos.equals(actualPos) && expectedFace == actualFace;
    }

    public static boolean matchesTarget(long expectedPosKey, int expectedFace,
                                        long actualPosKey, int actualFace) {
        return expectedPosKey == actualPosKey && expectedFace == actualFace;
    }

    public static boolean isWithinReach(double playerX, double playerY, double playerZ, BlockPos pos) {
        if (pos == null) return false;
        return isWithinReach(playerX, playerY, playerZ, pos.getX(), pos.getY(), pos.getZ());
    }

    public static boolean isWithinReach(double playerX, double playerY, double playerZ,
                                        int targetX, int targetY, int targetZ) {
        double dx = playerX - (targetX + 0.5D);
        double dy = playerY - (targetY + 0.5D);
        double dz = playerZ - (targetZ + 0.5D);
        return dx * dx + dy * dy + dz * dz <= MAX_REACH_SQUARED;
    }
}
