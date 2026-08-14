package com.coobird.staticlogistics.logistics.group;


import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.UUID;

/**
 * 组操作的静态门面——权限检查、获取下一个组 ID、组重命名、团队同步等入口。
 */
public class GroupService {
    private static final PermissionService permissionService = PermissionService.getInstance();

    // 检查玩家是否有权访问 owner 的资源
    public static boolean canAccess(UUID owner, Player actor) {
        if (owner == null) return false;
        return permissionService.canAccess(owner, actor);
    }

    // 检查玩家是否有权修改 owner 的配置
    public static boolean canModify(UUID owner, Player actor) {
        if (owner == null) return false;
        return permissionService.canModify(owner, actor);
    }

    // 重命名组（门面，委托给 GroupRenameService）
    public static boolean renameGroup(Level level, Player player, String oldId, String newId, GlobalLogisticsManager globalManager) {
        return renameGroup(level, player, player.getUUID(), oldId, newId, globalManager);
    }

    public static boolean renameGroup(Level level, Player player, UUID ownerId,
                                      String oldId, String newId,
                                      GlobalLogisticsManager globalManager) {
        if (oldId == null || oldId.isEmpty() || oldId.equals(newId)) return false;
        GroupRenameService renameService = new GroupRenameService(permissionService, globalManager);
        return renameService.renameGroup(level, player, ownerId, oldId, newId);
    }

    public static boolean renameGroupAsAdmin(Level level, Player player, UUID ownerId,
                                             String oldId, String newId,
                                             GlobalLogisticsManager globalManager) {
        if (oldId == null || oldId.isEmpty() || oldId.equals(newId)) return false;
        GroupRenameService renameService = new GroupRenameService(permissionService, globalManager);
        return renameService.renameGroupAsAdmin(level, player, ownerId, oldId, newId);
    }
}
