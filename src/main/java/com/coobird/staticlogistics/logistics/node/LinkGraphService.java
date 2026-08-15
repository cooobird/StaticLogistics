package com.coobird.staticlogistics.logistics.node;

import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.api.group.GroupKey;
import com.coobird.staticlogistics.logistics.group.GlobalLogisticsManager;
import com.coobird.staticlogistics.logistics.group.PlayerGroupStore;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 分组作用域链接图的存储适配服务。
 *
 * <p>双向邻接与批次语义由纯领域图实现；本类只解析跨维度端点，并把领域动作映射到面配置聚合根。
 */
final class LinkGraphService {
    private static final LinkMutationPermit MUTATION_PERMIT = new LinkMutationPermit();

    private final MinecraftServer server;
    private final GlobalLogisticsManager globalManager;
    private final ReciprocalLinkGraph<LogisticsNode, GroupKey> graph;

    LinkGraphService(MinecraftServer server, GlobalLogisticsManager globalManager) {
        this.server = server;
        this.globalManager = globalManager;
        this.graph = new ReciprocalLinkGraph<>(this::resolveEndpoint);
    }

    void addEdge(GroupKey groupKey, LogisticsNode first, LogisticsNode second) {
        mutatePair(first, second, () -> graph.addEdge(groupKey, first, second));
    }

    void removeEdge(LogisticsNode first, LogisticsNode second) {
        Set<GroupKey> groups = groupsConnecting(first, second);
        mutatePair(first, second, () -> {
            if (graph.removeEdge(first, second)) {
                groups.forEach(group -> deferConnectionNameRemoval(group, first, second));
            }
        });
    }

    void removeEdge(GroupKey groupKey, LogisticsNode first, LogisticsNode second) {
        mutatePair(first, second, () -> {
            if (graph.removeEdge(groupKey, first, second)) {
                deferConnectionNameRemoval(groupKey, first, second);
            }
        });
    }

    void removeEdgeWithoutCleanup(GroupKey groupKey, LogisticsNode first, LogisticsNode second) {
        mutatePair(first, second, () -> {
            if (graph.removeEdgeWithoutCleanup(groupKey, first, second)) {
                deferConnectionNameRemoval(groupKey, first, second);
            }
        });
    }

    void removeNodeFromGroup(GroupKey groupKey, LogisticsNode node) {
        removeNodeFromGroup(groupKey, node, false);
    }

    void removeNodeFromGroupWithoutCleanup(GroupKey groupKey, LogisticsNode node) {
        removeNodeFromGroup(groupKey, node, true);
    }

    private void removeNodeFromGroup(GroupKey groupKey, LogisticsNode node, boolean deferCleanup) {
        FaceConfigComposite config = findConfig(node);
        if (config == null) return;
        Set<LogisticsNode> affected = new LinkedHashSet<>(config.getLinkedNodes(groupKey));
        try (NodeMutationTransaction transaction = NodeMutationTransaction.begin(server)) {
            transaction.capture(node);
            captureAvailableStates(transaction, affected);
            if (deferCleanup) graph.removeNodeFromGroupWithoutCleanup(groupKey, node);
            else graph.removeNodeFromGroup(groupKey, node);
            affected.forEach(target ->
                deferConnectionNameRemoval(groupKey, node, target));
            deferEmptyGroupRemoval(groupKey);
            transaction.commit();
        }
    }

    void cascadeRemove(LogisticsNode node, FaceConfigComposite config) {
        if (node == null || config == null) return;
        Set<LogisticsNode> counterparts = new LinkedHashSet<>(config.getLinkedNodes());
        counterparts.addAll(globalManager.getSourcesLinkedTo(node));
        Set<GroupKey> affectedGroups =
            Set.copyOf(config.faceConfig.getGroupKeys());
        try (NodeMutationTransaction transaction = NodeMutationTransaction.begin(server)) {
            transaction.capture(node);
            captureAvailableStates(transaction, counterparts);
            Map<LogisticsNode, Set<GroupKey>> removedGroups = new LinkedHashMap<>();
            counterparts.forEach(counterpart ->
                removedGroups.put(counterpart, groupsConnecting(node, counterpart)));
            graph.cascadeRemove(node, counterparts);
            removedGroups.forEach((counterpart, groups) -> groups.forEach(group ->
                deferConnectionNameRemoval(group, node, counterpart)));
            affectedGroups.forEach(this::deferEmptyGroupRemoval);
            globalManager.cleanupOrphanedGroupIds(config.faceConfig.getOwner());
            transaction.commit();
        }
    }

