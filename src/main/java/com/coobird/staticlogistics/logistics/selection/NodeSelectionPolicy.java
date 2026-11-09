package com.coobird.staticlogistics.logistics.selection;

import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.api.NodeRole;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

import java.util.Collection;
import java.util.List;
import java.util.function.Function;

/// 工具选点的统一领域边界，扫描、持久化和网络同步不得各自定义上限。
public final class NodeSelectionPolicy {
    public static final int MAX_NODES = 4096;
    public static final int MAX_SCANNED_BLOCKS = 32768;
    public static final Codec<List<LogisticsNode>> CODEC = Codec.list(LogisticsNode.CODEC)
        .validate(nodes -> nodes.size() <= MAX_NODES ? DataResult.success(nodes) : DataResult.error(() -> "Stored node count exceeds " + MAX_NODES))
        .xmap(List::copyOf, List::copyOf);

    private NodeSelectionPolicy() {
    }

    /// 多选连接按当前端点的角色和通道选择同侧节点，不限制物理朝向。
    public static List<LogisticsNode> configurationTargets(Collection<LogisticsNode> nodes,
                                                           LogisticsNode focus,
                                                           Function<LogisticsNode, NodeRole> role) {
        NodeRole selectedRole = role.apply(focus);
        if (selectedRole == null) return List.of(focus);
        return nodes.stream().filter(node -> node.channel() == focus.channel()
            && role.apply(node) == selectedRole).distinct().toList();
    }

    public static int remainingCapacity(int currentSize) {
        return Math.max(0, MAX_NODES - currentSize);
    }
}
