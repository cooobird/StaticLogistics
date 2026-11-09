package com.coobird.staticlogistics.logistics.group;

import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.api.NodeRole;
import com.coobird.staticlogistics.api.group.GroupKey;
import com.coobird.staticlogistics.api.group.GroupRef;
import com.coobird.staticlogistics.logistics.node.ConnectionKey;
import com.coobird.staticlogistics.logistics.node.EndpointAddress;
import com.coobird.staticlogistics.logistics.node.LinkManager;
import com.mojang.logging.LogUtils;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;

import java.util.*;

/// 服务器启动后从权威面配置收敛分组目录和全服运行时成员索引。
public final class GroupDirectoryReconciler {
    private static final Logger LOGGER = LogUtils.getLogger();

    private GroupDirectoryReconciler() {
    }

    public static Report reconcile(MinecraftServer server) {

        Map<LogisticsNode, DesiredNode> desiredNodes = new LinkedHashMap<>();
        Map<UUID, LinkedHashMap<GroupKey, GroupRef>> groupsByOwner = new LinkedHashMap<>();
        Set<ConnectionKey> connections = new LinkedHashSet<>();
        int faceCount = 0;
        int conflicts = 0;

        for (var level : server.getAllLevels()) {
            LinkManager manager = LinkManager.get(level);
            for (EndpointAddress faceKey : manager.getAllConfigKeys()) {
                var config = manager.getEndpointConfig(faceKey);
                if (config == null || config.isDefault()) continue;
                faceCount++;
                LogisticsNode node = manager.createNodeFromKey(faceKey);
                NodeRole role = config.determineRole();
                Map<GroupKey, GroupRef> nodeGroups = new LinkedHashMap<>();
                for (GroupRef group : config.faceConfig.getGroups()) {
                    if (group.key().isLegacyUnowned()) continue;
                    UUID owner = config.faceConfig.getOwner();
                    if (owner == null || !owner.equals(group.key().ownerId())) {
                        conflicts++;
                        LOGGER.error("Skipping group with mismatched face owner during reconciliation: {}", group.key());
                        continue;
                    }
                    GroupRef previous = groupsByOwner
                        .computeIfAbsent(owner, ignored -> new LinkedHashMap<>())
                        .putIfAbsent(group.key(), group);
                    if (previous != null && !previous.equals(group)) {
                        conflicts++;
                        LOGGER.error("Conflicting group metadata during reconciliation: {}", group.key());
                        continue;
                    }
                    nodeGroups.put(group.key(), group);
                    for (LogisticsNode linked : config.getLinkedNodes(group.key())) {
                        if (!node.gPos().equals(linked.gPos())) {
                            connections.add(new ConnectionKey(group.key(), node, linked));
                        }
                    }
                }
                if (!nodeGroups.isEmpty()) desiredNodes.put(node, new DesiredNode(role, nodeGroups));
            }
        }

        DirectoryResult directory = reconcileDirectory(server, groupsByOwner);
        int metadataRemovals = PlayerGroupStore.get(server)
            .reconcileConnectionMetadata(connections);
        conflicts += directory.conflicts();
        int indexRemovals = 0;
        int indexRegistrations = 0;
        GlobalLogisticsManager global = GlobalLogisticsManager.get(server);

        for (GroupKey groupKey : Set.copyOf(global.getActiveGroupKeys())) {
            for (LogisticsNode node : List.copyOf(global.getNodesInGroup(groupKey).keySet())) {
                DesiredNode desired = desiredNodes.get(node);
                if (desired == null || !desired.groups.containsKey(groupKey)) {
                    global.unregisterNode(groupKey, node);
                    indexRemovals++;
                }
            }
        }
        for (var nodeEntry : desiredNodes.entrySet()) {
            for (GroupRef group : nodeEntry.getValue().groups.values()) {
                NodeRole current = global.getNodesInGroup(group.key())
                    .getOrDefault(nodeEntry.getKey(), NodeRole.NONE);
                if (current != nodeEntry.getValue().role) indexRegistrations++;
                global.registerNode(group, nodeEntry.getKey(), nodeEntry.getValue().role);
            }
        }

        Report report = new Report(faceCount, directory.additions(), metadataRemovals, conflicts,
            indexRegistrations, indexRemovals);
        if (report.changed()) {
            LOGGER.info("Logistics reconciliation completed: faces={}, directoryAdditions={}, "
                    + "metadataRemovals={}, conflicts={}, indexRegistrations={}, indexRemovals={}",
                report.faceCount(), report.directoryAdditions(), report.metadataRemovals(), report.conflicts(),
                report.indexRegistrations(), report.indexRemovals());
        }
        return report;
    }

    private static DirectoryResult reconcileDirectory(
        MinecraftServer server,
        Map<UUID, LinkedHashMap<GroupKey, GroupRef>> groupsByOwner
    ) {
        PlayerGroupStore store = PlayerGroupStore.get(server);
        int additions = 0;
        int conflicts = 0;
        for (var ownerEntry : groupsByOwner.entrySet()) {
            UUID owner = ownerEntry.getKey();
            List<GroupRef> safeAdditions = new ArrayList<>();
            for (GroupRef group : ownerEntry.getValue().values()) {
                GroupRef sameKey = store.findGroup(group.key());
                if (sameKey != null) {
                    if (!sameKey.displayName().equals(group.displayName())) {
                        conflicts++;
                        LOGGER.error("Group identity has conflicting names during reconciliation: {}", group.key());
                    }
                    continue;
                }
                GroupRef sameName = store.findGroup(owner, group.displayName());
                if (sameName != null && !sameName.key().equals(group.key())) {
                    conflicts++;
                    LOGGER.error("Group name belongs to another identity during reconciliation: owner={}, name={}",
                        owner, group.displayName());
                    continue;
                }
                safeAdditions.add(group);
            }
            if (safeAdditions.isEmpty()) continue;
            try {
                store.registerClaimedGroups(owner, safeAdditions);
                additions += safeAdditions.size();
            } catch (IllegalArgumentException | IllegalStateException exception) {
                conflicts += safeAdditions.size();
                LOGGER.error("Failed to reconcile group directory for owner {}", owner, exception);
            }
        }
        return new DirectoryResult(additions, conflicts);
    }

    private record DesiredNode(NodeRole role, Map<GroupKey, GroupRef> groups) {
        DesiredNode {
            groups = Map.copyOf(groups);
        }
    }

    private record DirectoryResult(int additions, int conflicts) {
    }

    public record Report(int faceCount, int directoryAdditions, int metadataRemovals, int conflicts,
                         int indexRegistrations, int indexRemovals) {
        public boolean changed() {
            return directoryAdditions > 0 || metadataRemovals > 0 || conflicts > 0
                || indexRegistrations > 0 || indexRemovals > 0;
        }
    }
}
