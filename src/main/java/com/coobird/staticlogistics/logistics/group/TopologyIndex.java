package com.coobird.staticlogistics.logistics.group;

import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.api.group.GroupKey;
import com.coobird.staticlogistics.logistics.node.ConnectionKey;

import java.util.*;
import java.util.stream.Collectors;

/// 服务端拓扑的单一运行时投影；所有派生索引在同一次替换中更新。
final class TopologyIndex {
    private final Map<LogisticsNode, Map<GroupKey, Set<LogisticsNode>>> outgoingByGroup = new HashMap<>();
    private final Map<LogisticsNode, Set<LogisticsNode>> incomingSources = new HashMap<>();
    private final Map<GroupKey, Map<ConnectionKey, Integer>> connectionReferences = new HashMap<>();

    void replaceOutgoing(LogisticsNode source, Map<GroupKey, Set<LogisticsNode>> targetsByGroup) {
        removeOutgoing(source);
        if (source == null || targetsByGroup == null || targetsByGroup.isEmpty()) return;
        Map<GroupKey, Set<LogisticsNode>> scoped = new LinkedHashMap<>();
        targetsByGroup.forEach((group, targets) -> {
            if (group == null || targets == null) return;
            Set<LogisticsNode> copied = targets.stream().filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
            if (copied.isEmpty()) return;
            scoped.put(group, Set.copyOf(copied));
            for (LogisticsNode target : copied) {
                if (source.gPos().equals(target.gPos())) continue;
                connectionReferences.computeIfAbsent(group, ignored -> new HashMap<>())
                    .merge(new ConnectionKey(group, source, target), 1, Integer::sum);
            }
        });
        if (scoped.isEmpty()) return;
        outgoingByGroup.put(source, Map.copyOf(scoped));
        scoped.values().stream().flatMap(Set::stream).distinct().forEach(target ->
            incomingSources.computeIfAbsent(target, ignored -> new LinkedHashSet<>()).add(source));
    }

    void removeOutgoing(LogisticsNode source) {
        Map<GroupKey, Set<LogisticsNode>> scoped = outgoingByGroup.remove(source);
        if (scoped == null) return;
        scoped.forEach((group, targets) -> targets.forEach(target -> {
            if (source.gPos().equals(target.gPos())) return;
            ConnectionKey key = new ConnectionKey(group, source, target);
            Map<ConnectionKey, Integer> references = connectionReferences.get(group);
            if (references == null) return;
            Integer count = references.get(key);
            if (count == null || count <= 1) references.remove(key);
            else references.put(key, count - 1);
            if (references.isEmpty()) connectionReferences.remove(group);
        }));
        scoped.values().stream().flatMap(Set::stream).distinct().forEach(target -> {
            Set<LogisticsNode> sources = incomingSources.get(target);
            if (sources == null) return;
            sources.remove(source);
            if (sources.isEmpty()) incomingSources.remove(target);
        });
    }

    Set<LogisticsNode> sourcesLinkedTo(LogisticsNode target) {
        if (target == null) return Set.of();
        return Set.copyOf(incomingSources.getOrDefault(target, Set.of()));
    }

    Map<LogisticsNode, Set<LogisticsNode>> sourcesLinkedTo(Collection<LogisticsNode> targets) {
        if (targets == null || targets.isEmpty()) return Map.of();
        Map<LogisticsNode, Set<LogisticsNode>> result = new LinkedHashMap<>();
        targets.stream().filter(Objects::nonNull).distinct().forEach(target -> {
            Set<LogisticsNode> sources = incomingSources.get(target);
            if (sources != null && !sources.isEmpty()) result.put(target, Set.copyOf(sources));
        });
        return Map.copyOf(result);
    }

    Set<ConnectionKey> connections(GroupKey group) {
        if (group == null) return Set.of();
        return Set.copyOf(connectionReferences.getOrDefault(group, Map.of()).keySet());
    }

    boolean hasRoute(GroupKey group, LogisticsNode source, LogisticsNode target) {
        return outgoingByGroup.getOrDefault(source, Map.of()).getOrDefault(group, Set.of()).contains(target);
    }

    Set<LogisticsNode> linkedNodes(GroupKey group, LogisticsNode node) {
        Set<LogisticsNode> result = new HashSet<>(outgoingByGroup.getOrDefault(node, Map.of()).getOrDefault(group, Set.of()));
        for (LogisticsNode source : incomingSources.getOrDefault(node, Set.of()))
            if (hasRoute(group, source, node)) result.add(source);
        return Set.copyOf(result);
    }
}
