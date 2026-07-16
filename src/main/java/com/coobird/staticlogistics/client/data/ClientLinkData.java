package com.coobird.staticlogistics.client.data;

import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.api.group.GroupKey;
import com.coobird.staticlogistics.api.group.GroupRef;
import com.coobird.staticlogistics.logistics.node.FaceTopology;
import com.coobird.staticlogistics.logistics.node.ScopedTopologyLink;
import com.coobird.staticlogistics.logistics.node.FaceAddress;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;

import java.util.BitSet;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 客户端物流投影。
 *
 * <p>长期状态只保存分组目录和轻量拓扑；完整面配置仅在配置界面自己的菜单数据中存在。
 * 权威快照必须收齐全部页面后才会原子替换当前投影。
 */
@OnlyIn(Dist.CLIENT)
public enum ClientLinkData {
    INSTANCE;

    private static final int MAX_ASSEMBLY_PAGES = 16_384;
    private static final int MAX_ASSEMBLY_ENTRIES = 1_000_000;
    private static final long ASSEMBLY_TIMEOUT_NANOS = 30_000_000_000L;

    private final AtomicInteger dataVersion = new AtomicInteger();
    private volatile ProjectionState projection = new ProjectionState();
    private long committedSnapshotSequence = Long.MIN_VALUE;
    private SnapshotAssembly stagingSnapshot;
    private long committedUpdateSequence = Long.MIN_VALUE;
    private TopologyUpdateAssembly stagingUpdate;

    private record FaceKey(ResourceKey<Level> dimension, FaceAddress address) {
        static FaceKey of(LogisticsNode node) {
            return new FaceKey(node.gPos().dimension(), FaceAddress.of(node));
        }

        LogisticsNode toNode() {
            return address.toNode(dimension);
        }
    }

    private static final class ProjectionState {
        final Map<ResourceKey<Level>, Map<FaceAddress, FaceTopology>> topologyByDimension = new ConcurrentHashMap<>();
        final Map<GroupKey, GroupRef> knownGroupRefs = new ConcurrentHashMap<>();
        final Map<UUID, String> knownOwnerNames = new ConcurrentHashMap<>();
        final Map<UUID, Set<GroupRef>> groupDirectoryByOwner = new ConcurrentHashMap<>();
        final Map<GroupKey, Set<FaceKey>> groupFaces = new ConcurrentHashMap<>();
        final Map<FaceKey, Set<GroupKey>> faceGroups = new ConcurrentHashMap<>();
        final Map<GroupKey, Map<LogisticsNode, Set<LogisticsNode>>> scopedLinks = new ConcurrentHashMap<>();
        final VersionGate<FaceKey> versionGate = new VersionGate<>();
    }

    private static final class SnapshotAssembly {
        final long sequence;
        final int pageCount;
        final long startedNanos = System.nanoTime();
        final BitSet receivedPages;
        final Map<FaceKey, FaceTopology> faces = new LinkedHashMap<>();
        final Set<ScopedTopologyLink> links = new LinkedHashSet<>();
        final Map<GroupKey, GroupRef> groups = new LinkedHashMap<>();

        SnapshotAssembly(long sequence, int pageCount) {
            this.sequence = sequence;
            this.pageCount = pageCount;
            this.receivedPages = new BitSet(pageCount);
        }

        boolean expired() {
            return System.nanoTime() - startedNanos > ASSEMBLY_TIMEOUT_NANOS;
        }
    }

    private static final class TopologyUpdateAssembly {
        final long sequence;
        final int pageCount;
        final long startedNanos = System.nanoTime();
        final BitSet receivedPages;
        final Map<FaceKey, FaceTopology> faces = new LinkedHashMap<>();
        final Set<ScopedTopologyLink> links = new LinkedHashSet<>();

        TopologyUpdateAssembly(long sequence, int pageCount) {
            this.sequence = sequence;
            this.pageCount = pageCount;
            this.receivedPages = new BitSet(pageCount);
        }

        boolean expired() {
            return System.nanoTime() - startedNanos > ASSEMBLY_TIMEOUT_NANOS;
        }
    }

    public int getDataVersion() {
        return dataVersion.get();
    }

    private void incrementDataVersion() {
        dataVersion.incrementAndGet();
    }

    private Map<FaceAddress, FaceTopology> getOrCreateTopologyMap(
        ProjectionState state,
        ResourceKey<Level> dimension
    ) {
        return state.topologyByDimension.computeIfAbsent(dimension, ignored -> new ConcurrentHashMap<>());
    }

