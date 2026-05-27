package com.coobird.staticlogistics.transfer.strategy.distribute;

import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.core.manager.GlobalLogisticsManager;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;

/**
 * 轮询排序：从上次位置开始旋转目标列表，确保目标均匀分配。
 */
public enum RoundRobinGroupSorter implements GroupSorter {
    INSTANCE;

    @Override
    public List<LogisticsNode> sort(List<LogisticsNode> group, BlockPos sourcePos,
                                    LogisticsNode sourceNode, GlobalLogisticsManager glm) {
        int n = group.size();
        if (n <= 1) return new ArrayList<>(group);

        int index = glm.getNextRoundRobinIndex(sourceNode.toKey(), n);
        List<LogisticsNode> result = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            result.add(group.get((index + i) % n));
        }
        return result;
    }
}
