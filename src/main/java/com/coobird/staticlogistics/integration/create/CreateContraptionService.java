package com.coobird.staticlogistics.integration.create;

import com.coobird.staticlogistics.logistics.node.LinkManager;
import com.mojang.logging.LogUtils;
import com.simibubi.create.api.contraption.storage.fluid.MountedFluidStorage;
import com.simibubi.create.api.contraption.storage.item.MountedItemStorage;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.Contraption;
import com.simibubi.create.content.contraptions.StructureTransform;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.Capabilities;
import org.slf4j.Logger;

import java.util.*;
import java.util.function.UnaryOperator;

/// 机械动力直接兼容层。仅在 ModList 确认机械动力已加载后调用此类。
public final class CreateContraptionService {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<Level, Map<AbstractContraptionEntity, MountedContraption>> ACTIVE =
        new IdentityHashMap<>();

    private CreateContraptionService() {
    }

    /// 为服务端和客户端动态结构实体登记装配前的稳定坐标。
    public static synchronized void onContraptionSet(AbstractContraptionEntity entity, Contraption contraption) {
        if (entity == null || contraption == null || contraption.anchor == null) return;
        removeEntity(entity);
        Set<BlockPos> configuredNodes = new LinkedHashSet<>();
        if (entity.level() instanceof ServerLevel serverLevel) {
            LinkManager manager = LinkManager.get(serverLevel);
            for (BlockPos localPos : contraption.getBlocks().keySet()) {
                BlockPos stablePos = contraption.anchor.offset(localPos);
                if (manager.getContainerConfig(stablePos) != null) configuredNodes.add(stablePos);
            }
        }
        ACTIVE.computeIfAbsent(entity.level(), ignored -> new IdentityHashMap<>())
            .put(entity, new MountedContraption(contraption, Set.copyOf(configuredNodes)));
    }

    /// 机械动力把结构放回世界后，一次性迁移节点身份。
    public static synchronized void onBlocksPlaced(Level level, Contraption contraption, StructureTransform transform) {
        if (level == null || contraption == null || transform == null) return;
        AbstractContraptionEntity entity = contraption.entity;
        if (level instanceof ServerLevel serverLevel && contraption.anchor != null) {
            LinkManager manager = LinkManager.get(serverLevel);
            Map<BlockPos, BlockPos> relocations = new HashMap<>();
            MountedContraption mounted = activeContraption(level, entity, contraption);
            Set<BlockPos> configuredNodes = mounted == null
                ? captureConfiguredNodes(manager, contraption) : mounted.configuredNodes();
            for (BlockPos stablePos : configuredNodes) {
                BlockPos localPos = stablePos.subtract(contraption.anchor);
                if (contraption.getBlocks().containsKey(localPos)) {
                    relocations.put(stablePos, transform.apply(localPos));
                }
            }
            if (!relocations.isEmpty()
                && !manager.relocateBlocks(relocations, transform::rotateFacing)) {
                LOGGER.error("Failed to relocate {} logistics container(s) placed by a Create contraption",
                    relocations.size());
                Map<Direction, Direction> faces = new EnumMap<>(Direction.class);
                for (Direction direction : Direction.values()) {
                    faces.put(direction, transform.rotateFacing(direction));
                }
                CreateRelocationRecoveryData.get(serverLevel).add(relocations, faces);
            }
        }
        removeEntity(entity);
    }

    public static synchronized boolean isMounted(Level level, BlockPos stablePos) {
        if (liveNode(level, stablePos) != null) return true;
        if (!(level instanceof ServerLevel serverLevel)) return false;
        return CreateRelocationRecoveryData.get(serverLevel).containsSource(stablePos);
    }

    public static synchronized Vec3 toWorld(Level level, Vec3 stablePosition, float partialTick) {
        BlockPos stableBlock = BlockPos.containing(stablePosition);
        MountedNode node = liveNode(level, stableBlock);
        if (node == null) return stablePosition;
        Vec3 offset = stablePosition.subtract(Vec3.atLowerCornerOf(stableBlock));
        return node.entity.toGlobalVector(Vec3.atLowerCornerOf(node.localPos).add(offset), partialTick);
    }

    public static synchronized Vec3 rotateNormal(Level level, BlockPos stablePos, Vec3 normal, float partialTick) {
        MountedNode node = liveNode(level, stablePos);
        return node == null ? normal : node.entity.applyRotation(normal, partialTick).normalize();
    }

