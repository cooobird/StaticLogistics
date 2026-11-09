package com.coobird.staticlogistics.logistics.group;

import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.api.group.GroupConstraints;
import com.coobird.staticlogistics.api.group.GroupKey;
import com.coobird.staticlogistics.content.event.PlayerEvents;
import com.coobird.staticlogistics.content.item.BulkWorkScheduler;
import com.coobird.staticlogistics.logistics.node.*;
import com.mojang.logging.LogUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/// 连接的统一单选／批量重命名、移动与删除的服务端权威用例。
public final class ConnectionCommandService {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final long MUTATION_COOLDOWN_TICKS = 2L;
    private static final String DIRECTORY_DELETION_TASK = "directory_deletion";
    private static final String CONNECTION_DELETION_TASK = "connection_deletion";
    private static final Map<RateKey, Long> LAST_MUTATION_TICK = new LinkedHashMap<>();

    private final MinecraftServer server;

    public ConnectionCommandService(MinecraftServer server) {
        this.server = server;
    }

    /// 服务器停止时释放限流状态，避免静态表长期持有旧服务器实例。
    public static void release(MinecraftServer server) {
        if (server != null) {
            LAST_MUTATION_TICK.keySet().removeIf(key -> key.server() == server);
        }
    }

    public boolean rename(ServerPlayer actor, ConnectionKey requestedKey, String displayName) {
        return renameBatch(actor, List.of(requestedKey), displayName);
    }

    public boolean renameBatch(ServerPlayer actor, List<ConnectionKey> keys, String baseName) {
        if (actor == null || BulkWorkScheduler.isPending(actor, DIRECTORY_DELETION_TASK)) return false;
        if (!validBatchShape(keys) || baseName == null || baseName.isBlank()
            || !tryAcquireMutation(actor)) return false;
        if (hasLockedConnection(keys)) return false;
        if (resolveBatch(actor, keys) == null) return false;
        Map<ConnectionKey, String> names = new LinkedHashMap<>();
        try {
            for (int i = 0; i < keys.size(); i++) {
                names.put(keys.get(i), GroupConstraints.normalizeConnectionName(keys.size() == 1 ? baseName.trim() : baseName.trim() + " " + (i + 1)));
            }
        } catch (IllegalArgumentException exception) {
            return false;
        }
        PlayerGroupStore store = PlayerGroupStore.get(server);
        Map<ConnectionKey, String> previous = new LinkedHashMap<>();
        for (ConnectionKey key : keys) previous.put(key, store.getConnectionName(key));
        for (var entry : names.entrySet()) {
            if (!store.setConnectionName(entry.getKey(), entry.getValue())) {
                previous.forEach(store::setConnectionName);
                return false;
            }
        }
        refreshAuthorizedClients(keys.stream().map(key -> key.groupKey().ownerId()).collect(Collectors.toSet()));
        return true;
    }

    public boolean moveBatch(ServerPlayer actor, List<ConnectionKey> keys, String folder) {
        if (actor == null || BulkWorkScheduler.isPending(actor, DIRECTORY_DELETION_TASK)) return false;
        if (!validBatchShape(keys) || folder == null || !tryAcquireMutation(actor)) return false;
        if (hasLockedConnection(keys)) return false;
        if (resolveBatch(actor, keys) == null) return false;
        PlayerGroupStore store = PlayerGroupStore.get(server);
        var data = store.getHierarchy(actor.getUUID()).copy();
        var entries = data.getCompound("entries");
        if (!folder.isEmpty() && !entries.contains(folder)) return false;
        for (ConnectionKey key : keys)
            if (!GroupHierarchy.edit(data, "assign", GroupHierarchy.connectionId(key), folder, "")) return false;
        store.setHierarchy(actor.getUUID(), data);
        PlayerEvents.requestHierarchyRefresh(actor);
        return true;
    }

    private List<ResolvedConnection> resolveBatch(ServerPlayer actor, List<ConnectionKey> keys) {
        if (!validBatchShape(keys)) return null;
        return resolveConnections(actor, keys);
    }

    private static boolean validBatchShape(List<ConnectionKey> keys) {
        return keys != null && !keys.isEmpty()
            && keys.size() <= GroupConstraints.MAX_HIERARCHY_LINKS
            && new HashSet<>(keys).size() == keys.size();
    }

