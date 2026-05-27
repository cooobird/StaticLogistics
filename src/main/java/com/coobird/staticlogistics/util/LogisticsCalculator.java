package com.coobird.staticlogistics.util;

import com.coobird.staticlogistics.api.type.TransferType;
import com.coobird.staticlogistics.config.SLConfig;
import com.coobird.staticlogistics.storage.config.ContainerConfig;
import net.minecraft.core.BlockPos;

/**
 * 物流计算工具类 - 集中所有与升级倍率、范围、距离相关的计算
 */
public final class LogisticsCalculator {

    private LogisticsCalculator() {
    }

    /**
     * 获取速度倍率（直接读取缓存）
     */
    public static int getSpeedMultiplier(ContainerConfig container) {
        return container == null ? 1 : container.getSpeedMultiplier();
    }

    /**
     * 获取范围倍率（直接读取缓存）
     */
    public static int getRangeMultiplier(ContainerConfig container) {
        return container == null ? 1 : container.getRangeMultiplier();
    }

    /**
     * 获取堆叠倍率（直接读取缓存）
     */
    public static int getStackMultiplier(ContainerConfig container) {
        return container == null ? 1 : container.getStackMultiplier();
    }

    /**
     * 是否支持跨维度
     */
    public static boolean isDimensionEffective(ContainerConfig container) {
        return container != null && container.isDimensionEffective();
    }

    // 算一次最多传多少，不管配置文件的上限了——源/目标自己有多能装就是多大量
    public static long getTransferLimit(ContainerConfig container, TransferType type) {
        if (container == null) {
            return type.getBaseStackSize();
        }
        long stackMult = getStackMultiplier(container);
        if (stackMult >= ContainerConfig.INFINITY_MARKER) {
            return Integer.MAX_VALUE;
        }
        long limit = (long) type.getBaseStackSize() * stackMult;
        return Math.min(limit, Integer.MAX_VALUE);
    }

    // 算最远传多远，不设人工上限，倍率多少就是多少
    // 倍率大到没意义(>=21亿)就直接返回无限远，后面距离检查直接跳过
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
     * 检查两个节点是否在传输范围内（基于发送端容器）
     */
    public static boolean isWithinRange(BlockPos senderPos, BlockPos receiverPos, ContainerConfig senderContainer) {
        if (isDimensionEffective(senderContainer)) return true; // 跨维度忽略距离
        double maxDist = getMaxTransferDistance(senderContainer);
        if (Double.isInfinite(maxDist)) return true;
        double actualDistSq = senderPos.distSqr(receiverPos);
        return actualDistSq <= maxDist * maxDist;
    }

}