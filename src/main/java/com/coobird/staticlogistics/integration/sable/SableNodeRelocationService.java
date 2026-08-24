package com.coobird.staticlogistics.integration.sable;

import com.coobird.staticlogistics.StaticLogistics;
import com.coobird.staticlogistics.integration.ModCompat;
import com.coobird.staticlogistics.logistics.node.FaceAddress;
import com.coobird.staticlogistics.logistics.node.FaceConfigComposite;
import com.coobird.staticlogistics.logistics.node.LinkManager;
import com.coobird.staticlogistics.logistics.node.NodeMutationTransaction;
import com.coobird.staticlogistics.api.LogisticsNode;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.Rotation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.HashSet;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 在 Sable 世界/plot 搬移方块后同步搬移独立于方块实体保存的物流配置。
 */
@EventBusSubscriber(modid = StaticLogistics.MODID)
public final class SableNodeRelocationService {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<MinecraftServer, Map<NodePosition, BlockPos>> LAST_WORLD_POSITIONS = new java.util.WeakHashMap<>();
    private static final ThreadLocal<Deque<AssemblyScope>> ACTIVE_ASSEMBLIES = new ThreadLocal<>();

    private SableNodeRelocationService() {
    }

    /**
     * 接收 Sable 装配器提供的精确方块换址表。该入口是主迁移路径，避免仅凭方块类型
     * 在重叠结构或相邻同类容器之间猜测目标位置。
     */
    public static AssemblyScope beginAssemblyMove(
        ServerLevel originLevel, ServerLevel resultingLevel, Iterable<BlockPos> oldPositions,
        PositionTransform transform, Rotation rotation
    ) {
        AssemblyScope scope = new AssemblyScope(originLevel, resultingLevel);
        Deque<AssemblyScope> scopes = ACTIVE_ASSEMBLIES.get();
        if (scopes == null) {
            scopes = new ArrayDeque<>();
            ACTIVE_ASSEMBLIES.set(scopes);
        }
        scopes.push(scope);
        if (!ModCompat.isSableLoaded()) return scope;
        if (originLevel != resultingLevel
            && !originLevel.dimension().equals(resultingLevel.dimension())) {
            return scope;
        }

        LinkManager manager = LinkManager.get(originLevel);
        Set<BlockPos> configuredPositions = manager.getAllConfiguredBlockPositions();
        if (configuredPositions.isEmpty()) return scope;
        Map<BlockPos, BlockPos> moves = new LinkedHashMap<>();
        Set<LogisticsNode> snapshots = new HashSet<>();
        for (BlockPos oldPosition : oldPositions) {
            if (!configuredPositions.contains(oldPosition)) continue;
            BlockPos newPosition = transform.apply(oldPosition);
            scope.protect(originLevel, oldPosition);
            scope.protect(resultingLevel, newPosition);
            if (!oldPosition.equals(newPosition)) {
                moves.put(oldPosition.immutable(), newPosition.immutable());
            }
            for (Direction face : Direction.values()) {
                LogisticsNode node = new LogisticsNode(
                    net.minecraft.core.GlobalPos.of(originLevel.dimension(), oldPosition), face);
                FaceConfigComposite config = manager.getFaceConfig(FaceAddress.of(node));
                if (config == null) continue;
                snapshots.add(node);
                snapshots.addAll(config.getLinkedNodes());
            }
        }
        scope.captureSnapshots(snapshots);
        scope.prepareRelocation(manager, moves,
            rotation == null ? Rotation.NONE : rotation);
        LOGGER.debug("Prepared Sable assembly relocation for {} mapped blocks and {} logistics faces",
            moves.size(), snapshots.size());
        return scope;
    }

    /**
     * 搬运事务期间，新旧位置均不应触发普通方块生命周期清理。
     */
    public static boolean isAssemblyPosition(ServerLevel level, BlockPos position) {
        Deque<AssemblyScope> scopes = ACTIVE_ASSEMBLIES.get();
        if (scopes == null) return false;
        for (AssemblyScope scope : scopes) {
            if (scope.isProtected(level, position)) return true;
        }
        return false;
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (!ModCompat.isSableLoaded()) return;
        MinecraftServer server = event.getServer();
        for (ServerLevel level : server.getAllLevels()) relocateLevel(level);
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        LAST_WORLD_POSITIONS.remove(event.getServer());
    }

    private static void relocateLevel(ServerLevel level) {
        LinkManager manager = LinkManager.get(level);
        Map<NodePosition, BlockPos> remembered = LAST_WORLD_POSITIONS.computeIfAbsent(
            level.getServer(), ignored -> new HashMap<>());
        Set<BlockPos> checked = new HashSet<>();
        for (FaceAddress key : manager.getAllConfigKeys()) {
            BlockPos storedPos = key.pos();
            if (!checked.add(storedPos)) continue;
            // 普通机械动力动态结构在解体前始终保留装配前的节点身份。
            if (DynamicNodeSpace.isCreateContraption(level, storedPos)) continue;
            FaceConfigComposite config = manager.getFaceConfig(key);
            if (config == null) continue;
            NodePosition identity = new NodePosition(level.dimension(), storedPos);

            boolean dynamic = DynamicNodeSpace.isDynamic(level, storedPos);
            if (matchesEndpoint(level, storedPos, config.getEndpointFingerprint())) {
                if (dynamic) {
                    remembered.put(identity, BlockPos.containing(
                        DynamicNodeSpace.blockCenter(level, storedPos, 1.0F)));
                } else {
                    remembered.remove(identity);
                }
                continue;
            }

            BlockPos worldLookup = dynamic ? BlockPos.containing(
                DynamicNodeSpace.blockCenter(level, storedPos, 1.0F)) : storedPos;
            BlockPos relocated = DynamicNodeSpace.findDynamicStoragePosition(
                level, worldLookup,
                candidate -> matchesEndpoint(level, candidate, config.getEndpointFingerprint()));
            if (relocated == null) {
                BlockPos lastWorld = remembered.get(identity);
                relocated = findNearbyEndpoint(level, lastWorld, config.getEndpointFingerprint());
            }
            if (relocated != null && manager.relocateBlock(storedPos, relocated)) {
                remembered.remove(identity);
            }
        }
    }

