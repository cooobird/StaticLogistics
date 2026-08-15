package com.coobird.staticlogistics.logistics.node;

import com.coobird.staticlogistics.api.LogisticsNode;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraftforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 节点、容器配置与升级物移交的统一生命周期服务。
 * <p>图层只产生孤儿候选；本服务决定是否删除面，并保证升级物移交发生在配置删除之前。
 */
public final class NodeLifecycleService {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final LinkManager manager;
    private final FaceConfigHandler faces;
    private final ContainerConfigService containers;
    private final UpgradeHandoff upgradeHandoff;
    private int handoffDepth;

    public NodeLifecycleService(LinkManager manager, FaceConfigHandler faces,
                                ContainerConfigService containers, UpgradeHandoff upgradeHandoff) {
        this.manager = manager;
        this.faces = faces;
        this.containers = containers;
        this.upgradeHandoff = upgradeHandoff;
    }

    boolean isHandoffInProgress() {
        return handoffDepth > 0;
    }

    /**
     * 物理销毁一批方块；每个位置独立失败，其他位置继续处理。
     */
    public RemovalReport destroyBlocks(Collection<BlockPos> positions) {
        int removed = 0;
        int failed = 0;
        for (BlockPos pos : new LinkedHashSet<>(positions)) {
            if (pos == null) continue;
            try {
                if (destroyBlock(pos)) removed++;
            } catch (RuntimeException exception) {
                failed++;
                LOGGER.error("Failed to destroy logistics lifecycle at {}", pos, exception);
            }
        }
        return new RemovalReport(removed, failed);
    }

    /**
     * 显式删除一个面；过滤升级先移交，容器级升级不受影响。
     */
    public boolean removeFace(FaceAddress key) {
        FaceHandle face = findFace(key);
        if (face == null) return false;
        try {
            executeRemoval(List.of(face), null);
            return true;
        } catch (RuntimeException exception) {
            LOGGER.error("Failed to remove logistics face at {} face {}",
                face.node().gPos().pos(), face.node().face(), exception);
            return false;
        }
    }

    /**
     * 只删除真正没有链接、角色和面过滤升级的孤儿面。
     */
    public boolean removeOrphan(LogisticsNode node, FaceConfigComposite expected) {
        if (node == null || expected == null
            || !expected.getLinkedNodes().isEmpty()
            || expected.isGlobalInputEnabled()
            || expected.isGlobalOutputEnabled()
            || !expected.filterConfig.isDefault()) return false;
        FaceHandle live = findFace(FaceAddress.of(node));
        if (live == null || live.config() != expected) return false;
        removeFaceAfterHandoff(live, true, true);
        return true;
    }

    /**
     * 冻结本次待删除的孤立面、共享容器和升级来源。
     */
    public DisconnectedRemoval prepareDisconnectedRemoval(Collection<LogisticsNode> nodes) {
        if (nodes == null) {
            throw new IllegalArgumentException("Disconnected nodes are required");
        }
        LinkedHashSet<FaceHandle> disconnected = new LinkedHashSet<>();
        for (LogisticsNode node : nodes) {
            if (node == null) continue;
            FaceHandle face = findFace(FaceAddress.of(node));
            if (face != null && face.config().getLinkedNodes().isEmpty()) disconnected.add(face);
        }
        if (disconnected.isEmpty()) {
            return new DisconnectedRemoval(List.of(), List.of(), List.of());
        }

        Set<FaceAddress> removingKeys = disconnected.stream()
            .map(FaceHandle::key).collect(Collectors.toSet());
        LinkedHashSet<BlockPos> positions = disconnected.stream()
            .map(face -> face.node().gPos().pos())
            .collect(Collectors.toCollection(LinkedHashSet::new));
        List<ContainerHandle> containersToRemove = new ArrayList<>();
        for (BlockPos pos : positions) {
            ContainerHandle container = findContainer(pos);
            if (container != null
                && removingKeys.containsAll(container.config().getLinkedFaceKeys())) {
                containersToRemove.add(container);
            }
        }
        List<UpgradeSource> sources = new ArrayList<>();
        for (FaceHandle face : disconnected) {
            sources.add(new UpgradeSource(
                face.node().gPos().pos(), face.config().filterConfig.getUpgrades()));
        }
        for (ContainerHandle container : containersToRemove) {
            sources.add(new UpgradeSource(container.pos(), container.config().getUpgrades()));
        }
        return new DisconnectedRemoval(
            List.copyOf(disconnected), List.copyOf(containersToRemove), List.copyOf(sources));
    }

    /**
     * 应用已经冻结的孤立端删除；升级移交由外层事务统一控制。
     */
    public void applyDisconnectedRemoval(DisconnectedRemoval removal) {
        if (removal == null) throw new IllegalArgumentException("Disconnected removal is required");
        try (RemovalScope ignored = beginRemoval()) {
            for (FaceHandle face : removal.faces()) {
                removeFaceAfterHandoff(face, true, true);
            }
            for (ContainerHandle container : removal.containers()) {
                removeContainerAfterHandoff(container);
            }
        } catch (RuntimeException exception) {
            reconcile(removal.faces(), removal.containers());
            throw exception;
        }
    }

