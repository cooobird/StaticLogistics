package com.coobird.staticlogistics.transfer.strategy.distribute;

import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.api.TransferCursorProvider;
import com.coobird.staticlogistics.api.type.GroupSorter;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;

/**
 * 近到远排序：距离源节点最近的目标优先。
 * 用 int[] 代替 Integer[] 避免装箱开销。
 */
public enum NearestGroupSorter implements GroupSorter {
    INSTANCE;

    @Override
    public List<LogisticsNode> sort(List<LogisticsNode> group, BlockPos sourcePos,
                                    LogisticsNode sourceNode, TransferCursorProvider cursorProvider) {
        int n = group.size();
        if (n <= 1) return new ArrayList<>(group);

        // 计算距离并用原地排序的索引数组，避免 Integer 装箱
        double[] dists = new double[n];
        int[] idx = new int[n];
        for (int i = 0; i < n; i++) {
            dists[i] = group.get(i).gPos().pos().distSqr(sourcePos);
            idx[i] = i;
        }

        // 简单插入排序（n 通常很小，< 50）
        for (int i = 1; i < n; i++) {
            int key = idx[i];
            double keyDist = dists[key];
            int j = i - 1;
            while (j >= 0 && dists[idx[j]] > keyDist) {
                idx[j + 1] = idx[j];
                j--;
            }
            idx[j + 1] = key;
        }

        List<LogisticsNode> result = new ArrayList<>(n);
        for (int i = 0; i < n; i++) result.add(group.get(idx[i]));
        return result;
    }
}
