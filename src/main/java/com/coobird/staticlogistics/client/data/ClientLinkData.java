package com.coobird.staticlogistics.client.data;

import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.api.group.GroupKey;
import com.coobird.staticlogistics.api.group.GroupRef;
import com.coobird.staticlogistics.logistics.node.ConnectionKey;
import com.coobird.staticlogistics.logistics.node.EndpointAddress;
import com.coobird.staticlogistics.logistics.node.EndpointTopology;
import com.coobird.staticlogistics.logistics.node.ScopedTopologyLink;
import com.coobird.staticlogistics.network.c2s.C2SRequestAccessSnapshotPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.core.GlobalPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;
import org.mesdag.portlib.network.PortPacketDistributor;

import java.util.*;
import java.util.stream.Collectors;

/// 客户端物流投影。
/// 长期状态只保存分组目录和轻量拓扑；完整面配置仅在配置界面自己的菜单数据中存在。
/// 权威快照必须收齐全部页面后才会原子替换当前投影。
@OnlyIn(Dist.CLIENT)
public enum ClientLinkData {
    INSTANCE;

    private static final int MAX_ASSEMBLY_PAGES = 16_384;
    private static final int MAX_ASSEMBLY_ENTRIES = 1_000_000;
    private static final long ASSEMBLY_TIMEOUT_NANOS = 30_000_000_000L;

    private record ConnectionIndex(List<ClientConnection> routes, List<ClientConnection> connections,
                                   Map<ConnectionKey, ClientConnection> byKey) {
    }

    private final Map<GroupKey, ConnectionIndex> connectionIndexes = new HashMap<>();

    private int dataVersion;
    private ProjectionState projection = new ProjectionState();
    private long committedSequence = Long.MIN_VALUE;
    private TopologyAssembly stagingSnapshot;
    private TopologyAssembly stagingUpdate;

    private record FaceKey(ResourceKey<Level> dimension, EndpointAddress address) {
        static FaceKey of(LogisticsNode node) {
            return new FaceKey(node.gPos().dimension(), EndpointAddress.of(node));
        }

        LogisticsNode toNode() {
            return address.toNode(dimension);
        }
    }

    private static final class ProjectionState {
        final Map<ResourceKey<Level>, Map<EndpointAddress, EndpointTopology>> topologyByDimension = new HashMap<>();
        final Map<GroupKey, GroupRef> knownGroupRefs = new HashMap<>();
        final Map<UUID, String> knownOwnerNames = new HashMap<>();
        final Map<UUID, Set<GroupRef>> groupDirectoryByOwner = new HashMap<>();
        final Map<GroupKey, Set<FaceKey>> groupFaces = new HashMap<>();
        final Map<FaceKey, Set<GroupKey>> faceGroups = new HashMap<>();
        final Map<GroupKey, Map<LogisticsNode, Set<LogisticsNode>>> scopedLinks = new HashMap<>();
        final Map<ConnectionKey, String> connectionNames = new HashMap<>();
        final Map<ConnectionKey, Integer> connectionReferences = new HashMap<>();
        final VersionGate<FaceKey> versionGate = new VersionGate<>();
    }

    private static final class TopologyAssembly {
        final long sequence;
        final int pageCount;
        final long startedNanos = System.nanoTime();
        final BitSet receivedPages;
        final Map<FaceKey, EndpointTopology> faces = new LinkedHashMap<>();
        final Set<ScopedTopologyLink> links = new LinkedHashSet<>();
        final Map<GroupKey, GroupRef> groups = new LinkedHashMap<>();

        TopologyAssembly(long sequence, int pageCount) {
            this.sequence = sequence;
            this.pageCount = pageCount;
            this.receivedPages = new BitSet(pageCount);
        }

        boolean expired() {
            return System.nanoTime() - startedNanos > ASSEMBLY_TIMEOUT_NANOS;
        }
    }

    public int getDataVersion() {
        return dataVersion;
    }