    private List<ResolvedConnection> resolveConnections(ServerPlayer actor, Collection<ConnectionKey> keys) {
        return resolveConnections(actor, keys, true);
    }

    private List<ResolvedConnection> resolveConnections(ServerPlayer actor,
                                                        Collection<ConnectionKey> keys,
                                                        boolean requireConfigurator) {
        for (ConnectionKey key : keys) if (!canResolve(actor, key, requireConfigurator)) return null;
        var routes = ContainerConnections.routesBatch(server, keys);
        return resolveConnections(actor, keys, routes, requireConfigurator);
    }

    private List<ResolvedConnection> resolveConnections(
        ServerPlayer actor,
        Collection<ConnectionKey> keys,
        Map<ConnectionKey, List<ContainerConnections.Route>> routes,
        boolean requireConfigurator
    ) {
        List<ResolvedConnection> resolved = new ArrayList<>();
        Map<GroupKey, Set<LogisticsNode>> validated = new LinkedHashMap<>();
        for (ConnectionKey key : keys) {
            var connection = resolve(actor, key, routes.getOrDefault(key, List.of()), validated, requireConfigurator);
            if (connection == null) return null;
            resolved.add(connection);
        }
        return resolved;
    }

    private List<ResolvedConnection> resolveDeletionConnections(
        ServerPlayer actor,
        Collection<ConnectionKey> keys,
        boolean requireConfigurator
    ) {
        return resolveDeletionConnections(actor, keys,
            ContainerConnections.referencesBatch(server, keys), requireConfigurator);
    }

    private List<ResolvedConnection> resolveDeletionConnections(
        ServerPlayer actor,
        Collection<ConnectionKey> keys,
        Map<ConnectionKey, List<ContainerConnections.Route>> references,
        boolean requireConfigurator
    ) {
        for (ConnectionKey key : keys) {
            if (!canResolve(actor, key, requireConfigurator)) return null;
        }
        Map<GroupKey, Set<LogisticsNode>> validated = new LinkedHashMap<>();
        List<ResolvedConnection> resolved = new ArrayList<>();
        for (ConnectionKey key : keys) {
            List<ContainerConnections.Route> routes = references.getOrDefault(key, List.of());
            if (routes.isEmpty()) continue;
            Set<LogisticsNode> checked = validated.computeIfAbsent(
                key.groupKey(), ignored -> new HashSet<>());
            for (var route : routes) {
                for (LogisticsNode node : List.of(route.first(), route.second())) {
                    if (checked.contains(node)) continue;
                    ServerLevel level = server.getLevel(node.gPos().dimension());
                    EndpointConfig config = level == null ? null
                        : LinkManager.get(level).getEndpointConfig(EndpointAddress.of(node));
                    if (config == null) continue;
                    if (!NodeInteractionValidator.canMutateRemote(
                        actor, level, node.gPos().pos())
                        || !config.faceConfig.containsGroup(key.groupKey())
                        || !config.canPlayerModify(actor)) return null;
                    checked.add(node);
                }
            }
            resolved.add(new ResolvedConnection(key, routes));
        }
        return resolved;
    }

    private static List<ResolvedConnection> deletionConnections(
        Collection<ConnectionKey> keys,
        Map<ConnectionKey, List<ContainerConnections.Route>> references
    ) {
        return keys.stream()
            .map(key -> new ResolvedConnection(
                key, references.getOrDefault(key, List.of())))
            .filter(connection -> !connection.routes().isEmpty())
            .toList();
    }

    /// 目录删除由服务端枚举全部成员，不受界面当前页、折叠状态或多选数量影响。
    public boolean deleteDirectory(ServerPlayer actor, String directoryId) {
        if (actor == null) return false;
        var pending = LogisticsOperationStore.get(server)
            .getDirectoryDeletion(actor.getUUID());
        if (BulkWorkScheduler.isPending(actor, DIRECTORY_DELETION_TASK)) {
            return pending != null && pending.directoryId().equals(directoryId);
        }
        if (pending != null && !pending.directoryId().equals(directoryId)) return false;
        return startDirectoryDeletion(actor, directoryId);
    }

    public boolean resumePendingDirectoryDeletion(ServerPlayer actor) {
        if (actor == null) return false;
        var pending = LogisticsOperationStore.get(server)
            .getDirectoryDeletion(actor.getUUID());
        if (pending == null) return false;
        return pending.groups().isEmpty()
            ? startDirectoryDeletion(actor, pending.directoryId())
            : resumePendingDirectoryDeletion(actor.getUUID());
    }

