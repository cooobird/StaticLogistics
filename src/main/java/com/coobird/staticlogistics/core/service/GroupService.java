package com.coobird.staticlogistics.core.service;

import com.coobird.staticlogistics.compat.ftb.FTBTeamService;
import com.coobird.staticlogistics.core.manager.GlobalLogisticsManager;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.Set;
import java.util.UUID;

public class GroupService {
    private static final PermissionService permissionService = new PermissionService();
    private static final TeamSyncService teamSyncService = new TeamSyncService(new FTBTeamService());

    public static boolean canAccess(UUID owner, Player actor) {
        if (owner == null) return true;
        return permissionService.canAccess(owner, actor);
    }

    public static boolean canModify(UUID owner, Player actor) {
        if (owner == null) return true;
        return permissionService.canModify(owner, actor);
    }

    public static String getNextGroupId(String currentId, Set<String> existing) {
        if (currentId == null || currentId.isEmpty()) return "1";
        if (!existing.contains(currentId)) return currentId;

        if (currentId.matches("\\d+")) {
            int max = existing.stream()
                .filter(s -> s.matches("\\d+"))
                .mapToInt(Integer::parseInt)
                .max()
                .orElse(Integer.parseInt(currentId));
            return String.valueOf(max + 1);
        }

        String next = currentId + "_copy";
        int counter = 1;
        while (existing.contains(next)) {
            next = currentId + "_" + counter++;
        }
        return next;
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