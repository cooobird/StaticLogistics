package com.coobird.staticlogistics.transfer;

import com.coobird.staticlogistics.config.SLConfig;
import com.coobird.staticlogistics.integration.sable.DynamicNodeSpace;
import com.coobird.staticlogistics.logistics.node.ContainerConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.world.level.Level;

/**
 * 物流计算工具类 - 集中所有与升级倍率、范围、距离相关的计算
 */
public final class LogisticsCalculator {

    private LogisticsCalculator() {
    }

    /**
     * 获取速度倍率
     */
    public static long getSpeedMultiplier(ContainerConfig container) {
        return container == null ? 1 : container.getSpeedMultiplier();
    }

    /**
     * 获取范围倍率
     */
    public static long getRangeMultiplier(ContainerConfig container) {
        return container == null ? 1 : container.getRangeMultiplier();
    }

    /**
     * 获取堆叠倍率
     */
    public static long getStackMultiplier(ContainerConfig container) {
        return container == null ? 1 : container.getStackMultiplier();
    }

    /**
     * 是否支持跨维度
     */
    public static boolean isDimensionEffective(ContainerConfig container) {
        return SLConfig.isSimpleMode() || container != null && container.isDimensionEffective();
    }

    /**
     * 返回整数格范围。范围倍率溢出时使用 {@link Integer#MAX_VALUE} 表示无限。
     */
    public static int getMaxTransferBlocks(ContainerConfig container) {
        int baseRadius = SLConfig.getDefaultRadius();
        if (SLConfig.isSimpleMode()) return Integer.MAX_VALUE;
        if (container == null) return baseRadius;
        long rangeMult = getRangeMultiplier(container);
        if (rangeMult >= ContainerConfig.INFINITY_MARKER) {
            return Integer.MAX_VALUE;
        }
        if (rangeMult <= 1) return baseRadius;
        if (rangeMult > Integer.MAX_VALUE / baseRadius) return Integer.MAX_VALUE;
        return (int) (baseRadius * rangeMult);
    }

    /**
     * 使用发送端容器评估一条传输方向的距离能力。
     */
    public static TransferRangeAssessment assessTransferRange(
        GlobalPos sender,
        GlobalPos receiver,
        ContainerConfig senderContainer
    ) {
        return assessTransferRange(
            sender, receiver, getMaxTransferBlocks(senderContainer),
            isDimensionEffective(senderContainer));
    }

    /**
     * 按真实世界位置评估范围；Sable plot 中的坐标会先经过子世界姿态变换。
     */
    public static TransferRangeAssessment assessTransferRange(
        Level level,
        GlobalPos sender,
        GlobalPos receiver,
        ContainerConfig senderContainer
    ) {
        return assessTransferRange(level, sender, receiver,
            getMaxTransferBlocks(senderContainer), isDimensionEffective(senderContainer));
    }

    public static TransferRangeAssessment assessTransferRange(
        Level level,
        GlobalPos sender,
        GlobalPos receiver,
        int maximumBlocks,
        boolean dimensionEffective
    ) {
        if (level == null || sender == null || receiver == null) {
            throw new IllegalArgumentException("Transfer range level and endpoints are required");
        }
        if (maximumBlocks < 0) {
            throw new IllegalArgumentException("Maximum transfer blocks must not be negative");
        }
        boolean crossDimension = !sender.dimension().equals(receiver.dimension());
        int actualBlocks = crossDimension ? 0 : ceilBlocks(Math.sqrt(
            DynamicNodeSpace.distanceSquared(level, sender.pos(), receiver.pos())));
        if (dimensionEffective) {
            return new TransferRangeAssessment(
                crossDimension, true, true, actualBlocks, Integer.MAX_VALUE);
        }
        if (crossDimension) {
            return new TransferRangeAssessment(true, false, false, 0, maximumBlocks);
        }
        boolean unlimited = maximumBlocks == Integer.MAX_VALUE;
        return new TransferRangeAssessment(false, unlimited,
            unlimited || actualBlocks <= maximumBlocks, actualBlocks, maximumBlocks);
    }

    /**
     * 客户端拓扑与服务端传输共同使用的纯整数范围评估。
     */
    public static TransferRangeAssessment assessTransferRange(
        GlobalPos sender,
        GlobalPos receiver,
        int maximumBlocks,
        boolean dimensionEffective
    ) {
        if (sender == null || receiver == null) {
            throw new IllegalArgumentException("Transfer range endpoints are required");
        }
        if (maximumBlocks < 0) {
            throw new IllegalArgumentException("Maximum transfer blocks must not be negative");
        }
        boolean crossDimension = !sender.dimension().equals(receiver.dimension());
        if (dimensionEffective) {
            int actualBlocks = crossDimension ? 0 : distanceBlocks(sender.pos(), receiver.pos());
            return new TransferRangeAssessment(
                crossDimension, true, true, actualBlocks, Integer.MAX_VALUE);
        }
        if (crossDimension) {
            return new TransferRangeAssessment(true, false, false, 0, maximumBlocks);
        }
        int actualBlocks = distanceBlocks(sender.pos(), receiver.pos());
        boolean unlimited = maximumBlocks == Integer.MAX_VALUE;
        return new TransferRangeAssessment(
            false, unlimited, unlimited || actualBlocks <= maximumBlocks,
            actualBlocks, maximumBlocks);
    }

    /**
     * 两个方块坐标之间的欧氏距离，向上取整为完整方块格数。
     */
    public static int distanceBlocks(BlockPos first, BlockPos second) {
        if (first == null || second == null) {
            throw new IllegalArgumentException("Distance positions are required");
        }
        return ceilBlocks(Math.sqrt(first.distSqr(second)));
    }

    private static int ceilBlocks(double value) {
        if (!Double.isFinite(value) || value >= Integer.MAX_VALUE) return Integer.MAX_VALUE;
        return Math.max(0, (int) Math.ceil(value));
    }

    /**
     * 统一计算速度间隔：interval = baseInterval / speedMult（线性梯度）。
     * 所有需要显示或使用速度间隔的地方都应调用此方法。
     *
     * @param baseInterval 基础间隔（tick），来自配置
     * @param speedMult    速度倍率
     * @return 实际间隔（tick），最小为 1
     */
    public static int calcSpeedInterval(int baseInterval, long speedMult) {
        if (speedMult <= 1) return baseInterval;
        if (speedMult >= ContainerConfig.INFINITY_MARKER) return 1;
        return Math.max(1, (int) (baseInterval / speedMult));
    }

    /**
     * 统一计算传输限制：limit = baseStackSize × stackMult。
     * 所有需要计算传输量的地方都应调用此方法。
     *
     * @param type      资源类型
     * @param stackMult 堆叠倍率
     * @return 传输限制（long），溢出或 INFINITY_MARKER 时返回 Long.MAX_VALUE
     */
    public static long calcTransferLimit(LogisticsResource<?> type, long stackMult) {
        if (SLConfig.isSimpleMode()) {
            return SLConfig.MAX_TRANSFER_AMOUNT;
        }
        if (stackMult >= ContainerConfig.INFINITY_MARKER) {
            return Long.MAX_VALUE;
        }
        long base = type.getBaseStackSize();
        if (base <= 0 || stackMult <= 0) return base;
        long result = base * stackMult;
        if (result / stackMult != base || result < 0) {
            return Long.MAX_VALUE;
        }
        return result;
    }

    /**
     * 一条传输方向的统一整数范围结论。
     */
    public record TransferRangeAssessment(
        boolean crossDimension,
        boolean unlimited,
        boolean allowed,
        int actualBlocks,
        int maximumBlocks
    ) {
    }

}
