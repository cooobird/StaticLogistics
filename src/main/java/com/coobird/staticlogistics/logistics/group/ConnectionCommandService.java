package com.coobird.staticlogistics.logistics.group;

import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.content.event.PlayerEvents;
import com.coobird.staticlogistics.logistics.node.*;
import com.mojang.logging.LogUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.*;

/**
 * 连接重命名与单连接删除的服务端权威用例。
 */
public final class ConnectionCommandService {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final long MUTATION_COOLDOWN_TICKS = 2L;
    private static final Map<RateKey, Long> LAST_MUTATION_TICK = new LinkedHashMap<>();

    private final MinecraftServer server;

    public ConnectionCommandService(MinecraftServer server) {
        if (server == null) throw new IllegalArgumentException("Connection command server is required");
        this.server = server;
    }

    /**
     * 服务器停止时释放限流状态，避免静态表长期持有旧服务器实例。
     */
    public static void release(MinecraftServer server) {
        if (server != null) {
            LAST_MUTATION_TICK.keySet().removeIf(key -> key.server() == server);
        }
    }

    public boolean rename(ServerPlayer actor, ConnectionKey requestedKey, String displayName) {
        ResolvedConnection connection = resolve(actor, requestedKey);
        if (connection == null || !tryAcquireMutation(actor)) return false;
        try {
            if (!PlayerGroupStore.get(server).setConnectionName(connection.key(), displayName)) {
                return false;
            }
            refreshAuthorizedClients(actor);
            return true;
        } catch (RuntimeException exception) {
            LOGGER.error("Failed to rename logistics connection in group {}",
                requestedKey.groupKey().internalId(), exception);
            return false;
        }
    }

    public boolean delete(ServerPlayer actor, ConnectionKey requestedKey) {
        ResolvedConnection connection = resolve(actor, requestedKey);
        if (connection == null || !tryAcquireMutation(actor)) return false;
        try {
            removeConnectionAndOrphans(actor, connection);
            refreshAuthorizedClients(actor);
            return true;
        } catch (RuntimeException exception) {
            LOGGER.error("Failed to delete logistics connection in group {}",
                requestedKey.groupKey().internalId(), exception);
            return false;
        }
    }

    /**
     * 验证连接仍然存在，并且玩家当前有权通过配置器查看它。
     */
    public boolean isSelectable(ServerPlayer actor, ConnectionKey requestedKey) {
        return resolve(actor, requestedKey) != null;
    }

    private void removeConnectionAndOrphans(
        ServerPlayer actor,
        ResolvedConnection connection
    ) {
        ConnectionKey key = connection.key();
        try (NodeMutationTransaction transaction = NodeMutationTransaction.begin(server)) {
            transaction.capture(key.first());
            transaction.capture(key.second());
            transaction.captureContainer(connection.firstLevel(), key.first().gPos().pos());
            transaction.captureContainer(connection.secondLevel(), key.second().gPos().pos());

            connection.firstManager().removeLinkWithoutCleanup(
                key.groupKey(), key.first(), key.second());
            if (connection.firstConfig().getLinkedNodes(key.groupKey()).contains(key.second())
                || connection.secondConfig().getLinkedNodes(key.groupKey()).contains(key.first())) {
                throw new IllegalStateException("Connection remained after reciprocal removal");
            }

            Map<LinkManager, List<LogisticsNode>> candidates = new LinkedHashMap<>();
            candidates.computeIfAbsent(connection.firstManager(), ignored -> new ArrayList<>())
                .add(key.first());
            candidates.computeIfAbsent(connection.secondManager(), ignored -> new ArrayList<>())
                .add(key.second());

            List<PreparedRemoval> removals = new ArrayList<>();
            List<NodeLifecycleService.UpgradeSource> sources = new ArrayList<>();
            candidates.forEach((manager, nodes) -> {
                NodeLifecycleService.DisconnectedRemoval removal =
                    manager.prepareDisconnectedRemoval(nodes);
                if (!removal.faces().isEmpty()) {
                    removals.add(new PreparedRemoval(manager, removal));
                    sources.addAll(removal.sources());
                }
            });

            PlayerUpgradeHandoff handoff = new PlayerUpgradeHandoff(actor);
            try (NodeLifecycleService.HandoffReceipt receipt = handoff.begin(sources)) {
                removals.forEach(removal ->
                    removal.manager().applyDisconnectedRemoval(removal.removal()));
                transaction.commit();
                receipt.commit();
            }
        }
    }

