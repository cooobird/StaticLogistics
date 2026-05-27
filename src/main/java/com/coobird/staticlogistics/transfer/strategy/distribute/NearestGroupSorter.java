package com.coobird.staticlogistics.transfer.strategy.distribute;

import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.core.manager.GlobalLogisticsManager;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * 近到远排序：距离源节点最近的目标优先。
 */
public enum NearestGroupSorter implements GroupSorter {
    INSTANCE;

    @Override
    public List<LogisticsNode> sort(List<LogisticsNode> group, BlockPos sourcePos,
                                    LogisticsNode sourceNode, GlobalLogisticsManager glm) {
        int n = group.size();
        if (n <= 1) return new ArrayList<>(group);

        double[] dists = new double[n];
        for (int i = 0; i < n; i++) {
            dists[i] = group.get(i).gPos().pos().distSqr(sourcePos);
        }
        Integer[] idx = new Integer[n];
        for (int i = 0; i < n; i++) idx[i] = i;
        Arrays.sort(idx, Comparator.comparingDouble(a -> dists[a]));

        List<LogisticsNode> result = new ArrayList<>(n);
        for (int i = 0; i < n; i++) result.add(group.get(idx[i]));
        return result;
    }
}