    public synchronized void discardExpiredAssemblies() {
        boolean expired = false;
        if (stagingSnapshot != null && stagingSnapshot.expired()) {
            stagingSnapshot = null;
            expired = true;
        }
        if (stagingUpdate != null && stagingUpdate.expired()) {
            stagingUpdate = null;
            expired = true;
        }
        if (expired && Minecraft.getInstance().getConnection() != null) {
            PortPacketDistributor.sendToServer(new C2SRequestAccessSnapshotPayload());
        }
    }

    private void incrementDataVersion() {
        connectionIndexes.clear();
        dataVersion++;
    }

    private void incrementDataVersion(Set<GroupKey> groups) {
        groups.forEach(connectionIndexes::remove);
        dataVersion++;
    }

    private Map<EndpointAddress, EndpointTopology> getOrCreateTopologyMap(
        ProjectionState state,
        ResourceKey<Level> dimension
    ) {
        return state.topologyByDimension.computeIfAbsent(dimension, ignored -> new HashMap<>());
    }

    public void removeFaceTopology(LogisticsNode node, long version) {
        ProjectionState state = projection;
        GlobalPos pos = node.gPos();
        FaceKey key = FaceKey.of(node);
        if (!state.versionGate.acceptRemoval(key, version)) return;

        Set<GroupKey> affectedGroups = new LinkedHashSet<>(state.faceGroups.getOrDefault(key, Set.of()));
        Map<EndpointAddress, EndpointTopology> dimension = state.topologyByDimension.get(pos.dimension());
        if (dimension != null) {
            dimension.remove(key.address());
            if (dimension.isEmpty()) state.topologyByDimension.remove(pos.dimension(), dimension);
        }
        unindexFace(state, key);
        affectedGroups.addAll(removeIncidentLinks(state, node));
        if (!affectedGroups.isEmpty()) incrementDataVersion(affectedGroups);
    }

    public void invalidate() {
        synchronized (this) {
            committedSequence = Long.MIN_VALUE;
            stagingSnapshot = null;
            stagingUpdate = null;
            projection = new ProjectionState();
        }
        incrementDataVersion();
    }

    /// 收齐全部页面后一次性替换当前投影；缺页和旧页永远不会暴露给界面。
    public synchronized void acceptAuthoritativeSnapshotPage(
        long sequence,
        int pageIndex,
        int pageCount,
        List<EndpointTopology> pageFaces,
        List<ScopedTopologyLink> pageLinks,
        List<GroupRef> pageGroups
    ) {
        if (sequence <= committedSequence) return;
        if (stagingUpdate != null) {
            if (sequence < stagingUpdate.sequence) return;
            if (sequence == stagingUpdate.sequence) {
                throw new IllegalStateException("Topology sequence reused across stream types");
            }
            stagingUpdate = null;
        }
        if (pageCount < 1 || pageCount > MAX_ASSEMBLY_PAGES
            || pageIndex < 0 || pageIndex >= pageCount) {
            throw new IllegalArgumentException("Invalid authoritative snapshot page");
        }
        if (stagingSnapshot != null && stagingSnapshot.expired()) stagingSnapshot = null;
        if (stagingSnapshot == null || sequence > stagingSnapshot.sequence) {
            stagingSnapshot = new TopologyAssembly(sequence, pageCount);
        } else if (sequence < stagingSnapshot.sequence) {
            return;
        } else if (stagingSnapshot.pageCount != pageCount) {
            throw new IllegalStateException("Authoritative snapshot page count changed");
        }
        if (stagingSnapshot.receivedPages.get(pageIndex)) return;
        long assembledEntries = (long) stagingSnapshot.faces.size()
            + stagingSnapshot.links.size() + stagingSnapshot.groups.size()
            + pageFaces.size() + pageLinks.size() + pageGroups.size();
        if (assembledEntries > MAX_ASSEMBLY_ENTRIES) {
            stagingSnapshot = null;
            throw new IllegalStateException("Authoritative snapshot assembly is too large");
        }

        for (EndpointTopology face : pageFaces) {
            FaceKey key = FaceKey.of(face.node());
            if (stagingSnapshot.faces.putIfAbsent(key, face) != null) {
                throw new IllegalStateException("Duplicate face in authoritative snapshot");
            }
        }
        for (ScopedTopologyLink link : pageLinks) {
            if (!stagingSnapshot.links.add(link)) {
                throw new IllegalStateException("Duplicate link in authoritative snapshot");
            }
        }
        for (GroupRef group : pageGroups) {
            GroupRef previous = stagingSnapshot.groups.putIfAbsent(group.key(), group);
            if (previous != null) {
                throw new IllegalStateException(previous.equals(group)
                    ? "Duplicate group in authoritative snapshot"
                    : "Conflicting group in authoritative snapshot");
            }
        }

        stagingSnapshot.receivedPages.set(pageIndex);
        if (stagingSnapshot.receivedPages.cardinality() == stagingSnapshot.pageCount) {
            commitAuthoritativeSnapshot(stagingSnapshot);
        }
    }