    private static BlockPos findNearbyEndpoint(
        ServerLevel level, BlockPos center, FaceConfigComposite.EndpointFingerprint fingerprint
    ) {
        if (center == null) return null;
        for (BlockPos candidate : BlockPos.betweenClosed(center.offset(-2, -2, -2), center.offset(2, 2, 2))) {
            if (matchesEndpoint(level, candidate, fingerprint)) return candidate.immutable();
        }
        return null;
    }

    private static boolean matchesEndpoint(
        ServerLevel level, BlockPos position, FaceConfigComposite.EndpointFingerprint fingerprint
    ) {
        if (fingerprint == null || level.getBlockState(position).isAir()) return false;
        BlockEntity blockEntity = level.getBlockEntity(position);
        return blockEntity != null
            && fingerprint.blockId().equals(BuiltInRegistries.BLOCK.getKey(level.getBlockState(position).getBlock()))
            && fingerprint.blockEntityTypeId().equals(
            BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(blockEntity.getType()));
    }

    private record NodePosition(ResourceKey<Level> dimension, BlockPos position) {
    }

    @FunctionalInterface
    public interface PositionTransform {
        BlockPos apply(BlockPos position);
    }

    public static final class AssemblyScope implements AutoCloseable {
        private final MinecraftServer server;
        private final Map<ServerLevel, Set<BlockPos>> protectedPositions = new LinkedHashMap<>();
        private LinkManager manager;
        private Map<BlockPos, BlockPos> moves = Map.of();
        private Rotation rotation = Rotation.NONE;
        private NodeMutationTransaction transaction;
        private boolean committed;
        private boolean closed;

        private AssemblyScope(ServerLevel originLevel, ServerLevel resultingLevel) {
            this.server = originLevel.getServer();
            protectedPositions.put(originLevel, new HashSet<>());
            protectedPositions.computeIfAbsent(resultingLevel, ignored -> new HashSet<>());
        }

        private void protect(ServerLevel level, BlockPos position) {
            protectedPositions.computeIfAbsent(level, ignored -> new HashSet<>())
                .add(position.immutable());
        }

        private boolean isProtected(ServerLevel level, BlockPos position) {
            Set<BlockPos> positions = protectedPositions.get(level);
            return positions != null && positions.contains(position);
        }

        private void prepareRelocation(LinkManager manager,
                                       Map<BlockPos, BlockPos> moves,
                                       Rotation rotation) {
            this.manager = manager;
            this.moves = Map.copyOf(moves);
            this.rotation = rotation;
        }

        /**
         * 装配期间即使第三方生命周期回调清理了面，也能从事务快照恢复后重新换址。
         */
        private void captureSnapshots(Set<LogisticsNode> nodes) {
            if (nodes.isEmpty()) return;
            transaction = NodeMutationTransaction.begin(server);
            for (LogisticsNode node : nodes) {
                ServerLevel nodeLevel = server.getLevel(node.gPos().dimension());
                if (nodeLevel == null) continue;
                transaction.captureState(node);
                transaction.captureContainer(nodeLevel, node.gPos().pos());
            }
        }

        /**
         * 等 Sable 已把目标方块和方块实体完整放好后再迁移配置，避免配置短暂指向空气
         * 而被放置事件或完整性扫描判为孤儿。
         */
        public void commit() {
            if (committed) return;
            boolean relocated = manager == null || moves.isEmpty()
                || manager.relocateBlocks(moves, rotation::rotate);
            if (!relocated && transaction != null) {
                // 生命周期回调若在 Sable 内部删除了旧面，先完整回滚，再用同一映射重试。
                transaction.close();
                transaction = null;
                relocated = manager.relocateBlocks(moves, rotation::rotate);
            }
            if (!relocated) {
                LOGGER.warn("Sable assembly completed, but no logistics nodes could be relocated for {} mapped blocks",
                    moves.size());
                return;
            }
            if (transaction != null) {
                transaction.commit();
                transaction.close();
                transaction = null;
            }
            committed = true;
        }

        @Override
        public void close() {
            if (closed) return;
            closed = true;
            if (transaction != null) {
                transaction.close();
                transaction = null;
            }
            Deque<AssemblyScope> scopes = ACTIVE_ASSEMBLIES.get();
            if (scopes == null) return;
            scopes.remove(this);
            if (scopes.isEmpty()) ACTIVE_ASSEMBLIES.remove();
        }
    }
}
