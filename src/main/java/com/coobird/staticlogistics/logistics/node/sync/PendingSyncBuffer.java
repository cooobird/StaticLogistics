package com.coobird.staticlogistics.logistics.node.sync;

import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.api.group.GroupKey;
import com.coobird.staticlogistics.logistics.node.EndpointAddress;
import com.coobird.staticlogistics.logistics.node.EndpointConfig;
import com.coobird.staticlogistics.logistics.node.EndpointTopology;
import com.coobird.staticlogistics.logistics.util.VersionOrder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/// 入队时冻结轻量拓扑，同一面只保留最高版本的更新或删除墓碑。
public final class PendingSyncBuffer {
    private static final AtomicLong CONTENT_REVISIONS = new AtomicLong();

    /// 与分页消息序号分开：记录内容冻结的先后，全量快照据此淘汰排队中的旧内容。
    public static long snapshotBoundary() {
        return CONTENT_REVISIONS.incrementAndGet();
    }

    private static final int MAX_BATCH_FACES = 32;
    private static final int MAX_BATCH_LINKS = 2048;

    public record PendingSyncEntry(BlockPos pos, Direction face, int channel,
                                   @Nullable EndpointTopology topology,
                                   Map<GroupKey, Set<LogisticsNode>> linksByGroup,
                                   long version, @Nullable UUID ownerId, long contentRevision) {
        public PendingSyncEntry {
            linksByGroup = copyLinks(linksByGroup);
        }

        public boolean isRemoval() {
            return topology == null;
        }

        static PendingSyncEntry update(
            ServerLevel level,
            LogisticsNode node,
            EndpointConfig config
        ) {
            return new PendingSyncEntry(node.gPos().pos(), node.face(), node.channel(),
                EndpointTopology.from(level, node, config), config.getLinkedNodesByGroup(),
                config.getVersion(), config.faceConfig.getOwner(), CONTENT_REVISIONS.incrementAndGet());
        }

        static PendingSyncEntry removal(LogisticsNode node, long version, @Nullable UUID ownerId) {
            return new PendingSyncEntry(
                node.gPos().pos(), node.face(), node.channel(), null, Map.of(), version, ownerId, CONTENT_REVISIONS.incrementAndGet());
        }

        private static Map<GroupKey, Set<LogisticsNode>> copyLinks(
            Map<GroupKey, Set<LogisticsNode>> links
        ) {
            Map<GroupKey, Set<LogisticsNode>> copy = new LinkedHashMap<>();
            links.forEach((key, value) -> copy.put(key, Set.copyOf(value)));
            return Map.copyOf(copy);
        }
    }

    private final Map<EndpointAddress, PendingSyncEntry> pending = new LinkedHashMap<>();
    private boolean flushing;

    public void schedule(
        ServerLevel level,
        LogisticsNode node,
        EndpointConfig config
    ) {
        enqueue(node, PendingSyncEntry.update(level, node, config));
    }

    public void scheduleRemoval(LogisticsNode node, long version, @Nullable UUID ownerId) {
        enqueue(node, PendingSyncEntry.removal(node, version, ownerId));
    }

    private void enqueue(LogisticsNode node, PendingSyncEntry next) {
        pending.merge(EndpointAddress.of(node), next, PendingSyncBuffer::newer);
    }

    private static PendingSyncEntry newer(PendingSyncEntry current, PendingSyncEntry next) {
        return VersionOrder.preferCandidate(
            current.version(), current.isRemoval(), next.version(), next.isRemoval()) ? next : current;
    }

    /// 队列由维度级 LinkManager 持有；发送器只接收本维度的批次。
    public void flush(Consumer<List<PendingSyncEntry>> sender) {
        if (flushing || pending.isEmpty()) return;
        Map<EndpointAddress, PendingSyncEntry> batch = new LinkedHashMap<>();
        int linksLeft = MAX_BATCH_LINKS;
        var entries = pending.entrySet().iterator();
        while (entries.hasNext() && batch.size() < MAX_BATCH_FACES && linksLeft > 0) {
            var entry = entries.next();
            batch.put(entry.getKey(), entry.getValue());
            /// 单个端点的出边不可拆分，最后一个端点可以超过软上限。
            for (var links : entry.getValue().linksByGroup().values()) linksLeft -= links.size();
            entries.remove();
        }
        flushing = true;
        try {
            sender.accept(new ArrayList<>(batch.values()));
        } catch (RuntimeException exception) {
            batch.forEach((key, value) -> pending.merge(key, value, PendingSyncBuffer::newer));
            throw exception;
        } finally {
            flushing = false;
        }
    }
}
