package com.coobird.staticlogistics.logistics.node;

import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.logistics.group.GlobalLogisticsManager;
import com.coobird.staticlogistics.api.group.GroupKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.util.LinkedHashSet;
import java.util.Set;

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
        mutatePair(first, second, () -> graph.removeEdge(first, second));
    }

    void removeEdge(GroupKey groupKey, LogisticsNode first, LogisticsNode second) {
        mutatePair(first, second, () -> graph.removeEdge(groupKey, first, second));
    }

    void removeNodeFromGroup(GroupKey groupKey, LogisticsNode node) {
        FaceConfigComposite config = findConfig(node);
        if (config == null) return;
        Set<LogisticsNode> affected = new LinkedHashSet<>(config.getLinkedNodes(groupKey));
        try (NodeMutationTransaction transaction = NodeMutationTransaction.begin(server)) {
            transaction.capture(node);
            captureAvailableStates(transaction, affected);
            graph.removeNodeFromGroup(groupKey, node);
            transaction.commit();
        }
    }

    void cascadeRemove(LogisticsNode node, FaceConfigComposite config) {
        if (node == null || config == null) return;
        Set<LogisticsNode> counterparts = new LinkedHashSet<>(config.getLinkedNodes());
        counterparts.addAll(globalManager.getSourcesLinkedTo(node));
        try (NodeMutationTransaction transaction = NodeMutationTransaction.begin(server)) {
            transaction.capture(node);
            captureAvailableStates(transaction, counterparts);
            graph.cascadeRemove(node, counterparts);
            globalManager.cleanupOrphanedGroupIds(config.faceConfig.getOwner());
            transaction.commit();
        }
    }

    void repairReciprocalEdges(LogisticsNode node, FaceConfigComposite config) {
        if (node == null || config == null) return;
        Set<LogisticsNode> affected = new LinkedHashSet<>(config.getLinkedNodes());
        try (NodeMutationTransaction transaction = NodeMutationTransaction.begin(server)) {
            transaction.capture(node);
            captureAvailableStates(transaction, affected);
            graph.repairReciprocalEdges(node);
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

    /** 损坏拓扑中的远端可能已消失；只捕获当前可解析维度的“存在或缺失”状态。 */
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
            return config.faceConfig.getGroupKeys().contains(group);
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
}
