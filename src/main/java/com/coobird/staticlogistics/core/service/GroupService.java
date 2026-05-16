package com.coobird.staticlogistics.core.service;

import com.coobird.staticlogistics.core.manager.GlobalLogisticsManager;
import com.coobird.staticlogistics.integration.ftb.FTBTeamService;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.UUID;

public class GroupService {
    private static final PermissionService permissionService = PermissionService.getInstance();
    private static final TeamSyncService teamSyncService = new TeamSyncService(new FTBTeamService());

    public static boolean canAccess(UUID owner, Player actor) {
        if (owner == null) return true;
        return permissionService.canAccess(owner, actor);
    }

    public static boolean canModify(UUID owner, Player actor) {
        if (owner == null) return true;
        return permissionService.canModify(owner, actor);
    }

    public static String getNextGroupIdForPlayer(Player player) {
        GlobalLogisticsManager manager = GlobalLogisticsManager.get(player.getServer());
        return manager.getNextGroupIdForPlayer(player.getUUID());
    }

    public static void renameGroup(Level level, Player player, String oldId, String newId, GlobalLogisticsManager globalManager) {
        if (oldId == null || oldId.isEmpty() || oldId.equals(newId)) return;
        GroupRenameService renameService = new GroupRenameService(permissionService, globalManager);
        renameService.renameGroup(level, player, oldId, newId);
    }

    public static void syncToTeamMembers(ServerPlayer player, CustomPacketPayload payload) {
        teamSyncService.syncToTeamMembers(player, payload);
    }
}