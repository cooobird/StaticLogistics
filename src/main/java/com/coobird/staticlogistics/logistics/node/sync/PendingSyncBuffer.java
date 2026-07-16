package com.coobird.staticlogistics.logistics.node.sync;

import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.api.group.GroupKey;
import com.coobird.staticlogistics.logistics.node.FaceAddress;
import com.coobird.staticlogistics.logistics.node.FaceConfigComposite;
import com.coobird.staticlogistics.logistics.node.FaceTopology;
import com.coobird.staticlogistics.logistics.util.VersionOrder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * 入队时冻结轻量拓扑，同一面只保留最高版本的更新或删除墓碑。
 */
public final class PendingSyncBuffer {
    public record PendingSyncEntry(BlockPos pos, Direction face,
                                   @Nullable FaceTopology topology,
                                   Map<GroupKey, Set<LogisticsNode>> linksByGroup,
                                   long version, @Nullable UUID ownerId) {
        public PendingSyncEntry {
            linksByGroup = copyLinks(linksByGroup);
        }

        public boolean isRemoval() {
            return topology == null;
        }

        static PendingSyncEntry update(LogisticsNode node, FaceConfigComposite config) {
            return new PendingSyncEntry(node.gPos().pos(), node.face(),
                FaceTopology.from(node, config), config.getLinkedNodesByGroup(),
                config.getVersion(), config.faceConfig.getOwner());
        }

        static PendingSyncEntry removal(LogisticsNode node, long version, @Nullable UUID ownerId) {
            return new PendingSyncEntry(
                node.gPos().pos(), node.face(), null, Map.of(), version, ownerId);
        }

        private static Map<GroupKey, Set<LogisticsNode>> copyLinks(
            Map<GroupKey, Set<LogisticsNode>> links
        ) {
            Map<GroupKey, Set<LogisticsNode>> copy = new LinkedHashMap<>();
            links.forEach((key, value) -> copy.put(key, Set.copyOf(value)));
            return Map.copyOf(copy);
        }
    }

    private final Map<ResourceKey<Level>, Map<FaceAddress, PendingSyncEntry>> pending = new HashMap<>();
    private boolean flushing;

    public void schedule(LogisticsNode node, FaceConfigComposite config) {
        enqueue(node, PendingSyncEntry.update(node, config));
    }

    public void scheduleRemoval(LogisticsNode node, long version, @Nullable UUID ownerId) {
        enqueue(node, PendingSyncEntry.removal(node, version, ownerId));
    }

    private void enqueue(LogisticsNode node, PendingSyncEntry next) {
        pending.computeIfAbsent(node.gPos().dimension(), ignored -> new LinkedHashMap<>())
            .merge(FaceAddress.of(node), next, PendingSyncBuffer::newer);
    }

    private static PendingSyncEntry newer(PendingSyncEntry current, PendingSyncEntry next) {
        return VersionOrder.preferCandidate(
            current.version(), current.isRemoval(), next.version(), next.isRemoval()) ? next : current;
    }

    public void flush(TopologySyncPort syncPort) {
        if (flushing || pending.isEmpty()) return;
        Map<ResourceKey<Level>, Map<FaceAddress, PendingSyncEntry>> toSend = new HashMap<>(pending);
        pending.clear();
        flushing = true;
        try {
            for (Map<FaceAddress, PendingSyncEntry> entries : toSend.values()) {
                syncPort.syncPendingToDimension(new ArrayList<>(entries.values()));
            }
        } catch (RuntimeException exception) {
            for (Map.Entry<ResourceKey<Level>, Map<FaceAddress, PendingSyncEntry>> dimension : toSend.entrySet()) {
                Map<FaceAddress, PendingSyncEntry> retry = pending.computeIfAbsent(
                    dimension.getKey(), ignored -> new LinkedHashMap<>());
                dimension.getValue().forEach((key, value) -> retry.merge(
                    key, value, PendingSyncBuffer::newer));
            }
            throw exception;
        } finally {
            flushing = false;
        }
    }
}

