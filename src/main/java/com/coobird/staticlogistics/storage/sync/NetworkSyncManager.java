package com.coobird.staticlogistics.storage.sync;

import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.network.s2c.S2CRemoveBulkFaceConfigPacket;
import com.coobird.staticlogistics.network.s2c.S2CSyncBulkFaceConfigPacket;
import com.coobird.staticlogistics.network.s2c.S2CSyncFaceConfigPacket;
import com.coobird.staticlogistics.storage.config.FaceConfigComposite;
import com.coobird.staticlogistics.util.LogisticsConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class NetworkSyncManager {
    private final ServerLevel level;

    public NetworkSyncManager(ServerLevel level) {
        this.level = level;
    }

    public void syncToDimension(BlockPos pos, Direction face, FaceConfigComposite config) {
        ChunkPos chunkPos = new ChunkPos(pos);
        PacketDistributor.sendToPlayersTrackingChunk(
            level, chunkPos,
            new S2CSyncFaceConfigPacket(GlobalPos.of(level.dimension(), pos), face, config)
        );
    }

    public void syncToPlayer(ServerPlayer player, BlockPos pos, Direction face, FaceConfigComposite config) {
        PacketDistributor.sendToPlayer(player,
            new S2CSyncFaceConfigPacket(GlobalPos.of(level.dimension(), pos), face, config));
    }

    public void syncBulkToPlayer(ServerPlayer player, List<Map.Entry<Long, FaceConfigComposite>> configs) {
        List<S2CSyncBulkFaceConfigPacket.Entry> entries = new ArrayList<>();
        for (var entry : configs) {
            long key = entry.getKey();
            FaceConfigComposite config = entry.getValue();
            if (config.isDefault()) continue;
            BlockPos pos = LogisticsNode.keyToPos(key);
            Direction face = LogisticsNode.keyToFace(key);
            entries.add(new S2CSyncBulkFaceConfigPacket.Entry(
                GlobalPos.of(level.dimension(), pos), face, config, config.getVersion()
            ));
            if (entries.size() >= LogisticsConstants.Network.getMaxBulkEntries()) {
                PacketDistributor.sendToPlayer(player, new S2CSyncBulkFaceConfigPacket(new ArrayList<>(entries)));
                entries.clear();
            }
        }
        if (!entries.isEmpty()) {
            PacketDistributor.sendToPlayer(player, new S2CSyncBulkFaceConfigPacket(entries));
        }
    }

    public void syncRemovalToDimension(BlockPos pos, Direction face) {
        ChunkPos chunkPos = new ChunkPos(pos);
        FaceConfigComposite emptyConfig = new FaceConfigComposite();
        PacketDistributor.sendToPlayersTrackingChunk(
            level, chunkPos,
            new S2CSyncFaceConfigPacket(GlobalPos.of(level.dimension(), pos), face, emptyConfig)
        );
    }

    public void syncRemovalToDimension(BlockPos pos) {
        for (Direction face : Direction.values()) {
            syncRemovalToDimension(pos, face);
        }
    }

    public void syncRemovalBulkToDimension(List<GlobalPos> positions, List<Direction> faces) {
        if (positions.isEmpty() || positions.size() != faces.size()) return;
        Map<ChunkPos, List<S2CRemoveBulkFaceConfigPacket.Entry>> grouped = new HashMap<>();
        for (int i = 0; i < positions.size(); i++) {
            GlobalPos pos = positions.get(i);
            Direction face = faces.get(i);
            ChunkPos chunkPos = new ChunkPos(pos.pos());
            grouped.computeIfAbsent(chunkPos, k -> new ArrayList<>()).add(new S2CRemoveBulkFaceConfigPacket.Entry(pos, face));
        }
        for (var entry : grouped.entrySet()) {
            ChunkPos chunkPos = entry.getKey();
            List<S2CRemoveBulkFaceConfigPacket.Entry> entries = entry.getValue();
            PacketDistributor.sendToPlayersTrackingChunk(level, chunkPos, new S2CRemoveBulkFaceConfigPacket(entries));
        }
    }
}