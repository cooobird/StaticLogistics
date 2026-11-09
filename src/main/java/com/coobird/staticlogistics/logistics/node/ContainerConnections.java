package com.coobird.staticlogistics.logistics.node;

import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.api.group.GroupKey;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.MinecraftServer;

import java.util.*;

/// 容器连接的通道使用关系；通道编号不参与容器连接身份。
public final class ContainerConnections {
    private ContainerConnections() {
    }

    public record Route(LogisticsNode first, LogisticsNode second) {
    }

    public static List<Route> routes(MinecraftServer server, ConnectionKey connection) {
        return routesBatch(server, List.of(connection)).getOrDefault(connection, List.of());
    }

    /// 同一批次的每个起点容器只扫描一次，不为每条连接重复枚举全部相邻节点。
    public static Map<ConnectionKey, List<Route>> routesBatch(MinecraftServer server, Collection<ConnectionKey> connections) {
        return collectBatch(server, connections, true);
    }

    /// 删除与修复使用全部现存引用，包括只有一端保留的损坏连接。
    public static Map<ConnectionKey, List<Route>> referencesBatch(
        MinecraftServer server,
        Collection<ConnectionKey> connections
    ) {
        return collectBatch(server, connections, false);
    }

    private static Map<ConnectionKey, List<Route>> collectBatch(
        MinecraftServer server,
        Collection<ConnectionKey> connections,
        boolean requireReciprocal
    ) {
        Map<GlobalPos, Map<GroupKey, Set<GlobalPos>>> requested = new LinkedHashMap<>();
        for (ConnectionKey key : connections) {
            requested.computeIfAbsent(key.first(), ignored -> new HashMap<>())
                .computeIfAbsent(key.groupKey(), ignored -> new HashSet<>()).add(key.second());
            if (!requireReciprocal) {
                requested.computeIfAbsent(key.second(), ignored -> new HashMap<>())
                    .computeIfAbsent(key.groupKey(), ignored -> new HashSet<>()).add(key.first());
            }
        }
        Map<ConnectionKey, Set<Route>> result = new HashMap<>();
        requested.forEach((position, groups) -> {
            var level = server.getLevel(position.dimension());
            if (level == null) return;
            var manager = LinkManager.get(level);
            var container = manager.getContainerConfig(position.pos());
            if (container == null) return;
            for (var address : container.getLinkedFaceKeys()) {
                var source = address.toNode(level.dimension());
                var config = manager.getEndpointConfig(address);
                if (config == null) continue;
                config.forEachLinkedNode((group, target) -> {
                    Set<GlobalPos> targets = groups.get(group);
                    if (targets == null || !targets.contains(target.gPos())) return;
                    if (requireReciprocal) {
                        var targetLevel = server.getLevel(target.gPos().dimension());
                        var other = targetLevel == null ? null
                            : LinkManager.get(targetLevel).getEndpointConfig(EndpointAddress.of(target));
                        if (other == null || !other.isLinkedTo(group, source)) return;
                    }
                    ConnectionKey key = new ConnectionKey(group, position, target.gPos());
                    result.computeIfAbsent(key, ignored -> new LinkedHashSet<>())
                        .add(new Route(source, target));
                });
            }
        });
        Map<ConnectionKey, List<Route>> immutable = new HashMap<>();
        result.forEach((key, routes) -> immutable.put(key, List.copyOf(routes)));
        return Map.copyOf(immutable);
    }
}
