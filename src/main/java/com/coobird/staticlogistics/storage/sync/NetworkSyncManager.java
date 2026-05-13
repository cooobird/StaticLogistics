package com.coobird.staticlogistics.storage.sync;

import com.coobird.staticlogistics.network.s2c.S2CSyncBulkFaceConfigPacket;
import com.coobird.staticlogistics.network.s2c.S2CSyncFaceConfigPacket;
import com.coobird.staticlogistics.storage.config.FaceConfigComposite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class NetworkSyncManager {
    private final ServerLevel level;
    private static final int MAX_BULK_ENTRIES = 100;  // 每个批量包最多包含的条目数

    public NetworkSyncManager(ServerLevel level) {
        this.level = level;
    }

    // 单个配置同步（实时更新用）
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

    // 批量同步（用于登录/维度切换，自动分片）
    public void syncBulkToPlayer(ServerPlayer player, List<Map.Entry<Long, FaceConfigComposite>> configs) {
        List<S2CSyncBulkFaceConfigPacket.Entry> entries = new ArrayList<>();
        for (var entry : configs) {
            long key = entry.getKey();
            FaceConfigComposite config = entry.getValue();
            if (config.isDefault()) continue;
            BlockPos pos = BlockPos.of(key >> 3);
            Direction face = Direction.from3DDataValue((int) (key & 0x7));
            entries.add(new S2CSyncBulkFaceConfigPacket.Entry(
                GlobalPos.of(level.dimension(), pos), face, config, config.getVersion()
            ));
            if (entries.size() >= MAX_BULK_ENTRIES) {
                PacketDistributor.sendToPlayer(player, new S2CSyncBulkFaceConfigPacket(new ArrayList<>(entries)));
                entries.clear();
            }
        }
        if (!entries.isEmpty()) {
            PacketDistributor.sendToPlayer(player, new S2CSyncBulkFaceConfigPacket(entries));
        }
    }

    // 删除同步
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
}