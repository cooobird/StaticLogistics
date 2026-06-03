package com.coobird.staticlogistics.transfer.strategy.distribute;

import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.core.manager.GlobalLogisticsManager;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;

/**
 * 顺序排序：保持原始插入顺序不变，不做任何重排序。
 */
public enum SequentialGroupSorter implements GroupSorter {
    INSTANCE;

    @Override
    public List<LogisticsNode> sort(List<LogisticsNode> group, BlockPos sourcePos,
                                    LogisticsNode sourceNode, GlobalLogisticsManager glm) {
        return new ArrayList<>(group);
    }
}