    public boolean resumePendingDirectoryDeletion(UUID owner) {
        var pending = LogisticsOperationStore.get(server).getDirectoryDeletion(owner);
        return pending != null && scheduleDirectoryDeletion(owner, pending.directoryId());
    }

    public boolean resumePendingGroupDeletion(GroupKey groupKey) {
        if (groupKey == null || !LogisticsOperationStore.get(server)
            .isGroupDeletionPending(groupKey)) return false;
        GroupDeletionTask taskKey = new GroupDeletionTask(groupKey);
        if (BulkWorkScheduler.tryReplaceRecoveringOffline(
            server, groupKey.ownerId(), taskKey, Set.of(groupKey), (player, deadline) -> {
                GlobalLogisticsManager global = GlobalLogisticsManager.get(server);
                List<ConnectionKey> connections = global.getConnections(groupKey).stream()
                    .limit(64).toList();
                if (!connections.isEmpty()) {
                    var references = ContainerConnections.referencesBatch(server, connections);
                    List<ResolvedConnection> resolved = deletionConnections(connections, references);
                    if (!deleteResolved(groupKey.ownerId(), connections, resolved, false)) {
                        throw new IllegalStateException("Group connection deletion failed");
                    }
                    return false;
                }

                List<LogisticsNode> nodes = global.getNodesInGroup(groupKey).keySet().stream()
                    .limit(64).toList();
                if (!nodes.isEmpty()) {
                    removeTopologyAndOrphans(groupKey.ownerId(), nodes, transaction -> {
                        for (LogisticsNode node : nodes) {
                            ServerLevel level = server.getLevel(node.gPos().dimension());
                            if (level == null) {
                                throw new IllegalStateException(
                                    "Group node dimension is unavailable: "
                                        + node.gPos().dimension().location());
                            }
                            LinkManager.get(level).removeNodeFromGroupWithoutCleanup(groupKey, node);
                        }
                    });
                    return false;
                }
                if (!global.completePendingGroupDeletion(groupKey)) {
                    throw new IllegalStateException("Group metadata deletion failed");
                }
                if (player != null) PlayerEvents.requestClientStateRefresh(player);
                return true;
            })) return true;
        return BulkWorkScheduler.isPending(server, groupKey.ownerId(), taskKey);
    }

    private boolean startDirectoryDeletion(ServerPlayer actor, String directoryId) {
        if (actor == null
            || !NodeInteractionValidator.holdsConfigurator(actor)) return false;
        var store = PlayerGroupStore.get(server);
        var operations = LogisticsOperationStore.get(server);
        UUID owner = actor.getUUID();
        var hierarchy = store.getHierarchy(owner).copy();
        var entries = hierarchy.getCompound("entries");
        var saved = operations.getDirectoryDeletion(owner);
        if (!entries.contains(directoryId)) return false;
        boolean hadPendingDeletion = saved != null && saved.directoryId().equals(directoryId);
        DirectoryContents contents = connectionsInDirectory(actor, hierarchy, directoryId);
        if (contents.hasReadOnlyConnections()) return false;
        List<ConnectionKey> keys = contents.connections();
        if (!tryAcquireMutation(actor)) return false;
        if (keys.isEmpty()) {
            if (!GroupHierarchy.edit(hierarchy, "delete", directoryId, "", "")) return false;
            store.setHierarchy(owner, hierarchy);
            operations.completeDirectoryDeletion(owner, directoryId);
            PlayerEvents.requestClientStateRefresh(actor);
            return true;
        }
        var currentRoutes = ContainerConnections.referencesBatch(server, keys);
        List<ConnectionKey> validatedExisting = keys.stream()
            .filter(key -> !currentRoutes.getOrDefault(key, List.of()).isEmpty()).toList();
        if (!hadPendingDeletion && !validatedExisting.isEmpty()
            && resolveDeletionConnections(actor, validatedExisting,
            currentRoutes, false) == null) return false;
        if (!operations.beginDirectoryDeletion(owner, directoryId,
            contents.groups())) return false;
        if (!scheduleDirectoryDeletion(owner, directoryId)) {
            if (!hadPendingDeletion) {
                operations.completeDirectoryDeletion(owner, directoryId);
            }
            return false;
        }
        return true;
    }

