package com.coobird.staticlogistics.logistics.group;

import com.coobird.staticlogistics.api.LogisticsNode;
import com.coobird.staticlogistics.api.group.GroupConstraints;
import com.coobird.staticlogistics.api.group.GroupKey;
import com.coobird.staticlogistics.api.group.GroupRef;
import com.coobird.staticlogistics.content.item.LinkConfiguratorSelection;
import com.coobird.staticlogistics.logistics.node.FaceConfigComposite;
import com.coobird.staticlogistics.logistics.node.LinkManager;
import com.coobird.staticlogistics.logistics.node.NodeMutationTransaction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 分组重命名及同一所有者下同名分组合并的事务入口。
 */
public class GroupRenameService {
    private final PermissionService permissionService;
    private final GlobalLogisticsManager globalManager;

    public GroupRenameService(PermissionService permissionService,
                              GlobalLogisticsManager globalManager) {
        this.permissionService = permissionService;
        this.globalManager = globalManager;
    }

    public boolean renameGroup(Level level, Player player, String oldName, String newName) {
        return renameGroup(level, player, player.getUUID(), oldName, newName, false);
    }

    public boolean renameGroup(Level level, Player player, UUID ownerId,
                               String oldName, String newName) {
        return renameGroup(level, player, ownerId, oldName, newName, false);
    }

    /**
     * 按稳定身份重命名，避免同名分组或重命名竞态选错目标。
     */
    public boolean renameGroup(Level level, Player player, GroupKey groupKey, String newName) {
        if (level == null || player == null || groupKey == null) return false;
        MinecraftServer server = level.getServer();
        if (server == null) return false;
        GroupRef current = PlayerGroupStore.get(server).findGroup(groupKey);
        if (current == null) return false;
        return renameGroup(level, player, groupKey.ownerId(),
            current.displayName(), newName, false);
    }

    public boolean renameGroupAsAdmin(Level level, Player player, UUID ownerId,
                                      String oldName, String newName) {
        if (!player.hasPermissions(2)) return false;
        return renameGroup(level, player, ownerId, oldName, newName, true);
    }

    private boolean renameGroup(Level level, Player player, UUID ownerId,
                                String oldName, String newName, boolean administrative) {
        if (level == null || player == null || ownerId == null
            || oldName == null || newName == null || oldName.equals(newName)) return false;
        String normalized;
        try {
            normalized = GroupConstraints.normalizeName(newName);
        } catch (IllegalArgumentException exception) {
            return false;
        }
        MinecraftServer server = level.getServer();
        if (server == null || (!administrative && !permissionService.canModify(ownerId, player))) {
            return false;
        }
        PlayerGroupStore store = PlayerGroupStore.get(server);
        GroupRef source = store.findGroup(ownerId, oldName);
        if (source == null || source.displayName().equals(normalized)) return false;
        GroupRef target = store.findGroup(ownerId, normalized);
        if (target != null && !target.key().equals(source.key())) {
            return mergeGroup(server, player, source, target, administrative);
        }
        return renameIdentity(server, player, source, normalized, administrative);
    }

    private boolean renameIdentity(MinecraftServer server, Player player, GroupRef source,
                                   String newDisplayName, boolean administrative) {
        PlayerGroupStore store = PlayerGroupStore.get(server);
        if (!store.canRenameGroup(source.key(), newDisplayName)) return false;
        List<LogisticsNode> affected = collectAffected(server, player, source.key(), administrative);
        if (affected == null) return false;

        try (NodeMutationTransaction transaction = NodeMutationTransaction.begin(server)) {
            transaction.captureAll(affected);
            transaction.onRollback(() -> globalManager.updateGroupDisplayName(
                source.key(), source.displayName()));
            transaction.onRollback(() -> {
                if (!store.renameGroup(source.key(), source.displayName())) {
                    throw new IllegalStateException("Group directory rollback failed during rename");
                }
            });
            for (LogisticsNode node : affected) {
                ServerLevel nodeLevel = requireLevel(server, node);
                LinkManager.get(nodeLevel).renameGroupMetadata(
                    node, source.key(), newDisplayName);
            }
            if (!store.renameGroup(source.key(), newDisplayName)) {
                throw new IllegalStateException("Group directory changed during rename");
            }
            globalManager.updateGroupDisplayName(source.key(), newDisplayName);
            transaction.commit();
        }
        globalManager.markGroupDirty(source.key());
        LinkConfiguratorSelection.replaceIfSelected(player, source.key(), source.displayName(),
            new GroupRef(source.key(), newDisplayName));
        return true;
    }

    /**
     * 仅合并同一所有者下的两个稳定分组身份。
     */
    private boolean mergeGroup(MinecraftServer server, Player player,
                               GroupRef source, GroupRef target, boolean administrative) {
        if (!source.key().ownerId().equals(target.key().ownerId())) return false;
        if (!administrative
            && !permissionService.canModify(source.key().ownerId(), player)) return false;
        List<LogisticsNode> affected = collectAffected(
            server, player, source.key(), administrative);
        if (affected == null) return false;
        PlayerGroupStore store = PlayerGroupStore.get(server);

        try (NodeMutationTransaction transaction = NodeMutationTransaction.begin(server)) {
            transaction.captureAll(affected);
            transaction.onRollback(() -> store.registerClaimedGroups(
                source.key().ownerId(), List.of(source)));
            for (LogisticsNode node : affected) {
                ServerLevel nodeLevel = requireLevel(server, node);
                LinkManager.get(nodeLevel).mergeGroupMetadata(node, source, target);
            }
            if (!store.removeGroup(source.key())) {
                throw new IllegalStateException("Source group directory changed during merge");
            }
            transaction.commit();
        }
        globalManager.updateGroupDisplayName(target.key(), target.displayName());
        globalManager.retireGroupIdentity(source.key());
        globalManager.markGroupDirty(target.key());
        LinkConfiguratorSelection.replaceIfSelected(
            player, source.key(), source.displayName(), target);
        return true;
    }

    private List<LogisticsNode> collectAffected(MinecraftServer server, Player player,
                                                GroupKey groupKey, boolean administrative) {
        List<LogisticsNode> result = new ArrayList<>();
        for (ServerLevel level : server.getAllLevels()) {
            LinkManager manager = LinkManager.get(level);
            for (var address : manager.getAllConfigKeys()) {
                FaceConfigComposite config = manager.getFaceConfig(address);
                if (config == null || !config.faceConfig.getGroupKeys().contains(groupKey)) continue;
                if (!groupKey.ownerId().equals(config.faceConfig.getOwner())
                    || (!administrative
                    && !permissionService.canModify(config.faceConfig.getOwner(), player))) return null;
                result.add(address.toNode(level.dimension()));
            }
        }
        return result;
    }

    private static ServerLevel requireLevel(MinecraftServer server, LogisticsNode node) {
        ServerLevel level = server.getLevel(node.gPos().dimension());
        if (level == null) {
            throw new IllegalStateException(
                "Group mutation dimension is unavailable: " + node.gPos().dimension().location());
        }
        return level;
    }
}