    private void commitAuthoritativeSnapshot(TopologyAssembly snapshot) {
        ProjectionState next = new ProjectionState();
        for (var entry : snapshot.faces.entrySet()) {
            FaceKey key = entry.getKey();
            EndpointTopology topology = entry.getValue();
            getOrCreateTopologyMap(next, key.dimension()).put(key.address(), topology);
            next.versionGate.seed(key, topology.version());
            indexFace(next, key, topology);
            rememberTopologyOwner(next, topology);
        }
        snapshot.links.forEach(link -> putScopedLink(next, link));
        for (GroupRef group : snapshot.groups.values()) {
            next.knownGroupRefs.put(group.key(), group);
            next.groupDirectoryByOwner.computeIfAbsent(
                group.key().ownerId(), ignored -> new LinkedHashSet<>()).add(group);
        }
        projection = next;
        committedSequence = snapshot.sequence;
        stagingSnapshot = null;
        stagingUpdate = null;
        incrementDataVersion();
    }

    /// 收齐一次拓扑增量的全部页面后，按面版本原子替换涉及的节点和出边。
    public synchronized void acceptTopologyUpdatePage(
        long sequence,
        int pageIndex,
        int pageCount,
        List<EndpointTopology> pageFaces,
        List<ScopedTopologyLink> pageLinks
    ) {
        if (sequence <= committedSequence) return;
        if (stagingSnapshot != null) {
            if (sequence < stagingSnapshot.sequence) return;
            if (sequence == stagingSnapshot.sequence) {
                throw new IllegalStateException("Topology sequence reused across stream types");
            }
            stagingSnapshot = null;
        }
        if (pageCount < 1 || pageCount > MAX_ASSEMBLY_PAGES
            || pageIndex < 0 || pageIndex >= pageCount) {
            throw new IllegalArgumentException("Invalid topology update page");
        }
        if (stagingUpdate != null && stagingUpdate.expired()) stagingUpdate = null;
        if (stagingUpdate == null || sequence > stagingUpdate.sequence) {
            stagingUpdate = new TopologyAssembly(sequence, pageCount);
        } else if (sequence < stagingUpdate.sequence) {
            return;
        } else if (stagingUpdate.pageCount != pageCount) {
            throw new IllegalStateException("Topology update page count changed");
        }
        if (stagingUpdate.receivedPages.get(pageIndex)) return;
        long assembledEntries = (long) stagingUpdate.faces.size() + stagingUpdate.links.size()
            + pageFaces.size() + pageLinks.size();
        if (assembledEntries > MAX_ASSEMBLY_ENTRIES) {
            stagingUpdate = null;
            throw new IllegalStateException("Topology update assembly is too large");
        }

        for (EndpointTopology face : pageFaces) {
            if (stagingUpdate.faces.putIfAbsent(FaceKey.of(face.node()), face) != null) {
                throw new IllegalStateException("Duplicate face in topology update");
            }
        }
        for (ScopedTopologyLink link : pageLinks) {
            if (!stagingUpdate.links.add(link)) {
                throw new IllegalStateException("Duplicate link in topology update");
            }
        }
        stagingUpdate.receivedPages.set(pageIndex);
        if (stagingUpdate.receivedPages.cardinality() == stagingUpdate.pageCount) {
            commitTopologyUpdate(stagingUpdate);
        }
    }

