package com.coobird.staticlogistics.logistics.group;

import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.api.group.GroupKey;
import com.coobird.staticlogistics.content.event.PlayerEvents;
import com.coobird.staticlogistics.logistics.node.*;
import com.coobird.staticlogistics.logistics.redstone.RedstoneControlStore;
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
            RedstoneControlStore.get(server).unbind(connection.key());
            refreshAuthorizedClients(actor);
            return true;
        } catch (RuntimeException exception) {
            LOGGER.error("Failed to delete logistics connection in group {}",
                requestedKey.groupKey().internalId(), exception);
            return false;
        }
    }

    /**
     * 从指定分组移除一个端点及其在该分组中的全部连接。
     *
     * <p>工具移除模式与界面删除共用同一事务：先修改互惠图，再统一识别真正失去全部连接的面；
     * 面过滤器优先返还玩家背包，且只有容器的全部关联面都被移除时才返还共享升级。</p>
     */
    public boolean deleteNodeFromGroup(ServerPlayer actor, GroupKey groupKey, LogisticsNode node) {
        ResolvedGroupNode resolved = resolveGroupNode(actor, groupKey, node);
        if (resolved == null || !tryAcquireMutation(actor)) return false;
        try {
            LinkedHashSet<LogisticsNode> candidates = new LinkedHashSet<>();
            candidates.add(node);
            candidates.addAll(resolved.config().getLinkedNodes(groupKey));
            removeTopologyAndOrphans(actor, candidates,
                () -> resolved.manager().removeNodeFromGroupWithoutCleanup(groupKey, node));
            refreshAuthorizedClients(actor);
            return true;
        } catch (RuntimeException exception) {
            LOGGER.error("Failed to remove logistics node from group {}",
                groupKey.internalId(), exception);
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
        removeTopologyAndOrphans(actor, List.of(key.first(), key.second()), () -> {
            connection.firstManager().removeLinkWithoutCleanup(
                key.groupKey(), key.first(), key.second());
            if (connection.firstConfig().getLinkedNodes(key.groupKey()).contains(key.second())
                || connection.secondConfig().getLinkedNodes(key.groupKey()).contains(key.first())) {
                throw new IllegalStateException("Connection remained after reciprocal removal");
            }
        });
    }

    private void removeTopologyAndOrphans(ServerPlayer actor,
                                          Collection<LogisticsNode> candidates,
                                          Runnable topologyMutation) {
        try (NodeMutationTransaction transaction = NodeMutationTransaction.begin(server)) {
            Map<LinkManager, List<LogisticsNode>> candidatesByManager = new LinkedHashMap<>();
            for (LogisticsNode candidate : new LinkedHashSet<>(candidates)) {
                ServerLevel level = server.getLevel(candidate.gPos().dimension());
                if (level == null) {
                    throw new IllegalStateException(
                        "Logistics node dimension is unavailable: "
                            + candidate.gPos().dimension().location());
                }
                transaction.capture(candidate);
                transaction.captureContainer(level, candidate.gPos().pos());
                candidatesByManager.computeIfAbsent(LinkManager.get(level), ignored -> new ArrayList<>())
                    .add(candidate);
            }

            topologyMutation.run();

            List<PreparedRemoval> removals = new ArrayList<>();
            List<NodeLifecycleService.UpgradeSource> sources = new ArrayList<>();
            candidatesByManager.forEach((manager, nodes) -> {
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
    private ResolvedGroupNode resolveGroupNode(ServerPlayer actor, GroupKey groupKey,
                                               LogisticsNode node) {
        if (actor == null || groupKey == null || node == null
            || !NodeInteractionValidator.holdsConfigurator(actor)
            || !NodeInteractionValidator.isDirectInteractionTargetValid(
            actor, node.gPos().pos(), node.face())
            || !PermissionService.getInstance().canModify(groupKey.ownerId(), actor)
            || PlayerGroupStore.get(server).findGroup(groupKey) == null) return null;
        ServerLevel level = server.getLevel(node.gPos().dimension());
        if (level == null) return null;
        LinkManager manager = LinkManager.get(level);
        FaceConfigComposite config = manager.getFaceConfig(FaceAddress.of(node));
        if (config == null || !config.canPlayerModify(actor)
            || !config.faceConfig.getGroupKeys().contains(groupKey)) return null;
        return new ResolvedGroupNode(manager, config);
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
        if (!NodeInteractionValidator.canMutateRemote(
            actor, firstLevel, key.first().gPos().pos())
            || !NodeInteractionValidator.canMutateRemote(
            actor, secondLevel, key.second().gPos().pos())) return null;
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
            key, firstManager, secondManager, firstConfig, secondConfig);
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

    private record ResolvedGroupNode(LinkManager manager, FaceConfigComposite config) {
    }

    private record RateKey(MinecraftServer server, UUID playerId) {
    }
}