    /**
     * 清除指向已不存在端点的所有反向引用。
     *
     * <p>该入口不依赖被移除端点仍保有本地面配置，因此可以修复单边残留的损坏拓扑。
     */
    boolean purgeInboundReferences(LogisticsNode removedNode) {
        if (removedNode == null) return false;
        return purgeInboundReferences(List.of(removedNode));
    }

    /**
     * 以一次全局索引扫描和一个事务清除多个已消失端点的反向引用。
     */
    boolean purgeInboundReferences(Collection<LogisticsNode> removedNodes) {
        Set<LogisticsNode> removed = removedNodes == null
            ? Set.of()
            : removedNodes.stream().filter(Objects::nonNull)
            .collect(Collectors.toCollection(LinkedHashSet::new));
        if (removed.isEmpty()) return false;

        Map<LogisticsNode, Set<LogisticsNode>> sourcesByTarget =
            globalManager.getSourcesLinkedTo(removed);
        if (sourcesByTarget.isEmpty()) return false;
        Set<LogisticsNode> counterparts = sourcesByTarget.values().stream()
            .flatMap(Collection::stream)
            .collect(Collectors.toCollection(LinkedHashSet::new));

        try (NodeMutationTransaction transaction = NodeMutationTransaction.begin(server)) {
            removed.forEach(transaction::captureState);
            captureAvailableStates(transaction, counterparts);
            sourcesByTarget.forEach((target, sources) -> sources.forEach(source -> {
                Set<GroupKey> groups = groupsConnecting(target, source);
                if (graph.removeEdge(target, source)) {
                    groups.forEach(group -> deferConnectionNameRemoval(group, target, source));
                }
            }));
            transaction.commit();
        }
        return true;
    }

    void repairReciprocalEdges(LogisticsNode node, FaceConfigComposite config) {
        if (node == null || config == null) return;
        Set<LogisticsNode> affected = new LinkedHashSet<>(config.getLinkedNodes());
        Set<GroupKey> affectedGroups =
            Set.copyOf(config.faceConfig.getGroupKeys());
        try (NodeMutationTransaction transaction = NodeMutationTransaction.begin(server)) {
            transaction.capture(node);
            captureAvailableStates(transaction, affected);
            graph.repairReciprocalEdges(node);
            affectedGroups.forEach(this::deferEmptyGroupRemoval);
            transaction.commit();
        }
    }

    private void mutatePair(LogisticsNode first, LogisticsNode second, Runnable mutation) {
        if (first == null || second == null || first.equals(second)) return;
        if (findConfig(first) == null || findConfig(second) == null) return;
        try (NodeMutationTransaction transaction = NodeMutationTransaction.begin(server)) {
            transaction.capture(first);
            transaction.capture(second);
            mutation.run();
            transaction.commit();
        }
    }

    /**
     * 损坏拓扑中的远端可能已消失；只捕获当前可解析维度的“存在或缺失”状态。
     */
    private void captureAvailableStates(NodeMutationTransaction transaction,
                                        Iterable<LogisticsNode> nodes) {
        for (LogisticsNode candidate : nodes) {
            if (candidate != null && server.getLevel(candidate.gPos().dimension()) != null) {
                transaction.captureState(candidate);
            }
        }
    }

    private FaceConfigComposite findConfig(LogisticsNode node) {
        if (node == null) return null;
        ServerLevel level = server.getLevel(node.gPos().dimension());
        return level == null ? null : LinkManager.get(level).getFaceConfig(FaceAddress.of(node));
    }