    private void commitTopologyUpdate(TopologyAssembly update) {
        ProjectionState state = projection;
        /// 增量可以重命名已有连接，但同一批次内部仍必须保持名称一致。
        Map<ConnectionKey, String> updateNames = new HashMap<>();
        for (ScopedTopologyLink link : update.links) {
            ConnectionKey key = new ConnectionKey(link.groupKey(), link.source(), link.target());
            String previous = updateNames.putIfAbsent(key, link.displayName());
            if (previous != null && !previous.equals(link.displayName()))
                throw new IllegalStateException("Conflicting connection names in topology update");
        }
        Set<LogisticsNode> acceptedSources = new LinkedHashSet<>();
        Set<GroupKey> affectedGroups = new LinkedHashSet<>();
        for (var entry : update.faces.entrySet()) {
            FaceKey key = entry.getKey();
            EndpointTopology topology = entry.getValue();
            if (!state.versionGate.acceptUpdate(key, topology.version())) continue;
            affectedGroups.addAll(state.faceGroups.getOrDefault(key, Set.of()));
            topology.groups().forEach(group -> affectedGroups.add(group.key()));
            unindexFace(state, key);
            affectedGroups.addAll(removeSourceLinks(state, topology.node()));
            getOrCreateTopologyMap(state, key.dimension()).put(key.address(), topology);
            indexFace(state, key, topology);
            rememberTopologyDirectory(state, topology);
            acceptedSources.add(topology.node());
        }
        update.links.stream()
            .filter(link -> acceptedSources.contains(link.source()))
            .forEach(link -> {
                state.connectionNames.put(new ConnectionKey(link.groupKey(), link.source(), link.target()), link.displayName());
                putScopedLink(state, link);
            });
        committedSequence = update.sequence;
        stagingUpdate = null;
        stagingSnapshot = null;
        if (!acceptedSources.isEmpty()) incrementDataVersion(affectedGroups);
    }

    /// 替换某位所有者的权威分组目录；其中也包含没有节点的空分组。
    public void replaceGroupDirectory(UUID owner, Set<GroupRef> groups) {
        ProjectionState state = projection;
        Set<GroupRef> owned = new LinkedHashSet<>(groups);
        state.groupDirectoryByOwner.put(owner, owned);

        Set<GroupKey> currentKeys = owned.stream().map(GroupRef::key).collect(Collectors.toSet());
        state.knownGroupRefs.keySet().removeIf(key -> key.ownerId().equals(owner) && !currentKeys.contains(key));
        owned.forEach(group -> state.knownGroupRefs.put(group.key(), group));
        incrementDataVersion();
    }

    /// 返回服务端已经按读取权限过滤后的权威分组集合。
    public List<GroupRef> getAccessibleGroupRefs() {
        return List.copyOf(projection.knownGroupRefs.values());
    }

    public Set<LogisticsNode> getLinkedNodes(GroupKey groupKey, LogisticsNode source) {
        Map<LogisticsNode, Set<LogisticsNode>> sources = projection.scopedLinks.get(groupKey);
        if (sources == null) return Set.of();
        Set<LogisticsNode> targets = sources.get(source);
        return targets == null ? Set.of() : Set.copyOf(targets);
    }

    /// 返回分组中的唯一连接视图。
    /// 服务端会分别同步两个端点的出边，因此先按规范化端点对去重。只有两端拓扑都在同一份
    /// 已提交快照中存在时才公开，避免界面短暂展示无法配置的半连接。
    public List<ClientConnection> getConnectionsForGroup(GroupKey groupKey) {
        return connectionIndex(groupKey).connections();
    }

