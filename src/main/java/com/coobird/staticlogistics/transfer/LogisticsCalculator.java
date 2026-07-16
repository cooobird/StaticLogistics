package com.coobird.staticlogistics.transfer;

import com.coobird.staticlogistics.config.SLConfig;
import com.coobird.staticlogistics.logistics.node.ContainerConfig;
import net.minecraft.core.BlockPos;

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
        return container != null && container.isDimensionEffective();
    }

    // 算最远传多远，不设人工上限，倍率多少就是多少
    // 如果倍率异常<=0，兜底回退默认半径
    public static double getMaxTransferDistance(ContainerConfig container) {
        if (container == null) return SLConfig.getDefaultRadius();
        double baseRadius = SLConfig.getDefaultRadius();
        long rangeMult = getRangeMultiplier(container);
        if (rangeMult >= ContainerConfig.INFINITY_MARKER) {
            return Double.POSITIVE_INFINITY;
        }
        if (rangeMult <= 0) {
            return baseRadius;
        }
        return baseRadius * rangeMult;
    }

    /**
     * 检查两个节点是否超出传输范围（基于发送端容器）。
     * 返回 true 表示超出范围，应跳过传输。
     */
    public static boolean isOutOfRange(BlockPos senderPos, BlockPos receiverPos, ContainerConfig senderContainer) {
        if (isDimensionEffective(senderContainer)) return false; // 跨维度忽略距离
        double maxDist = getMaxTransferDistance(senderContainer);
        if (Double.isInfinite(maxDist)) return false;
        double actualDistSq = senderPos.distSqr(receiverPos);
        return actualDistSq > maxDist * maxDist;
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

}