    @SuppressWarnings("unchecked")
    public static synchronized <C> C getCapability(
        Level level, BlockPos stablePos, Direction face, BlockCapability<C, Direction> capability
    ) {
        MountedNode node = liveNode(level, stablePos);
        if (node == null) return null;
        if (capability == Capabilities.ItemHandler.BLOCK) {
            MountedItemStorage storage = node.contraption.getStorage().getAllItemStorages().get(node.localPos);
            return (C) storage;
        }
        if (capability == Capabilities.FluidHandler.BLOCK) {
            MountedFluidStorage storage = node.contraption.getStorage().getFluids().storages.get(node.localPos);
            return (C) storage;
        }
        return null;
    }

    private static MountedNode liveNode(Level level, BlockPos stablePos) {
        Map<AbstractContraptionEntity, MountedContraption> entities = ACTIVE.get(level);
        if (entities == null) return null;
        var iterator = entities.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            AbstractContraptionEntity entity = entry.getKey();
            MountedContraption mounted = entry.getValue();
            Contraption contraption = mounted.contraption();
            if (!entity.isAlive() || entity.getContraption() != contraption || contraption.anchor == null) {
                iterator.remove();
                continue;
            }
            if (level instanceof ServerLevel
                && !mounted.configuredNodes().contains(stablePos)) continue;
            BlockPos localPos = stablePos.subtract(contraption.anchor);
            if (contraption.getBlocks().containsKey(localPos)) {
                return new MountedNode(entity, contraption, localPos);
            }
        }
        if (entities.isEmpty()) ACTIVE.remove(level);
        return null;
    }

    private static void removeEntity(AbstractContraptionEntity entity) {
        if (entity == null) return;
        Map<AbstractContraptionEntity, MountedContraption> entities = ACTIVE.get(entity.level());
        if (entities == null) return;
        entities.remove(entity);
        if (entities.isEmpty()) ACTIVE.remove(entity.level());
    }

    public static synchronized void prune(Level level) {
        Map<AbstractContraptionEntity, MountedContraption> entities = ACTIVE.get(level);
        if (entities != null) {
            entities.entrySet().removeIf(entry -> !entry.getKey().isAlive()
                || entry.getKey().getContraption() != entry.getValue().contraption());
            if (entities.isEmpty()) ACTIVE.remove(level);
        }
        if (level instanceof ServerLevel serverLevel) retryPending(serverLevel);
    }

    public static synchronized void release(Level level) {
        ACTIVE.remove(level);
    }

    private record MountedNode(
        AbstractContraptionEntity entity, Contraption contraption, BlockPos localPos
    ) {
    }

    private static MountedContraption activeContraption(Level level,
                                                        AbstractContraptionEntity entity,
                                                        Contraption contraption) {
        Map<AbstractContraptionEntity, MountedContraption> entities = ACTIVE.get(level);
        if (entities == null || entity == null) return null;
        MountedContraption mounted = entities.get(entity);
        return mounted != null && mounted.contraption() == contraption ? mounted : null;
    }

    private static Set<BlockPos> captureConfiguredNodes(LinkManager manager, Contraption contraption) {
        Set<BlockPos> nodes = new LinkedHashSet<>();
        for (BlockPos localPos : contraption.getBlocks().keySet()) {
            BlockPos stablePos = contraption.anchor.offset(localPos);
            if (manager.getContainerConfig(stablePos) != null) nodes.add(stablePos);
        }
        return nodes;
    }

    private record MountedContraption(Contraption contraption, Set<BlockPos> configuredNodes) {
    }

    private static void retryPending(ServerLevel level) {
        if (level.getGameTime() % 20L != 0L) return;
        CreateRelocationRecoveryData recovery = CreateRelocationRecoveryData.get(level);
        LinkManager manager = LinkManager.get(level);
        for (CreateRelocationRecoveryData.Entry relocation : recovery.entries()) {
            if (relocation.moves().keySet().stream()
                .noneMatch(position -> manager.getContainerConfig(position) != null)) {
                recovery.remove(relocation.id());
                continue;
            }
            UnaryOperator<Direction> transform = direction ->
                relocation.faces().getOrDefault(direction, direction);
            if (manager.relocateBlocks(relocation.moves(), transform)) {
                recovery.remove(relocation.id());
            }
        }
    }

}
