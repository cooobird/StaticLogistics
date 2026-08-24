package com.coobird.staticlogistics.integration.create;

import com.coobird.staticlogistics.logistics.node.LinkManager;
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

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * 机械动力直接兼容层。仅在 ModList 确认机械动力已加载后调用此类。
 */
public final class CreateContraptionService {
    private static final Map<Level, Map<BlockPos, MountedNode>> BY_LEVEL = new WeakHashMap<>();
    private static final Map<AbstractContraptionEntity, Map<BlockPos, BlockPos>> BY_ENTITY =
        new IdentityHashMap<>();

    private CreateContraptionService() {
    }

    /**
     * 为服务端和客户端动态结构实体登记装配前的稳定坐标。
     */
    public static synchronized void onContraptionSet(AbstractContraptionEntity entity, Contraption contraption) {
        if (entity == null || contraption == null || contraption.anchor == null) return;
        removeEntity(entity);
        Map<BlockPos, MountedNode> levelNodes = BY_LEVEL.computeIfAbsent(entity.level(), ignored -> new HashMap<>());
        Map<BlockPos, BlockPos> entityNodes = new HashMap<>();
        for (BlockPos localPos : contraption.getBlocks().keySet()) {
            BlockPos stablePos = contraption.anchor.offset(localPos).immutable();
            BlockPos local = localPos.immutable();
            levelNodes.put(stablePos, new MountedNode(entity, contraption, local));
            entityNodes.put(stablePos, local);
        }
        BY_ENTITY.put(entity, entityNodes);
    }

    /**
     * 机械动力把结构放回世界后，一次性迁移节点身份。
     */
    public static synchronized void onBlocksPlaced(Level level, Contraption contraption, StructureTransform transform) {
        if (!(level instanceof ServerLevel serverLevel) || contraption == null || transform == null) return;
        AbstractContraptionEntity entity = contraption.entity;
        Map<BlockPos, BlockPos> nodes = entity == null ? null : BY_ENTITY.get(entity);
        if (nodes == null) return;
        LinkManager manager = LinkManager.get(serverLevel);
        Map<BlockPos, BlockPos> relocations = new HashMap<>();
        for (Map.Entry<BlockPos, BlockPos> node : Map.copyOf(nodes).entrySet()) {
            relocations.put(node.getKey(), transform.apply(node.getValue()));
        }
        manager.relocateBlocks(relocations, transform::rotateFacing);
        removeEntity(entity);
    }

    public static synchronized boolean isMounted(Level level, BlockPos stablePos) {
        return liveNode(level, stablePos) != null;
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
        Map<BlockPos, MountedNode> nodes = BY_LEVEL.get(level);
        if (nodes == null) return null;
        MountedNode node = nodes.get(stablePos);
        if (node == null) return null;
        if (!node.entity.isAlive() || node.entity.getContraption() != node.contraption) {
            removeEntity(node.entity);
            return null;
        }
        return node;
    }

    private static void removeEntity(AbstractContraptionEntity entity) {
        if (entity == null) return;
        Map<BlockPos, BlockPos> positions = BY_ENTITY.remove(entity);
        if (positions == null) return;
        Map<BlockPos, MountedNode> levelNodes = BY_LEVEL.get(entity.level());
        if (levelNodes == null) return;
        positions.keySet().forEach(pos -> {
            MountedNode current = levelNodes.get(pos);
            if (current != null && current.entity == entity) levelNodes.remove(pos);
        });
        if (levelNodes.isEmpty()) BY_LEVEL.remove(entity.level());
    }

    private record MountedNode(
        AbstractContraptionEntity entity, Contraption contraption, BlockPos localPos
    ) {
    }
}