    public void removeFaceTopology(GlobalPos pos, Direction face, long version) {
        ProjectionState state = projection;
        LogisticsNode node = new LogisticsNode(pos, face);
        FaceKey key = FaceKey.of(node);
        if (!state.versionGate.acceptRemoval(key, version)) return;

        Map<FaceAddress, FaceTopology> dimension = state.topologyByDimension.get(pos.dimension());
        if (dimension == null || dimension.remove(key.address()) == null) return;
        if (dimension.isEmpty()) state.topologyByDimension.remove(pos.dimension(), dimension);
        unindexFace(state, key);
        removeIncidentLinks(state, node);
        incrementDataVersion();
    }

    public void invalidate() {
        synchronized (this) {
            committedSnapshotSequence = Long.MIN_VALUE;
            stagingSnapshot = null;
            committedUpdateSequence = Long.MIN_VALUE;
            stagingUpdate = null;
            projection = new ProjectionState();
        }
        incrementDataVersion();
    }

    /** 收齐全部页面后一次性替换当前投影；缺页和旧页永远不会暴露给界面。 */
    public synchronized void acceptAuthoritativeSnapshotPage(
        long sequence,
        int pageIndex,
        int pageCount,
        List<FaceTopology> pageFaces,
        List<ScopedTopologyLink> pageLinks,
        List<GroupRef> pageGroups
    ) {
        if (sequence <= Math.max(committedSnapshotSequence, committedUpdateSequence)) return;
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
            stagingSnapshot = new SnapshotAssembly(sequence, pageCount);
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

        for (FaceTopology face : pageFaces) {
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

    private void commitAuthoritativeSnapshot(SnapshotAssembly snapshot) {
        ProjectionState next = new ProjectionState();
        for (var entry : snapshot.faces.entrySet()) {
            FaceKey key = entry.getKey();
            FaceTopology topology = entry.getValue();
            getOrCreateTopologyMap(next, key.dimension()).put(key.address(), topology);
            next.versionGate.seed(key, topology.version());
            indexFace(next, key, topology);
            rememberTopologyDirectory(next, topology);
        }
        snapshot.links.forEach(link -> putScopedLink(next, link));
        for (GroupRef group : snapshot.groups.values()) {
            next.knownGroupRefs.put(group.key(), group);
            next.groupDirectoryByOwner.computeIfAbsent(
                group.key().ownerId(), ignored -> ConcurrentHashMap.newKeySet()).add(group);
        }
        projection = next;
        committedSnapshotSequence = snapshot.sequence;
        committedUpdateSequence = Math.max(committedUpdateSequence, snapshot.sequence);
        stagingSnapshot = null;
        stagingUpdate = null;
        incrementDataVersion();
    }

    /** 收齐一次拓扑增量的全部页面后，按面版本原子替换涉及的节点和出边。 */
    public synchronized void acceptTopologyUpdatePage(
        long sequence,
        int pageIndex,
        int pageCount,
        List<FaceTopology> pageFaces,
        List<ScopedTopologyLink> pageLinks
    ) {
        if (sequence <= Math.max(committedSnapshotSequence, committedUpdateSequence)) return;
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
            stagingUpdate = new TopologyUpdateAssembly(sequence, pageCount);
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

        for (FaceTopology face : pageFaces) {
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

    private void commitTopologyUpdate(TopologyUpdateAssembly update) {
        ProjectionState state = projection;
        Set<LogisticsNode> acceptedSources = new LinkedHashSet<>();
        for (var entry : update.faces.entrySet()) {
            FaceKey key = entry.getKey();
            FaceTopology topology = entry.getValue();
            if (!state.versionGate.acceptUpdate(key, topology.version())) continue;
            unindexFace(state, key);
            removeSourceLinks(state, topology.node());
            getOrCreateTopologyMap(state, key.dimension()).put(key.address(), topology);
            indexFace(state, key, topology);
            rememberTopologyDirectory(state, topology);
            acceptedSources.add(topology.node());
        }
        update.links.stream()
            .filter(link -> acceptedSources.contains(link.source()))
            .forEach(link -> putScopedLink(state, link));
        committedUpdateSequence = update.sequence;
        committedSnapshotSequence = Math.max(committedSnapshotSequence, update.sequence);
        stagingUpdate = null;
        stagingSnapshot = null;
        if (!acceptedSources.isEmpty()) incrementDataVersion();
    }

    /** 替换某位所有者的权威分组目录；其中也包含没有节点的空分组。 */
    public void replaceGroupDirectory(UUID owner, Set<GroupRef> groups) {
        ProjectionState state = projection;
        Set<GroupRef> previous = state.groupDirectoryByOwner.remove(owner);
        if (!groups.isEmpty()) {
            Set<GroupRef> owned = ConcurrentHashMap.newKeySet();
            owned.addAll(groups);
            state.groupDirectoryByOwner.put(owner, owned);
            owned.forEach(group -> state.knownGroupRefs.put(group.key(), group));
        }
        if (previous != null) {
            previous.forEach(group -> retireGroupIfUnreferenced(state, group.key()));
        }
        incrementDataVersion();
    }

    /** 返回服务端已经按读取权限过滤后的权威分组集合。 */
    public List<GroupRef> getAccessibleGroupRefs() {
        return List.copyOf(projection.knownGroupRefs.values());
    }

    public Map<LogisticsNode, FaceTopology> getActiveTopology(ResourceKey<Level> dimension) {
        Map<FaceAddress, FaceTopology> values = projection.topologyByDimension.get(dimension);
        if (values == null || values.isEmpty()) return Collections.emptyMap();
        Map<LogisticsNode, FaceTopology> result = new LinkedHashMap<>();
        values.values().forEach(topology -> result.put(topology.node(), topology));
        return Collections.unmodifiableMap(result);
    }

    public Set<LogisticsNode> getLinkedNodes(GroupKey groupKey, LogisticsNode source) {
        Map<LogisticsNode, Set<LogisticsNode>> sources = projection.scopedLinks.get(groupKey);
        if (sources == null) return Set.of();
        Set<LogisticsNode> targets = sources.get(source);
        return targets == null ? Set.of() : Set.copyOf(targets);
    }

    public void addKnownGroup(GroupRef group, String ownerName) {
        ProjectionState state = projection;
        state.knownGroupRefs.put(group.key(), group);
        if (ownerName != null && !ownerName.isEmpty()) {
            state.knownOwnerNames.putIfAbsent(group.key().ownerId(), ownerName);
        }
        incrementDataVersion();
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

    /** 仅在显示名称唯一时为旧物品解析身份，避免同名不同所有者的分组串线。 */
    @Nullable
    public GroupKey resolveUniqueGroupKey(String displayName) {
        GroupKey match = null;
        for (GroupRef group : projection.knownGroupRefs.values()) {
            if (!group.displayName().equals(displayName)) continue;
            if (match != null && !match.equals(group.key())) return null;
            match = group.key();
        }
        return match;
    }

    /** 返回完整节点身份，保留维度与具体面。 */
    public List<LogisticsNode> getNodesForGroup(GroupKey groupKey) {
        Set<FaceKey> faces = projection.groupFaces.get(groupKey);
        if (faces == null || faces.isEmpty()) return List.of();
        return faces.stream().map(FaceKey::toNode).toList();
    }

    private void rememberTopologyDirectory(ProjectionState state, FaceTopology topology) {
        topology.groups().forEach(group -> state.knownGroupRefs.put(group.key(), group));
        if (topology.ownerId() != null && !"Unknown".equals(topology.ownerName())) {
            state.knownOwnerNames.putIfAbsent(topology.ownerId(), topology.ownerName());
        }
    }

    private void indexFace(ProjectionState state, FaceKey face, FaceTopology topology) {
        Set<GroupKey> keys = topology.groupKeys();
        if (keys.isEmpty()) return;
        state.faceGroups.put(face, keys);
        for (GroupKey key : keys) {
            state.groupFaces.computeIfAbsent(key, ignored -> ConcurrentHashMap.newKeySet()).add(face);
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

    /**
     * 分组目录与拓扑共同持有显示身份；两侧都不再引用时才回收。
     */
    private void retireGroupIfUnreferenced(ProjectionState state, GroupKey key) {
        if (state.groupFaces.containsKey(key)) return;
        Set<GroupRef> directory = state.groupDirectoryByOwner.get(key.ownerId());
        if (directory != null && directory.stream().anyMatch(group -> group.key().equals(key))) return;
        state.knownGroupRefs.remove(key);
    }

    private void putScopedLink(ProjectionState state, ScopedTopologyLink link) {
        state.scopedLinks
            .computeIfAbsent(link.groupKey(), ignored -> new ConcurrentHashMap<>())
            .computeIfAbsent(link.source(), ignored -> ConcurrentHashMap.newKeySet())
            .add(link.target());
    }

    private void removeSourceLinks(ProjectionState state, LogisticsNode source) {
        state.scopedLinks.entrySet().removeIf(group -> {
            group.getValue().remove(source);
            return group.getValue().isEmpty();
        });
    }

    private void removeIncidentLinks(ProjectionState state, LogisticsNode node) {
        state.scopedLinks.entrySet().removeIf(group -> {
            Map<LogisticsNode, Set<LogisticsNode>> sources = group.getValue();
            sources.remove(node);
            sources.entrySet().removeIf(entry -> {
                Set<LogisticsNode> targets = entry.getValue();
                targets.remove(node);
                return targets.isEmpty();
            });
            return sources.isEmpty();
        });
    }

    public String getOwnerName(UUID owner) {
        String known = projection.knownOwnerNames.get(owner);
        return known == null || known.isEmpty() ? owner.toString() : known;
    }
}
