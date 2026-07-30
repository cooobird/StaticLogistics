package com.coobird.staticlogistics.network.sync;

import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.logistics.group.GroupService;
import com.coobird.staticlogistics.logistics.group.PlayerGroupStore;
import com.coobird.staticlogistics.logistics.node.FaceAddress;
import com.coobird.staticlogistics.logistics.node.FaceConfigComposite;
import com.coobird.staticlogistics.logistics.node.sync.PendingSyncBuffer;
import com.coobird.staticlogistics.logistics.node.sync.TopologySyncPort;
import com.coobird.staticlogistics.logistics.util.LogisticsConstants;
import com.coobird.staticlogistics.network.s2c.S2CRemoveFaceTopologyPayload;
import com.coobird.staticlogistics.network.s2c.S2CTopologyUpdatePayload;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 服务端轻量拓扑增量与删除墓碑的统一发送入口。
 */
public class NetworkSyncManager implements TopologySyncPort {
    private final ServerLevel level;

    public NetworkSyncManager(ServerLevel level) {
        this.level = level;
    }

    @Override
    public void syncToPlayer(ServerPlayer player, BlockPos pos, Direction face, FaceConfigComposite config) {
        if (!GroupService.canAccess(config.faceConfig.getOwner(), player)) return;
        LogisticsNode node = new LogisticsNode(GlobalPos.of(level.dimension(), pos), face);
        sendTopologyPages(player, List.of(
            S2CTopologyUpdatePayload.FaceUpdate.from(level, node, config)));
    }

    /**
     * 将本刻冻结的面拓扑按玩家权限过滤，再以原子分页事务发送。
     */
    @Override
    public void syncPendingToDimension(List<PendingSyncBuffer.PendingSyncEntry> entries) {
        if (entries.isEmpty()) return;
        List<PendingSyncBuffer.PendingSyncEntry> updates = new ArrayList<>();
        List<PendingSyncBuffer.PendingSyncEntry> removals = new ArrayList<>();
        for (PendingSyncBuffer.PendingSyncEntry entry : entries) {
            if (entry.isRemoval()) {
                removals.add(entry);
            } else {
                updates.add(entry);
            }
        }

        for (ServerPlayer player : level.players()) {
            List<S2CTopologyUpdatePayload.FaceUpdate> authorized = updates.stream()
                .filter(entry -> GroupService.canAccess(entry.ownerId(), player))
                .map(entry -> new S2CTopologyUpdatePayload.FaceUpdate(
                    entry.topology(), entry.linksByGroup()))
                .toList();
            sendTopologyPages(player, authorized);
            sendRemovalPages(player, removals.stream()
                .filter(entry -> entry.ownerId() != null
                    && GroupService.canAccess(entry.ownerId(), player))
                .map(entry -> new S2CRemoveFaceTopologyPayload.Entry(
                    GlobalPos.of(level.dimension(), entry.pos()), entry.face(), entry.version()))
                .toList());
        }
    }

    @Override
    public void syncBulkToPlayer(ServerPlayer player, List<Map.Entry<FaceAddress, FaceConfigComposite>> configs) {
        List<S2CTopologyUpdatePayload.FaceUpdate> updates = new ArrayList<>();
        for (var entry : configs) {
            FaceConfigComposite config = entry.getValue();
            if (config.isDefault() || !GroupService.canAccess(config.faceConfig.getOwner(), player)) continue;
            LogisticsNode node = entry.getKey().toNode(level.dimension());
            updates.add(S2CTopologyUpdatePayload.FaceUpdate.from(level, node, config));
        }
        sendTopologyPages(player, updates);
    }

    private static void sendTopologyPages(
        ServerPlayer player,
        List<S2CTopologyUpdatePayload.FaceUpdate> updates
    ) {
        PlayerGroupStore store = PlayerGroupStore.get(player.server);
        S2CTopologyUpdatePayload.pages(updates, store::getConnectionName)
            .forEach(payload -> PacketDistributor.sendToPlayer(player, payload));
    }

    private static void sendRemovalPages(
        ServerPlayer player,
        List<S2CRemoveFaceTopologyPayload.Entry> removals
    ) {
        for (int i = 0; i < removals.size(); i += LogisticsConstants.Network.getMaxBulkEntries()) {
            int end = Math.min(i + LogisticsConstants.Network.getMaxBulkEntries(), removals.size());
            PacketDistributor.sendToPlayer(player,
                new S2CRemoveFaceTopologyPayload(removals.subList(i, end)));
        }
    }
}