    private ConnectionIndex connectionIndex(GroupKey groupKey) {
        return connectionIndexes.computeIfAbsent(groupKey, key -> {
            List<ClientConnection> routes = buildConnectionRoutes(key);
            Map<ConnectionKey, ClientConnection> unique = new LinkedHashMap<>();
            routes.stream().sorted(Comparator.comparingInt(route ->
                    (route.first().isConnectionFace() ? 1 : 0) + (route.second().isConnectionFace() ? 1 : 0)))
                .forEach(route -> unique.putIfAbsent(route.key(), route));
            return new ConnectionIndex(routes, List.copyOf(unique.values()), Map.copyOf(unique));
        });
    }


    public List<ClientConnection> getConnectionRoutes(GroupKey groupKey) {
        return connectionIndex(groupKey).routes();
    }

    private List<ClientConnection> buildConnectionRoutes(GroupKey groupKey) {
        ProjectionState state = projection;
        Map<LogisticsNode, Set<LogisticsNode>> sources = state.scopedLinks.get(groupKey);
        if (sources == null || sources.isEmpty()) return List.of();

        List<ClientConnection> connections = new ArrayList<>();
        sources.forEach((source, targets) -> targets.forEach(target -> {
            Set<LogisticsNode> reciprocal = sources.get(target);
            if (reciprocal == null || !reciprocal.contains(source)) return;
            ConnectionKey key = new ConnectionKey(groupKey, source, target);
            if (ConnectionKey.NODE_ORDER.compare(source, target) >= 0) return;
            EndpointTopology first = findTopology(state, source);
            EndpointTopology second = findTopology(state, target);
            if (first == null || second == null) return;
            connections.add(new ClientConnection(
                groupKey, source, target, first, second,
                state.connectionNames.getOrDefault(key, "")));
        }));
        return connections.stream()
            .sorted(ClientConnection.DISPLAY_ORDER)
            .toList();
    }

    /// 使用稳定身份从最新客户端拓扑中解析连接。
    /// 调用方不得长期保存 ClientConnection，因为其中的端点拓扑会随服务端同步更新。
    @Nullable
    public ClientConnection findConnection(ConnectionKey key) {
        return connectionIndex(key.groupKey()).byKey().get(key);
    }

    @Nullable
    public EndpointTopology getTopology(LogisticsNode node) {
        return findTopology(projection, node);
    }

    @Nullable
    private EndpointTopology findTopology(ProjectionState state, LogisticsNode node) {
        Map<EndpointAddress, EndpointTopology> dimension = state.topologyByDimension.get(node.gPos().dimension());
        return dimension == null ? null : dimension.get(EndpointAddress.of(node));
    }

    @Nullable
    public GroupRef findGroupRef(UUID owner, String displayName) {
        return projection.knownGroupRefs.values().stream()
            .filter(group -> group.key().ownerId().equals(owner)
                && group.displayName().equals(displayName))
            .findFirst().orElse(null);
    }

    @Nullable
    public GroupRef findGroupRef(GroupKey key) {
        return projection.knownGroupRefs.get(key);
    }

    /// 返回完整节点身份，保留维度与具体面。
    public List<LogisticsNode> getNodesForGroup(GroupKey groupKey) {
        Set<FaceKey> faces = projection.groupFaces.get(groupKey);
        if (faces == null || faces.isEmpty()) return List.of();
        return faces.stream().map(FaceKey::toNode).toList();
    }

    private void rememberTopologyDirectory(ProjectionState state, EndpointTopology topology) {
        topology.groups().forEach(group -> {
            Set<GroupRef> directory = state.groupDirectoryByOwner.get(group.key().ownerId());
            if (directory == null) {
                state.knownGroupRefs.putIfAbsent(group.key(), group);
                return;
            }
            directory.stream()
                .filter(candidate -> candidate.key().equals(group.key()))
                .findFirst()
                .ifPresent(candidate -> state.knownGroupRefs.put(candidate.key(), candidate));
        });
        rememberTopologyOwner(state, topology);
    }

    private void rememberTopologyOwner(ProjectionState state, EndpointTopology topology) {
        if (topology.ownerId() != null && !"Unknown".equals(topology.ownerName())) {
            state.knownOwnerNames.putIfAbsent(topology.ownerId(), topology.ownerName());
        }
    }

