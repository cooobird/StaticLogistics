package com.coobird.staticlogistics.logistics.node;

import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.api.group.GroupRef;
import com.coobird.staticlogistics.logistics.group.PlayerGroupStore;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * 验证物理目标并解析或认领节点配置的打开用例。
 */
public final class NodeOpenService {
    @Nullable
    public FaceConfigComposite resolve(ServerPlayer player, BlockPos pos, Direction face) {
        if (!NodeInteractionValidator.holdsConfigurator(player)
            || !NodeInteractionValidator.isDirectInteractionTargetValid(player, pos, face)) return null;
        LinkManager manager = LinkManager.get(player.serverLevel());
        FaceAddress address = FaceAddress.of(pos, face);
        FaceConfigComposite config = manager.getFaceConfig(address);
        if (config == null) {
            LogisticsNode node = manager.createNodeFromKey(address);
            try (NodeMutationTransaction transaction = NodeMutationTransaction.begin(player.server)) {
                transaction.captureState(node);
                config = manager.getOrCreateFaceConfig(pos, face);
                manager.claimOwner(node, player.getGameProfile());
                transaction.commit();
            }
            return config;
        }
        if (config.faceConfig.getOwner() == null
            && !claimConnectedComponent(player, manager.createNodeFromKey(address))) return null;
        return config.canPlayerModify(player) ? config : null;
    }

    /**
     * 将相连的旧版无所有者节点作为整体认领，避免同一链接出现两个所有者。
     */
    private boolean claimConnectedComponent(ServerPlayer actor, LogisticsNode start) {
        Map<LogisticsNode, FaceConfigComposite> unowned = new LinkedHashMap<>();
        Set<LogisticsNode> visited = new LinkedHashSet<>();
        ArrayDeque<LogisticsNode> pending = new ArrayDeque<>();
        pending.add(start);
        while (!pending.isEmpty()) {
            LogisticsNode node = pending.removeFirst();
            if (!visited.add(node)) continue;
            FaceConfigComposite config = resolveConfig(actor, node);
            if (config == null) continue;
            if (config.faceConfig.getOwner() == null) {
                config.validateOwnershipClaim();
                unowned.put(node, config);
            } else if (!actor.getUUID().equals(config.faceConfig.getOwner())) {
                return false;
            }
            config.getLinkedNodes().forEach(pending::addLast);
        }
        if (unowned.isEmpty()) return true;

        Set<GroupRef> claimedGroups = new LinkedHashSet<>();
        for (FaceConfigComposite config : unowned.values()) {
            config.faceConfig.getGroups().forEach(group -> claimedGroups.add(
                new GroupRef(group.key().withOwner(actor.getUUID()), group.displayName())));
        }
        PlayerGroupStore store = PlayerGroupStore.get(actor.server);
        store.validateClaimedGroups(actor.getUUID(), claimedGroups);
        try (NodeMutationTransaction transaction = NodeMutationTransaction.begin(actor.server)) {
            transaction.captureAll(unowned.keySet());
            for (LogisticsNode node : unowned.keySet()) {
                ServerLevel level = actor.server.getLevel(node.gPos().dimension());
                if (level == null) {
                    throw new IllegalStateException("Ownership claim dimension became unavailable");
                }
                LinkManager.get(level).claimOwner(node, actor.getGameProfile());
            }
            store.registerClaimedGroups(actor.getUUID(), claimedGroups);
            transaction.commit();
        }
        return true;
    }

    @Nullable
    private FaceConfigComposite resolveConfig(ServerPlayer actor, LogisticsNode node) {
        ServerLevel level = actor.server.getLevel(node.gPos().dimension());
        return level == null ? null : LinkManager.get(level).getFaceConfig(FaceAddress.of(node));
    }
}
