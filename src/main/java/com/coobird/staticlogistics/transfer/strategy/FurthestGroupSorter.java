package com.coobird.staticlogistics.transfer.strategy;

import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.api.TransferCursorProvider;
import com.coobird.staticlogistics.api.type.GroupSorter;
import com.coobird.staticlogistics.integration.sable.DynamicNodeSpace;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * 远到近排序：距离源节点最远的目标优先。
 */
public enum FurthestGroupSorter implements GroupSorter {
    INSTANCE;

    @Override
    public List<LogisticsNode> sort(List<LogisticsNode> group, BlockPos sourcePos,
                                    LogisticsNode sourceNode, TransferCursorProvider cursorProvider) {
        int n = group.size();
        if (n <= 1) return new ArrayList<>(group);

        double[] dists = new double[n];
        for (int i = 0; i < n; i++)
            dists[i] = group.get(i).gPos().pos().distSqr(sourcePos);
        Integer[] idx = new Integer[n];
        for (int i = 0; i < n; i++) idx[i] = i;
        Arrays.sort(idx, Comparator.comparingDouble((Integer a) -> dists[a]).reversed());

        List<LogisticsNode> result = new ArrayList<>(n);
        for (int i = 0; i < n; i++) result.add(group.get(idx[i]));
        return result;
    }

    @Override
    public List<LogisticsNode> sort(ServerLevel level, List<LogisticsNode> group,
                                    BlockPos sourcePos, LogisticsNode sourceNode,
                                    TransferCursorProvider cursorProvider) {
        int n = group.size();
        if (n <= 1) return new ArrayList<>(group);
        Integer[] indices = new Integer[n];
        double[] distances = new double[n];
        for (int i = 0; i < n; i++) {
            distances[i] = DynamicNodeSpace.distanceSquared(
                level, sourcePos, group.get(i).gPos().pos());
            indices[i] = i;
        }
        Arrays.sort(indices, Comparator.comparingDouble((Integer index) -> distances[index]).reversed());
        List<LogisticsNode> result = new ArrayList<>(n);
        for (int index : indices) result.add(group.get(index));
        return result;
    }
}
