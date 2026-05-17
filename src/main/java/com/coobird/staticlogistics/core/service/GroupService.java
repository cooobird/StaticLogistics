package com.coobird.staticlogistics.core.service;

import com.coobird.staticlogistics.core.manager.GlobalLogisticsManager;
import com.coobird.staticlogistics.integration.ftb.FTBTeamService;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.UUID;

/**
 * 组操作的静态门面——权限检查、获取下一个组 ID、组重命名、团队同步等入口。
 */
public class GroupService {
    private static final PermissionService permissionService = PermissionService.getInstance();
    private static final TeamSyncService teamSyncService = new TeamSyncService(new FTBTeamService());

    // 检查玩家是否有权访问 owner 的资源
    public static boolean canAccess(UUID owner, Player actor) {
        if (owner == null) return true;
        return permissionService.canAccess(owner, actor);
    }

    // 检查玩家是否有权修改 owner 的配置
    public static boolean canModify(UUID owner, Player actor) {
        if (owner == null) return true;
        return permissionService.canModify(owner, actor);
    }

    // 为玩家获取一个可用的下一个数字组 ID
    public static String getNextGroupIdForPlayer(Player player) {
        GlobalLogisticsManager manager = GlobalLogisticsManager.get(player.getServer());
        return manager.getNextGroupIdForPlayer(player.getUUID());
    }

    // 重命名组（门面，委托给 GroupRenameService）
    public static void renameGroup(Level level, Player player, String oldId, String newId, GlobalLogisticsManager globalManager) {
        if (oldId == null || oldId.isEmpty() || oldId.equals(newId)) return;
        GroupRenameService renameService = new GroupRenameService(permissionService, globalManager);
        renameService.renameGroup(level, player, oldId, newId);
    }

    // 将数据包同步给玩家的 FTB 团队成员
    public static void syncToTeamMembers(ServerPlayer player, CustomPacketPayload payload) {
        teamSyncService.syncToTeamMembers(player, payload);
    }
}