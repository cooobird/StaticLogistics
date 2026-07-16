package com.coobird.staticlogistics.transfer.strategy;

import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.api.TransferCursorProvider;
import com.coobird.staticlogistics.api.type.GroupSorter;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * 轮询排序：从上次位置开始旋转目标列表，确保目标均匀分配。
 */
public enum RoundRobinGroupSorter implements GroupSorter {
    INSTANCE;

    private static final ResourceLocation RR_KEY = StaticLogistics.asResource("group_rr");

    @Override
    public List<LogisticsNode> sort(List<LogisticsNode> group, BlockPos sourcePos,
                                    LogisticsNode sourceNode, TransferCursorProvider cursorProvider) {
        int n = group.size();
        if (n <= 1) return new ArrayList<>(group);

        // 使用固定的 ResourceLocation key 避免 null type
        int[] cursor = cursorProvider.getCursor(sourceNode, RR_KEY);
        int index = cursor[0] % n;
        cursor[0] = (index + 1) % n;

        List<LogisticsNode> result = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            result.add(group.get((index + i) % n));
        }
        return result;
    }
}
