package com.coobird.staticlogistics.transfer;

import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.logistics.node.EndpointAddress;
import com.coobird.staticlogistics.logistics.node.EndpointConfig;
import com.coobird.staticlogistics.logistics.node.LinkManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.util.*;
import java.util.stream.Collectors;

/// 统一生成节点的无副作用只读快照，并按完整节点依赖局部失效缓存。
/// 本服务只在服务器主线程使用。一个输入面的展示结果依赖其链接输出面，
/// 因此缓存同时维护反向依赖索引；任一配置或能力变化只清理该面及依赖它的快照。
public final class NodeQueryService {
    private static final Map<MinecraftServer, CacheState> CACHES = new HashMap<>();

    private record QueryKey(ResourceKey<Level> dimension, EndpointAddress address) {
        static QueryKey of(LogisticsNode node) {
            return new QueryKey(node.gPos().dimension(), EndpointAddress.of(node));
        }
    }

    private record CachedSnapshot(long configVersion, int registryGeneration,
                                  Set<QueryKey> dependencies, NodeQuerySnapshot snapshot) {
        CachedSnapshot {
            dependencies = Set.copyOf(dependencies);
        }
    }

    private static final class CacheState {
        private final Map<QueryKey, CachedSnapshot> snapshots = new HashMap<>();
        private final Map<QueryKey, Set<QueryKey>> dependents = new HashMap<>();

        void put(QueryKey key, CachedSnapshot snapshot) {
            removeSnapshot(key);
            snapshots.put(key, snapshot);
            for (QueryKey dependency : snapshot.dependencies()) {
                if (!dependency.equals(key)) {
                    dependents.computeIfAbsent(dependency, ignored -> new HashSet<>()).add(key);
                }
            }
        }

        void invalidate(QueryKey root) {
            ArrayDeque<QueryKey> pending = new ArrayDeque<>();
            Set<QueryKey> visited = new HashSet<>();
            pending.add(root);
            while (!pending.isEmpty()) {
                QueryKey key = pending.removeFirst();
                if (!visited.add(key)) continue;
                Set<QueryKey> children = dependents.get(key);
                if (children != null) pending.addAll(List.copyOf(children));
                dependents.remove(key);
                removeSnapshot(key);
            }
        }

        private void removeSnapshot(QueryKey key) {
            CachedSnapshot removed = snapshots.remove(key);
            if (removed != null) {
                for (QueryKey dependency : removed.dependencies()) {
                    Set<QueryKey> children = dependents.get(dependency);
                    if (children == null) continue;
                    children.remove(key);
                    if (children.isEmpty()) dependents.remove(dependency);
                }
            }
        }

        void release(ResourceKey<Level> dimension) {
            Set<QueryKey> roots = new HashSet<>();
            snapshots.keySet().stream()
                .filter(key -> key.dimension().equals(dimension))
                .forEach(roots::add);
            dependents.keySet().stream()
                .filter(key -> key.dimension().equals(dimension))
                .forEach(roots::add);
            roots.forEach(this::invalidate);
        }

        boolean isEmpty() {
            return snapshots.isEmpty() && dependents.isEmpty();
        }
    }

    private NodeQueryService() {
    }

    public static void invalidateNode(MinecraftServer server, LogisticsNode node) {
        CacheState state = CACHES.get(server);
        if (state != null) state.invalidate(QueryKey.of(node));
    }

    public static void invalidateFace(ServerLevel level, BlockPos pos, Direction face) {
        var container = LinkManager.get(level).getContainerConfig(pos);
        if (container != null) for (EndpointAddress key : container.getLinkedFaceKeys()) {
            if (key.face() == face) invalidateNode(level.getServer(), key.toNode(level.dimension()));
        }
    }

    public static void invalidateBlock(ServerLevel level, BlockPos pos) {
        for (Direction face : Direction.values()) invalidateFace(level, pos, face);
    }

    public static void release(MinecraftServer server) {
        CACHES.remove(server);
    }

