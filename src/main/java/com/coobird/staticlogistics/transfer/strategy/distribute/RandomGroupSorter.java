package com.coobird.staticlogistics.transfer.strategy.distribute;

import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.core.manager.GlobalLogisticsManager;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 随机排序：打乱目标顺序，每次结果不同。
 */
public enum RandomGroupSorter implements GroupSorter {
    INSTANCE;

    @Override
    public List<LogisticsNode> sort(List<LogisticsNode> group, BlockPos sourcePos,
                                    LogisticsNode sourceNode, GlobalLogisticsManager glm) {
        List<LogisticsNode> shuffled = new ArrayList<>(group);
        Collections.shuffle(shuffled);
        return shuffled;
    }
}
