package com.coobird.staticlogistics.transfer.strategy.distribute;

import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.core.manager.GlobalLogisticsManager;
import net.minecraft.core.BlockPos;

import java.util.List;

/**
 * 分发目标排序器 —— 对同一 priority 组内的目标节点排序。
 * <p>
 * 每种 {@link com.coobird.staticlogistics.api.type.DistributionStrategy} 持有一个对应的 GroupSorter 实例。
 * 通过 {@code strategy.getSorter()} 获取，不再使用本接口的静态工厂方法。
 */
public interface GroupSorter {

    /**
     * @param group      同 priority 组的目标节点列表
     * @param sourcePos  源节点坐标（NEAREST/FURTHEST 需要）
     * @param sourceNode 源节点（ROUND_ROBIN 用 key 查游标）
     * @param glm        全局管理器
     * @return 排序后的目标列表
     */
    List<LogisticsNode> sort(List<LogisticsNode> group, BlockPos sourcePos,
                             LogisticsNode sourceNode, GlobalLogisticsManager glm);
}
