package com.coobird.staticlogistics.logistics.group;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.UUID;

/**
 * 组操作的静态门面——权限检查、获取下一个组 ID、组重命名、团队同步等入口。
 */
public final class GroupService {
    private static final PermissionService PERMISSION_SERVICE = PermissionService.getInstance();

    private GroupService() {
    }

    public static boolean canAccess(UUID owner, Player actor) {
        if (owner == null) return false;
        return PERMISSION_SERVICE.canAccess(owner, actor);
    }

    public static boolean canModify(UUID owner, Player actor) {
        if (owner == null) return false;
        return PERMISSION_SERVICE.canModify(owner, actor);
    }

    public static String getNextGroupIdForPlayer(Player player) {
        GlobalLogisticsManager manager = GlobalLogisticsManager.get(player.getServer());
        return manager.getNextGroupIdForPlayer(player.getUUID());
    }

    public static boolean renameGroup(Level level, Player player, String oldId, String newId, GlobalLogisticsManager globalManager) {
        return renameGroup(level, player, player.getUUID(), oldId, newId, globalManager);
    }

    public static boolean renameGroup(
        Level level, Player player, UUID ownerId, String oldId, String newId, GlobalLogisticsManager globalManager
    ) {
        if (oldId == null || oldId.isEmpty() || oldId.equals(newId)) return false;
        GroupRenameService renameService = new GroupRenameService(PERMISSION_SERVICE, globalManager);
        return renameService.renameGroup(level, player, ownerId, oldId, newId);
    }

    /**
     * 仅供权限等级已由命令边界验证的管理员重命名任意所有者分组。
     */
    public static boolean renameGroupAsAdmin(
        Level level, Player player, UUID ownerId, String oldId, String newId, GlobalLogisticsManager globalManager
    ) {
        if (oldId == null || oldId.isEmpty() || oldId.equals(newId)) return false;
        GroupRenameService renameService = new GroupRenameService(PERMISSION_SERVICE, globalManager);
        return renameService.renameGroupAsAdmin(level, player, ownerId, oldId, newId);
    }
}