    private boolean scheduleDirectoryDeletion(UUID owner, String directoryId) {
        var operations = LogisticsOperationStore.get(server);
        var pending = operations.getDirectoryDeletion(owner);
        if (pending == null || !pending.directoryId().equals(directoryId)
            || pending.groups().isEmpty()) return false;
        Set<GroupKey> scope = pending.groups();
        if (BulkWorkScheduler.tryReplaceRecoveringOffline(server, owner,
            DIRECTORY_DELETION_TASK, Set.of(), (player, deadline) -> {
                var store = PlayerGroupStore.get(server);
                CompoundTag hierarchy = store.getHierarchy(owner).copy();
                if (!hierarchy.getCompound("entries").contains(directoryId)) {
                    operations.completeDirectoryDeletion(owner, directoryId);
                    return true;
                }
                List<ConnectionKey> remaining = connectionsInDirectory(
                    hierarchy, directoryId, scope, 64);
                if (remaining.isEmpty()) {
                    if (!GroupHierarchy.edit(hierarchy, "delete", directoryId, "", "")) {
                        throw new IllegalStateException("Directory metadata deletion failed");
                    }
                    store.setHierarchy(owner, hierarchy);
                    operations.completeDirectoryDeletion(owner, directoryId);
                    if (player != null) PlayerEvents.requestHierarchyRefresh(player);
                    return true;
                }
                var references = ContainerConnections.referencesBatch(server, remaining);
                List<ResolvedConnection> resolved = deletionConnections(remaining, references);
                if (!deleteResolved(owner, remaining, resolved, false)) {
                    throw new IllegalStateException("Directory connection deletion failed");
                }
                return false;
            })) return true;
        return BulkWorkScheduler.isPending(server, owner, DIRECTORY_DELETION_TASK);
    }

    private DirectoryContents connectionsInDirectory(ServerPlayer actor,
                                                     CompoundTag hierarchy,
                                                     String directoryId) {
        var entries = hierarchy.getCompound("entries");
        Set<String> directories = new HashSet<>();
        directories.add(directoryId);
        boolean expanded;
        do {
            expanded = false;
            for (String id : entries.getAllKeys()) {
                if (directories.contains(entries.getCompound(id).getString("parent"))) {
                    expanded |= directories.add(id);
                }
            }
        } while (expanded);
        Set<ConnectionKey> keys = new LinkedHashSet<>();
        Set<GroupKey> groups = new LinkedHashSet<>();
        boolean readOnly = false;
        var global = GlobalLogisticsManager.get(server);
        PermissionService permissions = PermissionService.getInstance();
        for (UUID groupOwner : permissions.accessibleDirectoryOwners(actor)) {
            for (var group : PlayerGroupStore.get(server).getGroupRefs(groupOwner)) {
                if (directories.contains(GroupHierarchy.groupFolder(
                    hierarchy, group.key()))) groups.add(group.key());
            }
        }
        for (GroupKey group : global.getActiveGroupKeys()) {
            if (!permissions.canAccess(group.ownerId(), actor)) continue;
            for (ConnectionKey key : global.getConnections(group)) {
                if (!directories.contains(GroupHierarchy.connectionFolder(hierarchy, key))) continue;
                keys.add(key);
                groups.add(group);
                if (!permissions.canModify(group.ownerId(), actor)) readOnly = true;
            }
        }
        return new DirectoryContents(List.copyOf(keys), Set.copyOf(groups), readOnly);
    }

    private List<ConnectionKey> connectionsInDirectory(CompoundTag hierarchy,
                                                       String directoryId,
                                                       Set<GroupKey> scope,
                                                       int limit) {
        var entries = hierarchy.getCompound("entries");
        Set<String> directories = new HashSet<>();
        directories.add(directoryId);
        boolean expanded;
        do {
            expanded = false;
            for (String id : entries.getAllKeys()) {
                if (directories.contains(entries.getCompound(id).getString("parent"))) {
                    expanded |= directories.add(id);
                }
            }
        } while (expanded);
        Set<ConnectionKey> keys = new LinkedHashSet<>();
        var global = GlobalLogisticsManager.get(server);
        for (GroupKey group : scope) {
            for (ConnectionKey key : global.getConnections(group)) {
                if (directories.contains(GroupHierarchy.connectionFolder(hierarchy, key))) {
                    keys.add(key);
                    if (keys.size() >= limit) return List.copyOf(keys);
                }
            }
        }
        return List.copyOf(keys);
    }

