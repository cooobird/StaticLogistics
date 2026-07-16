package com.coobird.staticlogistics.logistics.group;

import com.coobird.staticlogistics.api.group.GroupConstraints;
import com.coobird.staticlogistics.api.group.GroupKey;
import com.coobird.staticlogistics.api.group.GroupRef;
import com.mojang.logging.LogUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 创建、重命名和删除分组的服务端命令入口。
 */
public final class GroupCommandService {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final long MUTATION_COOLDOWN_TICKS = 4L;
    private static final Map<RateKey, Long> LAST_MUTATION_TICK = new HashMap<>();

    private final MinecraftServer server;
    private final GlobalLogisticsManager globalManager;

    public GroupCommandService(MinecraftServer server) {
        this.server = server;
        this.globalManager = GlobalLogisticsManager.get(server);
    }

    public GroupRef create(ServerPlayer actor, String displayName) {
        if (actor == null) throw new IllegalArgumentException("Group actor is required");
        String normalized = GroupConstraints.normalizeName(displayName);
        return PlayerGroupStore.get(server).createGroup(actor.getUUID(), normalized);
    }

    public boolean rename(ServerPlayer actor, GroupKey groupKey, String newDisplayName) {
        if (actor == null || groupKey == null || !tryAcquireMutation(actor)) return false;
        try {
            return new GroupRenameService(PermissionService.getInstance(), globalManager)
                .renameGroup(actor.level(), actor, groupKey, newDisplayName);
        } catch (RuntimeException exception) {
            LOGGER.error("Failed to rename logistics group {} for owner {}",
                groupKey.internalId(), groupKey.ownerId(), exception);
            return false;
        }
    }

    public boolean delete(ServerPlayer actor, GroupKey groupKey) {
        if (actor == null || groupKey == null || !tryAcquireMutation(actor)) return false;
        try {
            return globalManager.removeGroup(actor, groupKey);
        } catch (RuntimeException exception) {
            LOGGER.error("Failed to delete logistics group {} for owner {}",
                groupKey.internalId(), groupKey.ownerId(), exception);
            return false;
        }
    }

    /**
     * 限制昂贵的全图分组修改，避免连续数据包占满服务器主线程。
     */
    private boolean tryAcquireMutation(ServerPlayer actor) {
        long now = server.overworld().getGameTime();
        RateKey key = new RateKey(server, actor.getUUID());
        Long previous = LAST_MUTATION_TICK.get(key);
        if (previous != null && now - previous < MUTATION_COOLDOWN_TICKS) return false;
        LAST_MUTATION_TICK.put(key, now);
        if (LAST_MUTATION_TICK.size() > server.getMaxPlayers() * 4) {
            LAST_MUTATION_TICK.entrySet().removeIf(entry ->
                entry.getKey().server() == server && now - entry.getValue() > 200L);
        }
        return true;
    }

    private record RateKey(MinecraftServer server, UUID playerId) {
    }
}
