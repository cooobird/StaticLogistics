package com.coobird.staticlogistics.core.manager;

import com.coobird.staticlogistics.api.LogisticsNode;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 维护目标节点 → 源节点集合的映射，用于快速找到指向某节点的所有源节点。
 * 主要服务于级联删除。
 */
public class IncomingLinkIndex {
    private final Map<LogisticsNode, Set<LogisticsNode>> incoming = new ConcurrentHashMap<>();

    public void add(LogisticsNode source, LogisticsNode target) {
        incoming.computeIfAbsent(target, k -> ConcurrentHashMap.newKeySet()).add(source);
    }

    public void remove(LogisticsNode source, LogisticsNode target) {
        Set<LogisticsNode> sources = incoming.get(target);
        if (sources != null) {
            sources.remove(source);
            if (sources.isEmpty()) incoming.remove(target);
        }
    }

    public Set<LogisticsNode> getSourcesFor(LogisticsNode target) {
        return incoming.getOrDefault(target, Collections.emptySet());
    }

    /**
     * 移除与目标节点相关的所有记录（目标节点被删除时）
     */
    public void removeAllForTarget(LogisticsNode target) {
        incoming.remove(target);
    }

    /**
     * 移除源节点产生的所有记录（源节点被删除时，清理它作为源的所有条目）
     */
    public void removeAllFromSource(LogisticsNode source) {
        incoming.values().forEach(set -> set.remove(source));
    }
}