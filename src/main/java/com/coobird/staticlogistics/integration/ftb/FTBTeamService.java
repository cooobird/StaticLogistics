package com.coobird.staticlogistics.integration.ftb;

import com.coobird.staticlogistics.integration.ModCompat;
import com.coobird.staticlogistics.api.group.TeamAccessPolicy;
import com.coobird.staticlogistics.api.group.TeamMemberProvider;
import com.mojang.logging.LogUtils;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.TeamRank;
import org.slf4j.Logger;

import java.util.UUID;
import java.util.LinkedHashSet;
import java.util.Set;

public class FTBTeamService implements TeamAccessPolicy, TeamMemberProvider {
    private static final Logger LOGGER = LogUtils.getLogger();

    public boolean isFtbLoaded() {
        return ModCompat.isFtbTeamsLoaded();
    }

    @Override
    public boolean canAccess(UUID owner, UUID actor) {
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

    @Override
    public boolean canModify(UUID owner, UUID actor) {
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

    @Override
    public Set<UUID> membersOf(UUID playerId) {
        try {
            var manager = FTBTeamsAPI.api().getManager();
            if (manager == null) return Set.of();
            return manager.getTeamForPlayerID(playerId)
                .map(team -> Set.copyOf(new LinkedHashSet<>(team.getMembers())))
                .orElse(Set.of());
        } catch (RuntimeException exception) {
            LOGGER.warn("Failed to resolve FTB team members for player {}", playerId, exception);
            return Set.of();
        }
    }
}