    public boolean deleteBatch(ServerPlayer actor, List<ConnectionKey> keys) {
        if (actor == null || BulkWorkScheduler.isPending(actor, DIRECTORY_DELETION_TASK)) return false;
        if (hasLockedConnection(keys)) return false;
        if (keys == null || keys.isEmpty() || keys.size() > GroupConstraints.MAX_HIERARCHY_LINKS
            || new HashSet<>(keys).size() != keys.size() || !tryAcquireMutation(actor)) return false;
        var connections = resolveDeletionConnections(actor, keys, true);
        if (connections == null) return false;
        if (keys.size() > 64) return scheduleConnectionDeletion(actor, List.copyOf(keys));
        return deleteResolved(actor.getUUID(), keys, connections, true);
    }

    private boolean scheduleConnectionDeletion(ServerPlayer actor, List<ConnectionKey> pending) {
        int[] cursor = {0};
        return BulkWorkScheduler.tryReplace(actor, CONNECTION_DELETION_TASK, Set.copyOf(pending),
            (player, deadline) -> {
                while (cursor[0] < pending.size() && System.nanoTime() - deadline < 0) {
                    int end = Math.min(cursor[0] + 64, pending.size());
                    List<ConnectionKey> batch = pending.subList(cursor[0], end);
                    var references = ContainerConnections.referencesBatch(server, batch);
                    List<ResolvedConnection> resolved = deletionConnections(batch, references);
                    if (!deleteResolved(player.getUUID(), batch, resolved, false)) {
                        throw new IllegalStateException("Connection batch deletion failed");
                    }
                    cursor[0] = end;
                }
                if (cursor[0] < pending.size()) return false;
                refreshAuthorizedClients(pending.stream().map(key -> key.groupKey().ownerId())
                    .collect(Collectors.toSet()));
                return true;
            });
    }

    private boolean deleteResolved(UUID actorId, List<ConnectionKey> keys,
                                   List<ResolvedConnection> connections,
                                   boolean refreshClients) {
        try {
            var candidates = connections.stream().flatMap(connection -> connection.routes().stream())
                .flatMap(route -> Stream.of(route.first(), route.second()))
                .filter(node -> {
                    ServerLevel level = server.getLevel(node.gPos().dimension());
                    return level != null && LinkManager.get(level)
                        .getEndpointConfig(EndpointAddress.of(node)) != null;
                }).distinct().toList();
            removeTopologyAndOrphans(actorId, candidates, transaction -> {
                var store = PlayerGroupStore.get(server);
                /// 名称和所有玩家的目录归属随拓扑一起提交或恢复。
                var scope = keys.stream().collect(Collectors.toMap(key -> key, key -> key));
                var snapshot = store.captureConnectionMetadata(scope, Map.of());
                transaction.onRollback(() -> store.restoreConnectionMetadata(snapshot));
                store.removeConnectionMetadata(keys);
                for (var connection : connections) {
                    for (var route : connection.routes())
                        LinkManager.get(server.getLevel(route.first().gPos().dimension()))
                            .removeLinkWithoutCleanup(connection.key().groupKey(), route.first(), route.second());
                }
                if (!ContainerConnections.referencesBatch(server, keys).isEmpty())
                    throw new IllegalStateException("Connection remained after removal");
            });
            if (refreshClients) {
                refreshAuthorizedClients(keys.stream().map(key -> key.groupKey().ownerId())
                    .collect(Collectors.toSet()));
            }
            return true;
        } catch (RuntimeException exception) {
            LOGGER.error("Failed to delete logistics connections", exception);
            return false;
        }
    }

    public boolean delete(ServerPlayer actor, ConnectionKey requestedKey) {
        return deleteBatch(actor, List.of(requestedKey));
    }