    @Nullable
    private ResolvedConnection resolve(ServerPlayer actor, ConnectionKey requestedKey) {
        if (actor == null || requestedKey == null
            || !NodeInteractionValidator.holdsConfigurator(actor)
            || !PermissionService.getInstance()
            .canModify(requestedKey.groupKey().ownerId(), actor)
            || PlayerGroupStore.get(server).findGroup(requestedKey.groupKey()) == null) {
            return null;
        }
        ConnectionKey key;
        try {
            key = new ConnectionKey(
                requestedKey.groupKey(), requestedKey.first(), requestedKey.second());
        } catch (IllegalArgumentException exception) {
            return null;
        }
        ServerLevel firstLevel = server.getLevel(key.first().gPos().dimension());
        ServerLevel secondLevel = server.getLevel(key.second().gPos().dimension());
        if (firstLevel == null || secondLevel == null) return null;
        LinkManager firstManager = LinkManager.get(firstLevel);
        LinkManager secondManager = LinkManager.get(secondLevel);
        FaceConfigComposite firstConfig = firstManager.getFaceConfig(FaceAddress.of(key.first()));
        FaceConfigComposite secondConfig = secondManager.getFaceConfig(FaceAddress.of(key.second()));
        if (firstConfig == null || secondConfig == null
            || !firstConfig.faceConfig.getGroupKeys().contains(key.groupKey())
            || !secondConfig.faceConfig.getGroupKeys().contains(key.groupKey())
            || !firstConfig.canPlayerModify(actor)
            || !secondConfig.canPlayerModify(actor)
            || !firstConfig.getLinkedNodes(key.groupKey()).contains(key.second())
            || !secondConfig.getLinkedNodes(key.groupKey()).contains(key.first())) {
            return null;
        }
        return new ResolvedConnection(
            key, firstLevel, secondLevel,
            firstManager, secondManager, firstConfig, secondConfig);
    }

    /**
     * 重命名和删除是低频全服命令，限制连续恶意数据包。
     */
    private boolean tryAcquireMutation(ServerPlayer actor) {
        long now = server.overworld().getGameTime();
        RateKey key = new RateKey(server, actor.getUUID());
        Long previous = LAST_MUTATION_TICK.get(key);
        if (previous != null && now - previous < MUTATION_COOLDOWN_TICKS) return false;
        LAST_MUTATION_TICK.put(key, now);
        if (LAST_MUTATION_TICK.size() > server.getMaxPlayers() * 8) {
            LAST_MUTATION_TICK.entrySet().removeIf(entry ->
                entry.getKey().server() == server && now - entry.getValue() > 200L);
        }
        return true;
    }

    private void refreshAuthorizedClients(ServerPlayer actor) {
        Set<UUID> recipients = new LinkedHashSet<>(
            PermissionService.getInstance().teamMembersOf(actor.getUUID()));
        recipients.add(actor.getUUID());
        for (UUID playerId : recipients) {
            ServerPlayer recipient = server.getPlayerList().getPlayer(playerId);
            if (recipient != null) PlayerEvents.refreshClientState(recipient);
        }
    }

    private record ResolvedConnection(
        ConnectionKey key,
        ServerLevel firstLevel,
        ServerLevel secondLevel,
        LinkManager firstManager,
        LinkManager secondManager,
        FaceConfigComposite firstConfig,
        FaceConfigComposite secondConfig
    ) {
    }

    private record PreparedRemoval(
        LinkManager manager,
        NodeLifecycleService.DisconnectedRemoval removal
    ) {
    }

    private record RateKey(MinecraftServer server, UUID playerId) {
    }
}