    private boolean destroyBlock(BlockPos pos) {
        List<FaceHandle> faceHandles = findFaces(pos);
        ContainerHandle container = findContainer(pos);
        if (faceHandles.isEmpty() && container == null) return false;

        executeRemoval(faceHandles, container == null ? List.of() : List.of(container), upgradeHandoff);
        return true;
    }

    private void executeRemoval(List<FaceHandle> faces, @Nullable ContainerHandle container) {
        executeRemoval(faces, container == null ? List.of() : List.of(container), upgradeHandoff);
    }

    private void executeRemoval(
        List<FaceHandle> faces,
        List<ContainerHandle> containers,
        UpgradeHandoff handoff
    ) {
        try {
            try (RemovalScope ignored = beginRemoval()) {
                List<UpgradeSource> sources = new ArrayList<>();
                for (FaceHandle face : faces) {
                    sources.add(new UpgradeSource(
                        face.node().gPos().pos(), face.config().filterConfig.getUpgrades()));
                }
                for (ContainerHandle container : containers) {
                    sources.add(new UpgradeSource(container.pos(), container.config().getUpgrades()));
                }
                try (HandoffReceipt receipt = handoff.begin(sources)) {
                    for (FaceHandle face : faces) {
                        removeFaceAfterHandoff(face, true, true);
                    }
                    for (ContainerHandle container : containers) {
                        removeContainerAfterHandoff(container);
                    }
                    receipt.commit();
                }
            }
        } catch (RuntimeException exception) {
            reconcile(faces, containers);
            throw exception;
        }
    }

    @Nullable
    private FaceHandle findFace(FaceAddress key) {
        FaceConfigComposite config = faces.getFaceConfig(key);
        return config == null ? null : new FaceHandle(key, manager.createNodeFromKey(key), config);
    }

    private List<FaceHandle> findFaces(BlockPos pos) {
        List<FaceHandle> result = new ArrayList<>();
        for (Direction face : Direction.values()) {
            FaceHandle handle = findFace(FaceAddress.of(pos, face));
            if (handle != null) result.add(handle);
        }
        return List.copyOf(result);
    }

    @Nullable
    private ContainerHandle findContainer(BlockPos pos) {
        ContainerConfig config = containers.get(pos);
        return config == null ? null : new ContainerHandle(pos, config);
    }

    private RemovalScope beginRemoval() {
        handoffDepth++;
        return () -> {
            if (handoffDepth <= 0) {
                throw new IllegalStateException("Lifecycle handoff scope is not active");
            }
            handoffDepth--;
        };
    }

    private void removeFaceAfterHandoff(FaceHandle face, boolean cascade, boolean tombstone) {
        faces.removeFaceAfterHandoff(face.key(), face.config(), cascade, tombstone);
    }

    private void removeContainerAfterHandoff(ContainerHandle container) {
        if (!containers.removeAfterHandoff(container.pos(), container.config())) {
            throw new IllegalStateException(
                "Container configuration changed during lifecycle removal at " + container.pos());
        }
        manager.markContainerDirty(container.pos().asLong());
    }

    private void reconcile(Collection<FaceHandle> faceHandles,
                           Collection<ContainerHandle> containers) {
        for (FaceHandle face : faceHandles) {
            if (faces.getFaceConfig(face.key()) == face.config()) face.config().markDirty();
        }
        for (ContainerHandle container : containers) {
            if (this.containers.get(container.pos()) == container.config()) {
                container.config().markDirty();
            }
        }
    }

    public record FaceHandle(FaceAddress key, LogisticsNode node, FaceConfigComposite config) {
    }

    public record ContainerHandle(BlockPos pos, ContainerConfig config) {
    }

    /**
     * 一批待移交的升级物来源。
     * <p>默认构造器覆盖整个物品处理器；槽位范围构造器用于只移交某一侧过滤器，
     * 避免关闭输入或输出时误取另一侧仍在使用的过滤器。</p>
     */
    public record UpgradeSource(
        BlockPos pos,
        IItemHandler inventory,
        int firstSlot,
        int slotLimit
    ) {
        public UpgradeSource(BlockPos pos, IItemHandler inventory) {
            this(pos, inventory, 0, inventory == null ? 0 : inventory.getSlots());
        }

        public UpgradeSource {
            if (pos == null || inventory == null) {
                throw new IllegalArgumentException("Upgrade source fields must not be null");
            }
            if (firstSlot < 0 || slotLimit < firstSlot || slotLimit > inventory.getSlots()) {
                throw new IllegalArgumentException("Upgrade source slot range is invalid");
            }
        }
    }

    public record DisconnectedRemoval(
        List<FaceHandle> faces,
        List<ContainerHandle> containers,
        List<UpgradeSource> sources
    ) {
        public DisconnectedRemoval {
            faces = List.copyOf(faces);
            containers = List.copyOf(containers);
            sources = List.copyOf(sources);
        }
    }

    public record RemovalReport(int removedPositions, int failedPositions) {
    }

    @FunctionalInterface
    public interface UpgradeHandoff {
        HandoffReceipt begin(List<UpgradeSource> sources);
    }

    public interface HandoffReceipt extends AutoCloseable {
        void commit();

        @Override
        void close();
    }

    @FunctionalInterface
    private interface RemovalScope extends AutoCloseable {
        @Override
        void close();
    }
}
