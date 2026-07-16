package com.coobird.staticlogistics.logistics.node;

import com.coobird.staticlogistics.api.LogisticsNode;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * 节点、容器配置与升级物移交的统一生命周期用例。
 *
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

    /** 物理销毁一批方块；每个位置独立失败，其他位置继续处理。 */
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

    /** 显式删除一个面；过滤升级先移交，容器级升级不受影响。 */
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

    /** 只删除真正没有边、角色和面过滤升级的孤儿面。 */
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

    private boolean destroyBlock(BlockPos pos) {
        List<FaceHandle> faceHandles = findFaces(pos);
        ContainerHandle container = findContainer(pos);
        if (faceHandles.isEmpty() && container == null) return false;

        executeRemoval(faceHandles, container);
        return true;
    }

    private void executeRemoval(List<FaceHandle> faces, @Nullable ContainerHandle container) {
        try {
            try (RemovalScope ignored = beginRemoval()) {
                List<UpgradeSource> sources = new ArrayList<>();
                for (FaceHandle face : faces) {
                    sources.add(new UpgradeSource(
                        face.node().gPos().pos(), face.config().filterConfig.getUpgrades()));
                }
                if (container != null) {
                    sources.add(new UpgradeSource(container.pos(), container.config().getUpgrades()));
                }
                upgradeHandoff.handoff(sources);
            }
            for (FaceHandle face : faces) {
                removeFaceAfterHandoff(face, true, true);
            }
            if (container != null) removeContainerAfterHandoff(container);
        } catch (RuntimeException exception) {
            reconcile(faces, container);
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

    private void reconcile(Collection<FaceHandle> faceHandles, @Nullable ContainerHandle container) {
        for (FaceHandle face : faceHandles) {
            if (faces.getFaceConfig(face.key()) == face.config()) face.config().markDirty();
        }
        if (container != null && containers.get(container.pos()) == container.config()) {
            container.config().markDirty();
        }
    }

    public record FaceHandle(FaceAddress key, LogisticsNode node, FaceConfigComposite config) {
    }

    public record ContainerHandle(BlockPos pos, ContainerConfig config) {
    }

    public record UpgradeSource(BlockPos pos, IItemHandler inventory) {
        public UpgradeSource {
            if (pos == null || inventory == null) {
                throw new IllegalArgumentException("Upgrade source fields must not be null");
            }
        }
    }

    public record RemovalReport(int removedPositions, int failedPositions) {
    }

    @FunctionalInterface
    public interface UpgradeHandoff {
        void handoff(List<UpgradeSource> sources);
    }

    @FunctionalInterface
    private interface RemovalScope extends AutoCloseable {
        @Override
        void close();
    }
}
