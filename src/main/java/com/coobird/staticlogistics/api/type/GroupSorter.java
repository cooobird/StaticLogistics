package com.coobird.staticlogistics.api.type;

import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.api.TransferCursorProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.List;

/**
 * 分发目标排序器 —— 对同一 priority 组内的目标节点排序。
 * <p>
 * 每种 {@link DistributionStrategy} 持有一个对应的 GroupSorter 实例。
 */
@FunctionalInterface
public interface GroupSorter {
    /**
     * @param group          同 priority 组的目标节点列表
     * @param sourcePos      源节点坐标
     * @param sourceNode     源节点
     * @param cursorProvider round-robin 游标提供者
     * @return 排序后的目标列表
     */
    List<LogisticsNode> sort(List<LogisticsNode> group, BlockPos sourcePos,
                             LogisticsNode sourceNode, TransferCursorProvider cursorProvider);

    /**
     * 带世界上下文的排序入口，供动态子世界按真实位置排序。第三方旧实现继续复用原入口。
     */
    default List<LogisticsNode> sort(ServerLevel level, List<LogisticsNode> group,
                                     BlockPos sourcePos, LogisticsNode sourceNode,
                                     TransferCursorProvider cursorProvider) {
        return sort(group, sourcePos, sourceNode, cursorProvider);
    }
}
