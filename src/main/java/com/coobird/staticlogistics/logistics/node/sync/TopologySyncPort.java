package com.coobird.staticlogistics.logistics.node.sync;

import com.coobird.staticlogistics.logistics.node.FaceAddress;
import com.coobird.staticlogistics.logistics.node.FaceConfigComposite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * 物流域发布拓扑变化所依赖的网络输出端口。
 */
public interface TopologySyncPort {
    void syncToPlayer(ServerPlayer player, BlockPos pos, Direction face, FaceConfigComposite config);

    void syncPendingToDimension(List<PendingSyncBuffer.PendingSyncEntry> entries);

    void syncBulkToPlayer(ServerPlayer player, List<Map.Entry<FaceAddress, FaceConfigComposite>> configs);

    static void install(Function<ServerLevel, TopologySyncPort> factory) {
        Holder.factory = Objects.requireNonNull(factory, "Topology sync factory must not be null");
    }

    static TopologySyncPort create(ServerLevel level) {
        Function<ServerLevel, TopologySyncPort> factory = Holder.factory;
        if (factory == null) throw new IllegalStateException("Topology sync factory is not installed");
        return factory.apply(level);
    }

    final class Holder {
        private static Function<ServerLevel, TopologySyncPort> factory;

        private Holder() {
        }
    }
}