    public static void release(ServerLevel level) {
        CacheState state = CACHES.get(level.getServer());
        if (state == null) return;
        state.release(level.dimension());
        if (state.isEmpty()) CACHES.remove(level.getServer(), state);
    }

    public static Optional<NodeQuerySnapshot> query(ServerLevel level, BlockPos pos, Direction face) {
        return query(level, new LogisticsNode(GlobalPos.of(level.dimension(), pos), face));
    }

    public static Optional<NodeQuerySnapshot> query(ServerLevel level, LogisticsNode node) {
        if (node.isConnectionFace()) return Optional.empty();
        BlockPos pos = node.gPos().pos();
        Direction face = node.face();
        LinkManager manager = LinkManager.get(level);
        EndpointConfig config = manager.getEndpointConfig(EndpointAddress.of(node));
        if (config == null || config.isDefault()) {
            invalidateNode(level.getServer(), node);
            return Optional.empty();
        }

        CacheState state = CACHES.computeIfAbsent(level.getServer(), ignored -> new CacheState());
        QueryKey queryKey = QueryKey.of(node);
        CachedSnapshot cached = state.snapshots.get(queryKey);
        NodeQuerySnapshot base;
        int registryGeneration = TransferRegistries.generation();
        if (cached != null && cached.configVersion() == config.getVersion()
            && cached.registryGeneration() == registryGeneration) {
            base = cached.snapshot();
        } else {
            base = createRoutingSnapshot(level, pos, face, node, config);
            Set<QueryKey> dependencies = base.linkedNodes().stream()
                .map(QueryKey::of)
                .collect(Collectors.toCollection(HashSet::new));
            dependencies.add(queryKey);
            state.put(queryKey, new CachedSnapshot(
                config.getVersion(), registryGeneration, dependencies, base));
        }

        NodeStats stats = TransferLogManager.get(level.getServer()).getPerNodeStats(node);
        long currentTick = level.getServer().overworld().getGameTime();
        return Optional.of(base.withStats(
            stats == null ? 0 : stats.sentAmount(),
            stats == null ? 0 : stats.receivedAmount(),
            stats == null ? 0 : stats.getTransfersPerMinute(),
            stats == null ? -1 : stats.ticksSinceLastTransfer(currentTick)));
    }

    private static NodeQuerySnapshot createRoutingSnapshot(
        ServerLevel level, BlockPos pos, Direction face,
        LogisticsNode node, EndpointConfig config
    ) {
        List<ResourceLocation> presentTypes = ids(TransferUtils.getPresentTypes(level, pos, face));
        Set<ResourceLocation> presentSet = Set.copyOf(presentTypes);
        List<ResourceLocation> outputTypes = config.isGlobalOutputEnabled()
            ? config.getSelectedTypeIds().stream().filter(presentSet::contains).toList()
            : List.of();
        List<ResourceLocation> acceptedTypes = config.isGlobalInputEnabled()
            ? ids(TransferUtils.getEffectiveReceiveTypes(level, node, config))
            : List.of();

        return new NodeQuerySnapshot(
            List.copyOf(config.faceConfig.getGroupIds()),
            List.copyOf(config.faceConfig.getGroupKeys()),
            config.determineRole(),
            config.isGlobalInputEnabled(),
            config.isGlobalOutputEnabled(),
            config.linkConfig.getPriority(),
            config.linkConfig.getKeepStock(),
            config.linkConfig.getStrategy().id(),
            config.linkConfig.getStrategy().getDescriptionId(),
            config.linkConfig.getExtractionMode(),
            config.linkConfig.getExtractionMode().getDescriptionId(),
            config.faceConfig.getOwner(),
            config.faceConfig.getOwnerName(),
            config.getVersion(),
            config.getSelectedTypeIds(),
            presentTypes,
            outputTypes,
            acceptedTypes,
            config.getLinkedNodes().stream().toList(),
            0, 0, 0, -1
        );
    }

    private static List<ResourceLocation> ids(List<LogisticsResource<?>> types) {
        return types.stream().map(LogisticsResource::typeId).collect(Collectors.toUnmodifiableList());
    }
}
