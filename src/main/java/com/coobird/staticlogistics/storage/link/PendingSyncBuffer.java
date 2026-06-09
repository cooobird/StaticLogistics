package com.coobird.staticlogistics.storage.link;

import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.storage.model.FaceConfigComposite;
import com.coobird.staticlogistics.storage.sync.NetworkSyncManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 延迟批量网络同步缓冲 —— 缓存当前 tick 内所有需要同步的面配置，tick 结束时批量刷出。
 * <p>
 * 线程安全：schedule 和 flush 均在服务器主线程调用（通过 LinkManager），
 * 无需 synchronized —— 主线程单线程访问。
 * <p>
 * 去重机制：使用 Map&lt;Long, PendingSyncEntry&gt;，同节点同 tick 只保留最新配置。
 */
public class PendingSyncBuffer {

    public record PendingSyncEntry(BlockPos pos, Direction face, FaceConfigComposite config) {
    }

    private final Map<ResourceKey<Level>, Map<Long, PendingSyncEntry>> pending = new HashMap<>();
    private boolean isFlushing = false;

    private boolean suppress = false;

    void setSuppress(boolean suppress) {
        this.suppress = suppress;
    }

    void schedule(LogisticsNode node, FaceConfigComposite cfg) {
        if (suppress) return;
        ResourceKey<Level> dim = node.gPos().dimension();
        pending.computeIfAbsent(dim, k -> new LinkedHashMap<>())
            .put(node.toKey(), new PendingSyncEntry(node.gPos().pos(), node.face(), cfg));
    }

    void flush(NetworkSyncManager networkSyncManager, FaceConfigHandler faceConfigHandler) {
        if (isFlushing) return;
        if (pending.isEmpty()) return;

        Map<ResourceKey<Level>, Map<Long, PendingSyncEntry>> toSend = new HashMap<>(pending);
        pending.clear();

        isFlushing = true;
        try {
            for (var entry : toSend.entrySet()) {
                List<PendingSyncEntry> valid = entry.getValue().values().stream()
                    .filter(e -> {
                        long key = LogisticsNode.posToKey(e.pos(), e.face());
                        FaceConfigComposite live = faceConfigHandler.configRepository.get(key);
                        return live != null && !live.isDefault();
                    })
                    .toList();
                if (!valid.isEmpty()) {
                    networkSyncManager.syncBulkToDimension(valid);
                }
            }
        } finally {
            isFlushing = false;
        }
    }
}
