package com.coobird.staticlogistics.core.service;

import com.coobird.staticlogistics.compat.ftb.FTBTeamService;
import net.minecraft.world.entity.player.Player;

import java.util.UUID;

public class PermissionService {
    private final FTBTeamService ftbTeamService;

    public PermissionService() {
        this.ftbTeamService = new FTBTeamService();
    }

    public boolean canAccess(UUID owner, Player actor) {
        if (owner == null) return true;
        UUID actorId = actor.getUUID();
        if (owner.equals(actorId)) return true;

        return ftbTeamService.isFtbLoaded() && ftbTeamService.checkFTBTeamAlliance(owner, actorId);
    }

    public boolean canModify(UUID owner, Player actor) {
        if (owner == null) return true;
        UUID actorId = actor.getUUID();
        if (owner.equals(actorId)) return true;

        return ftbTeamService.isFtbLoaded() && ftbTeamService.isTeamAdminOf(owner, actorId);
    }
}