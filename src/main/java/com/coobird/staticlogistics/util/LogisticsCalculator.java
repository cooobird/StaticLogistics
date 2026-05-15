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

    /**
     * 获取某个传输类型的单次传输上限（考虑堆叠倍率和全局上限）
     */
    public static long getTransferLimit(ContainerConfig container, TransferType type) {
        if (container == null) {
            return Math.min(type.baseStackSize(), SLConfig.getMaxTransferLimit());
        }
        long stackMult = getStackMultiplier(container);
        long limit = (long) type.baseStackSize() * stackMult;
        long maxAllowed = SLConfig.getMaxTransferLimit();
        return Math.min(limit, maxAllowed);
    }

    /**
     * 计算最大有效传输距离（米）
     */
    public static double getMaxTransferDistance(ContainerConfig container) {
        if (container == null) return SLConfig.getDefaultRadius();
        double baseRadius = SLConfig.getDefaultRadius();
        long rangeMult = getRangeMultiplier(container);
        if (rangeMult >= ContainerConfig.INFINITY_MARKER) {
            return Double.POSITIVE_INFINITY;
        }
        return baseRadius * Math.min(rangeMult, 10000L);
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

    /**
     * 获取距离信息（用于提示）
     */
    public static DistanceInfo getDistanceInfo(BlockPos senderPos, BlockPos receiverPos, ContainerConfig senderContainer) {
        double maxDist = getMaxTransferDistance(senderContainer);
        double actualDist = Math.sqrt(senderPos.distSqr(receiverPos));
        return new DistanceInfo(actualDist, maxDist, maxDist >= Double.POSITIVE_INFINITY - 1);
    }

    public record DistanceInfo(double actual, double max, boolean infinite) {
    }
}