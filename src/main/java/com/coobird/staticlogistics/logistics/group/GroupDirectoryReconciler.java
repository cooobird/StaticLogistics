package com.coobird.staticlogistics.logistics.group;

import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.api.NodeRole;
import com.coobird.staticlogistics.api.group.GroupKey;
import com.coobird.staticlogistics.api.group.GroupRef;
import com.coobird.staticlogistics.logistics.node.FaceAddress;
import com.coobird.staticlogistics.logistics.node.LinkManager;
import com.mojang.logging.LogUtils;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;

import java.util.*;

/**
 * 服务器启动后从权威面配置收敛分组目录和全服运行时成员索引。
 */
public final class GroupDirectoryReconciler {
    private static final Logger LOGGER = LogUtils.getLogger();

    private GroupDirectoryReconciler() {
    }

    public static Report reconcile(MinecraftServer server) {
        if (server == null) throw new IllegalArgumentException("Reconciliation server must not be null");
        Map<LogisticsNode, DesiredNode> desiredNodes = new LinkedHashMap<>();
        Map<UUID, LinkedHashMap<GroupKey, GroupRef>> groupsByOwner = new LinkedHashMap<>();
        int faceCount = 0;
        int conflicts = 0;

        for (var level : server.getAllLevels()) {
            LinkManager manager = LinkManager.get(level);
            for (FaceAddress address : manager.getAllConfigKeys()) {
                var config = manager.getFaceConfig(address);
                if (config == null || config.isDefault()) continue;
                faceCount++;
                UUID owner = config.faceConfig.getOwner();
                LogisticsNode node = manager.createNodeFromKey(address);
                NodeRole role = config.determineRole();
                Map<GroupKey, GroupRef> nodeGroups = new LinkedHashMap<>();
                for (GroupRef group : config.faceConfig.getGroups()) {
                    if (group.key().isLegacyUnowned()) continue;
                    if (owner == null || !owner.equals(group.key().ownerId())) {
                        conflicts++;
                        LOGGER.error("Skipping group with mismatched face owner during reconciliation: {}",
                            group.key());
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
                    if (role != NodeRole.NONE) nodeGroups.put(group.key(), group);
                }
                if (!nodeGroups.isEmpty()) desiredNodes.put(node, new DesiredNode(role, nodeGroups));
            }
        }

        PlayerGroupStore store = PlayerGroupStore.get(server);
        int additions = 0;
        for (var ownerEntry : groupsByOwner.entrySet()) {
            List<GroupRef> accepted = new ArrayList<>();
            for (GroupRef group : ownerEntry.getValue().values()) {
                GroupRef byKey = store.findGroup(group.key());
                if (byKey != null) {
                    if (!byKey.displayName().equals(group.displayName())) {
                        conflicts++;
                        LOGGER.error("Group identity has conflicting names during reconciliation: {}",
                            group.key());
                    }
                    continue;
                }
                GroupRef byName = store.findGroup(ownerEntry.getKey(), group.displayName());
                if (byName != null && !byName.key().equals(group.key())) {
                    conflicts++;
                    LOGGER.error("Group name belongs to another identity during reconciliation: owner={}, name={}",
                        ownerEntry.getKey(), group.displayName());
                    continue;
                }
                accepted.add(group);
            }
            if (accepted.isEmpty()) continue;
            try {
                store.registerClaimedGroups(ownerEntry.getKey(), accepted);
                additions += accepted.size();
            } catch (IllegalArgumentException | IllegalStateException exception) {
                conflicts += accepted.size();
                LOGGER.error("Failed to reconcile group directory for owner {}", ownerEntry.getKey(), exception);
            }
        }

        int indexRemovals = 0;
        int indexRegistrations = 0;
        GlobalLogisticsManager globalManager = GlobalLogisticsManager.get(server);
        for (GroupKey groupKey : Set.copyOf(globalManager.getActiveGroupKeys())) {
            for (LogisticsNode node : List.copyOf(globalManager.getNodesInGroup(groupKey).keySet())) {
                DesiredNode desired = desiredNodes.get(node);
                if (desired == null || !desired.groups().containsKey(groupKey)) {
                    globalManager.unregisterNode(groupKey, node);
                    indexRemovals++;
                }
            }
        }
        for (var entry : desiredNodes.entrySet()) {
            for (GroupRef group : entry.getValue().groups().values()) {
                NodeRole current = globalManager.getNodesInGroup(group.key())
                    .getOrDefault(entry.getKey(), NodeRole.NONE);
                if (current != entry.getValue().role()) indexRegistrations++;
                globalManager.registerNode(group, entry.getKey(), entry.getValue().role());
            }
        }

        Report report = new Report(faceCount, additions, conflicts, indexRegistrations, indexRemovals);
        if (report.changed()) {
            LOGGER.info("Logistics reconciliation completed: faces={}, directoryAdditions={}, conflicts={}, "
                    + "indexRegistrations={}, indexRemovals={}",
                faceCount, additions, conflicts, indexRegistrations, indexRemovals);
        }
        return report;
    }

    private record DesiredNode(NodeRole role, Map<GroupKey, GroupRef> groups) {
        DesiredNode {
            groups = Map.copyOf(groups);
        }
    }

    public record Report(int faceCount, int directoryAdditions, int conflicts,
                         int indexRegistrations, int indexRemovals) {
        public boolean changed() {
            return directoryAdditions > 0 || conflicts > 0
                || indexRegistrations > 0 || indexRemovals > 0;
        }
    }
}