    /// 从指定分组移除一个端点及其在该分组中的全部连接。
    /// 工具移除模式与界面删除共用同一事务：先修改互惠图，再统一识别真正失去全部连接的面；
    /// 面过滤器优先返还玩家背包，且只有容器的全部关联面都被移除时才返还共享升级。
    public boolean deleteNodeFromGroup(ServerPlayer actor, GroupKey groupKey, LogisticsNode node) {
        if (actor == null || BulkWorkScheduler.isPending(actor, DIRECTORY_DELETION_TASK)) return false;
        ResolvedGroupNode resolved = resolveGroupNode(actor, groupKey, node);
        if (resolved != null && resolved.config().getLinkedNodes(groupKey).stream()
            .map(target -> new ConnectionKey(groupKey, node, target))
            .anyMatch(key -> BulkWorkScheduler.isLocked(server, key))) return false;
        if (resolved == null || !tryAcquireMutation(actor)) return false;
        try {
            LinkedHashSet<LogisticsNode> candidates = new LinkedHashSet<>();
            candidates.add(node);
            candidates.addAll(resolved.config().getLinkedNodes(groupKey));
            removeTopologyAndOrphans(actor.getUUID(), candidates,
                transaction -> resolved.manager().removeNodeFromGroupWithoutCleanup(groupKey, node));
            refreshAuthorizedClients(Set.of(groupKey.ownerId()));
            return true;
        } catch (RuntimeException exception) {
            LOGGER.error("Failed to remove logistics node from group {}", groupKey.internalId(), exception);
            return false;
        }
    }

    /// 验证连接仍然存在，并且玩家当前有权通过配置器查看它。
    public boolean isSelectable(ServerPlayer actor, ConnectionKey requestedKey) {
        return resolve(actor, requestedKey) != null;
    }

    private boolean hasLockedConnection(Collection<ConnectionKey> keys) {
        return keys != null && keys.stream().anyMatch(key -> BulkWorkScheduler.isLocked(server, key));
    }

    private void removeTopologyAndOrphans(UUID actorId,
                                          Collection<LogisticsNode> candidates,
                                          Consumer<NodeMutationTransaction> topologyMutation) {
        try (NodeMutationTransaction transaction = NodeMutationTransaction.begin(server)) {
            Map<LinkManager, List<LogisticsNode>> candidatesByManager = new LinkedHashMap<>();
            for (LogisticsNode candidate : new LinkedHashSet<>(candidates)) {
                ServerLevel level = server.getLevel(candidate.gPos().dimension());
                if (level == null) {
                    throw new IllegalStateException(
                        "Logistics node dimension is unavailable: "
                            + candidate.gPos().dimension().location());
                }
                transaction.capture(candidate);
                transaction.captureContainer(level, candidate.gPos().pos());
                candidatesByManager.computeIfAbsent(LinkManager.get(level), ignored -> new ArrayList<>())
                    .add(candidate);
            }

            topologyMutation.accept(transaction);

            List<PreparedRemoval> removals = new ArrayList<>();
            List<NodeLifecycleService.UpgradeSource> sources = new ArrayList<>();
            candidatesByManager.forEach((manager, nodes) -> {
                NodeLifecycleService.DisconnectedRemoval removal =
                    manager.prepareDisconnectedRemoval(nodes);
                if (!removal.faces().isEmpty()) {
                    removals.add(new PreparedRemoval(manager, removal));
                    sources.addAll(removal.sources());
                }
            });

            PlayerUpgradeHandoff handoff = new PlayerUpgradeHandoff(server, actorId);
            try (NodeLifecycleService.HandoffReceipt receipt = handoff.begin(sources)) {
                removals.forEach(removal ->
                    removal.manager().applyDisconnectedRemoval(removal.removal()));
                transaction.commit();
                receipt.commit();
            }
        }
    }

    @Nullable
    private ResolvedGroupNode resolveGroupNode(ServerPlayer actor, GroupKey groupKey,
                                               LogisticsNode node) {
        if (actor == null || groupKey == null || node == null
            || !NodeInteractionValidator.holdsConfigurator(actor)
            || !NodeInteractionValidator.isDirectInteractionTargetValid(
            actor, node.gPos().pos(), node.face())
            || !PermissionService.getInstance().canModify(groupKey.ownerId(), actor)
            || PlayerGroupStore.get(server).findGroup(groupKey) == null) return null;
        ServerLevel level = server.getLevel(node.gPos().dimension());
        if (level == null) return null;
        LinkManager manager = LinkManager.get(level);
        EndpointConfig config = manager.getEndpointConfig(EndpointAddress.of(node));
        if (config == null || !config.canPlayerModify(actor)
            || !config.faceConfig.getGroupKeys().contains(groupKey)) return null;
        return new ResolvedGroupNode(manager, config);
    }

