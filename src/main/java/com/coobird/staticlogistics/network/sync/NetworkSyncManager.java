package com.coobird.staticlogistics.network.sync;

import com.coobird.staticlogistics.logistics.group.PermissionService;
import com.coobird.staticlogistics.logistics.group.PlayerGroupStore;
import com.coobird.staticlogistics.logistics.node.EndpointAddress;
import com.coobird.staticlogistics.logistics.node.sync.PendingSyncBuffer;
import com.coobird.staticlogistics.logistics.util.LogisticsConstants;
import com.coobird.staticlogistics.logistics.util.VersionOrder;
import com.coobird.staticlogistics.network.s2c.S2CRemoveFaceTopologyPayload;
import com.coobird.staticlogistics.network.s2c.S2CTopologyUpdatePayload;
import com.mojang.logging.LogUtils;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.*;

/// 每位玩家独立合并端点变化、轮转发送和失败重试；全量快照建立内容失效边界。
public final class NetworkSyncManager {
    private static final int MAX_BATCH_FACES = 32;
    private static final int MAX_BATCH_LINKS = 2048;
    private final ServerLevel level;
    private final Map<UUID, Recipient> recipients = new LinkedHashMap<>();
    private final ArrayDeque<UUID> rotation = new ArrayDeque<>();

    private static final class Recipient {
        final Map<EndpointAddress, PendingSyncBuffer.PendingSyncEntry> pending = new LinkedHashMap<>();
        long snapshotBoundary;
        long retryAtTick;

        void enqueue(PendingSyncBuffer.PendingSyncEntry entry) {
            if (entry.contentRevision() <= snapshotBoundary) return;
            EndpointAddress key = new EndpointAddress(entry.pos().asLong(), entry.face(), entry.channel());
            pending.merge(key, entry, (current, next) ->
                VersionOrder.preferCandidate(current.version(), current.isRemoval(), next.version(), next.isRemoval())
                    ? next : current);
        }
    }

    public NetworkSyncManager(ServerLevel level) {
        this.level = level;
    }

    private Recipient recipient(UUID id) {
        return recipients.computeIfAbsent(id, ignored -> {
            rotation.addLast(id);
            return new Recipient();
        });
    }

    public void enqueueBroadcast(List<PendingSyncBuffer.PendingSyncEntry> entries) {
        if (entries.isEmpty()) return;
        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            Recipient recipient = recipient(player.getUUID());
            entries.forEach(recipient::enqueue);
        }
    }

    public void snapshotDelivered(ServerPlayer player, long contentBoundary) {
        Recipient recipient = recipient(player.getUUID());
        recipient.snapshotBoundary = Math.max(recipient.snapshotBoundary, contentBoundary);
        recipient.pending.values().removeIf(entry -> entry.contentRevision() <= recipient.snapshotBoundary);
        recipient.retryAtTick = 0;
    }

    public void flushPending(long deadline) {
        int remaining = rotation.size();
        while (remaining-- > 0 && !rotation.isEmpty() && System.nanoTime() - deadline < 0L) {
            UUID id = rotation.removeFirst();
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(id);
            Recipient recipient = recipients.get(id);
            if (player == null) {
                recipients.remove(id);
                continue;
            }
            rotation.addLast(id);
            if (recipient.pending.isEmpty() || level.getGameTime() < recipient.retryAtTick) continue;
            List<PendingSyncBuffer.PendingSyncEntry> batch = takeBatch(recipient);
            try {
                /// 同一个玩家的一次分页事务完整发送，避免不同批次的页面互相截断。
                sendPending(player, batch);
                recipient.retryAtTick = 0;
            } catch (RuntimeException failure) {
                batch.forEach(recipient::enqueue);
                recipient.retryAtTick = level.getGameTime() + 20;
                LogUtils.getLogger().error("Topology broadcast failed for {}; other recipients continue", id, failure);
            }
        }
    }

    private static List<PendingSyncBuffer.PendingSyncEntry> takeBatch(Recipient recipient) {
        List<PendingSyncBuffer.PendingSyncEntry> batch = new ArrayList<>();
        int linksLeft = MAX_BATCH_LINKS;
        var iterator = recipient.pending.values().iterator();
        while (iterator.hasNext() && batch.size() < MAX_BATCH_FACES && linksLeft > 0) {
            PendingSyncBuffer.PendingSyncEntry entry = iterator.next();
            batch.add(entry);
            for (var links : entry.linksByGroup().values()) linksLeft -= links.size();
            iterator.remove();
        }
        return batch;
    }

    private void sendPending(ServerPlayer player, List<PendingSyncBuffer.PendingSyncEntry> batch) {
        List<S2CTopologyUpdatePayload.FaceUpdate> authorized = batch.stream()
            .filter(entry -> !entry.isRemoval() && PermissionService.getInstance().canAccess(entry.ownerId(), player))
            .map(entry -> new S2CTopologyUpdatePayload.FaceUpdate(entry.topology(), entry.linksByGroup()))
            .toList();
        sendTopologyPages(player, authorized);
        sendRemovalPages(player, batch.stream()
            .filter(entry -> entry.isRemoval() && entry.ownerId() != null
                && PermissionService.getInstance().canAccess(entry.ownerId(), player))
            .map(entry -> new S2CRemoveFaceTopologyPayload.Entry(
                GlobalPos.of(level.dimension(), entry.pos()), entry.face(), entry.channel(), entry.version()))
            .toList());
    }

    private static void sendTopologyPages(ServerPlayer player, List<S2CTopologyUpdatePayload.FaceUpdate> updates) {
        PlayerGroupStore store = PlayerGroupStore.get(player.server);
        S2CTopologyUpdatePayload.pages(updates, store::getConnectionName)
            .forEach(payload -> PacketDistributor.sendToPlayer(player, payload));
    }

    private static void sendRemovalPages(ServerPlayer player, List<S2CRemoveFaceTopologyPayload.Entry> removals) {
        for (int i = 0; i < removals.size(); i += LogisticsConstants.Network.getMaxBulkEntries()) {
            int end = Math.min(i + LogisticsConstants.Network.getMaxBulkEntries(), removals.size());
            PacketDistributor.sendToPlayer(player, new S2CRemoveFaceTopologyPayload(removals.subList(i, end)));
        }
    }
}