    private void indexFace(ProjectionState state, FaceKey face, EndpointTopology topology) {
        Set<GroupKey> keys = topology.groupKeys();
        if (keys.isEmpty()) return;
        state.faceGroups.put(face, keys);
        for (GroupKey key : keys) {
            state.groupFaces.computeIfAbsent(key, ignored -> new LinkedHashSet<>()).add(face);
        }
    }

    private void unindexFace(ProjectionState state, FaceKey face) {
        Set<GroupKey> keys = state.faceGroups.remove(face);
        if (keys == null) return;
        for (GroupKey key : keys) {
            Set<FaceKey> faces = state.groupFaces.get(key);
            if (faces != null && faces.remove(face) && faces.isEmpty()) {
                state.groupFaces.remove(key, faces);
                retireGroupIfUnreferenced(state, key);
            }
        }
    }

    /// 分组目录与拓扑共同持有显示身份；两侧都不再引用时才回收。
    private void retireGroupIfUnreferenced(ProjectionState state, GroupKey key) {
        if (state.groupFaces.containsKey(key)) return;
        Set<GroupRef> directory = state.groupDirectoryByOwner.get(key.ownerId());
        if (directory != null && directory.stream().anyMatch(group -> group.key().equals(key))) return;
        state.knownGroupRefs.remove(key);
    }

    private void putScopedLink(ProjectionState state, ScopedTopologyLink link) {
        boolean added = state.scopedLinks
            .computeIfAbsent(link.groupKey(), ignored -> new LinkedHashMap<>())
            .computeIfAbsent(link.source(), ignored -> new LinkedHashSet<>())
            .add(link.target());
        ConnectionKey key = new ConnectionKey(link.groupKey(), link.source(), link.target());
        if (added) state.connectionReferences.merge(key, 1, Integer::sum);
        String previous = state.connectionNames.putIfAbsent(key, link.displayName());
        if (previous != null && !previous.equals(link.displayName())) {
            throw new IllegalStateException("Conflicting connection names in topology snapshot");
        }
    }

    private Set<GroupKey> removeSourceLinks(ProjectionState state, LogisticsNode source) {
        Set<GroupKey> affected = new LinkedHashSet<>();
        state.scopedLinks.entrySet().removeIf(group -> {
            Set<LogisticsNode> removedTargets = group.getValue().remove(source);
            if (removedTargets != null) {
                affected.add(group.getKey());
                for (LogisticsNode target : removedTargets) {
                    removeLinkReference(state, group.getKey(), source, target);
                }
            }
            return group.getValue().isEmpty();
        });
        return affected;
    }

    /// 名称属于容器连接；仅在最后一条面／通道引用消失后删除。
    private void removeLinkReference(ProjectionState state, GroupKey group, LogisticsNode source, LogisticsNode target) {
        ConnectionKey key = new ConnectionKey(group, source, target);
        Integer count = state.connectionReferences.get(key);
        if (count == null || count <= 1) {
            state.connectionReferences.remove(key);
            state.connectionNames.remove(key);
        } else state.connectionReferences.put(key, count - 1);
    }

    private Set<GroupKey> removeIncidentLinks(ProjectionState state, LogisticsNode node) {
        Set<GroupKey> affected = removeSourceLinks(state, node);
        state.scopedLinks.entrySet().removeIf(group -> {
            Map<LogisticsNode, Set<LogisticsNode>> sources = group.getValue();
            sources.entrySet().removeIf(entry -> {
                Set<LogisticsNode> targets = entry.getValue();
                if (targets.remove(node)) {
                    affected.add(group.getKey());
                    removeLinkReference(state, group.getKey(), entry.getKey(), node);
                }
                return targets.isEmpty();
            });
            return sources.isEmpty();
        });
        return affected;
    }

    public String getOwnerName(UUID owner) {
        String known = projection.knownOwnerNames.get(owner);
        return known == null || known.isEmpty() ? owner.toString() : known;
    }
}
