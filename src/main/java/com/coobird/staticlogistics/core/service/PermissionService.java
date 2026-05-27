package com.coobird.staticlogistics.core.service;

import com.coobird.staticlogistics.integration.ftb.FTBTeamService;
import net.minecraft.world.entity.player.Player;

import java.util.UUID;

/**
 * 权限服务——单例。核心逻辑：自己全权访问；非自己的情况走 FTB 团队同盟/admin 判断。
 */
public class PermissionService {
    private static volatile PermissionService instance;
    private final FTBTeamService ftbTeamService;
    private final Object lock = new Object();

    private PermissionService() {
        this.ftbTeamService = new FTBTeamService();
    }

    // DCL 单例获取
    public static PermissionService getInstance() {
        if (instance == null) {
            synchronized (PermissionService.class) {
                if (instance == null) {
                    instance = new PermissionService();
                }
            }
        }
        return instance;
    }

    // 检查是否可以访问：自己是 owner 或同 FTB 团队/同盟
    public boolean canAccess(UUID owner, Player actor) {
        if (owner == null) return true;
        if (actor == null) return false;

        UUID actorId = actor.getUUID();
        if (owner.equals(actorId)) return true;

        return ftbTeamService.isFtbLoaded() && ftbTeamService.checkFTBTeamAlliance(owner, actorId);
    }

    // 检查是否可以修改：自己是 owner 或 FTB 团队管理员
    public boolean canModify(UUID owner, Player actor) {
        if (owner == null) return true;
        if (actor == null) return false;

        UUID actorId = actor.getUUID();
        if (owner.equals(actorId)) return true;

        return ftbTeamService.isFtbLoaded() && ftbTeamService.isTeamAdminOf(owner, actorId);
    }

    // 是否就是 owner 本人
    public boolean isOwner(UUID owner, Player actor) {
        if (owner == null || actor == null) return false;
        return owner.equals(actor.getUUID());
    }

    // 是否同 FTB 团队成员（含 owner 自身）
    public boolean isTeamMember(UUID owner, Player actor) {
        if (owner == null || actor == null) return false;
        if (owner.equals(actor.getUUID())) return true;

        return ftbTeamService.isFtbLoaded() && ftbTeamService.checkFTBTeamAlliance(owner, actor.getUUID());
    }

    // 是否 FTB 团队管理员（含 owner 自身）
    public boolean isTeamAdmin(UUID owner, Player actor) {
        if (owner == null || actor == null) return false;
        if (owner.equals(actor.getUUID())) return true;

        return ftbTeamService.isFtbLoaded() && ftbTeamService.isTeamAdminOf(owner, actor.getUUID());
    }

    // 重置单例（用于测试或热重载）
    public void reset() {
        synchronized (lock) {
            instance = null;
        }
    }
}