    @Nullable
    private ResolvedConnection resolve(ServerPlayer actor, ConnectionKey requestedKey) {
        if (!canResolve(actor, requestedKey)) return null;
        return resolve(actor, requestedKey, ContainerConnections.routes(server, requestedKey), new LinkedHashMap<>());
    }

    @Nullable
    private ResolvedConnection resolve(ServerPlayer actor, ConnectionKey requestedKey,
                                       List<ContainerConnections.Route> routes,
                                       Map<GroupKey, Set<LogisticsNode>> validated) {
        return resolve(actor, requestedKey, routes, validated, true);
    }

    @Nullable
    private ResolvedConnection resolve(ServerPlayer actor, ConnectionKey requestedKey,
                                       List<ContainerConnections.Route> routes,
                                       Map<GroupKey, Set<LogisticsNode>> validated,
                                       boolean requireConfigurator) {
        if (!canResolve(actor, requestedKey, requireConfigurator)) return null;
        if (routes.isEmpty()) return null;
        Set<LogisticsNode> checked = validated.computeIfAbsent(requestedKey.groupKey(), ignored -> new HashSet<>());
        for (var route : routes) {
            for (LogisticsNode node : List.of(route.first(), route.second())) {
                if (checked.contains(node)) continue;
                ServerLevel level = server.getLevel(node.gPos().dimension());
                if (level == null || !NodeInteractionValidator.canMutateRemote(actor, level, node.gPos().pos()))
                    return null;
                EndpointConfig config = LinkManager.get(level).getEndpointConfig(EndpointAddress.of(node));
                if (config == null || !config.faceConfig.containsGroup(requestedKey.groupKey())
                    || !config.canPlayerModify(actor)) return null;
                checked.add(node);
            }
        }
        return new ResolvedConnection(requestedKey, routes);
    }

    private boolean canResolve(ServerPlayer actor, ConnectionKey key) {
        return canResolve(actor, key, true);
    }

    private boolean canResolve(ServerPlayer actor, ConnectionKey key, boolean requireConfigurator) {
        return actor != null && key != null
            && (!requireConfigurator || NodeInteractionValidator.holdsConfigurator(actor))
            && PermissionService.getInstance().canModify(key.groupKey().ownerId(), actor)
            && !LogisticsOperationStore.get(server).isGroupDeletionPending(key.groupKey())
            && PlayerGroupStore.get(server).findGroup(key.groupKey()) != null;
    }

    /// 重命名和删除是低频全服命令，限制连续恶意数据包。
    private boolean tryAcquireMutation(ServerPlayer actor) {
        long now = server.overworld().getGameTime();
        RateKey key = new RateKey(server, actor.getUUID());
        Long previous = LAST_MUTATION_TICK.get(key);
        if (previous != null && now - previous < MUTATION_COOLDOWN_TICKS) return false;
        LAST_MUTATION_TICK.put(key, now);
        if (LAST_MUTATION_TICK.size() > server.getMaxPlayers() * 8) {
            LAST_MUTATION_TICK.entrySet().removeIf(entry ->
                entry.getKey().server() == server && now - entry.getValue() > 200L);
        }
        return true;
    }

    private void refreshAuthorizedClients(Set<UUID> owners) {
        PermissionService permissions = PermissionService.getInstance();
        server.getPlayerList().getPlayers().stream()
            .filter(player -> owners.stream().anyMatch(owner -> permissions.canAccess(owner, player)))
            .forEach(PlayerEvents::requestClientStateRefresh);
    }

    private record ResolvedConnection(ConnectionKey key, List<ContainerConnections.Route> routes) {
    }

    private record GroupDeletionTask(GroupKey groupKey) {
    }

    private record DirectoryContents(List<ConnectionKey> connections,
                                     Set<GroupKey> groups,
                                     boolean hasReadOnlyConnections) {
    }

    private record PreparedRemoval(
        LinkManager manager,
        NodeLifecycleService.DisconnectedRemoval removal
    ) {
    }

    private record ResolvedGroupNode(LinkManager manager, EndpointConfig config) {
    }

    private record RateKey(MinecraftServer server, UUID playerId) {
    }

}