    private Set<GroupKey> groupsConnecting(LogisticsNode first, LogisticsNode second) {
        Set<GroupKey> groups = new LinkedHashSet<>();
        FaceConfigComposite firstConfig = findConfig(first);
        if (firstConfig != null) {
            firstConfig.getLinkedNodesByGroup().forEach((group, targets) -> {
                if (targets.contains(second)) groups.add(group);
            });
        }
        FaceConfigComposite secondConfig = findConfig(second);
        if (secondConfig != null) {
            secondConfig.getLinkedNodesByGroup().forEach((group, targets) -> {
                if (targets.contains(first)) groups.add(group);
            });
        }
        return Set.copyOf(groups);
    }

    private void deferConnectionNameRemoval(
        GroupKey group,
        LogisticsNode first,
        LogisticsNode second
    ) {
        ConnectionKey connection = new ConnectionKey(group, first, second);
        boolean deferred = NodeMutationTransaction.defer(
            server, new ConnectionNameRemoval(connection),
            () -> PlayerGroupStore.get(server).removeConnectionName(connection));
        if (!deferred) {
            throw new IllegalStateException(
                "Connection name removal requires an active node mutation");
        }
        deferEmptyGroupRemoval(group);
    }

    /**
     * 将空分组清理合并到当前图事务的提交阶段。
     *
     * <p>判断必须发生在全部端点修改提交之后；同一分组一次事务内删除多条边时，
     * 去重键保证只扫描一次权威拓扑。
     */
    private void deferEmptyGroupRemoval(GroupKey group) {
        boolean deferred = NodeMutationTransaction.defer(
            server, new EmptyGroupRemoval(group),
            () -> globalManager.removeGroupIfEmpty(group));
        if (!deferred) {
            throw new IllegalStateException(
                "Empty group removal requires an active node mutation");
        }
    }

    private ReciprocalLinkGraph.Endpoint<LogisticsNode, GroupKey> resolveEndpoint(LogisticsNode node) {
        if (node == null) return null;
        ServerLevel level = server.getLevel(node.gPos().dimension());
        if (level == null) return null;
        LinkManager manager = LinkManager.get(level);
        FaceConfigComposite config = manager.getFaceConfig(FaceAddress.of(node));
        return config == null ? null : new FaceEndpoint(node, manager, config);
    }

    private record FaceEndpoint(LogisticsNode node, LinkManager manager, FaceConfigComposite config)
        implements ReciprocalLinkGraph.Endpoint<LogisticsNode, GroupKey> {

        @Override
        public boolean belongsTo(GroupKey group) {
            return config.faceConfig.containsGroup(group);
        }

        @Override
        public Set<GroupKey> groups() {
            return config.faceConfig.getGroupKeys();
        }

        @Override
        public Set<LogisticsNode> linked() {
            return config.getLinkedNodes();
        }

        @Override
        public Set<LogisticsNode> linked(GroupKey group) {
            return config.getLinkedNodes(group);
        }

        @Override
        public ReciprocalLinkGraph.Edit beginEdit() {
            FaceConfigComposite.BulkEdit edit = config.beginBulkEdit();
            return edit::close;
        }

        @Override
        public void add(GroupKey group, LogisticsNode counterpart) {
            config.addLinkedNode(MUTATION_PERMIT, group, counterpart);
        }

        @Override
        public void remove(GroupKey group, LogisticsNode counterpart) {
            config.removeLinkedNode(MUTATION_PERMIT, group, counterpart);
        }

        @Override
        public void remove(LogisticsNode counterpart) {
            config.removeLinkedNode(MUTATION_PERMIT, counterpart);
        }

        @Override
        public void removeGroup(GroupKey group) {
            config.removeGroup(MUTATION_PERMIT, group);
        }

        @Override
        public void disableRoles() {
            config.setGlobalOutputEnabled(false);
            config.setGlobalInputEnabled(false);
        }

        @Override
        public void cleanup() {
            if (manager.getFaceConfig(FaceAddress.of(node)) == config) {
                manager.cleanUpFaceIfNeeded(node, config);
            }
        }
    }

    private record ConnectionNameRemoval(ConnectionKey connection) {
    }

    private record EmptyGroupRemoval(GroupKey group) {
    }
}
