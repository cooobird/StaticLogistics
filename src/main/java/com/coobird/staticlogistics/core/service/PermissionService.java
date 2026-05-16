package com.coobird.staticlogistics.core.service;

import com.coobird.staticlogistics.integration.ftb.FTBTeamService;
import net.minecraft.world.entity.player.Player;

import java.util.UUID;

public class PermissionService {
    private static volatile PermissionService instance;
    private final FTBTeamService ftbTeamService;
    private final Object lock = new Object();

    private PermissionService() {
        this.ftbTeamService = new FTBTeamService();
    }

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

    public boolean canAccess(UUID owner, Player actor) {
        if (owner == null) return true;
        if (actor == null) return false;

        UUID actorId = actor.getUUID();
        if (owner.equals(actorId)) return true;

        return ftbTeamService.isFtbLoaded() && ftbTeamService.checkFTBTeamAlliance(owner, actorId);
    }

    public boolean canModify(UUID owner, Player actor) {
        if (owner == null) return true;
        if (actor == null) return false;

        UUID actorId = actor.getUUID();
        if (owner.equals(actorId)) return true;

        return ftbTeamService.isFtbLoaded() && ftbTeamService.isTeamAdminOf(owner, actorId);
    }

    public boolean isOwner(UUID owner, Player actor) {
        if (owner == null || actor == null) return false;
        return owner.equals(actor.getUUID());
    }

    public boolean isTeamMember(UUID owner, Player actor) {
        if (owner == null || actor == null) return false;
        if (owner.equals(actor.getUUID())) return true;

        return ftbTeamService.isFtbLoaded() && ftbTeamService.checkFTBTeamAlliance(owner, actor.getUUID());
    }

    public boolean isTeamAdmin(UUID owner, Player actor) {
        if (owner == null || actor == null) return false;
        if (owner.equals(actor.getUUID())) return true;

        return ftbTeamService.isFtbLoaded() && ftbTeamService.isTeamAdminOf(owner, actor.getUUID());
    }

    public void reset() {
        synchronized (lock) {
            instance = null;
        }
    }
}