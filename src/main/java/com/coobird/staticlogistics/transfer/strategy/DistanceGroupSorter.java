package com.coobird.staticlogistics.transfer.strategy;

import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.api.TransferCursorProvider;
import com.coobird.staticlogistics.api.type.GroupSorter;
import com.coobird.staticlogistics.integration.sable.DynamicNodeSpace;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.ToDoubleFunction;

/// 两种距离策略共享稳定排序；每个目标只计算一次距离。动态空间只在主线程查询。
public enum DistanceGroupSorter implements GroupSorter {
    NEAREST, FURTHEST;

    private record Measured(LogisticsNode node, double distance) {
    }

    @Override
    public List<LogisticsNode> sort(List<LogisticsNode> group, BlockPos sourcePos, LogisticsNode source, TransferCursorProvider cursors) {
        return ordered(group, node -> node.isInSameDimension(source.gPos().dimension())
            ? node.gPos().pos().distSqr(sourcePos) : Double.POSITIVE_INFINITY);
    }

    @Override
    public List<LogisticsNode> sort(ServerLevel level, List<LogisticsNode> group, BlockPos sourcePos, LogisticsNode source, TransferCursorProvider cursors) {
        if (!level.getServer().isSameThread())
            throw new IllegalStateException("Dynamic distance queries require the server thread");
        return ordered(group, node -> distance(level, sourcePos, source, node));
    }

    /// 不同维度没有可比较的物理距离，统一视为无穷远；同维度才查询移动结构的世界坐标。
    static double distance(ServerLevel level, BlockPos sourcePos, LogisticsNode source, LogisticsNode target) {
        if (!target.isInSameDimension(source.gPos().dimension())) return Double.POSITIVE_INFINITY;
        return DynamicNodeSpace.distanceSquared(level, sourcePos, target.gPos().pos());
    }

    private List<LogisticsNode> ordered(List<LogisticsNode> group, ToDoubleFunction<LogisticsNode> distance) {
        if (group.size() <= 1) return new ArrayList<>(group);
        List<Measured> measured = new ArrayList<>(group.size());
        for (LogisticsNode node : group) measured.add(new Measured(node, distance.applyAsDouble(node)));
        Comparator<Measured> comparator = Comparator.comparingDouble(Measured::distance);
        measured.sort(this == NEAREST ? comparator : comparator.reversed());
        List<LogisticsNode> result = new ArrayList<>(measured.size());
        for (Measured target : measured) result.add(target.node());
        return result;
    }
}
