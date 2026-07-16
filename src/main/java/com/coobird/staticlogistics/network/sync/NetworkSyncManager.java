package com.coobird.staticlogistics.network.sync;

import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.logistics.group.GroupService;
import com.coobird.staticlogistics.logistics.node.FaceAddress;
import com.coobird.staticlogistics.logistics.node.FaceConfigComposite;
import com.coobird.staticlogistics.logistics.node.sync.PendingSyncBuffer;
import com.coobird.staticlogistics.logistics.node.sync.TopologySyncPort;
import com.coobird.staticlogistics.logistics.util.LogisticsConstants;
import com.coobird.staticlogistics.network.SLNetwork;
import com.coobird.staticlogistics.network.s2c.S2CRemoveFaceTopologyPayload;
import com.coobird.staticlogistics.network.s2c.S2CTopologyUpdatePayload;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 服务端轻量拓扑增量与删除墓碑的统一发送入口。
 */
public final class NetworkSyncManager implements TopologySyncPort {
    private final ServerLevel level;

    public NetworkSyncManager(ServerLevel level) {
        this.level = level;
    }

    @Override
    public void syncToPlayer(ServerPlayer player, BlockPos pos, Direction face,
                             FaceConfigComposite config) {
        if (!GroupService.canAccess(config.faceConfig.getOwner(), player)) return;
        LogisticsNode node = new LogisticsNode(GlobalPos.of(level.dimension(), pos), face);
        sendTopologyPages(player, List.of(S2CTopologyUpdatePayload.FaceUpdate.from(node, config)));
    }

    /**
     * 按接收者权限分别投影同一批更新，禁止跨所有者泄漏。
     */
    @Override
    public void syncPendingToDimension(List<PendingSyncBuffer.PendingSyncEntry> entries) {
        if (entries.isEmpty()) return;
        List<PendingSyncBuffer.PendingSyncEntry> updates = new ArrayList<>();
        List<PendingSyncBuffer.PendingSyncEntry> removals = new ArrayList<>();
        for (PendingSyncBuffer.PendingSyncEntry entry : entries) {
            if (entry.isRemoval()) removals.add(entry);
            else updates.add(entry);
        }
        for (ServerPlayer player : level.players()) {
            List<S2CTopologyUpdatePayload.FaceUpdate> authorized = updates.stream()
                .filter(entry -> GroupService.canAccess(entry.ownerId(), player))
                .map(entry -> new S2CTopologyUpdatePayload.FaceUpdate(
                    entry.topology(), entry.linksByGroup()))
                .toList();
            sendTopologyPages(player, authorized);
            List<S2CRemoveFaceTopologyPayload.Entry> authorizedRemovals = removals.stream()
                .filter(entry -> entry.ownerId() != null
                    && GroupService.canAccess(entry.ownerId(), player))
                .map(entry -> new S2CRemoveFaceTopologyPayload.Entry(
                    GlobalPos.of(level.dimension(), entry.pos()), entry.face(), entry.version()))
                .toList();
            sendRemovalPages(player, authorizedRemovals);
        }
    }

    @Override
    public void syncBulkToPlayer(
        ServerPlayer player,
        List<Map.Entry<FaceAddress, FaceConfigComposite>> configs
    ) {
        List<S2CTopologyUpdatePayload.FaceUpdate> updates = new ArrayList<>();
        for (Map.Entry<FaceAddress, FaceConfigComposite> entry : configs) {
            FaceConfigComposite config = entry.getValue();
            if (config.isDefault() || !GroupService.canAccess(config.faceConfig.getOwner(), player)) continue;
            LogisticsNode node = entry.getKey().toNode(level.dimension());
            updates.add(S2CTopologyUpdatePayload.FaceUpdate.from(node, config));
        }
        sendTopologyPages(player, updates);
    }

    private static void sendTopologyPages(
        ServerPlayer player,
        List<S2CTopologyUpdatePayload.FaceUpdate> updates
    ) {
        S2CTopologyUpdatePayload.pages(updates)
            .forEach(payload -> SLNetwork.HANDLER.sendToPlayer(player, payload));
    }

    private static void sendRemovalPages(
        ServerPlayer player,
        List<S2CRemoveFaceTopologyPayload.Entry> removals
    ) {
        int pageSize = LogisticsConstants.Network.getMaxBulkEntries();
        for (int start = 0; start < removals.size(); start += pageSize) {
            int end = Math.min(start + pageSize, removals.size());
            SLNetwork.HANDLER.sendToPlayer(player,
                new S2CRemoveFaceTopologyPayload(removals.subList(start, end)));
        }
    }
}
