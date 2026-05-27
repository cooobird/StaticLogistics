package com.coobird.staticlogistics.transfer.strategy.distribute;

import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.api.type.DistributionStrategy;
import com.coobird.staticlogistics.core.manager.GlobalLogisticsManager;
import net.minecraft.core.BlockPos;

import java.util.List;

/**
 * 分发目标排序器 —— 对同一 priority 组内的目标节点排序。
 */
public interface GroupSorter {

    /**
     * 根据分发策略获取对应排序器。
     */
    static GroupSorter forStrategy(DistributionStrategy strategy) {
        return switch (strategy) {
            case FURTHEST -> FurthestGroupSorter.INSTANCE;
            case RANDOM -> RandomGroupSorter.INSTANCE;
            case ROUND_ROBIN -> RoundRobinGroupSorter.INSTANCE;
            default -> NearestGroupSorter.INSTANCE;
        };
    }

    /**
     * @param group      同 priority 组的目标节点列表
     * @param sourcePos  源节点坐标（NEAREST/FURTHEST 需要）
     * @param sourceNode 源节点（ROUND_ROBIN 用 key 查游标）
     * @param glm        全局管理器
     * @return 排序后的目标列表
     */
    List<LogisticsNode> sort(List<LogisticsNode> group, BlockPos sourcePos, LogisticsNode sourceNode, GlobalLogisticsManager glm);
}
