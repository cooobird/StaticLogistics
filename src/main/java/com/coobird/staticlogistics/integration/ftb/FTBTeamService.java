package com.coobird.staticlogistics.integration.ftb;

import com.coobird.staticlogistics.integration.ModCompat;
import com.mojang.logging.LogUtils;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.TeamRank;
import org.slf4j.Logger;

import java.util.UUID;

public class FTBTeamService {
    private static final Logger LOGGER = LogUtils.getLogger();

    public boolean isFtbLoaded() {
        return ModCompat.isFtbTeamsLoaded();
    }

    public boolean checkFTBTeamAlliance(UUID owner, UUID actor) {
        try {
            var manager = FTBTeamsAPI.api().getManager();
            if (manager == null) return false;
            if (manager.arePlayersInSameTeam(owner, actor)) return true;
            return manager.getTeamForPlayerID(owner)
                .map(team -> team.getRankForPlayer(actor).getPower() >= TeamRank.ALLY.getPower())
                .orElse(false);
        } catch (Exception e) {
            LOGGER.warn("Failed to check FTB team alliance for owner {} and actor {}", owner, actor, e);
            return false;
        }
    }

    public boolean isTeamAdminOf(UUID owner, UUID actor) {
        try {
            var manager = FTBTeamsAPI.api().getManager();
            if (manager == null) return false;
            return manager.getTeamForPlayerID(owner)
                .map(team -> team.getRankForPlayer(actor).getPower() >= TeamRank.OFFICER.getPower())
                .orElse(false);
        } catch (Exception e) {
            LOGGER.warn("Failed to check FTB team admin status for owner {} and actor {}", owner, actor, e);
            return false;
        }
    }
}